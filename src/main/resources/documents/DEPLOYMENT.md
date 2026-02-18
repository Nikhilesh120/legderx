# LedgerX Lite — Deployment Guide

## Local Development

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker Desktop

### Start Database
```bash
docker-compose up -d
# Verify: docker-compose ps  (postgres should be "healthy")
```

### Run Application
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Verify
```
http://localhost:8080/api/actuator/health   → {"status":"UP"}
http://localhost:8080/api/swagger-ui/index.html
```

### Run Tests
```bash
mvn test
# Uses H2 in-memory DB — no docker needed for tests
```

---

## Production Deployment

### Required Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://db:5432/ledgerxlite` |
| `DB_USERNAME` | DB user | `ledgerx_user` |
| `DB_PASSWORD` | DB password | (from secrets manager) |
| `JWT_SECRET` | JWT signing secret (min 32 chars) | (from secrets manager) |
| `JWT_EXPIRY_MS` | Token lifetime ms | `3600000` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |

### Build Production JAR
```bash
mvn clean package -DskipTests -Pprod
# Output: target/ledgerx-lite-0.0.1-SNAPSHOT.jar
```

### Run Production JAR
```bash
java \
  -Dspring.profiles.active=prod \
  -DJWT_SECRET="$JWT_SECRET" \
  -DDB_URL="$DB_URL" \
  -DDB_USERNAME="$DB_USERNAME" \
  -DDB_PASSWORD="$DB_PASSWORD" \
  -jar target/ledgerx-lite-0.0.1-SNAPSHOT.jar
```

### Schema Migrations
Flyway runs automatically on startup. Migration files are in `src/main/resources/db/migration/`.

- `V1__create_users_table.sql`
- `V2__create_wallets_table.sql`
- `V3__create_ledger_entries_table.sql`

Flyway will only run migrations that haven't been applied yet. Safe to restart.

### Health Check
```
GET /api/actuator/health
```
Configure your load balancer to probe this endpoint. Returns `{"status":"UP"}` when DB is reachable.

### HikariCP Connection Pool (Production Defaults)
```yaml
maximum-pool-size: 20
minimum-idle: 5
connection-timeout: 30000   # 30s
idle-timeout: 600000        # 10 min
max-lifetime: 1800000       # 30 min
leak-detection-threshold: 60000  # alert if connection held > 60s
```

---

## Pre-Production Checklist

- [ ] Set JWT_SECRET to minimum 32 random characters (not default)
- [ ] Set JWT_EXPIRY_MS to 3600000 (1 hour) or less
- [ ] Confirm Flyway migrations applied: `SELECT * FROM flyway_schema_history`
- [ ] Confirm `ddl-auto: none` in production profile
- [ ] Confirm `show-sql: false` in production profile
- [ ] Run `mvn test` — all tests must pass
- [ ] Verify `/actuator/health` returns UP
- [ ] Smoke test: register → login → deposit → withdraw → transfer
- [ ] Confirm HTTPS is terminating at load balancer
- [ ] Remove or restrict Swagger UI access (`/swagger-ui/**`)
- [ ] Delete/disable UserTestController — already done
- [ ] Confirm DB has `CHECK (balance >= 0)` constraint
- [ ] Confirm ledger_entries UPDATE/DELETE trigger is active
