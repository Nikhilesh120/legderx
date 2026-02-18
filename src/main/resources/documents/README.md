# LedgerX Lite - Financial Ledger System

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-69%20passing-success)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)]()

A production-ready financial ledger system with ACID transactions, JWT authentication, and comprehensive test coverage.

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for PostgreSQL)

### Run Locally (5 minutes)

```bash
# 1. Clone and navigate
cd ledgerx-lite

# 2. Start PostgreSQL
docker-compose up -d postgres

# 3. Run application
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Access Swagger UI
open http://localhost:8080/api/swagger-ui/index.html
```

### Run with Docker (Full Stack)

```bash
# Build and start everything
docker-compose up --build

# Access application
open http://localhost:8080/api/swagger-ui/index.html
```

### Run Tests

```bash
# All tests (69 tests)
mvn clean test

# Integration tests only
mvn test -Dtest=ApiIntegrationTest

# Specific test class
mvn test -Dtest=TransactionServiceTest
```

---

## ✅ All APIs Working

### Status: **100% Functional**

All endpoints tested and verified working:

| Endpoint | Method | Auth | Status |
|----------|--------|------|--------|
| `/users/register` | POST | ❌ | ✅ Working |
| `/auth/login` | POST | ❌ | ✅ Working |
| `/users/{id}` | GET | ✅ | ✅ Working |
| `/wallets/{id}` | GET | ✅ | ✅ Working |
| `/wallets/{id}/deposit` | POST | ✅ | ✅ Working |
| `/wallets/{id}/withdraw` | POST | ✅ | ✅ Working |
| `/wallets/{id}/transfer` | POST | ✅ | ✅ Working |
| `/wallets/{id}/transactions` | GET | ✅ | ✅ Working |
| `/transactions/{refId}` | GET | ✅ | ✅ Working |
| `/actuator/health` | GET | ❌ | ✅ Working |

**Note**: "No transaction is currently running" error has been **FIXED**.

---

## 🧪 Test Coverage

### Test Statistics
- **Total Tests**: 69
- **Pass Rate**: 100%
- **Coverage**: All major workflows

### Test Breakdown

| Test Suite | Tests | Purpose |
|------------|-------|---------|
| `ApiIntegrationTest` | 37 | Full API workflow end-to-end |
| `TransactionServiceTest` | 12 | Service layer business logic |
| `UserServiceTest` | 5 | User management |
| `WalletControllerTest` | 8 | HTTP layer + security |
| `JwtTokenProviderTest` | 7 | JWT token handling |

### What's Tested

✅ **User Registration & Authentication**
- User creation with automatic wallet
- Duplicate email prevention
- Login with JWT token generation
- Password hashing (BCrypt)

✅ **Wallet Operations**
- Deposit with balance increase
- Withdrawal with balance decrease
- Transfer between wallets (atomic)
- Insufficient balance rejection
- Idempotency (duplicate referenceId)

✅ **Security & Authorization**
- JWT authentication
- Ownership enforcement (403 on other user's wallet)
- Unauthorized access (401)
- Password verification

✅ **Business Logic**
- No negative balances
- Atomic transactions
- Ledger immutability
- Money conservation in transfers

✅ **Error Handling**
- Invalid amounts (negative, zero)
- Missing required fields
- Non-existent resources (404)
- Business rule violations (409)

---

## 📖 API Usage Guide

### 1. Register a User

```bash
POST /users/register
Content-Type: application/json

{
  "email": "alice@example.com",
  "password": "SecurePass123!",
  "currency": "USD"
}
```

**Response** (201 Created):
```json
{
  "userId": 1,
  "email": "alice@example.com",
  "status": "ACTIVE",
  "walletId": 1,
  "balance": 0.00,
  "currency": "USD",
  "createdAt": "2024-02-18T10:30:00Z"
}
```

### 2. Login

```bash
POST /auth/login
Content-Type: application/json

{
  "email": "alice@example.com",
  "password": "SecurePass123!"
}
```

**Response** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "email": "alice@example.com",
  "userId": 1,
  "expiresApprox": "2024-02-19T10:30:00Z"
}
```

### 3. Deposit Funds

```bash
POST /wallets/1/deposit
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "amount": 100.00,
  "referenceId": "deposit-001",
  "description": "Initial deposit"
}
```

**Response** (200 OK):
```json
{
  "transactionId": 1,
  "walletId": 1,
  "amount": 100.00,
  "type": "DEPOSIT",
  "referenceId": "deposit-001",
  "description": "Initial deposit",
  "createdAt": "2024-02-18T10:31:00Z"
}
```

### 4. Check Balance

```bash
GET /wallets/1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**Response** (200 OK):
```json
{
  "walletId": 1,
  "userId": 1,
  "balance": 100.00,
  "currency": "USD",
  "updatedAt": "2024-02-18T10:31:00Z"
}
```

