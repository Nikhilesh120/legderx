# Domain Model Design - LedgerX Lite

## Overview

The domain layer implements a **transaction-safe, append-only financial ledger** using three core entities:
- **User** - Account owners
- **Wallet** - Financial accounts holding balances
- **LedgerEntry** - Immutable transaction records (source of truth)

---

## Entity Relationships

```
User (1) ──────── (1) Wallet (1) ──────── (*) LedgerEntry
         owns              contains
```

- **One User → One Wallet**: Each user has exactly one wallet (1:1)
- **One Wallet → Many LedgerEntries**: Wallet contains all transaction history (1:N)

---

## Entity Details

### 1. User Entity

**Purpose**: Represents authenticated users who own wallets.

**Key Fields**:
- `id`: Auto-generated primary key
- `email`: Unique business identifier
- `passwordHash`: Hashed password (never plaintext)
- `status`: Account lifecycle (ACTIVE, SUSPENDED, CLOSED)
- `createdAt`: Account creation timestamp

**Indexes**:
- `idx_users_email` (unique): Fast lookup by email for authentication
- `idx_users_status`: Filter active/suspended users

**Design Decisions**:
- Email uniqueness enforced at database level
- UserStatus enum stored as STRING for readability in database
- No Lombok - explicit getters/setters for transparency
- Business methods (`suspend()`, `activate()`, `close()`) enforce state transitions
- Closed accounts cannot be reactivated (financial compliance)

---

### 2. Wallet Entity

**Purpose**: Financial account holding balance and currency information.

**Key Fields**:
- `id`: Auto-generated primary key
- `user`: One-to-one relationship with User
- `balance`: Current balance (BigDecimal, precision 19, scale 4)
- `currency`: ISO 4217 code (USD, EUR, BTC, etc.)
- `updatedAt`: Last balance modification timestamp
- `version`: Optimistic locking version

**Indexes**:
- `idx_wallets_user_id` (unique): One wallet per user
- `idx_wallets_currency`: Group wallets by currency

**Design Decisions**:
- **BigDecimal for all money**: Never use float/double (prevents rounding errors)
- **Precision 19, Scale 4**: Supports values from -999,999,999,999,999.9999 to +999,999,999,999,999.9999
  - Handles both fiat currencies (2 decimals) and cryptocurrencies (8 decimals)
- **No public balance setter**: Balance modified only through `credit()` and `debit()` methods
- **Optimistic locking (@Version)**: Prevents concurrent balance corruption
- **Balance is cached**: Source of truth is sum of LedgerEntry records
- **Business methods validate**: `credit()` and `debit()` enforce positive amounts and sufficient balance

**Critical Rule**: Balance updates must happen in same transaction as LedgerEntry creation.

---

### 3. LedgerEntry Entity

**Purpose**: Immutable, append-only transaction records. **This is the source of truth.**

**Key Fields**:
- `id`: Auto-generated primary key
- `wallet`: Reference to wallet (immutable)
- `amount`: Transaction amount (positive=credit, negative=debit)
- `type`: Entry classification (DEPOSIT, WITHDRAWAL, TRANSFER_IN, etc.)
- `referenceId`: Unique idempotency key
- `description`: Optional transaction memo
- `createdAt`: Entry creation timestamp

**Indexes**:
- `idx_ledger_wallet_id`: Query entries by wallet
- `idx_ledger_created_at`: Time-based queries
- `idx_ledger_reference_id` (unique): Idempotency enforcement
- `idx_ledger_type`: Filter by transaction type
- `idx_ledger_wallet_created`: Composite index for wallet transaction history

**Design Decisions**:
- **Fully immutable**: All fields marked `updatable = false` in JPA
- **No setters**: Only getters (except JPA-internal)
- **Constructor-based initialization**: All fields set at creation
- **Idempotency via referenceId**: Prevents duplicate transactions
  - Client provides unique referenceId (UUID recommended)
  - Database unique constraint prevents double-processing
