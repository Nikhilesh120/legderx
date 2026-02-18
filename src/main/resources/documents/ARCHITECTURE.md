# LedgerX Lite — Architecture Decision Record

## System Overview

LedgerX Lite is a production-grade financial ledger REST API built with Spring Boot 3.2, PostgreSQL, and JWT authentication. It provides wallet management, deposits, withdrawals, and transfers with strong financial correctness guarantees.

## Core Design Principles

### 1. Append-Only Ledger as Source of Truth
`LedgerEntry` records are immutable and can never be updated or deleted. `Wallet.balance` is a denormalised cache derived from the ledger sum. Every mutation writes the ledger entry first, then updates the balance — both within one atomic transaction.

### 2. Pessimistic Locking Before Every Mutation
`SELECT … FOR UPDATE` is acquired on the wallet row before any balance read or write. This serialises all concurrent operations on the same wallet, eliminating lost-update races without relying on optimistic retry loops.

### 3. Idempotency via referenceId
Every mutating operation requires a client-supplied `referenceId`. The system checks for an existing entry with that ID (after acquiring the lock) and returns it unchanged if found. This makes all endpoints safe to retry on network failure.

### 4. Fail-Fast Inline Invariants
All financial invariants are enforced inline inside `TransactionService`. No external validators, no helper classes. Every violation throws a clearly labelled exception before touching the database.

### 5. Stateless Authentication
JWT tokens carry user identity. No server-side session state. Tokens are validated on every request by `JwtAuthenticationFilter`.

## Layer Responsibilities

```
HTTP Request
    │
    ▼
Controller (thin delegation — no logic)
    │
    ▼
Service (all invariants enforced here)
    │
    ├─ WalletRepository.findByIdForUpdate()   ← SELECT FOR UPDATE
    ├─ LedgerEntryRepository.save()           ← append ledger first
    └─ WalletRepository.save()               ← update balance second
    │
    ▼
PostgreSQL (unique constraints + CHECK constraints + no-mutation trigger)
```

## Key Technical Decisions

| Decision | Choice | Reason |
|---|---|---|
| ORM | Spring Data JPA / Hibernate | Standard, pessimistic lock support |
| Locking | PESSIMISTIC_WRITE | Eliminate lost updates under concurrency |
| Schema migrations | Flyway | Repeatable, versioned, auditable |
| Auth | JWT (JJWT 0.12.3) | Stateless, no session storage needed |
| Password hashing | BCrypt (cost 12) | Industry standard, resistant to GPU attacks |
| Money type | BigDecimal (19,4) | No floating-point rounding errors |
| API docs | Springdoc OpenAPI 2.2 | Auto-generated, always in sync |

## Transaction Execution Order (All Mutations)

```
1. Validate inputs (amount > 0, referenceId non-blank)
2. Acquire PESSIMISTIC_WRITE lock on wallet(s)
3. Check idempotency — after lock, sees committed state
4. Validate wallet state (ACTIVE, balance, currency)
5. Write LedgerEntry — source of truth first
6. Update Wallet.balance — derived value second
7. Verify post-conditions before commit
8. @Transactional commits or rolls back both writes atomically
```

## Database Constraints (Defence in Depth)

| Layer | Enforcement |
|---|---|
| Application | Inline checks in TransactionService |
| Domain | Wallet.debit() throws on negative balance |
| Database | CHECK (balance >= 0), CHECK (amount != 0), UNIQUE (reference_id) |
| Database | Trigger blocks UPDATE/DELETE on ledger_entries |
