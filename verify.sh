#!/bin/bash
# Quick verification script for LedgerX Lite
# Verifies all critical fixes are in place

set -e

echo "🔍 LedgerX Lite - Verification Script"
echo "======================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check 1: @EnableTransactionManagement present
echo -n "✓ Checking @EnableTransactionManagement... "
if grep -q "@EnableTransactionManagement" src/main/java/com/ledgerxlite/LedgerXLiteApplication.java; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC}"
    echo "  Missing @EnableTransactionManagement in main application class"
    exit 1
fi

# Check 2: All tests in src/test
echo -n "✓ Checking test file locations... "
MAIN_TESTS=$(find src/main -name "*Test.java" 2>/dev/null | wc -l)
if [ "$MAIN_TESTS" -eq 0 ]; then
    echo -e "${GREEN}PASS${NC} (no tests in src/main)"
else
    echo -e "${RED}FAIL${NC}"
    echo "  Found $MAIN_TESTS test files in src/main (should be 0)"
    find src/main -name "*Test.java"
    exit 1
fi

# Check 3: Test profile configured
echo -n "✓ Checking application-test.yml... "
if [ -f "src/main/resources/application-test.yml" ]; then
    if grep -q "H2Dialect" src/main/resources/application-test.yml; then
        echo -e "${GREEN}PASS${NC}"
    else
        echo -e "${RED}FAIL${NC}"
        echo "  application-test.yml missing H2 configuration"
        exit 1
    fi
else
    echo -e "${RED}FAIL${NC}"
    echo "  application-test.yml not found"
    exit 1
fi

# Check 4: Integration test exists
echo -n "✓ Checking ApiIntegrationTest... "
if [ -f "src/test/java/com/ledgerxlite/integration/ApiIntegrationTest.java" ]; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC}"
    echo "  ApiIntegrationTest.java not found"
    exit 1
fi

# Check 5: Count test files
echo -n "✓ Counting test files... "
TEST_COUNT=$(find src/test -name "*Test.java" | wc -l)
if [ "$TEST_COUNT" -ge 5 ]; then
    echo -e "${GREEN}PASS${NC} ($TEST_COUNT tests found)"
else
    echo -e "${RED}FAIL${NC}"
    echo "  Expected at least 5 test files, found $TEST_COUNT"
    exit 1
fi

# Check 6: Flyway migrations present
echo -n "✓ Checking Flyway migrations... "
MIGRATIONS=$(find src/main/resources/db/migration -name "V*.sql" 2>/dev/null | wc -l)
if [ "$MIGRATIONS" -ge 3 ]; then
    echo -e "${GREEN}PASS${NC} ($MIGRATIONS migrations)"
else
    echo -e "${YELLOW}WARNING${NC}"
    echo "  Expected at least 3 migrations, found $MIGRATIONS"
fi

# Check 7: pom.xml has required dependencies
echo -n "✓ Checking Maven dependencies... "
if grep -q "spring-boot-starter-data-jpa" pom.xml && \
   grep -q "spring-boot-starter-security" pom.xml && \
   grep -q "flyway-core" pom.xml; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC}"
    echo "  Missing required dependencies in pom.xml"
    exit 1
fi

echo ""
echo "======================================"
echo -e "${GREEN}✓ All verification checks passed!${NC}"
echo ""
echo "Next steps:"
echo "  1. Run tests:  mvn clean test"
echo "  2. Start app:  mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo "  3. API docs:   http://localhost:8080/api/swagger-ui/index.html"
echo ""
