# Database-Level Safety Constraints

## Overview

This document specifies database constraints that enforce financial invariants at the storage layer.

**Philosophy:** Defense in depth - even if application code has bugs, the database prevents corruption.

---

## Current Database Constraints (Already Implemented via JPA)

### Users Table

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Indexes
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
```

**Constraints:**
- ✅ PRIMARY KEY on `id` (uniqueness, not null)
- ✅ UNIQUE on `email` (INVARIANT-6 support)
- ✅ NOT NULL on critical columns

---

### Wallets Table

```sql
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(19,4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) 
        REFERENCES users(id)
);

-- Indexes
CREATE UNIQUE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_currency ON wallets(currency);
```

**Constraints:**
- ✅ PRIMARY KEY on `id`
- ✅ UNIQUE on `user_id` (INVARIANT-6: one wallet per user)
- ✅ NOT NULL on all critical columns
- ✅ FOREIGN KEY to users (referential integrity)
- ✅ `version` for optimistic locking support

---

### Ledger Entries Table

```sql
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_entry_wallet FOREIGN KEY (wallet_id) 
        REFERENCES wallets(id)
);

-- Indexes
CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at);
CREATE UNIQUE INDEX idx_ledger_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_type ON ledger_entries(type);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries(wallet_id, created_at);
```

**Constraints:**
- ✅ PRIMARY KEY on `id`
- ✅ UNIQUE on `reference_id` (INVARIANT-2: idempotency)
- ✅ NOT NULL on critical columns
- ✅ FOREIGN KEY to wallets (orphaned entries prevented)
- ✅ Composite index for efficient history queries

---

## Proposed Additional Constraints

### 1. Check Constraint: Non-Negative Balance (INVARIANT-10)

**SQL:**
```sql
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_balance_non_negative 
CHECK (balance >= 0);
```

**Purpose:**
- Enforces INVARIANT-10 at database level
- Prevents ANY code from creating negative balances
- Even admin scripts cannot violate this

**Pros:**
- ✅ Absolute guarantee (cannot be bypassed)
- ✅ Catches bugs in application code
- ✅ Protects against SQL injection
- ✅ Prevents accidental manual queries from corrupting data

**Cons:**
- ⚠️ Performance impact: Checked on every UPDATE
- ⚠️ Cannot temporarily go negative (even in multi-step operations)
- ⚠️ May complicate future features (e.g., overdraft protection)

**Recommendation:** **IMPLEMENT**
- The performance impact is negligible (simple comparison)
- Multi-step operations are wrapped in @Transactional (no visibility of intermediate state)
- If overdraft is needed later, change to `CHECK (balance >= overdraft_limit)`

---

### 2. Check Constraint: Valid Currency Codes (INVARIANT-7)

**SQL:**
```sql
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_currency_format
CHECK (currency ~ '^[A-Z]{3,10}$');
```

**Purpose:**
- Enforces basic currency format (3-10 uppercase letters)
- Prevents typos like 'usd' or 'U$D'

**Pros:**
- ✅ Prevents garbage data
- ✅ Validates on INSERT and UPDATE

**Cons:**
- ⚠️ Doesn't validate against actual ISO 4217 codes
- ⚠️ May need adjustment for crypto (e.g., 'USDT', 'USDC')

**Recommendation:** **IMPLEMENT**
- Basic validation is better than none
- Application code does more thorough validation
- Regex is simple and performant

**Alternative (Stricter):**
```sql
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_currency_iso4217
CHECK (currency IN ('USD', 'EUR', 'GBP', 'JPY', 'CNY', ...));  -- Explicit list
```

---

### 3. Check Constraint: Valid Entry Types (INVARIANT-3)

**SQL:**
```sql
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_entry_type_valid
CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT', 'FEE', 'REFUND', 'ADJUSTMENT'));
```

**Purpose:**
- Prevents invalid entry types
- Database-level validation of enum values

**Pros:**
- ✅ Prevents typos in manual queries
- ✅ Catches bugs if enum values change

**Cons:**
- ⚠️ Must be updated when adding new entry types
- ⚠️ Migration required for schema changes

**Recommendation:** **OPTIONAL**
- JPA already validates enum values
- Only useful if direct SQL access is common
- Consider if regulatory compliance requires it

---

### 4. Check Constraint: Non-Zero Amounts (INVARIANT-5)

**SQL:**
```sql
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_entry_amount_non_zero
CHECK (amount != 0);
```

**Purpose:**
- Zero-amount transactions are meaningless
- Prevents noise in transaction log

**Pros:**
- ✅ Simple check
- ✅ Prevents garbage data

**Cons:**
- ⚠️ Very minor - application already validates

**Recommendation:** **IMPLEMENT**
- Low cost, high value for data quality

---

### 5. Partial Index: Active Users (Performance)

**SQL:**
```sql
CREATE INDEX idx_users_active ON users(email) 
WHERE status = 'ACTIVE';
```

**Purpose:**
- Faster lookups for active users
- Most queries filter on active status

**Pros:**
- ✅ Smaller index (only active users)
- ✅ Faster authentication queries

**Cons:**
- ⚠️ Requires PostgreSQL 11+ (partial index support)

**Recommendation:** **IMPLEMENT** (if using PostgreSQL 11+)

---

### 6. Trigger: Prevent Ledger Entry Updates (INVARIANT-3)

**SQL:**
```sql
CREATE OR REPLACE FUNCTION prevent_ledger_update()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'INVARIANT-3 VIOLATION: Ledger entries are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'INVARIANT-3 VIOLATION: Ledger entries cannot be deleted';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_update();
```

**Purpose:**
- Absolute enforcement of append-only invariant
- Even admin cannot update/delete (must disable trigger first)

**Pros:**
- ✅ Strongest possible enforcement
- ✅ Audit trail cannot be tampered with
- ✅ Regulatory compliance (SOX, etc.)

**Cons:**
- ⚠️ Corrections require offsetting entries (intentional)
- ⚠️ Complicates disaster recovery (need to drop trigger)
- ⚠️ Testing may require trigger disable

**Recommendation:** **IMPLEMENT** (with documented emergency procedures)

**Emergency Override:**
```sql
-- Only in catastrophic situations
ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_immutable;
-- Perform emergency fix
ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_immutable;
-- Log action in audit trail
```

---

### 7. Materialized View: Balance Reconciliation (INVARIANT-1)

**SQL:**
```sql
CREATE MATERIALIZED VIEW wallet_balance_reconciliation AS
SELECT 
    w.id AS wallet_id,
    w.balance AS wallet_balance,
    COALESCE(SUM(le.amount), 0) AS ledger_balance,
    w.balance - COALESCE(SUM(le.amount), 0) AS discrepancy
