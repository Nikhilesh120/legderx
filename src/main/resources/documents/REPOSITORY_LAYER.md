# Repository Layer Documentation - LedgerX Lite

## Overview

The repository layer provides **transaction-safe data access** for the financial ledger system using Spring Data JPA.

**Key Principles:**
- Repositories are interfaces only (no custom implementations)
- Financial safety enforced through pessimistic locking
- Idempotency guaranteed via reference ID checks
- Append-only operations for ledger entries

---

## Repository Implementations

### 1. UserRepository

**File:** `src/main/java/com/ledgerxlite/repository/UserRepository.java`

**Purpose:** User lookup and authentication support

**Methods:**

| Method | Return Type | Purpose | Index Used |
|--------|-------------|---------|------------|
| `findByEmail(String)` | `Optional<User>` | Login, authentication | `idx_users_email` (unique) |
| `existsByEmail(String)` | `boolean` | Duplicate email check | `idx_users_email` (unique) |
| `save(User)` | `User` | Create/update user | - |
| `findById(Long)` | `Optional<User>` | User lookup by ID | Primary key |

**Usage Example:**
```java
// Login
Optional<User> user = userRepository.findByEmail("alice@example.com");

// Registration validation
if (userRepository.existsByEmail(email)) {
    throw new EmailAlreadyExistsException();
}
```

---

### 2. WalletRepository

**File:** `src/main/java/com/ledgerxlite/repository/WalletRepository.java`

**Purpose:** Wallet access with **pessimistic locking** for safe balance updates

**Methods:**

| Method | Return Type | Lock Type | Purpose | Index Used |
|--------|-------------|-----------|---------|------------|
| `findByUserId(Long)` | `Optional<Wallet>` | None | Read-only balance display | `idx_wallets_user_id` |
| `findByIdForUpdate(Long)` | `Optional<Wallet>` | **PESSIMISTIC_WRITE** | Balance modification | Primary key |
| `existsByUserId(Long)` | `boolean` | None | Wallet existence check | `idx_wallets_user_id` |
| `save(Wallet)` | `Wallet` | Optimistic (@Version) | Create/update wallet | - |

