# Transaction Invariants Specification

## Overview

This document formally defines the **financial correctness invariants** for the LedgerX Lite system. These invariants are mathematical properties that MUST hold at all times for the system to be considered correct.

**Purpose:** Make implicit assumptions explicit and enforceable.

---

## Invariant Classification

Invariants are classified by enforcement level:

- **I1 (Critical):** Database-level enforcement (constraints)
- **I2 (Essential):** Service-level enforcement (runtime checks)
- **I3 (Derived):** Computed properties that must remain consistent

---

## INVARIANT 1: Balance-Ledger Consistency (I3 - CRITICAL)

### Formal Definition

```
∀ wallet w:
  w.balance = Σ(entry.amount) for all entries where entry.wallet_id = w.id
```

**In English:**
A wallet's balance MUST always equal the sum of all ledger entry amounts for that wallet.

### Why This Exists

- **Source of Truth:** The ledger is the authoritative record
- **Auditability:** Balance must be derivable from immutable log
- **Reconciliation:** Enables verification and dispute resolution
- **Compliance:** Required for financial audits (SOX, GDPR, etc.)

### When This Can Break

1. **Balance updated without ledger entry**
   ```java
   wallet.credit(100);  // ❌ WRONG - no corresponding entry
   walletRepository.save(wallet);
   ```

2. **Ledger entry created without balance update**
   ```java
   LedgerEntry entry = new LedgerEntry(wallet, 100, ...);
   ledgerEntryRepository.save(entry);  // ❌ WRONG - balance not updated
   ```

3. **Race condition in concurrent transactions**
   - Without pessimistic locking, two threads could read balance=100
   - Both add 50, both write balance=150
   - Final balance: 150 (should be 200)

### Enforcement Strategy

- **I2 (Service):** TransactionService MUST create ledger entry before updating balance
- **I2 (Service):** Wallet balance mutations MUST only occur within @Transactional methods
- **I3 (Verification):** Periodic reconciliation job to verify invariant holds

### Current Implementation Status

✅ **ENFORCED** via:
- `TransactionService.deposit()` creates entry at line 64-71, updates balance at line 74
- `TransactionService.withdraw()` creates entry at line 107-114, updates balance at line 117
- `TransactionService.transfer()` creates both entries before balance updates

---

## INVARIANT 2: Idempotency (I1 - CRITICAL)

### Formal Definition

```
∀ referenceId r, ∀ transactions t1, t2:
  if t1.referenceId = t2.referenceId
  then t1 = t2 (same transaction)
```

**In English:**
Each referenceId can only appear once in the ledger. Duplicate submissions return the same transaction.

### Why This Exists

- **Exactly-Once Semantics:** Prevent duplicate charges
- **Network Reliability:** Safe retries during network failures
- **Consumer Protection:** No accidental double-billing
- **Financial Integrity:** Money doesn't get created by retrying

### When This Can Break

1. **Check-then-act race condition**
   ```java
   // Thread A checks: not exists
   // Thread B checks: not exists
   // Thread A creates entry
   // Thread B creates entry  // ❌ DUPLICATE!
   ```

2. **Missing idempotency check**
   ```java
   public LedgerEntry deposit(..., String refId, ...) {
       // No check for existing refId
       LedgerEntry entry = new LedgerEntry(...);
       return ledgerEntryRepository.save(entry);  // ❌ MAY CREATE DUPLICATE
   }
   ```

### Enforcement Strategy

- **I1 (Database):** UNIQUE constraint on `ledger_entries.reference_id`
- **I2 (Service):** Check `existsByReferenceId()` before creating entry
- **I2 (Service):** Return existing entry if found (idempotent behavior)

### Current Implementation Status

✅ **ENFORCED** via:
- Database: `@Index(name = "idx_ledger_reference_id", unique = true)` on referenceId
- Service: `TransactionService` checks at lines 54-57 (deposit), 97-100 (withdraw), 155-161 (transfer)

---

## INVARIANT 3: Append-Only Ledger (I1 - CRITICAL)

### Formal Definition

```
∀ ledger entry e:
  once created, e.amount, e.type, e.referenceId, e.wallet_id NEVER change
  AND e is NEVER deleted
```

**In English:**
Ledger entries are immutable after creation. No updates, no deletes.

### Why This Exists

- **Audit Trail:** Complete, tamper-proof history
- **Regulatory Compliance:** Required for financial audits
- **Forensics:** Investigate issues by replaying history
- **Trust:** Customers can verify their transaction history

### When This Can Break

