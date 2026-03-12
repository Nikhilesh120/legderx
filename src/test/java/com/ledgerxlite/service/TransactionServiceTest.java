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

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks TransactionService service;

    private User activeUser;
    private User suspendedUser;
    private Wallet activeWallet;
    private Wallet suspendedWallet;

    @BeforeEach
    void setUp() throws Exception {
        activeUser = makeUser(1L, User.UserStatus.ACTIVE);
        suspendedUser = makeUser(2L, User.UserStatus.SUSPENDED);

        activeWallet = makeWallet(10L, activeUser, "USD", new BigDecimal("1000.00"));
        suspendedWallet = makeWallet(20L, suspendedUser, "USD", new BigDecimal("500.00"));
    }

    // ───────────────── helpers ─────────────────

    private User makeUser(Long id, User.UserStatus status) throws Exception {
        var user = new User("user" + id + "@test.com", "hash");
        setId(user, id);
        if (status == User.UserStatus.SUSPENDED) user.suspend();
        return user;
    }

    private Wallet makeWallet(Long id, User user, String currency, BigDecimal balance) throws Exception {
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
        return new LedgerEntry(wallet, amount, type, refId, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEPOSIT
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    class DepositTests {

        @Test
        void nullAmount() {
            assertThatThrownBy(() -> service.deposit(10L, null, "REF", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVARIANT-5");
        }

        @Test
        void walletNotFound() {
            when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deposit(99L, BigDecimal.TEN, "REF", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Wallet not found");
        }

        @Test
        void idempotentDeposit() {
            var existing = mockEntry(activeWallet, BigDecimal.TEN,
                    LedgerEntry.EntryType.DEPOSIT, "REF-DUP");

            when(ledgerEntryRepository.findByReferenceId("REF-DUP"))
                    .thenReturn(Optional.of(existing));

            LedgerEntry result =
                    service.deposit(10L, BigDecimal.TEN, "REF-DUP", null);

            assertThat(result).isSameAs(existing);
            verify(ledgerEntryRepository, never()).save(any());
            verify(walletRepository, never()).save(any());
        }

        @Test
        void successfulDeposit() {
            when(walletRepository.findByIdForUpdate(10L))
                    .thenReturn(Optional.of(activeWallet));
            when(ledgerEntryRepository.findByReferenceId("REF-OK"))
                    .thenReturn(Optional.empty());

            when(ledgerEntryRepository.save(any()))
                    .thenReturn(mockEntry(activeWallet, BigDecimal.TEN,
                            LedgerEntry.EntryType.DEPOSIT, "REF-OK"));

            BigDecimal before = activeWallet.getBalance();

            service.deposit(10L, BigDecimal.TEN, "REF-OK", null);

            assertThat(activeWallet.getBalance())
                    .isEqualByComparingTo(before.add(BigDecimal.TEN));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WITHDRAW
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    class WithdrawTests {

        @Test
        void insufficientBalance() {
            when(walletRepository.findByIdForUpdate(10L))
                    .thenReturn(Optional.of(activeWallet));
            when(ledgerEntryRepository.findByReferenceId(any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.withdraw(10L, new BigDecimal("9999"), "REF", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVARIANT-10");
        }

        @Test
        void idempotentWithdraw() {
            var existing = mockEntry(activeWallet, new BigDecimal("-50"),
                    LedgerEntry.EntryType.WITHDRAWAL, "REF-DUP");

            when(ledgerEntryRepository.findByReferenceId("REF-DUP"))
                    .thenReturn(Optional.of(existing));

            LedgerEntry result =
                    service.withdraw(10L, new BigDecimal("50"), "REF-DUP", null);

            assertThat(result).isSameAs(existing);
            verify(ledgerEntryRepository, never()).save(any());
            verify(walletRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSFER
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    class TransferTests {

        private Wallet toWallet;

        @BeforeEach
        void setupTransfer() throws Exception {
            var toUser = makeUser(3L, User.UserStatus.ACTIVE);
            toWallet = makeWallet(30L, toUser, "USD", new BigDecimal("200"));
        }

        @Test
        void idempotentTransfer() {
            var debit = mockEntry(activeWallet, new BigDecimal("-50"),
                    LedgerEntry.EntryType.TRANSFER_OUT, "REF-DUP-OUT");
            var credit = mockEntry(toWallet, new BigDecimal("50"),
                    LedgerEntry.EntryType.TRANSFER_IN, "REF-DUP-IN");

            when(ledgerEntryRepository.findByReferenceId("REF-DUP-OUT"))
                    .thenReturn(Optional.of(debit));
            when(ledgerEntryRepository.findByReferenceId("REF-DUP-IN"))
                    .thenReturn(Optional.of(credit));

            LedgerEntry[] result =
                    service.transfer(10L, 30L, new BigDecimal("50"), "REF-DUP", null);

            assertThat(result[0]).isSameAs(debit);
            assertThat(result[1]).isSameAs(credit);

            verify(ledgerEntryRepository, never()).save(any());
            verify(walletRepository, never()).save(any());
        }
    }
}
