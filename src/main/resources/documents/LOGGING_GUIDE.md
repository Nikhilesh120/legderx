# Logging Guide - LedgerX Lite

## Overview

LedgerX Lite has comprehensive, step-by-step logging at every layer:

1. **HTTP Layer** - Request/Response logging
2. **Security Layer** - Authentication/Authorization events
3. **Service Layer** - Business logic step-by-step execution
4. **Repository Layer** - Database operations
5. **Transaction Layer** - Financial operation audit trail

## Log Files Generated

All log files are written to the `./logs/` directory:

| File | Purpose | Rotation | Retention |
|------|---------|----------|-----------|
| `application.log` | All application logs | 50MB/daily | 30 days |
| `error.log` | Errors only | 10MB/daily | 90 days |
| `transactions.log` | Financial operations audit | 100MB/daily | 365 days |
| `security.log` | Authentication/authorization | 50MB/daily | 365 days |
| `performance.log` | Slow operations (>1s) | 20MB/daily | 30 days |

## Log Levels by Profile

### Local Profile (`-Dspring.profiles.active=local`)
```
Root: INFO
Application (com.ledgerxlite): DEBUG
Security: DEBUG
SQL Queries: DEBUG
Transaction Management: DEBUG
```

**Use When**: Local development, debugging issues  
**Output**: Console + Files  
**Format**: Colored console, human-readable

### Docker Profile (`-Dspring.profiles.active=docker`)
```
Root: INFO
Application: INFO
TransactionService: DEBUG
SQL Queries: WARN (disabled)
```

**Use When**: Docker Compose deployment  
**Output**: Console + Files  
**Format**: Human-readable

### Production Profile (`-Dspring.profiles.active=prod`)
```
Root: WARN
Application: INFO
TransactionService: INFO
SQL Queries: ERROR (disabled)
```

**Use When**: Production deployment  
**Output**: JSON (stdout) + Files  
**Format**: Structured JSON (for log aggregators)

## Example Log Output

### Deposit Operation (Step-by-Step)

```
2024-02-18 10:30:15.123 [http-nio-8080-exec-1] INFO  TransactionService [traceId=abc123] [userId=1] 
=== DEPOSIT OPERATION START ===

2024-02-18 10:30:15.124 INFO  TransactionService - Step 1: Validating deposit request 
- walletId=1, amount=100.00, referenceId=dep-001, description=Initial deposit

2024-02-18 10:30:15.125 DEBUG TransactionService - Step 2: Validating positive amount (INVARIANT-5)

2024-02-18 10:30:15.125 DEBUG TransactionService - Step 2: ✓ Amount validation passed

2024-02-18 10:30:15.126 DEBUG TransactionService - Step 3: Checking idempotency (INVARIANT-2) 
- referenceId=dep-001

2024-02-18 10:30:15.127 DEBUG TransactionService - Step 3: ✓ No existing entry found, proceeding with new deposit

2024-02-18 10:30:15.128 DEBUG TransactionService - Step 4: Acquiring pessimistic write lock on wallet 
(INVARIANT-4) - walletId=1

2024-02-18 10:30:15.135 INFO  TransactionService - Step 4: ✓ Lock acquired 
- walletId=1, currentBalance=0.00, version=0

2024-02-18 10:30:15.136 DEBUG TransactionService - Step 5: Balance before deposit: 0.00

2024-02-18 10:30:15.137 INFO  TransactionService - Step 6: Creating ledger entry 
(INVARIANT-1 - ledger before balance)

2024-02-18 10:30:15.145 INFO  TransactionService - Step 6: ✓ Ledger entry created 
- entryId=1, amount=100.00, type=DEPOSIT

2024-02-18 10:30:15.146 INFO  TransactionService - Step 7: Updating wallet balance

2024-02-18 10:30:15.152 INFO  TransactionService - Step 7: ✓ Wallet balance updated 
- oldBalance=0.00, newBalance=100.00, increase=100.00

2024-02-18 10:30:15.153 DEBUG TransactionService - Step 8: Verifying post-conditions

2024-02-18 10:30:15.153 DEBUG TransactionService - Step 8: ✓ Post-condition verified 
- balance matches expected

2024-02-18 10:30:15.154 INFO  TransactionService 
=== DEPOSIT OPERATION SUCCESS ===

2024-02-18 10:30:15.154 INFO  TransactionService - Summary: walletId=1, entryId=1, 
amount=100.00, finalBalance=100.00, referenceId=dep-001
```