1. **Accidental update**
   ```java
   LedgerEntry entry = ledgerEntryRepository.findById(1);
   entry.setAmount(200);  // ❌ WRONG - entries are immutable
   ```

2. **Deletion**
   ```java
   ledgerEntryRepository.deleteById(1);  // ❌ WRONG - violates append-only
   ```

3. **Correction by mutation**
   ```java
   // ❌ WRONG - should create offsetting entry instead
   LedgerEntry wrong = ledgerEntryRepository.findById(1);
   wrong.setAmount(correctedAmount);
   ```

### Enforcement Strategy

- **I1 (Database):** All columns marked `updatable = false` in JPA
- **I2 (Domain):** No setters in LedgerEntry (immutable class)
- **I2 (Service):** Never call `delete()` on LedgerEntryRepository
- **I2 (Service):** Corrections done via new offsetting entries

### Current Implementation Status

✅ **ENFORCED** via:
- Domain: LedgerEntry has no setters (lines 138-208 in LedgerEntry.java)
- JPA: `@Column(updatable = false)` on amount, type, referenceId, wallet_id, created_at
- Service: No delete calls in TransactionService

---

## INVARIANT 4: Pessimistic Locking for Concurrent Balance Updates (I2 - CRITICAL)

### Formal Definition

```
∀ wallet w, ∀ transaction t that modifies w.balance:
  t MUST acquire PESSIMISTIC_WRITE lock on w before reading w.balance
```

**In English:**
Before any transaction modifies a wallet's balance, it must acquire an exclusive database lock.

### Why This Exists

- **Prevent Lost Updates:** Two concurrent deposits don't interfere
- **Serializable Isolation:** Equivalent to serial execution
- **Balance Integrity:** No race conditions in balance calculations

### Example of What This Prevents

**Without Lock:**
```
Time | Transaction A         | Transaction B
-----|----------------------|----------------------
T1   | Read balance: $100   |
T2   |                      | Read balance: $100
T3   | Add $50              |
T4   |                      | Add $30
T5   | Write balance: $150  |
T6   |                      | Write balance: $130  ❌ Lost update!
```

**With PESSIMISTIC_WRITE Lock:**
```
Time | Transaction A              | Transaction B
-----|----------------------------|---------------------------
T1   | LOCK wallet (balance=$100) |
T2   |                            | Attempts LOCK → WAITS
T3   | Add $50                    |
T4   | Write balance: $150        |
T5   | COMMIT & RELEASE LOCK      |
T6   |                            | Acquires LOCK (balance=$150)
T7   |                            | Add $30
T8   |                            | Write balance: $180 ✓ Correct!
```

### Enforcement Strategy

- **I2 (Repository):** `WalletRepository.findByIdForUpdate()` uses `@Lock(PESSIMISTIC_WRITE)`
- **I2 (Service):** ALL balance-modifying methods MUST call `findByIdForUpdate()`
- **I2 (Service):** Read-only methods use `findById()` (no lock)

### Current Implementation Status

✅ **ENFORCED** via:
- Repository: `@Lock(LockModeType.PESSIMISTIC_WRITE)` at line 86 of WalletRepository
- Service: TransactionService calls `findByIdForUpdate()` at lines 60, 103, 167-170

---

## INVARIANT 5: Positive Transaction Amounts (I2 - ESSENTIAL)

### Formal Definition

```
∀ transaction request req:
  req.amount > 0
```

**In English:**
All transaction amounts (deposit, withdraw, transfer) must be positive values.

### Why This Exists

- **Semantic Clarity:** Positive=credit, negative=debit (handled internally)
- **Prevent Mistakes:** User can't accidentally "withdraw -$50" to deposit
- **API Safety:** Clear contract for API consumers

### When This Can Break

1. **Invalid input**
   ```java
   deposit(walletId, new BigDecimal("-50"), ...);  // ❌ Negative deposit?
   ```

2. **Zero amount**
   ```java
   withdraw(walletId, BigDecimal.ZERO, ...);  // ❌ Meaningless transaction
   ```

### Enforcement Strategy

- **I2 (DTO):** `@DecimalMin(value = "0.01")` on TransactionRequest.amount
- **I2 (Service):** `validatePositiveAmount()` guard clause
- **I2 (Service):** Fail fast with IllegalArgumentException

### Current Implementation Status

✅ **ENFORCED** via:
- DTO: `@DecimalMin(value = "0.01")` in TransactionRequest.java line 14
- Service: `validatePositiveAmount()` called at lines 51, 94, 145 in TransactionService

---

