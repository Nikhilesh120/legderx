package com.ledgerxlite.service;

import com.ledgerxlite.domain.LedgerEntry;
import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.repository.LedgerEntryRepository;
import com.ledgerxlite.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService.
 * All financial invariants are exercised here.
 * Uses Mockito — no database, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock WalletRepository       walletRepository;
    @Mock LedgerEntryRepository  ledgerEntryRepository;

    @InjectMocks TransactionService service;

    // ── shared helpers ────────────────────────────────────────────────────────

    private User   activeUser;
    private User   suspendedUser;
    private Wallet activeWallet;
    private Wallet suspendedWallet;

    @BeforeEach
    void setUp() throws Exception {
        activeUser    = makeUser(1L, User.UserStatus.ACTIVE);
        suspendedUser = makeUser(2L, User.UserStatus.SUSPENDED);
        activeWallet  = makeWallet(10L, activeUser,    "USD", new BigDecimal("1000.00"));
        suspendedWallet = makeWallet(20L, suspendedUser, "USD", new BigDecimal("500.00"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User makeUser(Long id, User.UserStatus status) throws Exception {
        var user = new User("user" + id + "@test.com", "hash");
        setId(user, id);
        if (status == User.UserStatus.SUSPENDED) user.suspend();
        if (status == User.UserStatus.CLOSED)    user.close();
        return user;
    }

    private Wallet makeWallet(Long id, User user, String currency, BigDecimal balance)
            throws Exception {
        var wallet = new Wallet(user, currency);
        setId(wallet, id);
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            wallet.credit(balance);
        }
        return wallet;
    }

    private void setId(Object entity, Long id) throws Exception {
        var f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    private LedgerEntry mockEntry(Wallet wallet, BigDecimal amount,
                                   LedgerEntry.EntryType type, String refId) {
        var e = new LedgerEntry(wallet, amount, type, refId, null);
        return e;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DEPOSIT
    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("deposit()")
    class DepositTests {

        @Test @DisplayName("null amount → INVARIANT-5")
        void nullAmount() {
            assertThatThrownBy(() -> service.deposit(10L, null, "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-5");
        }

        @Test @DisplayName("zero amount → INVARIANT-5")
        void zeroAmount() {
            assertThatThrownBy(() -> service.deposit(10L, BigDecimal.ZERO, "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-5");
        }

        @Test @DisplayName("negative amount → INVARIANT-5")
        void negativeAmount() {
            assertThatThrownBy(() -> service.deposit(10L, new BigDecimal("-1"), "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-5");
        }

        @Test @DisplayName("null referenceId → INVARIANT-2")
        void nullReferenceId() {
            assertThatThrownBy(() -> service.deposit(10L, BigDecimal.TEN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-2");
        }

        @Test @DisplayName("blank referenceId → INVARIANT-2")
        void blankReferenceId() {
            assertThatThrownBy(() -> service.deposit(10L, BigDecimal.TEN, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-2");
        }

        @Test @DisplayName("wallet not found → exception")
        void walletNotFound() {
            when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deposit(99L, BigDecimal.TEN, "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found");
        }

        @Test @DisplayName("duplicate referenceId → returns existing entry (idempotent)")
        void idempotentDeposit() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            var existing = mockEntry(activeWallet, BigDecimal.TEN, LedgerEntry.EntryType.DEPOSIT, "REF-DUP");
            when(ledgerEntryRepository.findByReferenceId("REF-DUP")).thenReturn(Optional.of(existing));

            LedgerEntry result = service.deposit(10L, BigDecimal.TEN, "REF-DUP", null);

            assertThat(result).isSameAs(existing);
            verify(ledgerEntryRepository, never()).save(any());
            verify(walletRepository,      never()).save(any());
        }

        @Test @DisplayName("suspended user → INVARIANT-WALLET-ACTIVE")
        void suspendedUser() {
            when(walletRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(suspendedWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deposit(20L, BigDecimal.TEN, "REF-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVARIANT-WALLET-ACTIVE");
        }

        @Test @DisplayName("valid deposit → ledger written first, balance updated, amount exact")
        void successfulDeposit() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(ledgerEntryRepository.findByReferenceId("REF-OK")).thenReturn(Optional.empty());
            var savedEntry = mockEntry(activeWallet, BigDecimal.TEN, LedgerEntry.EntryType.DEPOSIT, "REF-OK");
            when(ledgerEntryRepository.save(any())).thenReturn(savedEntry);
            when(walletRepository.save(any())).thenReturn(activeWallet);

            BigDecimal before = activeWallet.getBalance();
            LedgerEntry result = service.deposit(10L, BigDecimal.TEN, "REF-OK", "test deposit");

            assertThat(result).isNotNull();
            assertThat(activeWallet.getBalance()).isEqualByComparingTo(before.add(BigDecimal.TEN));
            // Ledger saved before wallet
            var inOrder = inOrder(ledgerEntryRepository, walletRepository);
            inOrder.verify(ledgerEntryRepository).save(any());
            inOrder.verify(walletRepository).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WITHDRAW
    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("withdraw()")
    class WithdrawTests {

        @Test @DisplayName("null amount → INVARIANT-5")
        void nullAmount() {
            assertThatThrownBy(() -> service.withdraw(10L, null, "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-5");
        }

        @Test @DisplayName("null referenceId → INVARIANT-2")
        void nullRef() {
            assertThatThrownBy(() -> service.withdraw(10L, BigDecimal.TEN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-2");
        }

        @Test @DisplayName("suspended user → INVARIANT-WALLET-ACTIVE")
        void suspendedUser() {
            when(walletRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(suspendedWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.withdraw(20L, BigDecimal.TEN, "REF-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVARIANT-WALLET-ACTIVE");
        }

        @Test @DisplayName("insufficient balance → INVARIANT-10")
        void insufficientBalance() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.withdraw(10L, new BigDecimal("9999.00"), "REF-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVARIANT-10");
        }

        @Test @DisplayName("valid withdraw → balance decreases by exact amount, not negative")
        void successfulWithdraw() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(ledgerEntryRepository.findByReferenceId("REF-W")).thenReturn(Optional.empty());
            var savedEntry = mockEntry(activeWallet, new BigDecimal("-100.00"),
                                       LedgerEntry.EntryType.WITHDRAWAL, "REF-W");
            when(ledgerEntryRepository.save(any())).thenReturn(savedEntry);
            when(walletRepository.save(any())).thenReturn(activeWallet);

            BigDecimal before = activeWallet.getBalance();
            BigDecimal amount = new BigDecimal("100.00");

            service.withdraw(10L, amount, "REF-W", null);

            assertThat(activeWallet.getBalance()).isEqualByComparingTo(before.subtract(amount));
            assertThat(activeWallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

            var inOrder = inOrder(ledgerEntryRepository, walletRepository);
            inOrder.verify(ledgerEntryRepository).save(any());
            inOrder.verify(walletRepository).save(any());
        }

        @Test @DisplayName("idempotent withdraw → returns existing entry")
        void idempotentWithdraw() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            var existing = mockEntry(activeWallet, new BigDecimal("-50"), LedgerEntry.EntryType.WITHDRAWAL, "REF-DUP");
            when(ledgerEntryRepository.findByReferenceId("REF-DUP")).thenReturn(Optional.of(existing));

            LedgerEntry result = service.withdraw(10L, new BigDecimal("50"), "REF-DUP", null);

            assertThat(result).isSameAs(existing);
            verify(ledgerEntryRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TRANSFER
    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("transfer()")
    class TransferTests {

        private Wallet toWallet;

        @BeforeEach
        void setUp() throws Exception {
            var toUser = makeUser(3L, User.UserStatus.ACTIVE);
            toWallet = makeWallet(30L, toUser, "USD", new BigDecimal("200.00"));
        }

        @Test @DisplayName("same wallet → INVARIANT-DISTINCT-WALLETS")
        void sameWallet() {
            assertThatThrownBy(() ->
                service.transfer(10L, 10L, BigDecimal.TEN, "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-DISTINCT-WALLETS");
        }

        @Test @DisplayName("null referenceId → INVARIANT-2")
        void nullRef() {
            assertThatThrownBy(() ->
                service.transfer(10L, 30L, BigDecimal.TEN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-2");
        }

        @Test @DisplayName("currency mismatch → INVARIANT-CURRENCY-MATCH")
        void currencyMismatch() throws Exception {
            var eurUser   = makeUser(4L, User.UserStatus.ACTIVE);
            var eurWallet = makeWallet(40L, eurUser, "EUR", new BigDecimal("500.00"));

            // Lock order: min(10,40)=10 first, then 40
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(walletRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(eurWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.transfer(10L, 40L, BigDecimal.TEN, "REF-CM", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVARIANT-CURRENCY-MATCH");
        }

        @Test @DisplayName("source insufficient balance → INVARIANT-10")
        void insufficientBalance() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(walletRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(toWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.transfer(10L, 30L, new BigDecimal("99999.00"), "REF-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVARIANT-10");
        }

        @Test @DisplayName("from-wallet user suspended → INVARIANT-WALLET-ACTIVE")
        void fromWalletSuspended() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(walletRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(suspendedWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.transfer(20L, 10L, BigDecimal.TEN, "REF-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVARIANT-WALLET-ACTIVE");
        }

        @Test @DisplayName("valid transfer → money conservation: total delta = 0")
        void moneyConservation() {
            // Lock order ascending: 10 < 30
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(walletRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(toWallet));
            when(ledgerEntryRepository.findByReferenceId(any())).thenReturn(Optional.empty());

            var debitEntry  = mockEntry(activeWallet, new BigDecimal("-100"), LedgerEntry.EntryType.TRANSFER_OUT, "REF-T-OUT");
            var creditEntry = mockEntry(toWallet,     new BigDecimal("100"),  LedgerEntry.EntryType.TRANSFER_IN,  "REF-T-IN");
            when(ledgerEntryRepository.save(any()))
                .thenReturn(debitEntry)
                .thenReturn(creditEntry);
            when(walletRepository.save(any()))
                .thenReturn(activeWallet)
                .thenReturn(toWallet);

            BigDecimal fromBefore = activeWallet.getBalance();
            BigDecimal toBefore   = toWallet.getBalance();
            BigDecimal amount     = new BigDecimal("100.00");

            LedgerEntry[] entries = service.transfer(10L, 30L, amount, "REF-T", null);

            assertThat(entries).hasSize(2);

            BigDecimal fromAfter = activeWallet.getBalance();
            BigDecimal toAfter   = toWallet.getBalance();

            // Money conservation: total delta = 0
            BigDecimal delta = fromAfter.subtract(fromBefore).add(toAfter.subtract(toBefore));
            assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);

            // Exact amounts
            assertThat(fromAfter).isEqualByComparingTo(fromBefore.subtract(amount));
            assertThat(toAfter).isEqualByComparingTo(toBefore.add(amount));
        }

        @Test @DisplayName("idempotent transfer → returns both existing entries")
        void idempotentTransfer() {
            when(walletRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeWallet));
            when(walletRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(toWallet));

            var debit  = mockEntry(activeWallet, new BigDecimal("-50"), LedgerEntry.EntryType.TRANSFER_OUT, "REF-DUP-OUT");
            var credit = mockEntry(toWallet,     new BigDecimal("50"),  LedgerEntry.EntryType.TRANSFER_IN,  "REF-DUP-IN");

            when(ledgerEntryRepository.findByReferenceId("REF-DUP-OUT")).thenReturn(Optional.of(debit));
            when(ledgerEntryRepository.findByReferenceId("REF-DUP-IN")).thenReturn(Optional.of(credit));

            LedgerEntry[] result = service.transfer(10L, 30L, new BigDecimal("50"), "REF-DUP", null);

            assertThat(result[0]).isSameAs(debit);
            assertThat(result[1]).isSameAs(credit);
            verify(ledgerEntryRepository, never()).save(any());
        }
    }
}