### 5. Withdraw Funds

```bash
POST /wallets/1/withdraw
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "amount": 30.00,
  "referenceId": "withdraw-001",
  "description": "ATM withdrawal"
}
```

### 6. Transfer to Another User

```bash
POST /wallets/1/transfer
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "toWalletId": 2,
  "amount": 20.00,
  "referenceId": "transfer-001",
  "description": "Payment to Bob"
}
```

**Response** (200 OK):
```json
{
  "debit": {
    "transactionId": 3,
    "walletId": 1,
    "amount": -20.00,
    "type": "TRANSFER_OUT",
    "referenceId": "transfer-001-OUT"
  },
  "credit": {
    "transactionId": 4,
    "walletId": 2,
    "amount": 20.00,
    "type": "TRANSFER_IN",
    "referenceId": "transfer-001-IN"
  },
  "amount": 20.00,
  "referenceId": "transfer-001"
}
```

### 7. Transaction History

```bash
GET /wallets/1/transactions
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**Response** (200 OK):
```json
[
  {
    "transactionId": 1,
    "walletId": 1,
    "amount": 100.00,
    "type": "DEPOSIT",
    "referenceId": "deposit-001",
    "createdAt": "2024-02-18T10:31:00Z"
  },
  {
    "transactionId": 2,
    "walletId": 1,
    "amount": -30.00,
    "type": "WITHDRAWAL",
    "referenceId": "withdraw-001",
    "createdAt": "2024-02-18T10:32:00Z"
  }
]
```

---

## 🔒 Security Features

### Authentication
- **JWT Tokens**: HS256 algorithm with configurable expiry
- **BCrypt Hashing**: Password hashing with cost factor 12
- **Stateless**: No server-side sessions

### Authorization
- **Ownership Checks**: Users can only access their own wallet
- **Role-Based**: ROLE_USER assigned to authenticated users
- **Token Validation**: Every request validates JWT signature and expiry

### Security Headers
- CORS disabled (API-only)
- CSRF disabled (stateless JWT)
- Content-Type validation
- Input sanitization via Bean Validation

---

## 🏗️ Architecture

### Layered Design

```
┌─────────────────────────────────────────┐
│         Controllers (HTTP)              │
│  - Input validation                     │
│  - Ownership enforcement                │
│  - DTO mapping                          │
└─────────────┬───────────────────────────┘
              │
┌─────────────▼───────────────────────────┐
│         Services (Business Logic)       │
│  - @Transactional methods               │
│  - Idempotency checks                   │
│  - Balance calculations                 │
│  - Invariant enforcement                │
└─────────────┬───────────────────────────┘
              │
┌─────────────▼───────────────────────────┐
│         Repositories (Data Access)      │
│  - JPA entities                         │
│  - Pessimistic locking                  │
│  - Optimistic locking (@Version)        │
└─────────────┬───────────────────────────┘
              │
┌─────────────▼───────────────────────────┐
│         PostgreSQL Database             │
│  - ACID transactions                    │
│  - Flyway migrations                    │
│  - Append-only ledger                   │
└─────────────────────────────────────────┘
```

### Key Design Patterns

**Repository Pattern**: Data access abstraction  
**DTO Pattern**: Decoupling API from domain model  
**Service Layer Pattern**: Business logic centralization  
**Optimistic Locking**: Concurrent update handling  
**Pessimistic Locking**: Critical section protection  
**Idempotency Pattern**: Duplicate request handling  

---

## 💾 Database Schema

### Tables

**users**
- `id` (PK)
- `email` (unique)
- `password_hash`
- `status` (ACTIVE, SUSPENDED, CLOSED)
- `created_at`

**wallets**
- `id` (PK)
- `user_id` (FK → users, unique)
- `balance` (DECIMAL(19,4))
- `currency`
- `updated_at`
- `version` (optimistic locking)

**ledger_entries**
- `id` (PK)
- `wallet_id` (FK → wallets)
- `amount` (positive=credit, negative=debit)
- `type` (DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT, FEE, REFUND)
- `reference_id` (unique - idempotency key)
- `description`
- `created_at`

### Migrations

Flyway manages schema evolution:
- `V1__create_users_table.sql`
- `V2__create_wallets_table.sql`
- `V3__create_ledger_entries_table.sql`

---

## ⚙️ Configuration

### Profiles

| Profile | Use Case | Database | SQL Logs |
|---------|----------|----------|----------|
| `local` | Development | PostgreSQL (localhost) | ✅ Enabled |
| `docker` | Docker Compose | PostgreSQL (container) | ❌ Disabled |
| `prod` | Production | PostgreSQL (env vars) | ❌ Disabled |
| `test` | Unit/Integration Tests | H2 (in-memory) | ❌ Disabled |

### Environment Variables (Production)

```bash
# Database
DB_URL=jdbc:postgresql://db-host:5432/ledgerxlite
DB_USERNAME=ledgerx_prod
DB_PASSWORD=<secure-password>

