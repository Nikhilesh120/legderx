# LedgerX Lite — Financial Invariants Reference

This document is the authoritative record of all financial safety rules enforced by the system. Every invariant is implemented inline in `TransactionService`. This document is the source of truth for auditors and reviewers.

---

## Invariant Table

| ID | Name | Rule | Enforced In | Exception |
|----|------|------|-------------|-----------|
| INV-1 | Ledger First | LedgerEntry written before Wallet.balance updated | TransactionService, all methods | IllegalStateException post-condition |
| INV-2 | Idempotency | referenceId must be non-null, non-blank, unique | TransactionService (pre-lock + DB) | IllegalArgumentException |
| INV-4 | Pessimistic Lock | Wallet locked before any mutation | WalletRepository.findByIdForUpdate | PessimisticLockException |
| INV-5 | Positive Amount | amount must be strictly > 0 | TransactionService (first check) | IllegalArgumentException |
| INV-7 | Immutable Currency | Wallet currency cannot change after creation | Wallet constructor (no setter) | IllegalArgumentException |
| INV-8 | Transfer Atomicity | Both debit and credit or neither | @Transactional rollback | IllegalStateException |
| INV-8b | Money Conservation | fromDelta + toDelta = 0 in transfer | TransactionService post-condition | IllegalStateException |
| INV-9 | Deadlock Prevention | Wallets locked in ascending ID order | TransactionService.transfer() | Never — structural guarantee |
| INV-10 | No Negative Balance | balance must always be >= 0 | Service + Wallet.debit() + DB CHECK | IllegalStateException |
| INV-A | Wallet Active | User.status must be ACTIVE to transact | TransactionService (after lock) | IllegalStateException |
| INV-C | Currency Match | Transfer wallets must share currency | TransactionService.transfer() | IllegalArgumentException |
| INV-D | Distinct Wallets | fromWalletId != toWalletId in transfer | TransactionService.transfer() | IllegalArgumentException |

---

## Why Each Invariant Exists

**INV-1 (Ledger First)**
The ledger is the source of truth. If we updated the balance first and then crashed before writing the ledger, the balance would be wrong with no audit record explaining why.

**INV-2 (Idempotency)**
Networks fail. Clients retry. Without idempotency, a retry after a timeout would charge a user twice. The referenceId is the client's promise: "this is one logical operation."

**INV-4 (Pessimistic Lock)**
Two concurrent withdrawals from a wallet with $100 balance could both read $100, both see it as sufficient, and both proceed — resulting in a $100 overdraft. The lock serialises them: the second sees the updated balance.

**INV-5 (Positive Amount)**
Zero-amount transactions create ledger noise. Negative amounts passed as the debit side of a withdrawal would create a credit. Both are bugs with financial consequences.

**INV-8b (Money Conservation)**
In a transfer, the sum of all balance changes must be zero. If debit is $100 and credit is $99, $1 was destroyed. If credit is $101, $1 was created. Either is a financial integrity failure.

**INV-9 (Deadlock Prevention)**
If Thread A locks wallets [1, 2] and Thread B locks [2, 1], they will wait for each other forever. Locking in ascending ID order means both threads always attempt wallet 1 first — one proceeds, one waits. No circular wait is possible.

**INV-10 (No Negative Balance)**
The system must not lend money implicitly. A negative balance means the user spent money they didn't have. Enforced at three layers: service pre-check, domain debit() method, and database CHECK constraint.

---

## Failure Mode Analysis

| Failure Point | State | Recovery |
|---|---|---|
| Crash before lock | Nothing written | Retry safely |
| Crash after lock, before ledger write | Lock released on disconnect, nothing written | Retry safely |
| Crash after ledger write, before balance update | Both rolled back by @Transactional | Retry safely — idempotency returns existing entry |
| Crash after both writes, before commit | Both rolled back | Retry safely |
| Post-condition fails | IllegalStateException, full rollback | Indicates code bug — nothing committed |

**Key property:** No failure mode produces an inconsistent committed state.

---

## Audit Queries

```sql
-- 1. Balance ↔ Ledger consistency (should return 0 rows)
SELECT w.id, w.balance, SUM(e.amount) AS ledger_sum
FROM wallets w LEFT JOIN ledger_entries e ON e.wallet_id = w.id
GROUP BY w.id, w.balance
HAVING w.balance != COALESCE(SUM(e.amount), 0);

-- 2. Orphaned transfer legs (should return 0 rows)
SELECT reference_id FROM ledger_entries
WHERE type = 'TRANSFER_OUT'
  AND REPLACE(reference_id, '-OUT', '-IN') NOT IN (
      SELECT reference_id FROM ledger_entries WHERE type = 'TRANSFER_IN');

-- 3. Any negative balances (should return 0 rows)
SELECT id, balance FROM wallets WHERE balance < 0;

-- 4. Any zero-amount entries (should return 0 rows)
SELECT id, reference_id FROM ledger_entries WHERE amount = 0;
```