- **Signed amounts**: Positive = credit, negative = debit
  - Simplifies balance calculations: `SUM(amount)`
- **No delete/update operations**: Enforced by making entity immutable
- **No @Version needed**: Immutable entities never update

**Entry Types**:
- `DEPOSIT`: External funds coming in
- `WITHDRAWAL`: External funds going out
- `TRANSFER_IN`: Internal transfer (receiving)
- `TRANSFER_OUT`: Internal transfer (sending)
- `FEE`: Service fee deduction
- `REFUND`: Refund of previous transaction
- `ADJUSTMENT`: Manual correction (should be rare)

---

## Financial Safety Guarantees

### 1. Immutability
- LedgerEntry records are **never updated or deleted**
- Corrections done via new offsetting entries
- Complete audit trail maintained forever

### 2. Idempotency
- `referenceId` unique constraint prevents duplicate transactions
- Clients can safely retry failed requests
- Exactly-once semantics guaranteed

### 3. Consistency
- Wallet balance = `SUM(LedgerEntry.amount)` for that wallet
- Optimistic locking prevents concurrent balance corruption
- All operations must be wrapped in database transactions

### 4. Precision
- BigDecimal used everywhere (no floating-point errors)
- Precision 19, scale 4 supports all major currencies
- Never round intermediate calculations

---

## Database Schema

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- Wallets table
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    balance DECIMAL(19,4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_currency ON wallets(currency);

-- Ledger entries table
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id),
    amount DECIMAL(19,4) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at);
CREATE INDEX idx_ledger_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_type ON ledger_entries(type);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries(wallet_id, created_at);
```

---

## Usage Patterns

### Creating a User and Wallet

```java
// Create user
User user = new User("alice@example.com", hashedPassword);

// Create wallet for user
Wallet wallet = new Wallet(user, "USD");
```

### Recording a Transaction

```java
// Generate unique reference ID (client-provided)
String referenceId = UUID.randomUUID().toString();

// Create ledger entry (deposit $100)
BigDecimal amount = new BigDecimal("100.00");
LedgerEntry entry = new LedgerEntry(
    wallet,
    amount,  // positive = credit
    LedgerEntry.EntryType.DEPOSIT,
    referenceId,
    "Initial deposit"
);

// Update wallet balance (must be same transaction)
wallet.credit(amount);
```

### Recording a Withdrawal

```java
String referenceId = UUID.randomUUID().toString();

// Create ledger entry (withdraw $50)
BigDecimal amount = new BigDecimal("-50.00");  // negative = debit
LedgerEntry entry = new LedgerEntry(
    wallet,
    amount,
    LedgerEntry.EntryType.WITHDRAWAL,
    referenceId,
    "ATM withdrawal"
);

// Update wallet balance
wallet.debit(new BigDecimal("50.00"));  // debit takes positive value
```

---

## Validation Rules

### User
- Email must be non-null and unique
- Password hash must be non-null
- Status must be valid enum value
- Created timestamp set on construction (immutable)

### Wallet
- User reference must be non-null and unique
- Balance must be non-null (defaults to zero)
- Currency must be non-null
- Balance precision: 19 digits total, 4 decimal places
- Concurrent updates prevented by optimistic locking

### LedgerEntry
- Wallet reference must be non-null
- Amount must be non-null and non-zero
- Type must be valid enum value
- Reference ID must be non-null, non-blank, and unique
- All fields immutable after construction
- No updates or deletes allowed

---

## Next Steps

Now that the domain layer is complete:

1. ✅ Domain entities created
2. ⏳ Create repositories (Step 2)
3. ⏳ Implement services (Step 3)
4. ⏳ Add REST controllers (Step 4)
5. ⏳ Add validation (Step 5)
6. ⏳ Add security (Step 6)
7. ⏳ Database migrations (Step 7)
