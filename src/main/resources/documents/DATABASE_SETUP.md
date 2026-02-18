# PostgreSQL Docker Setup Guide

## Quick Start

### 1. Start PostgreSQL Container

```bash
# Make sure you're in the project root
cd E:\ledgerx

# Start PostgreSQL (and optionally pgAdmin)
docker-compose up -d

# Verify it's running
docker-compose ps
```

**Expected output:**
```
NAME                IMAGE                    STATUS
ledgerx-postgres    postgres:15-alpine       Up (healthy)
ledgerx-pgadmin     dpage/pgadmin4:latest    Up
```

---

### 2. Verify Database Connection

```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Connect to PostgreSQL
docker exec -it ledgerx-postgres psql -U ledgerx_user -d ledgerxlite

# Inside psql, run:
\dt   # List tables (should be empty initially)
\q    # Quit
```

---

### 3. Run Spring Boot Application

```bash
# Run with local profile (uses Docker PostgreSQL)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**What happens:**
- Application connects to PostgreSQL on localhost:5432
- Hibernate creates tables automatically (`ddl-auto: create-drop`)
- Tables: `users`, `wallets`, `ledger_entries`

---

### 4. Verify Tables Created

In another terminal:

```bash
docker exec -it ledgerx-postgres psql -U ledgerx_user -d ledgerxlite
```

```sql
-- List all tables
\dt

-- Expected tables:
-- users
-- wallets
-- ledger_entries

-- Describe table structure
\d users
\d wallets
\d ledger_entries

-- Check indexes
\di
```

---

## Access pgAdmin (Optional Database UI)

1. **Open browser:** http://localhost:5050
2. **Login:**
   - Email: `admin@ledgerx.local`
   - Password: `admin`

3. **Add Server:**
   - Right-click "Servers" → Create → Server
   - **General tab:**
     - Name: `LedgerX Local`
   - **Connection tab:**
     - Host: `postgres` (container name)
     - Port: `5432`
     - Database: `ledgerxlite`
     - Username: `ledgerx_user`
     - Password: `ledgerx_pass`
   - Click "Save"

---

## Docker Commands

### Start Database
```bash
docker-compose up -d          # Start in background
docker-compose up             # Start with logs visible
```

### Stop Database
```bash
docker-compose stop           # Stop containers (keep data)
docker-compose down           # Stop and remove containers (keep data)
docker-compose down -v        # Stop, remove containers AND delete data
```

### View Logs
```bash
docker-compose logs -f postgres    # Follow PostgreSQL logs
docker-compose logs -f             # All containers
```

### Database Management
```bash
# Connect to database
docker exec -it ledgerx-postgres psql -U ledgerx_user -d ledgerxlite

# Run SQL file
docker exec -i ledgerx-postgres psql -U ledgerx_user -d ledgerxlite < schema.sql

# Backup database
docker exec ledgerx-postgres pg_dump -U ledgerx_user ledgerxlite > backup.sql

# Restore database
docker exec -i ledgerx-postgres psql -U ledgerx_user -d ledgerxlite < backup.sql
```

---

## Application Profiles

### Development (No Database)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
- No database required
- Good for testing compilation

### Local (Docker PostgreSQL)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
- Uses Docker PostgreSQL
- Auto-creates schema
- Useful for development and testing

### Production
```bash
mvn spring-boot:run
```
- Uses production database settings
- Requires proper database credentials
- Schema must exist (no auto-create)

---

## Configuration Files

### docker-compose.yml
- Defines PostgreSQL and pgAdmin containers
- Network configuration
- Volume mounts for data persistence

### application-local.yml
- Spring Boot configuration for local development
- Database connection to Docker PostgreSQL
- `ddl-auto: create-drop` - auto-creates schema

### application-dev.yml
- No database configuration
- For testing without database

### application.yml
- Production configuration
- Minimal settings
- `ddl-auto: none` - requires existing schema

---

## Troubleshooting

### Port 5432 Already in Use
```bash
# Find what's using port 5432
netstat -ano | findstr :5432

# Option 1: Stop existing PostgreSQL
# Option 2: Change port in docker-compose.yml
ports:
  - "5433:5432"  # Use 5433 on host
```

### Connection Refused
```bash
# Check if container is running
docker-compose ps

# Check container logs
docker-compose logs postgres

# Restart containers
docker-compose restart
```

### Schema Not Created
```bash
# Check application-local.yml has:
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop

# Check application is using local profile:
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Data Persistence
```bash
# Data persists in Docker volumes even after `docker-compose down`
docker volume ls

# To completely reset:
docker-compose down -v  # Deletes volumes
docker-compose up -d    # Fresh start
```

---

## Database Schema (Auto-Created)

When you run with `-Dspring-boot.run.profiles=local`, Hibernate creates:

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
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

CREATE UNIQUE INDEX idx_wallets_user_id ON wallets(user_id);
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
CREATE UNIQUE INDEX idx_ledger_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_type ON ledger_entries(type);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries(wallet_id, created_at);
```

---

## Complete Workflow

```bash
# 1. Start database
cd E:\ledgerx
docker-compose up -d

# 2. Verify database is ready
docker-compose logs postgres | grep "ready to accept"

# 3. Run application
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Test health endpoint
curl http://localhost:8080/api/health

# 5. Verify tables created
docker exec -it ledgerx-postgres psql -U ledgerx_user -d ledgerxlite -c "\dt"

# 6. Stop when done
# Ctrl+C to stop Spring Boot
docker-compose stop  # Stop database (keeps data)
```

---

## For Production

Later, when deploying to production:

1. Create proper database migrations (Flyway/Liquibase)
2. Change `ddl-auto` to `validate` or `none`
3. Use environment variables for credentials
4. Set up database backups
5. Configure connection pooling properly

---

**Now you're ready to run LedgerX with PostgreSQL in Docker! 🐘🐳**
