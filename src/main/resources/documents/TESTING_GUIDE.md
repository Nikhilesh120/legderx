# LedgerX Lite — Testing Guide

## Test Stack

| Layer | Framework | DB |
|---|---|---|
| Unit (service) | JUnit 5 + Mockito | None |
| Unit (JWT) | JUnit 5 | None |
| Controller slice | @WebMvcTest + MockMvc | None |
| Integration | @SpringBootTest | H2 in-memory |

---

## Run Tests

```bash
# All tests
mvn test

# Specific class
mvn test -Dtest=TransactionServiceTest

# With coverage report
mvn test jacoco:report
# Report at: target/site/jacoco/index.html
```

---

## Test Coverage Map

### TransactionServiceTest (unit)

| Test | Invariant Covered |
|------|------------------|
| nullAmount | INV-5 |
| zeroAmount | INV-5 |
| negativeAmount | INV-5 |
| nullReferenceId | INV-2 |
| blankReferenceId | INV-2 |
| walletNotFound | INV-WALLET-EXISTS |
| idempotentDeposit | INV-2 idempotency |
| suspendedUser (deposit) | INV-A |
| successfulDeposit | INV-1 (ledger before balance) |
| nullAmount (withdraw) | INV-5 |
| suspendedUser (withdraw) | INV-A |
| insufficientBalance (withdraw) | INV-10 |
| successfulWithdraw | INV-1, INV-10 |
| idempotentWithdraw | INV-2 idempotency |
| sameWallet (transfer) | INV-D |
| nullRef (transfer) | INV-2 |
| currencyMismatch | INV-C |
| insufficientBalance (transfer) | INV-10 |
| fromWalletSuspended | INV-A |
| moneyConservation | INV-8b |
| idempotentTransfer | INV-2 idempotency |

### UserServiceTest (unit)

| Test | What It Verifies |
|------|-----------------|
| duplicateEmail | Duplicate prevention |
| passwordEncoded | BCrypt encoding, no plaintext |
| newUserIsActive | Initial status |
| suspendUser | Status change |
| bcryptNotReversible | BCrypt correctness |

### WalletControllerTest (slice)

| Test | HTTP Contract |
|------|--------------|
| getWalletNotFound | 404 mapping |
| depositMissingAmount | 400 DTO validation |
| depositMissingRef | 400 DTO validation |
| depositInactiveWallet | 409 from IllegalStateException |
| withdrawInsufficientBalance | 409 mapping |
| transferSameWallet | 400 from IllegalArgumentException |
| transactionHistory | 200 with list |
| unauthenticated | 401 without JWT |

### JwtTokenProviderTest (unit)

| Test | What It Verifies |
|------|-----------------|
| generateAndValidate | Token round-trip |
| extractEmail | Claim extraction |
| extractUserId | Custom claim |
| tamperedToken | Signature validation |
| nullToken | Null safety |
| expiredToken | Expiry enforcement |
| shortSecret | Construction guard |

---

## Manual Smoke Test (Swagger UI)

```
1. Open: http://localhost:8080/api/swagger-ui/index.html

2. POST /users/register
   { "email": "test@test.com", "password": "Password123!", "currency": "USD" }

3. POST /auth/login
   { "email": "test@test.com", "password": "Password123!" }
   → Copy the token

4. Click "Authorize" → paste: Bearer <token>

5. POST /wallets/1/deposit
   { "amount": 500, "referenceId": "DEP-001", "description": "Test" }

6. POST /wallets/1/withdraw
   { "amount": 100, "referenceId": "WDR-001", "description": "Test" }

7. GET /wallets/1 → balance should be 400.0000

8. GET /wallets/1/transactions → should show 2 entries

9. Retry POST /wallets/1/deposit with same referenceId "DEP-001"
   → Should return same transactionId (idempotency)

10. POST /wallets/1/deposit with amount: -50
    → Should return 400 INVARIANT-5 VIOLATION
```

---

## Adding New Tests

Place test files in:
```
src/test/java/com/ledgerxlite/
  service/     ← unit tests (Mockito, no Spring)
  controller/  ← @WebMvcTest slice tests
  repository/  ← @DataJpaTest slice tests
  security/    ← JWT unit tests
```

All tests use H2 (via `application-test.yml`). No external DB needed.
