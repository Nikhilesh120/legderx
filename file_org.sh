#!/bin/sh
set -e

echo "🧹 LedgerX project sanitization started..."

# -------------------------------------------------
# 1. Remove ALL duplicate SecurityConfig files
# -------------------------------------------------
find src -path "*SecurityConfig.java" \
  ! -path "src/main/java/com/ledgerxlite/security/SecurityConfig.java" \
  -print -delete

echo "✅ SecurityConfig duplicates removed"

# -------------------------------------------------
# 2. Remove duplicate ApiIntegrationTest
# -------------------------------------------------
find src/test/java -name "ApiIntegrationTest.java" \
  ! -path "src/test/java/com/ledgerxlite/integration/ApiIntegrationTest.java" \
  -print -delete

echo "✅ ApiIntegrationTest duplicates removed"

# -------------------------------------------------
# 3. Remove misplaced tests from main
# -------------------------------------------------
find src/main/java -name "*Test.java" -print -delete

echo "✅ Misplaced tests removed from main source set"

# -------------------------------------------------
# 4. Fix javax.servlet → jakarta.servlet (SAFE)
# -------------------------------------------------
FILES=$(grep -rl "javax.servlet" src || true)
if [ -n "$FILES" ]; then
  echo "$FILES" | xargs sed -i 's/javax\.servlet/jakarta.servlet/g'
  echo "✅ Servlet imports normalized"
else
  echo "✅ No javax.servlet imports found"
fi

# -------------------------------------------------
# 5. Final duplicate-class sanity check
# -------------------------------------------------
DUPLICATES=$(find src -name "*.java" | sed 's#.*/java/##' | sort | uniq -d)

if [ -n "$DUPLICATES" ]; then
  echo "❌ Duplicate Java classes still exist:"
  echo "$DUPLICATES"
  exit 1
fi

echo "🎉 Project structure is now canonical"
echo "▶️ Run: mvn clean test"

