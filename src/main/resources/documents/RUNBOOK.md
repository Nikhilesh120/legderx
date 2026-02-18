# LedgerX Lite — Operations Runbook

## Monitoring Alerts

### Critical — Immediate Action Required

**INVARIANT-X VIOLATION in logs**
Any log line containing "INVARIANT" at level ERROR means the post-condition check failed inside a committed (or about to rollback) transaction. This should never happen in production.

```
Action: Check logs for walletId, referenceId, amounts.
        Run reconciliation query:
        SELECT w.id, w.balance, SUM(e.amount) AS ledger_sum
        FROM wallets w
        JOIN ledger_entries e ON e.wallet_id = w.id
        GROUP BY w.id
        HAVING w.balance != SUM(e.amount);
```

**Negative balance detected**
The `CHECK (balance >= 0)` constraint will prevent DB writes, but if somehow bypassed:
```sql
SELECT id, balance FROM wallets WHERE balance < 0;
```

**Incomplete transfer (debit without credit)**
```sql
SELECT reference_id
FROM ledger_entries
WHERE type = 'TRANSFER_OUT'
  AND REPLACE(reference_id, '-OUT', '-IN') NOT IN (
      SELECT reference_id FROM ledger_entries WHERE type = 'TRANSFER_IN'
  );
-- Should return 0 rows always.
```

---

## Reconciliation Query

Run nightly or on demand. Balance must equal ledger sum for every wallet.

```sql
SELECT
    w.id           AS wallet_id,
    w.balance      AS cached_balance,
    COALESCE(SUM(e.amount), 0) AS ledger_sum,
    w.balance - COALESCE(SUM(e.amount), 0) AS discrepancy
FROM wallets w
LEFT JOIN ledger_entries e ON e.wallet_id = w.id
GROUP BY w.id, w.balance
HAVING w.balance != COALESCE(SUM(e.amount), 0);
-- Expected: 0 rows
```

---

## Common Operational Tasks

### Suspend a User
```bash
curl -X POST http://localhost:8080/api/users/{userId}/suspend \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Check Transaction by referenceId
```bash
curl http://localhost:8080/api/transactions/{referenceId} \
  -H "Authorization: Bearer $TOKEN"
```

### View Wallet History
```bash
curl http://localhost:8080/api/wallets/{walletId}/transactions \
  -H "Authorization: Bearer $TOKEN"
```

---

## Database Maintenance

### Check Flyway Migration Status
```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Check Table Sizes
```sql
SELECT relname AS table, pg_size_pretty(pg_total_relation_size(relid)) AS size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

### Index Health
```sql
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

---

## Rollback Procedure

Flyway does not support automatic rollback. To undo a migration:

1. Write a new migration `V{N+1}__rollback_previous_change.sql`
2. Deploy it
3. Never delete or modify existing migration files

---

## JWT Token Issues

**Symptom:** All requests returning 401 after deployment
**Cause:** JWT_SECRET changed — all existing tokens invalid
**Resolution:** Users must log in again. Expected behavior after secret rotation.

**Symptom:** Tokens not expiring as expected
**Check:** `echo $JWT_EXPIRY_MS` on server — default is 86400000 (24h), production should be 3600000 (1h)
