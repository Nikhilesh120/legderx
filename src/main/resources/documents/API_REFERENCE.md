# LedgerX Lite — API Reference

**Base URL:** `http://localhost:8080/api`
**Auth:** `Authorization: Bearer <jwt_token>` on all protected endpoints
**Swagger UI:** `http://localhost:8080/api/swagger-ui/index.html`

---

## Authentication

### POST /auth/login
Authenticate and receive a JWT token.

**Public — no token required.**

```json
Request:
{
  "email":    "user@example.com",
  "password": "yourpassword"
}

Response 200:
{
  "token":        "eyJhbGci...",
  "email":        "user@example.com",
  "userId":       1,
  "expiresApprox": "2024-01-02T12:00:00Z"
}
```

| Code | Reason |
|------|--------|
| 200  | Login successful |
| 401  | Wrong email or password |
| 409  | Account is SUSPENDED or CLOSED |

---

## Users

### POST /users/register
Create a new user account and wallet.

**Public — no token required.**

```json
Request:
{
  "email":    "alice@example.com",
  "password": "SecurePass123!",
  "currency": "USD"
}

Response 201:
{
  "userId":    1,
  "email":     "alice@example.com",
  "status":    "ACTIVE",
  "walletId":  1,
  "balance":   0.0000,
  "currency":  "USD",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

### GET /users/{userId}
Get user details. **Requires JWT.**

### POST /users/{userId}/suspend
Suspend a user account. **Requires JWT.**

### POST /users/{userId}/activate
Re-activate a suspended account. **Requires JWT.**

---

## Wallets

### GET /wallets/{walletId}
Get wallet balance and details. **Requires JWT.**

```json
Response 200:
{
  "walletId":  1,
  "userId":    1,
  "balance":   1500.0000,
  "currency":  "USD",
  "updatedAt": "2024-01-01T11:00:00Z"
}
```

### GET /wallets/{walletId}/transactions
Full transaction history, oldest first. **Requires JWT.**

```json
Response 200: [ { "transactionId": 1, "amount": 500.00, "type": "DEPOSIT", ... } ]
```

---

## Transactions

All transaction endpoints are **idempotent** via `referenceId`.
Submitting the same `referenceId` twice always returns the same entry — no duplicate debit/credit.

### POST /wallets/{walletId}/deposit

```json
Request:
{
  "amount":      500.00,
  "referenceId": "DEP-20240101-001",
  "description": "Initial funding"
}

Response 200:
{
  "transactionId": 1,
  "walletId":      1,
  "amount":        500.0000,
  "type":          "DEPOSIT",
  "referenceId":   "DEP-20240101-001",
  "description":   "Initial funding",
  "createdAt":     "2024-01-01T10:05:00Z"
}
```

| Code | Reason |
|------|--------|
| 200  | Deposited (or idempotent repeat) |
| 400  | amount ≤ 0 or missing referenceId |
| 401  | Missing/invalid JWT |
| 409  | User account not ACTIVE |

### POST /wallets/{walletId}/withdraw

```json
Request:
{
  "amount":      200.00,
  "referenceId": "WDR-20240101-001",
  "description": "ATM withdrawal"
}
```

| Code | Reason |
|------|--------|
| 200  | Withdrawn (or idempotent repeat) |
| 400  | amount ≤ 0 or blank referenceId |
| 409  | Insufficient balance or user not ACTIVE |

### POST /wallets/{walletId}/transfer

```json
Request:
{
  "toWalletId":  2,
  "amount":      300.00,
  "referenceId": "TRF-20240101-001",
  "description": "Payment to Bob"
}

Response 200:
{
  "debit":  { "type": "TRANSFER_OUT", "amount": -300.0000, "referenceId": "TRF-20240101-001-OUT" },
  "credit": { "type": "TRANSFER_IN",  "amount":  300.0000, "referenceId": "TRF-20240101-001-IN"  },
  "amount": 300.0000,
  "referenceId": "TRF-20240101-001"
}
```

| Code | Reason |
|------|--------|
| 200  | Transferred (or idempotent repeat) |
| 400  | Same wallet, currency mismatch, bad amount |
| 409  | Insufficient balance, either user not ACTIVE |

### GET /transactions/{referenceId}
Look up any transaction by its referenceId. **Requires JWT.**

```
GET /transactions/DEP-20240101-001
→ 200 { TransactionResponse }
→ 404 if not found
```

---

## Error Response Format

All errors return a consistent JSON body:

```json
{
  "error":     "CONFLICT",
  "message":   "INVARIANT-10 VIOLATION: Insufficient balance. Wallet 1: balance=100.00, required=500.00",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

| HTTP Code | `error` field         | When |
|-----------|----------------------|------|
| 400       | BAD_REQUEST          | Invalid input |
| 401       | (Spring default)     | Missing/invalid JWT |
| 404       | NOT_FOUND            | Resource not found |
| 409       | CONFLICT             | Business rule violation |
| 500       | INTERNAL_SERVER_ERROR | Unexpected error |

---

## Idempotency Guide

Every client must generate a unique `referenceId` per logical operation:

```
Good:  "DEP-" + UUID           → globally unique
Good:  "WDR-" + orderId        → tied to business event
Bad:   "deposit"               → collides between requests
Bad:   timestamp only          → collides in load-test scenarios
```

**Safe retry pattern:**
```
1. Generate referenceId before calling the API
2. Call the endpoint
3. If network error → retry with SAME referenceId
4. If 200 → check response (new or existing entry, both are correct)
5. If 4xx → fix the request, use a NEW referenceId
```
