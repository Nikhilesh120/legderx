# API Testing Guide

## Quick Start

### Run All Tests
```bash
mvn clean test
```

### Run Integration Tests Only
```bash
mvn test -Dtest=ApiIntegrationTest
```

### Run Specific Test Class
```bash
mvn test -Dtest=TransactionServiceTest
mvn test -Dtest=WalletControllerTest
mvn test -Dtest=UserServiceTest
mvn test -Dtest=JwtTokenProviderTest
```

## Test Coverage

### 1. Integration Tests (`ApiIntegrationTest`)
**Location**: `src/test/java/com/ledgerxlite/integration/ApiIntegrationTest.java`

Tests the complete API workflow with real transaction management:

#### Registration Tests
- ✅ Create user with valid data → 201 Created
- ✅ Duplicate email rejected → 400 Bad Request
- ✅ Wallet automatically created with balance 0

#### Authentication Tests
- ✅ Valid login → 200 OK + JWT token
- ✅ Wrong password → 404 Not Found
- ✅ Non-existent user → 404 Not Found

#### Wallet Access Tests
- ✅ No token → 401 Unauthorized
- ✅ Own wallet → 200 OK
- ✅ Other user's wallet → 403 Forbidden
- ✅ Non-existent wallet → 404 Not Found

#### Deposit Tests
- ✅ Valid deposit → 200 OK + balance increases
- ✅ Idempotent (duplicate referenceId) → returns same entry, balance increases only once
- ✅ Negative amount → 400 Bad Request
- ✅ Zero amount → 400 Bad Request
- ✅ Missing referenceId → 400 Bad Request
- ✅ Deposit to other user's wallet → 403 Forbidden

#### Withdrawal Tests
- ✅ Valid withdrawal → 200 OK + balance decreases
- ✅ Insufficient balance → 409 Conflict
- ✅ Idempotent withdrawal

#### Transfer Tests
- ✅ Valid transfer → 200 OK + both balances updated atomically
- ✅ Transfer to self → 400 Bad Request
- ✅ Insufficient balance → 409 Conflict
- ✅ Idempotent transfer

#### Transaction History Tests
- ✅ Get own history → 200 OK
- ✅ Cannot access other user's history → 403 Forbidden

#### Transaction Lookup Tests
- ✅ Find by referenceId → 200 OK
- ✅ Non-existent referenceId → 404 Not Found

#### Health Check
- ✅ Health endpoint accessible → 200 OK

### 2. Unit Tests

#### `TransactionServiceTest`
**Location**: `src/test/java/com/ledgerxlite/service/TransactionServiceTest.java`

Tests service layer business logic with mocked repositories:
- Deposit validation and invariants
- Withdrawal with balance checks
- Transfer atomicity and money conservation
- Idempotency enforcement

#### `UserServiceTest`
**Location**: `src/test/java/com/ledgerxlite/service/UserServiceTest.java`

Tests user management:
- User registration with BCrypt password hashing
- Duplicate email prevention
- User status lifecycle (ACTIVE, SUSPENDED, CLOSED)

#### `WalletControllerTest`
**Location**: `src/test/java/com/ledgerxlite/controller/WalletControllerTest.java`

Tests HTTP layer with Spring Security:
- Controller authentication
- Ownership enforcement
- HTTP status code mapping
- DTO serialization

#### `JwtTokenProviderTest`
**Location**: `src/test/java/com/ledgerxlite/security/JwtTokenProviderTest.java`

Tests JWT functionality:
- Token generation
- Token validation
- Claims extraction (email, userId)
- Tampered/expired token rejection

## Manual API Testing (using Swagger UI)

### 1. Start the Application
```bash
# Local profile (requires docker-compose up -d postgres)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or with Docker
docker-compose up --build
```

### 2. Access Swagger UI
Open: http://localhost:8080/api/swagger-ui/index.html

### 3. Test Workflow