FROM wallets w
LEFT JOIN ledger_entries le ON le.wallet_id = w.id
GROUP BY w.id, w.balance;

CREATE UNIQUE INDEX idx_reconciliation_wallet 
ON wallet_balance_reconciliation(wallet_id);
```

**Purpose:**
- Periodic verification of INVARIANT-1
- Detect balance-ledger inconsistencies

**Usage:**
```sql
-- Refresh periodically (e.g., hourly cron job)
REFRESH MATERIALIZED VIEW wallet_balance_reconciliation;

-- Check for discrepancies
SELECT * FROM wallet_balance_reconciliation
WHERE discrepancy != 0;
-- Should return 0 rows
```

**Pros:**
- ✅ Efficient batch verification
- ✅ Historical tracking (if logged)
- ✅ Compliance reporting

**Cons:**
- ⚠️ Not real-time (materialized views are snapshots)
- ⚠️ Refresh requires resources

**Recommendation:** **IMPLEMENT** (as monitoring tool, not enforcement)

---

## Constraint Enforcement Comparison

| Invariant | Service Layer | Database Layer | Recommendation |
|-----------|---------------|----------------|----------------|
| INVARIANT-1 (Balance=Ledger) | Post-check | Materialized view | Both |
| INVARIANT-2 (Idempotency) | Pre-check | UNIQUE constraint | Both (✅) |
| INVARIANT-3 (Append-only) | No delete calls | Trigger | Both |
| INVARIANT-4 (Locking) | PESSIMISTIC_WRITE | N/A | Service only |
| INVARIANT-5 (Positive) | Pre-check | CHECK (amount != 0) | Both |
| INVARIANT-6 (One wallet) | Pre-check | UNIQUE + FK | Both (✅) |
| INVARIANT-7 (Currency) | Constructor | CHECK (regex) | Both |
| INVARIANT-8 (Atomicity) | @Transactional | N/A | Service only |
| INVARIANT-9 (Deadlock-free) | Lock ordering | N/A | Service only |
| INVARIANT-10 (No negative) | Pre/post checks | CHECK (balance >= 0) | Both |

**Legend:**
- Both (✅) = Already implemented
- Both = Recommended to implement
- Service only = Cannot be enforced at DB level

---

## Migration Script

**File:** `V002__Add_Financial_Constraints.sql`

```sql
-- Migration: Add database-level financial safety constraints
-- Version: 2
-- Description: Enforce invariants at storage layer

BEGIN;

-- INVARIANT-10: No negative balances
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_balance_non_negative 
CHECK (balance >= 0);

-- INVARIANT-7: Valid currency format
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_currency_format
CHECK (currency ~ '^[A-Z]{3,10}$');

