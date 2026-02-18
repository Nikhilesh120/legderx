-- V3__create_ledger_entries_table.sql
-- Ledger entries: immutable, append-only financial event log

CREATE TABLE ledger_entries (
    id           BIGSERIAL       PRIMARY KEY,
    wallet_id    BIGINT          NOT NULL REFERENCES wallets(id),
    amount       NUMERIC(19, 4)  NOT NULL CHECK (amount != 0),
    type         VARCHAR(50)     NOT NULL
                 CHECK (type IN ('DEPOSIT','WITHDRAWAL','TRANSFER_IN',
                                 'TRANSFER_OUT','FEE','REFUND','ADJUSTMENT')),
    reference_id VARCHAR(255)    NOT NULL UNIQUE,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Fast lookup by wallet (used in history queries)
CREATE INDEX idx_ledger_wallet_id      ON ledger_entries (wallet_id);
-- Fast idempotency check
CREATE UNIQUE INDEX idx_ledger_reference_id ON ledger_entries (reference_id);
-- Fast chronological queries per wallet
CREATE INDEX idx_ledger_wallet_created ON ledger_entries (wallet_id, created_at);
-- Useful for type-based reporting
CREATE INDEX idx_ledger_type           ON ledger_entries (type);

-- ── APPEND-ONLY ENFORCEMENT ──────────────────────────────────────────────────
-- Block UPDATE and DELETE at the database level.
-- This is a safety net on top of the application-level enforcement
-- (LedgerEntryRepository overrides all delete methods with UnsupportedOperationException).
-- Even if a future developer bypasses the repository, the ledger remains intact.

CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'ledger_entries is append-only. UPDATE and DELETE are forbidden. '
        'Attempted operation: % on entry id=%',
        TG_OP, OLD.id;
END;
$$;

CREATE TRIGGER trg_ledger_no_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER trg_ledger_no_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

COMMENT ON TABLE ledger_entries IS
    'Immutable, append-only financial event log. '
    'Every credit and debit is recorded here. '
    'wallet.balance must always equal SUM(amount) for that wallet.';

COMMENT ON COLUMN ledger_entries.amount IS
    'Positive = CREDIT (money in). Negative = DEBIT (money out). Never zero.';

COMMENT ON COLUMN ledger_entries.reference_id IS
    'Client-supplied idempotency key. Globally unique. '
    'For transfers: base-referenceId + ''-OUT'' (debit) / ''-IN'' (credit).';