#### Step 1: Register a User
```
POST /users/register
{
  "email": "test@example.com",
  "password": "SecurePass123!",
  "currency": "USD"
}
```
**Expected**: 201 Created, returns `userId` and `walletId`

#### Step 2: Login
```
POST /auth/login
{
  "email": "test@example.com",
  "password": "SecurePass123!"
}
```
**Expected**: 200 OK, returns JWT `token`

#### Step 3: Authorize in Swagger
1. Click the **Authorize** button (padlock icon, top right)
2. Paste the token (without "Bearer " prefix)
3. Click **Authorize**

#### Step 4: Get Wallet Balance
```
GET /wallets/{walletId}
```
**Expected**: 200 OK, balance = 0.00

#### Step 5: Deposit Funds
```
POST /wallets/{walletId}/deposit
{
  "amount": 100.00,
  "referenceId": "unique-id-1",
  "description": "Test deposit"
}
```
**Expected**: 200 OK, balance increases to 100.00

#### Step 6: Verify Balance
```
GET /wallets/{walletId}
```
**Expected**: 200 OK, balance = 100.00

#### Step 7: Test Idempotency
Repeat Step 5 with the same `referenceId`.
**Expected**: 200 OK, same transaction returned, balance still 100.00 (not 200.00)

#### Step 8: Withdraw Funds
```
POST /wallets/{walletId}/withdraw
{
  "amount": 30.00,
  "referenceId": "unique-id-2",
  "description": "Test withdrawal"
}
```
**Expected**: 200 OK, balance decreases to 70.00

#### Step 9: Test Insufficient Balance
```
POST /wallets/{walletId}/withdraw
{
  "amount": 500.00,
  "referenceId": "unique-id-3",
  "description": "Overdraft attempt"
}
```
**Expected**: 409 Conflict, error message "Insufficient balance"

#### Step 10: Create Second User & Transfer
1. Register another user (different email)
2. Login to get their token
3. Note their `walletId`
4. Use first user's token to transfer:
```
POST /wallets/{user1WalletId}/transfer
{
  "toWalletId": {user2WalletId},
  "amount": 20.00,
  "referenceId": "unique-id-4",
  "description": "Test transfer"
}
```
**Expected**: 200 OK, user1 balance = 50.00, user2 balance = 20.00

#### Step 11: View Transaction History
```
GET /wallets/{walletId}/transactions
```
**Expected**: 200 OK, array of all transactions in chronological order

## Common Issues & Solutions

### "No transaction is currently running"

**Cause**: Missing `@EnableTransactionManagement` or transaction proxy not active.

**Fix**: The `LedgerXLiteApplication` class now has `@EnableTransactionManagement` explicitly declared.

**Verify**:
```bash
# Check application class has the annotation
grep -n "EnableTransactionManagement" src/main/java/com/ledgerxlite/LedgerXLiteApplication.java
```

### Tests Fail with "Wallet not found"

**Cause**: Test is not running in a transaction context, or H2 schema not created.

**Fix**: Ensure tests use `@SpringBootTest` and `@ActiveProfiles("test")`.

### 401 Unauthorized on All Endpoints

**Cause**: JWT token not included or invalid.

**Fix**: 
1. Login to get token
2. Add header: `Authorization: Bearer {token}`
3. In Swagger: Click Authorize and paste token

### 403 Forbidden on Wallet Operations

**Cause**: Trying to access another user's wallet.

**Fix**: Each user can only access their own wallet. Use the walletId from your own user registration response.

## Test Execution Output

When all tests pass, you should see:

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.5 s - in ApiIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.8 s - in TransactionServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.3 s - in UserServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.2 s - in WalletControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.2 s - in JwtTokenProviderTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

## Continuous Integration

### GitHub Actions / GitLab CI
```yaml
test:
  script:
    - mvn clean test
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
```

### Jenkins
```groovy
stage('Test') {
    steps {
        sh 'mvn clean test'
        junit 'target/surefire-reports/*.xml'
    }
}
```