**Critical Method: findByIdForUpdate()**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :walletId")
Optional<Wallet> findByIdForUpdate(@Param("walletId") Long walletId);
```

**Why PESSIMISTIC_WRITE is essential:**

**Without Lock (WRONG):**
```
Time | Transaction A              | Transaction B
-----|----------------------------|---------------------------
T1   | Read balance: $100         |
T2   |                            | Read balance: $100
T3   | Debit $50 (balance=$50)    |
T4   |                            | Debit $30 (balance=$70) ❌ WRONG!
T5   | Commit                     |
T6   |                            | Commit
Result: Final balance = $70 (should be $20) → LOST UPDATE!
```

**With PESSIMISTIC_WRITE Lock (CORRECT):**
```
Time | Transaction A              | Transaction B
-----|----------------------------|---------------------------
T1   | Lock wallet (balance=$100) |
T2   |                            | Attempts lock → WAITS
T3   | Debit $50 (balance=$50)    |
T4   | Commit (releases lock)     |
T5   |                            | Acquires lock (balance=$50)
T6   |                            | Debit $30 (balance=$20) ✓
T7   |                            | Commit
Result: Final balance = $20 ✓ CORRECT!
```

**Generated SQL:**
```sql
SELECT * FROM wallets WHERE id = ? FOR UPDATE
```

**Usage Example:**
```java
@Transactional
public void debitWallet(Long walletId, BigDecimal amount) {
    // MUST use findByIdForUpdate for any balance modification
    Wallet wallet = walletRepository.findByIdForUpdate(walletId)
        .orElseThrow(() -> new WalletNotFoundException());
    
    wallet.debit(amount);  // Modifies balance
    walletRepository.save(wallet);
    // Lock released on transaction commit
}
```

---

### 3. LedgerEntryRepository

**File:** `src/main/java/com/ledgerxlite/repository/LedgerEntryRepository.java`

**Purpose:** Append-only transaction log with idempotency

**Methods:**

| Method | Return Type | Purpose | Index Used |
|--------|-------------|---------|------------|
| `findByWalletIdOrderByCreatedAtAsc(Long)` | `List<LedgerEntry>` | Transaction history | `idx_ledger_wallet_created` |
| `existsByReferenceId(String)` | `boolean` | Idempotency check | `idx_ledger_reference_id` (unique) |
| `findByReferenceId(String)` | `Optional<LedgerEntry>` | Retrieve existing entry | `idx_ledger_reference_id` |
| `countByWalletId(Long)` | `long` | Total transactions | `idx_ledger_wallet_id` |
| `save(LedgerEntry)` | `LedgerEntry` | **Append-only** create | - |

**Critical: Idempotency via existsByReferenceId()**

**Why this matters:**
- Network failures cause retries
- Client may submit same transaction twice
- Without idempotency: double-charging customers
- With idempotency: safe retries

**Idempotency Flow:**
```java
@Transactional
public LedgerEntry processTransaction(TransactionRequest request) {
    String referenceId = request.getReferenceId();
    
    // Step 1: Check if already processed
    if (ledgerEntryRepository.existsByReferenceId(referenceId)) {
        // Return existing entry instead of creating duplicate
        return ledgerEntryRepository.findByReferenceId(referenceId)
            .orElseThrow();
    }
    
    // Step 2: Create new entry
    LedgerEntry entry = new LedgerEntry(
        wallet, amount, type, referenceId, description
    );
    
    // Step 3: Save (if duplicate referenceId, database throws exception)
    return ledgerEntryRepository.save(entry);
}
```

**Database Protection:**
```sql
-- Unique constraint on reference_id
CREATE UNIQUE INDEX idx_ledger_reference_id ON ledger_entries(reference_id);
```

**Concurrent Request Handling:**
```
Time | Request A (ref=TXN-123)    | Request B (ref=TXN-123)
-----|----------------------------|---------------------------
T1   | Check exists: false        |
T2   |                            | Check exists: false
T3   | Create entry               |
T4   |                            | Create entry → FAILS ✓
T5   | Commit                     |
T6   |                            | Rollback
Result: Only ONE entry created ✓
```

**Usage Example:**
```java
// Check transaction history
List<LedgerEntry> history = ledgerEntryRepository
    .findByWalletIdOrderByCreatedAtAsc(walletId);

