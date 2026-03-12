#!/bin/bash

set -e

BUNDLE_DIR="_debug_bundle"
TAR_NAME="debug_bundle.tar.gz"

echo "🔍 Searching project for required files (path-independent)..."

mkdir -p "$BUNDLE_DIR"

FILES=(
  Wallet.java
  SecurityConfig.java
  GlobalExceptionHandler.java
  TransactionServiceTest.java
  WalletControllerTest.java
  ApiIntegrationTest.java
)

for FILE in "${FILES[@]}"; do
  echo ""
  echo "➡ Searching for $FILE ..."
  FOUND=$(find . -type f -name "$FILE")

  if [ -z "$FOUND" ]; then
    echo "⚠ NOT FOUND: $FILE"
  else
    for F in $FOUND; do
      echo "✔ Found: $F"
      mkdir -p "$BUNDLE_DIR/$(dirname "$F")"
      cp "$F" "$BUNDLE_DIR/$F"
    done
  fi
done

echo ""
echo "📦 Creating tar.gz archive..."
tar -czf "$TAR_NAME" "$BUNDLE_DIR"

echo ""
echo "✅ Done!"
echo "➡ Upload: $TAR_NAME"