### Authentication Event

```
2024-02-18 10:29:30.456 DEBUG JwtAuthenticationFilter - 🔒 Authentication filter: POST /api/auth/login

2024-02-18 10:29:30.457 DEBUG JwtAuthenticationFilter - No JWT token in request 
(public endpoint or unauthenticated)

2024-02-18 10:29:30.500 INFO  UserService - User authentication attempt: email=alice@example.com

2024-02-18 10:29:30.520 INFO  UserService - ✓ Password verification successful for user: alice@example.com

2024-02-18 10:29:30.521 INFO  JwtTokenProvider - Generating JWT token for userId=1, email=alice@example.com

2024-02-18 10:29:30.535 INFO  JwtTokenProvider - ✓ JWT token generated, expires=2024-02-19T10:29:30Z
```

### Error Example

```
2024-02-18 10:31:45.789 ERROR TransactionService - Step 6: ✗ Insufficient balance 
- required=500.00, available=100.00, shortfall=400.00

2024-02-18 10:31:45.790 INFO  TransactionService 
=== WITHDRAWAL OPERATION FAILED (INSUFFICIENT BALANCE) ===

2024-02-18 10:31:45.791 ERROR GlobalExceptionHandler - Handling IllegalStateException: Insufficient balance

2024-02-18 10:31:45.792 ERROR GlobalExceptionHandler - Returning 409 CONFLICT to client
```

## Aspect-Based Automatic Logging

All service methods are automatically wrapped with detailed logging via `LoggingAspect`:

```
╔══════════════════════════════════════════════════════════════
║ SERVICE CALL: TransactionService.deposit
║ Execution ID: 1708255815123-42
║ Thread: http-nio-8080-exec-1
║ Parameters:
║   - walletId: 1
║   - amount: 100.00
║   - referenceId: dep-001
║   - description: Initial deposit
╠══════════════════════════════════════════════════════════════
[... step-by-step logs ...]
╠══════════════════════════════════════════════════════════════
║ ✓ SUCCESS
║ Return Value: LedgerEntry(id=1, amount=100.00)
║ Execution Time: 45 ms
╚══════════════════════════════════════════════════════════════
```

## MDC (Mapped Diagnostic Context)

Every log line automatically includes contextual information:

- **traceId**: Unique ID per HTTP request (from X-Request-ID header or generated)
- **userId**: Authenticated user's ID
- **userEmail**: Authenticated user's email
- **executionId**: Unique ID per service method call

Example:
```
[traceId=abc123] [userId=1] [userEmail=alice@example.com]
```

## Configuration

### Enable/Disable Logging

Edit `src/main/resources/logback-spring.xml`:

```xml
<!-- Change log level -->
<logger name="com.ledgerxlite.service.TransactionService" level="INFO" />

<!-- Disable file logging -->
<!-- Comment out appender references -->
```

### Change Log Directory

Set environment variable:
```bash
export LOG_DIR=/var/log/ledgerx
mvn spring-boot:run
```

Or in `application.yml`:
```yaml
logging:
  file:
    path: /var/log/ledgerx
```

### Increase/Decrease Verbosity

#### More Verbose (Debug Mode)
```yaml
logging:
  level:
    com.ledgerxlite: DEBUG
    org.hibernate.SQL: DEBUG
```

#### Less Verbose (Quiet Mode)
```yaml
logging:
  level:
    root: WARN
    com.ledgerxlite: INFO
```

## Viewing Logs

### Tail All Logs
```bash
tail -f logs/application.log
```

### View Errors Only
```bash
tail -f logs/error.log
```

### View Transaction Audit Trail
```bash
tail -f logs/transactions.log
```

### Search for Specific User
```bash
grep "userId=1" logs/application.log
```

### Search by Trace ID
```bash
grep "traceId=abc123" logs/application.log
```

### View Slow Operations
```bash
grep "SLOW OPERATION" logs/application.log
```

## Log Rotation

Logs automatically rotate based on:
- **Size**: When file reaches max size (e.g., 50MB)
- **Time**: Daily at midnight
- **Compression**: Old logs are gzipped
- **Retention**: Old logs deleted after retention period

Example rotated files:
```
logs/
├── application.log              (current)
├── application-2024-02-17.0.log.gz
├── application-2024-02-16.0.log.gz
├── error.log                    (current)
├── error-2024-02-17.0.log.gz
```