// Calculate balance from ledger
BigDecimal balance = history.stream()
    .map(LedgerEntry::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

---

## Financial Safety Guarantees

### 1. **Concurrent Balance Updates: SAFE**
- `findByIdForUpdate()` uses PESSIMISTIC_WRITE lock
- Database-level row locking prevents lost updates
- Transactions are serialized for same wallet

### 2. **Duplicate Transactions: PREVENTED**
- `existsByReferenceId()` checks before processing
- Unique index on `reference_id` at database level
- Safe retries without double-charging

### 3. **Append-Only Ledger: ENFORCED**
- No update methods in LedgerEntryRepository
- No delete methods in LedgerEntryRepository
- LedgerEntry entity marked immutable (`updatable = false`)

### 4. **Audit Trail: COMPLETE**
- `findByWalletIdOrderByCreatedAtAsc()` provides chronological history
- Composite index ensures efficient queries even with millions of entries
- Immutability guarantees history cannot be tampered with

---

## Index Strategy

All repositories leverage database indexes for performance:

### UserRepository Indexes
```sql
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
```

### WalletRepository Indexes
```sql
CREATE UNIQUE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_currency ON wallets(currency);
```

### LedgerEntryRepository Indexes
```sql
CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at);
CREATE UNIQUE INDEX idx_ledger_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_type ON ledger_entries(type);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries(wallet_id, created_at);
```

**Composite Index Usage:**
- `idx_ledger_wallet_created`: Efficient for wallet transaction history queries
- Covers both filtering (wallet_id) and sorting (created_at)

---

## Transaction Boundaries

**IMPORTANT:** Repositories do NOT manage transactions.

**Correct (Service Layer):**
```java
@Service
public class WalletService {
    
    @Transactional  // ✓ Transaction starts here
    public void transfer(Long fromWalletId, Long toWalletId, BigDecimal amount) {
        Wallet from = walletRepository.findByIdForUpdate(fromWalletId).orElseThrow();
        Wallet to = walletRepository.findByIdForUpdate(toWalletId).orElseThrow();
        
        from.debit(amount);
        to.credit(amount);
        
        walletRepository.save(from);
        walletRepository.save(to);
        // Transaction commits here (or rolls back on exception)
    }
}
```

**Wrong (Repository Layer):**
```java
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    @Transactional  // ✗ WRONG - Don't put here
    Optional<Wallet> findByIdForUpdate(Long walletId);
}
```

---

## Service Layer Integration Pattern

When implementing services (Step 3), follow this pattern:

```java
@Service
@Transactional  // Default: all public methods transactional
public class TransactionService {
    
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    
    // Constructor injection
    
    public LedgerEntry deposit(Long walletId, BigDecimal amount, String referenceId) {
        // 1. Idempotency check
        if (ledgerEntryRepository.existsByReferenceId(referenceId)) {
            return ledgerEntryRepository.findByReferenceId(referenceId).orElseThrow();
        }
        
        // 2. Lock wallet for update
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() -> new WalletNotFoundException(walletId));
        
        // 3. Create ledger entry (FIRST - source of truth)
        LedgerEntry entry = new LedgerEntry(
            wallet, amount, LedgerEntry.EntryType.DEPOSIT, referenceId
        );
        entry = ledgerEntryRepository.save(entry);
        
        // 4. Update wallet balance (SECOND - derived from ledger)
        wallet.credit(amount);
        walletRepository.save(wallet);
        
        // Transaction commits here
        return entry;
    }
}
```

---

## Query Performance Expectations

### Read Operations (No Lock)
- `findByEmail()`: O(log n) - unique index
- `findByUserId()`: O(log n) - unique index
- `findByReferenceId()`: O(log n) - unique index
- `findByWalletIdOrderByCreatedAtAsc()`: O(k log n) where k = result size

### Write Operations (With Lock)
- `findByIdForUpdate()`: O(log n) + lock acquisition time
- Lock acquisition: instant if no contention, waits if wallet already locked
- Lock timeout: database-dependent (30-60s typical)

### Concurrent Access
- Multiple reads: No blocking
- Read + Write: Read sees committed data, not blocked
- Multiple writes (same wallet): Serialized (one waits for other)

---

## Testing Considerations

When writing tests for services (Step 3):

```java
@DataJpaTest  // For repository tests
class WalletRepositoryTest {
    
    @Test
    void findByIdForUpdate_acquiresPessimisticLock() {
        // Test lock behavior
        // Requires multiple threads/transactions
    }
}

@SpringBootTest  // For integration tests
@Transactional
class TransactionServiceTest {
    
    @Test
    void concurrentDeposits_preventLostUpdates() {
        // Test concurrent transaction safety
    }
}
```

---

## Next Steps (Step 3: Service Layer)

Service layer will:
1. Use `@Transactional` to manage transaction boundaries
2. Call `findByIdForUpdate()` before balance modifications
3. Check `existsByReferenceId()` for idempotency
4. Create LedgerEntry BEFORE updating Wallet balance
5. Handle business validation and exceptions

---

## Compliance & Audit

This repository design supports:
- **SOX Compliance:** Immutable audit trail
- **PCI DSS:** Secure transaction logging
- **GDPR:** User data access patterns
- **Financial Regulations:** Complete transaction history

---

**Repository layer complete and ready for service implementation! ✅**