-- INVARIANT-5: No zero-amount transactions
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_entry_amount_non_zero
CHECK (amount != 0);

-- INVARIANT-3: Immutable ledger entries (prevent updates/deletes)
CREATE OR REPLACE FUNCTION prevent_ledger_update()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'INVARIANT-3 VIOLATION: Ledger entries are immutable. Use offsetting entries for corrections.';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'INVARIANT-3 VIOLATION: Ledger entries cannot be deleted. This is an append-only log.';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_update();

-- INVARIANT-1: Reconciliation view
CREATE MATERIALIZED VIEW wallet_balance_reconciliation AS
SELECT 
    w.id AS wallet_id,
    w.balance AS wallet_balance,
    COALESCE(SUM(le.amount), 0) AS ledger_balance,
    w.balance - COALESCE(SUM(le.amount), 0) AS discrepancy,
    COUNT(le.id) AS entry_count
FROM wallets w
LEFT JOIN ledger_entries le ON le.wallet_id = w.id
GROUP BY w.id, w.balance;

CREATE UNIQUE INDEX idx_reconciliation_wallet 
ON wallet_balance_reconciliation(wallet_id);

-- Performance: Partial index for active users
CREATE INDEX idx_users_active ON users(email) 
WHERE status = 'ACTIVE';

COMMIT;

-- Post-migration verification
DO $$
DECLARE
    discrepancy_count INTEGER;
BEGIN
    REFRESH MATERIALIZED VIEW wallet_balance_reconciliation;
    
    SELECT COUNT(*) INTO discrepancy_count
    FROM wallet_balance_reconciliation
    WHERE discrepancy != 0;
    
    IF discrepancy_count > 0 THEN
        RAISE WARNING 'Balance discrepancies detected in % wallets. Review required.', discrepancy_count;
    ELSE
        RAISE NOTICE 'All wallet balances are consistent with ledger entries.';
    END IF;
END $$;
```

---

## Monitoring & Alerts

### Daily Reconciliation Job

```sql
-- Run daily (e.g., 2 AM UTC)
REFRESH MATERIALIZED VIEW wallet_balance_reconciliation;

-- Check for violations
SELECT 
    wallet_id,
    wallet_balance,
    ledger_balance,
    discrepancy,
    entry_count
FROM wallet_balance_reconciliation
WHERE discrepancy != 0
ORDER BY ABS(discrepancy) DESC;
```

**Alert if ANY rows returned:** Critical financial corruption detected

### Constraint Violation Monitoring

```sql
-- Log all constraint violations (PostgreSQL)
CREATE TABLE constraint_violations_log (
    id SERIAL PRIMARY KEY,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    table_name TEXT,
    constraint_name TEXT,
    error_message TEXT,
    query_text TEXT
);

-- Requires PostgreSQL logging configuration
-- log_statement = 'all'
-- log_min_error_statement = 'error'
```

---

## Rollback Plan

If constraints cause production issues:

```sql
-- Emergency rollback (execute in this order)

-- 1. Disable trigger (most likely to cause issues)
ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_immutable;

-- 2. Drop check constraints
ALTER TABLE wallets DROP CONSTRAINT chk_wallet_balance_non_negative;
ALTER TABLE wallets DROP CONSTRAINT chk_wallet_currency_format;
ALTER TABLE ledger_entries DROP CONSTRAINT chk_entry_amount_non_zero;

-- 3. Drop materialized view
DROP MATERIALIZED VIEW wallet_balance_reconciliation;

-- 4. Drop function
DROP FUNCTION prevent_ledger_update();
```

**Note:** UNIQUE and FOREIGN KEY constraints should NOT be rolled back - they're critical.

---

## Conclusion

### Recommended Immediate Implementation

1. ✅ **CHECK (balance >= 0)** - Critical for INVARIANT-10
2. ✅ **CHECK (currency ~ '^[A-Z]{3,10}$')** - Prevents garbage data
3. ✅ **CHECK (amount != 0)** - Data quality
4. ✅ **Trigger on ledger_entries** - Enforces INVARIANT-3
5. ✅ **Materialized view** - Monitoring INVARIANT-1

### Implementation Strategy

1. **Test in staging first** (with production-like load)
2. **Monitor performance impact** (expect negligible)
3. **Deploy during low-traffic window**
4. **Run reconciliation immediately after**
5. **Keep rollback plan ready**

### Database vs Service Enforcement

**Service Layer:**
- Business logic validation
- Complex invariants (atomicity, locking)
- Performance optimization (fewer DB round-trips)

**Database Layer:**
- Simple, declarative constraints
- Last line of defense against bugs
- Protection against SQL injection
- Compliance and audit requirements

**Best Practice:** Enforce at BOTH layers for defense in depth.