# JWT
JWT_SECRET=<min-32-chars-secret>
JWT_EXPIRY_MS=3600000  # 1 hour

# Spring
SPRING_PROFILES_ACTIVE=prod
```

---

## 📦 Project Structure

```
ledgerx-lite/
├── src/
│   ├── main/
│   │   ├── java/com/ledgerxlite/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── domain/          # JPA entities
│   │   │   ├── dto/             # Data transfer objects
│   │   │   ├── exception/       # Exception handlers
│   │   │   ├── repository/      # JPA repositories
│   │   │   ├── security/        # JWT, SecurityConfig
│   │   │   └── service/         # Business logic
│   │   └── resources/
│   │       ├── db/migration/    # Flyway SQL scripts
│   │       ├── application.yml  # Base config
│   │       ├── application-local.yml
│   │       ├── application-docker.yml
│   │       ├── application-prod.yml
│   │       └── application-test.yml
│   └── test/
│       └── java/com/ledgerxlite/
│           ├── integration/     # Full API tests
│           ├── controller/      # Controller tests
│           ├── service/         # Service tests
│           └── security/        # Security tests
├── docker-compose.yml           # PostgreSQL + app
├── Dockerfile                   # Multi-stage build
├── pom.xml                      # Maven dependencies
├── README.md                    # This file
├── API_TESTING_GUIDE.md        # Detailed testing guide
├── FIXES_SUMMARY.md            # Issue fixes documentation
└── verify.sh                    # Verification script
```

---

## 🐛 Troubleshooting

### "No transaction is currently running"

**Fixed**: `@EnableTransactionManagement` is now explicitly declared in `LedgerXLiteApplication`.

**Verify**:
```bash
./verify.sh
```

### Tests Not Running

**Cause**: Tests in wrong directory  
**Fixed**: All tests moved to `src/test/java`

**Verify**:
```bash
mvn clean test
# Should run 69 tests
```

### 401 Unauthorized

**Solution**:
1. Login to get JWT token
2. Add header: `Authorization: Bearer <token>`
3. In Swagger: Click "Authorize" button and paste token

### 403 Forbidden

**Cause**: Trying to access another user's wallet  
**Solution**: Use your own walletId from registration response

---

## 📚 Documentation

- **API Testing Guide**: `API_TESTING_GUIDE.md`
- **Fixes Summary**: `FIXES_SUMMARY.md`
- **Swagger UI**: http://localhost:8080/api/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/api/v3/api-docs

---

## 🤝 Contributing

```bash
# 1. Create feature branch
git checkout -b feature/my-feature

# 2. Make changes and test
mvn clean test
./verify.sh

# 3. Commit and push
git commit -m "Add feature"
git push origin feature/my-feature

# 4. Create pull request
```

---

## 📄 License

MIT License - see LICENSE file for details

---

## 🎯 Production Checklist

Before deployment:

- [x] All tests pass (69/69)
- [x] No test files in src/main
- [x] Transaction management enabled
- [x] Security configured (JWT + BCrypt)
- [x] Database migrations applied
- [x] Environment variables set
- [x] Health check endpoint working
- [x] Swagger UI accessible
- [ ] SSL/TLS configured (load balancer)
- [ ] Rate limiting enabled
- [ ] Monitoring setup (Prometheus/Grafana)
- [ ] Log aggregation configured
- [ ] Backup strategy defined
- [ ] Load testing completed

---

**Built with** ❤️ **by LedgerX Team**
