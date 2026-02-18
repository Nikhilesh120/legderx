-- V2__create_wallets_table.sql
-- Wallets table: one per user, balance is denormalised projection of ledger

CREATE TABLE wallets (
    id         BIGSERIAL           PRIMARY KEY,
    user_id    BIGINT              NOT NULL UNIQUE REFERENCES users(id),
    balance    NUMERIC(19, 4)      NOT NULL DEFAULT 0.0000
               CHECK (balance >= 0),
    currency   VARCHAR(10)         NOT NULL,
    updated_at TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    version    BIGINT              NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_wallets_user_id ON wallets (user_id);
CREATE        INDEX idx_wallets_currency ON wallets (currency);

COMMENT ON COLUMN wallets.balance IS
    'Denormalised cache of SUM(ledger_entries.amount). '
    'Source of truth is ledger_entries. '
    'Always updated in same transaction as the ledger write.';

COMMENT ON COLUMN wallets.version IS
    'JPA optimistic-lock version counter. Incremented on every UPDATE.';