## Monitoring & Alerts

### Key Metrics to Monitor

1. **Error Rate**: Count of ERROR level logs per minute
2. **Slow Operations**: Count of logs with "> 1000 ms"
3. **Authentication Failures**: Count of "Authentication rejected"
4. **Insufficient Balance**: Count of "INSUFFICIENT BALANCE"
5. **Transaction Volume**: Count of "OPERATION SUCCESS"

### Sample Alert Rules

```yaml
# Prometheus alert rules
groups:
  - name: ledgerx_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(log_errors_total[5m]) > 10
        for: 5m
        annotations:
          summary: "High error rate detected"
          
      - alert: SlowTransactions
        expr: histogram_quantile(0.95, transaction_duration_seconds) > 1
        for: 5m
        annotations:
          summary: "95th percentile transaction time > 1s"
```

## Production Best Practices

### 1. Use Structured Logging (JSON)
In production, use JSON format for log aggregators:
```yaml
spring:
  profiles:
    active: prod
```

### 2. Ship Logs to Aggregator
Configure log shipping to:
- ELK Stack (Elasticsearch, Logstash, Kibana)
- Splunk
- Datadog
- CloudWatch Logs

### 3. Set Up Alerts
Monitor for:
- ERROR logs
- Slow operations (>1s)
- Authentication failures
- Invariant violations

### 4. Retain Transaction Logs Longer
Financial audit logs should be kept 1-7 years depending on regulations:
```xml
<maxHistory>2555</maxHistory>  <!-- 7 years -->
```

### 5. Mask Sensitive Data
Passwords, tokens, and secrets are automatically masked:
```
║   - password: [REDACTED]
║   - token: [REDACTED]
```

## Troubleshooting

### No Log Files Created

**Check**:
1. Log directory exists and is writable: `ls -la logs/`
2. Correct profile active: `grep "Active profile" logs/application.log`
3. Logback configuration loaded: `grep "logback" logs/application.log`

**Fix**:
```bash
mkdir -p logs
chmod 755 logs
```

### Logs Too Verbose

**Solution**: Change log level in `application-{profile}.yml`:
```yaml
logging:
  level:
    com.ledgerxlite: INFO  # Was DEBUG
```

### Missing Transaction Logs

**Check**: TransactionService logger is configured:
```xml
<logger name="com.ledgerxlite.service.TransactionService" level="INFO" />
```

### Slow Log Writing (Performance Issue)

**Solution**: Ensure async appenders are used:
```xml
<appender name="ASYNC_FILE_ALL" class="ch.qos.logback.classic.AsyncAppender">
  <queueSize>512</queueSize>
  <appender-ref ref="FILE_ALL" />
</appender>
```

## Log Analysis Examples

### Find All Failed Transactions
```bash
grep "OPERATION FAILED" logs/transactions.log
```

### Count Deposits Today
```bash
grep "DEPOSIT OPERATION SUCCESS" logs/transactions.log | \
  grep "$(date +%Y-%m-%d)" | wc -l
```

### Find User's Activity
```bash
grep "userId=1" logs/application.log | \
  grep -E "(DEPOSIT|WITHDRAWAL|TRANSFER)"
```

### Calculate Average Transaction Time
```bash
grep "Execution Time:" logs/application.log | \
  awk '{sum+=$NF} END {print "Average:", sum/NR, "ms"}'
```

## Security Considerations

### What's Logged
✅ Transaction amounts and IDs  
✅ User IDs and emails  
✅ Operation success/failure  
✅ Authentication attempts

### What's NOT Logged
❌ Plain-text passwords (always masked)  
❌ JWT tokens (masked as [REDACTED])  
❌ Credit card numbers (N/A for this app)  
❌ Full stack traces in production (summarized)

### Log Access Control
Ensure log files have restricted permissions:
```bash
chmod 640 logs/*.log
chown ledgerx:ledgerx logs/*.log
```

## Summary

LedgerX Lite provides:
- ✅ **5 specialized log files** for different purposes
- ✅ **Step-by-step operation logging** for debugging
- ✅ **Automatic aspect-based logging** for all methods
- ✅ **MDC context** in every log line
- ✅ **Automatic log rotation** and retention
- ✅ **Sensitive data masking**
- ✅ **Performance tracking** (slow operation warnings)
- ✅ **Profile-specific** verbosity levels

All logs are production-ready and audit-compliant for financial applications.