## INVARIANT 6: Wallet-User One-to-One Relationship (I1 - ESSENTIAL)

### Formal Definition

```
∀ user u: exactly one wallet w exists where w.user_id = u.id
∀ wallet w: exactly one user u exists where u.id = w.user_id
```

**In English:**
Each user has exactly one wallet. Each wallet belongs to exactly one user.

### Why This Exists

- **Business Rule:** Simplified account model (one wallet per user)
- **Data Integrity:** Orphaned wallets are prevented
- **User Experience:** Users don't need to choose between multiple wallets

### Enforcement Strategy

- **I1 (Database):** UNIQUE constraint on `wallets.user_id`
- **I1 (Database):** NOT NULL + FOREIGN KEY on `wallets.user_id`
- **I2 (Service):** `WalletService.createWallet()` checks `existsByUserId()`

### Current Implementation Status

✅ **ENFORCED** via:
- JPA: `@Index(name = "idx_wallets_user_id", unique = true)` in Wallet.java line 28
- JPA: `@OneToOne(optional = false)` and `@JoinColumn(nullable = false, unique = true)` in Wallet.java line 38-39
- Service: Check at line 37 in WalletService.java

---

## INVARIANT 7: Currency Consistency (I1 - ESSENTIAL)

### Formal Definition

```
∀ wallet w, ∀ ledger entries e1, e2 where e1.wallet_id = e2.wallet_id = w.id:
  w.currency is immutable after creation
```

**In English:**
A wallet's currency never changes. All transactions for a wallet are in the same currency.

### Why This Exists

- **Prevent Mixing:** Can't add USD to a EUR wallet
- **Exchange Rate Safety:** No accidental currency conversions
- **Accounting Accuracy:** Balance calculations assume single currency

### Enforcement Strategy

- **I1 (Domain):** Wallet.currency has no setter
- **I2 (Service):** Currency set on wallet creation, never modified
- **Future:** Multi-currency requires separate wallets per currency

### Current Implementation Status

✅ **ENFORCED** via:
- Domain: Wallet has `getCurrency()` but no `setCurrency()` (line 91 in Wallet.java)
- JPA: No `updatable = false` needed since there's no setter to call

---

## INVARIANT 8: Transfer Atomicity (I2 - CRITICAL)

### Formal Definition

```
∀ transfer t from wallet w1 to wallet w2 with amount a:
  (debit entry created in w1's ledger ∧ credit entry created in w2's ledger ∧ w1.balance -= a ∧ w2.balance += a)
  OR (none of the above happen)
```

**In English:**
A transfer either completes fully (debit, credit, both balance updates) or doesn't happen at all. No partial transfers.

### Why This Exists

- **Double-Entry Bookkeeping:** Debits and credits must balance
- **Money Conservation:** Money can't be created or destroyed
- **System Consistency:** Prevents "money in flight" scenarios

### Example of What This Prevents

**Without Atomicity:**
```
1. Debit Alice's wallet: -$100 ✓
2. Credit Bob's wallet: +$100 ❌ System crashes!
Result: $100 disappeared from the system
```

**With @Transactional Atomicity:**
```
1. Debit Alice's wallet: -$100
2. Credit Bob's wallet: +$100
3. COMMIT ✓
OR
1. Debit Alice's wallet: -$100
2. Credit Bob's wallet: ERROR!
3. ROLLBACK - Alice's debit is undone ✓
```

### Enforcement Strategy

- **I2 (Service):** Entire `transfer()` method wrapped in `@Transactional`
- **I2 (Service):** Both ledger entries created before any balance updates
- **I2 (Service):** If either wallet update fails, entire transaction rolls back

### Current Implementation Status

✅ **ENFORCED** via:
- Service: Class-level `@Transactional` in TransactionService.java line 25
- Service: Both entries created (lines 177-194) before balance updates (lines 197-201)

---

## INVARIANT 9: Deadlock-Free Transfer Ordering (I2 - CRITICAL)

### Formal Definition

```
∀ transfers t1, t2 that lock wallets w1, w2:
  wallets are locked in ascending order by ID
```

**In English:**
When a transfer needs to lock two wallets, always lock them in ascending ID order.

### Why This Exists

**Deadlock Scenario Without Ordering:**
```
Time | Transfer A (w1→w2)    | Transfer B (w2→w1)
-----|----------------------|----------------------
T1   | LOCK wallet 1        |
T2   |                      | LOCK wallet 2
T3   | Attempts LOCK w2     |
T4   |                      | Attempts LOCK w1
     | → WAITS on w2        | → WAITS on w1
     | ⚠️ DEADLOCK!         | ⚠️ DEADLOCK!
```

