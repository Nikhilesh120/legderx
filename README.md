# LedgerX Lite

A **transaction-safe financial ledger backend** built using Spring Boot, designed with append-only architecture to ensure financial data integrity and auditability.

## Overview

LedgerX Lite is a double-entry accounting system that maintains immutable financial records. Every transaction is recorded as an append-only ledger entry, ensuring complete audit trails and compliance with financial regulations.

**Key Principles:**
- **Immutability**: Ledger entries are never updated or deleted
- **Append-Only**: All changes create new records
- **Transaction Safety**: ACID compliance for all financial operations
- **Audit Trail**: Complete history of all financial movements

## Tech Stack

- **Java**: 17
- **Framework**: Spring Boot 3.2.0
- **Build Tool**: Maven
- **Database**: PostgreSQL
- **API Style**: REST

## Project Structure

```
com.ledgerxlite
├── controller      # REST API endpoints (no business logic)
├── service         # Business logic layer
├── repository      # Data access layer
├── domain          # JPA entities
├── security        # Authentication & authorization (future)
├── config          # Spring configurations
├── dto             # Data transfer objects
└── exception       # Custom exceptions & handlers
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 14+ (for production mode)

## Running the Application

### Option 1: Development Mode (No Database Required)

For initial testing without setting up PostgreSQL:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start on `http://localhost:8080/api`

Test the health endpoint:
```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "application": "LedgerX Lite",
  "timestamp": "2024-XX-XXTXX:XX:XXZ"
}
```

### Option 2: Production Mode (Requires PostgreSQL)

1. **Set up PostgreSQL database:**

```sql
CREATE DATABASE ledgerxlite;
CREATE USER ledgerx_user WITH PASSWORD 'ledgerx_pass';
GRANT ALL PRIVILEGES ON DATABASE ledgerxlite TO ledgerx_user;
```

2. **Run the application:**

```bash
mvn spring-boot:run
```

## Build

```bash
mvn clean package
```

The executable JAR will be created in `target/ledgerx-lite-0.0.1-SNAPSHOT.jar`

## API Endpoints

### Health Check
- **GET** `/api/health` - Application health status

### Actuator (Management)
- **GET** `/api/actuator/health` - Detailed health information
- **GET** `/api/actuator/info` - Application information
- **GET** `/api/actuator/metrics` - Application metrics

## Architecture Principles

1. **Layered Architecture** - Clear separation between controller, service, and repository layers
2. **No Business Logic in Controllers** - Controllers only handle HTTP concerns
3. **Clean Package Structure** - Each layer has its dedicated package
4. **Production-Ready** - Following enterprise-grade naming and patterns

## Current Status

✅ Project skeleton created
✅ Package structure established
✅ Basic health check endpoint
⏳ Business logic (pending)
⏳ Security implementation (pending)
⏳ Database migrations (pending)

## Next Steps

1. Define domain entities
2. Implement repository interfaces
3. Add business services
4. Create REST controllers
5. Add security layer
6. Implement database migrations

## Development Notes

- The project uses Lombok for reducing boilerplate code
- JPA validation is included for entity validation
- Actuator is configured for monitoring
- Logging is configured for debugging

---

**Version**: 0.0.1-SNAPSHOT  
**License**: TBD
