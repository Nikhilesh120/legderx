package com.ledgerxlite.service;

import com.ledgerxlite.domain.LedgerEntry;
import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.repository.LedgerEntryRepository;
import com.ledgerxlite.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service for financial transaction operations.
 * 
 * CRITICAL: All methods that modify wallet balances MUST:
 * 1. Check idempotency (existsByReferenceId)
 * 2. Lock wallet (findByIdForUpdate)
 * 3. Create LedgerEntry FIRST
 * 4. Update Wallet balance SECOND
 * 5. Use @Transactional for ACID compliance
 */
@Service
@Transactional
public class TransactionService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionService(
            WalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Deposit funds into a wallet.
     * 
     * Idempotent: Safe to retry with same referenceId.
     * 
     * INVARIANTS ENFORCED:
     * - INVARIANT-2: Idempotency (unique referenceId)
     * - INVARIANT-4: Pessimistic locking before balance mutation
     * - INVARIANT-5: Positive transaction amounts
     * - INVARIANT-1: Ledger entry created before balance update
     * - POST: Balance increased by exactly the amount deposited
     * 
     * @param walletId wallet to credit
     * @param amount amount to deposit (must be positive)
     * @param referenceId unique transaction ID (for idempotency)
     * @param description optional description
     * @return created ledger entry
     * @throws IllegalArgumentException if amount is not positive or wallet not found
     * @throws IllegalStateException if invariant violated
     * @throws org.springframework.dao.DataIntegrityViolationException if referenceId already exists (concurrent insert)
     */
    public LedgerEntry deposit(Long walletId, BigDecimal amount, String referenceId, String description) {
        // INVARIANT-5: Positive amounts only
        validatePositiveAmount(amount);

        // INVARIANT-2: referenceId must be present
        validateReferenceId(referenceId);

        // INVARIANT-2: Idempotency check - return existing entry if already processed
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByReferenceId(referenceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // INVARIANT-4: Lock wallet for update (PESSIMISTIC_WRITE)
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        
        // INVARIANT-WALLET-ACTIVE: Only active users can perform transactions
        validateWalletUserActive(wallet);
        
        // Capture balance before mutation for verification
        BigDecimal balanceBefore = wallet.getBalance();
        
        // INVARIANT-1: Create ledger entry FIRST (source of truth)
        // Database unique constraint on referenceId provides final safety net
        LedgerEntry entry = new LedgerEntry(
            wallet,
            amount,  // Positive for credit
            LedgerEntry.EntryType.DEPOSIT,
            referenceId,
            description
        );
        entry = ledgerEntryRepository.save(entry);
        
        // INVARIANT-1: Update wallet balance SECOND (derived from ledger)
        wallet.credit(amount);
        walletRepository.save(wallet);
        
        // POST-CONDITION: Verify balance increased by exactly the deposited amount
        BigDecimal balanceAfter = wallet.getBalance();
        BigDecimal expectedBalance = balanceBefore.add(amount);
        if (balanceAfter.compareTo(expectedBalance) != 0) {
            throw new IllegalStateException(
                String.format("INVARIANT-1 VIOLATION: Balance change mismatch. " +
                    "Expected balance: %s, Actual balance: %s, Deposit amount: %s",
                    expectedBalance, balanceAfter, amount)
            );
        }
        
        return entry;
    }

    /**
     * Withdraw funds from a wallet.
     * 
     * Idempotent: Safe to retry with same referenceId.
     * 
     * INVARIANTS ENFORCED:
     * - INVARIANT-2: Idempotency (unique referenceId)
     * - INVARIANT-4: Pessimistic locking before balance mutation
     * - INVARIANT-5: Positive transaction amounts
     * - INVARIANT-10: No negative balance (checked in Wallet.debit())
     * - INVARIANT-1: Ledger entry created before balance update
     * - POST: Balance decreased by exactly the withdrawn amount
     * 
     * @param walletId wallet to debit
     * @param amount amount to withdraw (must be positive)
     * @param referenceId unique transaction ID
     * @param description optional description
     * @return created ledger entry
     * @throws IllegalArgumentException if amount invalid or wallet not found
     * @throws IllegalStateException if insufficient balance or invariant violated
     */
    public LedgerEntry withdraw(Long walletId, BigDecimal amount, String referenceId, String description) {
        // INVARIANT-5: Positive amounts only
        validatePositiveAmount(amount);

        // INVARIANT-2: referenceId must be present
        validateReferenceId(referenceId);

        // INVARIANT-2: Idempotency check
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByReferenceId(referenceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // INVARIANT-4: Lock wallet for update (PESSIMISTIC_WRITE)
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        
        // INVARIANT-WALLET-ACTIVE: Only active users can perform transactions
        validateWalletUserActive(wallet);
        
        // Capture balance before mutation for verification
        BigDecimal balanceBefore = wallet.getBalance();
        
        // INVARIANT-1: Create ledger entry FIRST (negative for debit)
        LedgerEntry entry = new LedgerEntry(
            wallet,
            amount.negate(),  // Negative for debit
            LedgerEntry.EntryType.WITHDRAWAL,
            referenceId,
            description
        );
        entry = ledgerEntryRepository.save(entry);
        
        // INVARIANT-1 & INVARIANT-10: Update wallet balance SECOND
        // Wallet.debit() will throw IllegalStateException if insufficient balance
        wallet.debit(amount);
        walletRepository.save(wallet);
        
        // POST-CONDITION: Verify balance decreased by exactly the withdrawn amount
        BigDecimal balanceAfter = wallet.getBalance();
        BigDecimal expectedBalance = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(expectedBalance) != 0) {
            throw new IllegalStateException(
                String.format("INVARIANT-1 VIOLATION: Balance change mismatch. " +
                    "Expected balance: %s, Actual balance: %s, Withdrawal amount: %s",
                    expectedBalance, balanceAfter, amount)
            );
        }
        
        // POST-CONDITION: INVARIANT-10 - Verify no negative balance
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                String.format("INVARIANT-10 VIOLATION: Negative balance detected. " +
                    "Wallet %d balance: %s (should never be negative)",
                    walletId, balanceAfter)
            );
        }
        
        return entry;
    }

    /**
     * Transfer funds between two wallets.
     * 
     * Atomic: Both debit and credit happen or neither happens.
     * Idempotent: Safe to retry with same referenceId.
     * 
     * INVARIANTS ENFORCED:
     * - INVARIANT-2: Idempotency (unique referenceId for both entries)
     * - INVARIANT-4: Pessimistic locking on both wallets
     * - INVARIANT-5: Positive transaction amounts
     * - INVARIANT-8: Transfer atomicity (both entries or neither)
     * - INVARIANT-9: Deadlock-free lock ordering (ascending wallet IDs)
     * - INVARIANT-10: No negative balance in source wallet
     * - POST: Money conservation (total delta = 0)
     * - POST: Both balances changed by exact amounts
     * 
     * @param fromWalletId source wallet
     * @param toWalletId destination wallet
     * @param amount amount to transfer (must be positive)
     * @param referenceId unique transaction ID
     * @param description optional description
     * @return array of [debit entry, credit entry]
     * @throws IllegalArgumentException if amount invalid or wallets not found
     * @throws IllegalStateException if insufficient balance or invariant violated
     */
    public LedgerEntry[] transfer(
            Long fromWalletId, 
            Long toWalletId, 
            BigDecimal amount, 
            String referenceId,
            String description) {
        
        // INVARIANT-5: Positive amounts only
        validatePositiveAmount(amount);

        // INVARIANT-DISTINCT-WALLETS: Cannot transfer to same wallet
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException(
                "INVARIANT-DISTINCT-WALLETS VIOLATION: Cannot transfer to same wallet");
        }

        // INVARIANT-2: referenceId must be present
        validateReferenceId(referenceId);

        // INVARIANT-2: Idempotency check - look for debit entry
        String debitRefId = referenceId + "-OUT";
        String creditRefId = referenceId + "-IN";
        
        Optional<LedgerEntry> existingDebit = ledgerEntryRepository.findByReferenceId(debitRefId);
        if (existingDebit.isPresent()) {
            // Transfer already processed, return both entries
            LedgerEntry creditEntry = ledgerEntryRepository.findByReferenceId(creditRefId)
                .orElseThrow(() -> new IllegalStateException(
                    "INVARIANT-8 VIOLATION: Incomplete transfer detected - debit exists but credit missing"));
            return new LedgerEntry[]{existingDebit.get(), creditEntry};
        }
        
        // INVARIANT-9: Lock both wallets in ascending ID order (deadlock prevention)
        Long firstLock = Math.min(fromWalletId, toWalletId);
        Long secondLock = Math.max(fromWalletId, toWalletId);
        
        Wallet first = walletRepository.findByIdForUpdate(firstLock)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + firstLock));
        Wallet second = walletRepository.findByIdForUpdate(secondLock)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + secondLock));
        
        // Identify source and destination after locking
        Wallet fromWallet = fromWalletId.equals(first.getId()) ? first : second;
        Wallet toWallet = toWalletId.equals(first.getId()) ? first : second;

        // INVARIANT-CURRENCY-MATCH: Both wallets must use the same currency
        if (!fromWallet.getCurrency().equals(toWallet.getCurrency())) {
            throw new IllegalArgumentException(
                String.format("INVARIANT-CURRENCY-MATCH VIOLATION: Cannot transfer between different currencies. " +
                    "From: %s, To: %s", fromWallet.getCurrency(), toWallet.getCurrency()));
        }
        
        // INVARIANT-WALLET-ACTIVE: Both users must be active to perform transfer
        validateWalletUserActive(fromWallet);
        validateWalletUserActive(toWallet);
        
        // Capture balances before mutation for verification
        BigDecimal fromBalanceBefore = fromWallet.getBalance();
        BigDecimal toBalanceBefore = toWallet.getBalance();
        
        // INVARIANT-1: Create BOTH ledger entries FIRST
        LedgerEntry debitEntry = new LedgerEntry(
            fromWallet,
            amount.negate(),
            LedgerEntry.EntryType.TRANSFER_OUT,
            debitRefId,
            description
        );
        debitEntry = ledgerEntryRepository.save(debitEntry);
        
        LedgerEntry creditEntry = new LedgerEntry(
            toWallet,
            amount,
            LedgerEntry.EntryType.TRANSFER_IN,
            creditRefId,
            description
        );
        creditEntry = ledgerEntryRepository.save(creditEntry);
        
        // INVARIANT-1 & INVARIANT-8: Update both wallet balances SECOND
        fromWallet.debit(amount);
        toWallet.credit(amount);
        
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
        
        // POST-CONDITION: Verify both balances changed correctly
        BigDecimal fromBalanceAfter = fromWallet.getBalance();
        BigDecimal toBalanceAfter = toWallet.getBalance();
        
        BigDecimal fromExpected = fromBalanceBefore.subtract(amount);
        BigDecimal toExpected = toBalanceBefore.add(amount);
        
        if (fromBalanceAfter.compareTo(fromExpected) != 0) {
            throw new IllegalStateException(
                String.format("INVARIANT-1 VIOLATION: Source wallet balance mismatch. " +
                    "Expected: %s, Actual: %s", fromExpected, fromBalanceAfter)
            );
        }
        
        if (toBalanceAfter.compareTo(toExpected) != 0) {
            throw new IllegalStateException(
                String.format("INVARIANT-1 VIOLATION: Destination wallet balance mismatch. " +
                    "Expected: %s, Actual: %s", toExpected, toBalanceAfter)
            );
        }
        
        // POST-CONDITION: INVARIANT-8 - Verify money conservation (total delta = 0)
        BigDecimal totalDelta = fromBalanceAfter.subtract(fromBalanceBefore)
                                .add(toBalanceAfter.subtract(toBalanceBefore));
        
        if (totalDelta.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException(
                String.format("INVARIANT-8 VIOLATION: Money not conserved in transfer. " +
                    "Total delta: %s (should be 0). From: %s→%s, To: %s→%s",
                    totalDelta, fromBalanceBefore, fromBalanceAfter, toBalanceBefore, toBalanceAfter)
            );
        }
        
        return new LedgerEntry[]{debitEntry, creditEntry};
    }

    /**
     * Get transaction history for a wallet.
     * 
     * @param walletId wallet's ID
     * @return list of transactions in chronological order
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> getTransactionHistory(Long walletId) {
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    /**
     * Get a specific transaction by reference ID.
     * 
     * @param referenceId transaction reference ID
     * @return ledger entry if found
     */
    @Transactional(readOnly = true)
    public Optional<LedgerEntry> findByReferenceId(String referenceId) {
        return ledgerEntryRepository.findByReferenceId(referenceId);
    }

    /**
     * Check if a transaction has already been processed.
     * 
     * @param referenceId transaction reference ID
     * @return true if transaction exists
     */
    @Transactional(readOnly = true)
    public boolean transactionExists(String referenceId) {
        return ledgerEntryRepository.existsByReferenceId(referenceId);
    }

    /**
     * Validate referenceId is present and non-blank.
     *
     * INVARIANT-2: Every transaction must carry a unique referenceId for idempotency.
     */
    private void validateReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException(
                String.format("INVARIANT-2 VIOLATION: referenceId is required and must not be blank. Got: %s",
                    referenceId)
            );
        }
    }

    /**
     * Validate amount is positive.
     * 
     * INVARIANT-5: All transaction amounts must be positive.
     * Negative amounts are handled internally (e.g., amount.negate() for debits).
     * 
     * @param amount amount to validate
     * @throws IllegalArgumentException if amount is not positive
     */
    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                String.format("INVARIANT-5 VIOLATION: Transaction amount must be positive. Got: %s", amount)
            );
        }
    }

    /**
     * Validate that wallet's user is active.
     * 
     * INVARIANT-WALLET-ACTIVE: Only active users can perform financial operations.
     * Suspended and closed accounts are blocked from all transactions.
     * This prevents unauthorized or fraudulent activity on inactive accounts.
     * 
     * @param wallet wallet to check
     * @throws IllegalStateException if user is not active
     */
    private void validateWalletUserActive(Wallet wallet) {
        User.UserStatus status = wallet.getUser().getStatus();
        if (status != User.UserStatus.ACTIVE) {
            throw new IllegalStateException(
                String.format("INVARIANT-WALLET-ACTIVE VIOLATION: " +
                    "User account is %s and cannot perform financial operations. " +
                    "Wallet ID: %d, User ID: %d",
                    status, wallet.getId(), wallet.getUser().getId())
            );
        }
    }
}