**With Consistent Ordering:**
```
Time | Transfer A (w1→w2)    | Transfer B (w2→w1)
-----|----------------------|----------------------
T1   | LOCK wallet 1        |
T2   |                      | Attempts LOCK w1 → WAITS
T3   | LOCK wallet 2        |
T4   | Process transfer     |
T5   | COMMIT & RELEASE     |
T6   |                      | Acquires LOCK w1
T7   |                      | LOCK wallet 2
T8   |                      | Process transfer ✓
```

### Enforcement Strategy

- **I2 (Service):** Always lock `Math.min(id1, id2)` first, then `Math.max(id1, id2)`
- **I2 (Code Review):** Verify all multi-resource locking uses consistent ordering

### Current Implementation Status

✅ **ENFORCED** via:
- Service: Lines 164-174 in TransactionService.java implement ordered locking

---

## INVARIANT 10: No Negative Balances (I2 - ESSENTIAL)

### Formal Definition

```
∀ wallet w, ∀ time t:
  w.balance ≥ 0
```

**In English:**
Wallet balances can never go negative (no overdrafts).

### Why This Exists

- **Business Rule:** System doesn't support credit/overdraft
- **Financial Safety:** Prevents spending money that doesn't exist
- **Fraud Prevention:** Can't withdraw more than available

### Enforcement Strategy

- **I2 (Domain):** `Wallet.debit()` checks balance before subtracting
- **I2 (Service):** Validation in TransactionService before operations
- **Optional I1 (Database):** `CHECK (balance >= 0)` constraint

### Current Implementation Status

✅ **ENFORCED** via:
- Domain: Lines 142-150 in Wallet.java throw IllegalStateException if balance insufficient

---

## Invariant Summary Table

| ID | Invariant | Enforcement | Current Status |
|----|-----------|-------------|----------------|
| 1 | Balance = Σ(ledger entries) | I2 (Service) + I3 (Verification) | ✅ ENFORCED |
| 2 | Idempotency (unique referenceId) | I1 (DB) + I2 (Service) | ✅ ENFORCED |
| 3 | Append-only ledger | I1 (JPA) + I2 (Domain) | ✅ ENFORCED |
| 4 | Pessimistic locking | I2 (Repository + Service) | ✅ ENFORCED |
| 5 | Positive amounts | I2 (DTO + Service) | ✅ ENFORCED |
| 6 | One wallet per user | I1 (DB) + I2 (Service) | ✅ ENFORCED |
| 7 | Currency immutability | I1 (Domain) | ✅ ENFORCED |
| 8 | Transfer atomicity | I2 (Service @Transactional) | ✅ ENFORCED |
| 9 | Deadlock-free ordering | I2 (Service algorithm) | ✅ ENFORCED |
| 10 | No negative balance | I2 (Domain + Service) | ✅ ENFORCED |

---

## Verification Strategy

### Runtime Verification (I2)

Performed on every operation:
- Input validation
- Guard clauses
- Domain method checks
- Service-level assertions

### Periodic Reconciliation (I3)

Batch jobs (future implementation):
1. **Balance Reconciliation**
   ```sql
   SELECT w.id, w.balance, SUM(le.amount) as ledger_total
   FROM wallets w
   LEFT JOIN ledger_entries le ON le.wallet_id = w.id
   GROUP BY w.id
   HAVING w.balance != COALESCE(SUM(le.amount), 0);
   ```
   Should return 0 rows.

2. **Idempotency Check**
   ```sql
   SELECT reference_id, COUNT(*)
   FROM ledger_entries
   GROUP BY reference_id
   HAVING COUNT(*) > 1;
   ```
   Should return 0 rows.

### Database Integrity (I1)

Enforced continuously:
- UNIQUE constraints
- FOREIGN KEY constraints
- NOT NULL constraints
- CHECK constraints (optional)

---

## Future Enhancements

1. **Real-time Invariant Monitoring**
   - Emit metrics when invariant checks fail
   - Alert on suspicious patterns (many idempotency hits, etc.)

2. **Formal Verification**
   - Property-based testing (QuickCheck-style)
   - Verify invariants hold under random transaction sequences

3. **Circuit Breakers**
   - If reconciliation detects invariant violations, disable writes
   - Manual intervention required to restore consistency

---

## Conclusion

These invariants form the **mathematical contract** for financial correctness. 

**Any violation indicates a critical bug.**

The system is designed to fail loudly (exceptions, transaction rollbacks) rather than silently corrupt data.
