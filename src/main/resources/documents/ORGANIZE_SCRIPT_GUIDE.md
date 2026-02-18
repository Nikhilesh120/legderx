# organize-files.sh - Complete Usage Guide

## 🎯 What's New

Version 2.0 includes powerful new features for file organization and cleanup.

---

## ✨ New Features

### **1. File Removal System (`remove.txt`)**

Easily clean up unwanted files across the entire project.

**How to use:**

```bash
# 1. Create remove.txt in project root
cd E:\ledgerx
nano remove.txt

# 2. List files to remove (one per line, filenames only)
InvariantVerifier.java
TransactionServiceWithInvariants.java
OldController.java

# 3. Run the script
./organize-files.sh

# Files are found and deleted from anywhere in the project
# Then remove.txt is automatically deleted
```

**Features:**
- ✅ Searches entire project (not just root)
- ✅ Filenames only (no paths for safety)
- ✅ Skips comments (lines starting with `#`)
- ✅ Warns if file not found
- ✅ Auto-deletes remove.txt after processing

**Example remove.txt:**
```txt
# STEP 3 Cleanup - Remove over-engineered files
InvariantVerifier.java
TransactionServiceWithInvariants.java

# Old test files
OldUserServiceTest.java
```

---

### **2. Centralized Documentation**

All `*.md` files now go to `src/main/resources/documents/`

**Before:**
```
ledgerx/
├── README.md
├── TRANSACTION_INVARIANTS.md
├── DATABASE_SETUP.md
└── ... (cluttered root)
```

**After:**
```
ledgerx/
├── pom.xml
├── docker-compose.yml
└── src/main/resources/documents/
    ├── README.md
    ├── TRANSACTION_INVARIANTS.md
    ├── DATABASE_SETUP.md
    └── ...
```

**Benefits:**
- ✅ Cleaner project root
- ✅ All docs in one place
- ✅ Better for packaging
- ✅ Easier to navigate

---

### **3. Improved Test Organization**

Tests are automatically categorized by type:

```
src/test/java/com/ledgerxlite/
├── controller/
│   ├── UserControllerTest.java
│   └── WalletControllerTest.java
├── service/
│   ├── TransactionServiceTest.java
│   └── UserServiceTest.java
└── repository/
    ├── UserRepositoryTest.java
    └── WalletRepositoryTest.java
```

---

### **4. Better Output**

**With tree installed:**
```
✅ Organization complete
═══════════════════════════════════════════════

ledgerx
├── pom.xml
├── docker-compose.yml
└── src
    ├── main
    │   ├── java/com/ledgerxlite
    │   │   ├── controller
    │   │   ├── service
    │   │   └── repository
    │   └── resources
    │       ├── documents
    │       └── db/migration
    └── test
```

**Without tree:**
Shows first 40 files in organized structure.

---

## 📋 Complete File Organization Rules

| File Pattern | Destination | Category |
|--------------|-------------|----------|
| `*Application.java` | `src/main/java/.../` | Main Application |
| `*Controller.java` | `controller/` | Controller |
| `*Service*.java` | `service/` | Service |
| `*Verifier.java` | `service/` | Service |
| `*Repository.java` | `repository/` | Repository |
| `User.java`, `Wallet.java`, `LedgerEntry.java` | `domain/` | Domain Entity |
| `*Entity.java` | `domain/` | Domain Entity |
| `*Request.java`, `*Response.java` | `dto/` | DTO |
| `*DTO.java`, `*Dto.java` | `dto/` | DTO |
| `*Config.java`, `*Configuration.java` | `config/` | Configuration |
| `*Security.java`, `*Filter.java` | `security/` | Security |
| `*Exception.java`, `*ExceptionHandler.java` | `exception/` | Exception |
| `application*.yml`, `application*.properties` | `resources/` | Config File |
| `*.sql` | `resources/db/migration/` | DB Migration |
| `*Test.java` | `test/.../` (categorized) | Test |
| `*.md` | `resources/documents/` | Documentation |
| `pom.xml`, `.gitignore`, `docker-compose.yml` | Project root | Project File |

---

## 🚀 Common Workflows

### **Workflow 1: Download & Organize New Files**

```bash
# Download files from Claude to E:\ledgerx
cd E:\ledgerx

# Run organizer
./organize-files.sh

# Expected output:
# ✔ Organized UserController.java → Controller
# ✔ Organized TransactionService.java → Service
# ✔ Organized API_GUIDE.md → Documentation

# Verify
mvn clean compile
```

---

### **Workflow 2: Cleanup After Refactoring**

```bash
cd E:\ledgerx

# Create remove.txt
echo "OldService.java" > remove.txt
echo "DeprecatedController.java" >> remove.txt
echo "UnusedVerifier.java" >> remove.txt

# Run organizer (will remove files first, then organize)
./organize-files.sh

# Expected output:
# 🗑 Processing remove.txt
# ✖ Removed ./src/main/java/.../OldService.java
# ✖ Removed ./src/main/java/.../DeprecatedController.java
# ✖ Removed ./src/main/java/.../UnusedVerifier.java
# ✔ remove.txt processed and deleted
```

---

### **Workflow 3: Organize Tests**

```bash
# Download test files
cd E:\ledgerx

# Files in root:
# UserServiceTest.java
# WalletControllerTest.java
# TransactionRepositoryTest.java

./organize-files.sh

# Result:
# ✔ Organized UserServiceTest.java → Test (service/)
# ✔ Organized WalletControllerTest.java → Test (controller/)
# ✔ Organized TransactionRepositoryTest.java → Test (repository/)
```

---

### **Workflow 4: Add Documentation**

```bash
cd E:\ledgerx

# New docs in root:
# SECURITY_GUIDE.md
# DEPLOYMENT.md

./organize-files.sh

# Result:
# ✔ Organized SECURITY_GUIDE.md → Documentation
# ✔ Organized DEPLOYMENT.md → Documentation

# All docs now in:
# src/main/resources/documents/
```

---

## ⚠️ Safety Features

### **1. Path Rejection in remove.txt**

```bash
# ❌ REJECTED (paths not allowed for safety)
src/main/java/UserService.java

# ✅ ACCEPTED (filename only)
UserService.java
```

**Why:** Prevents accidental deletion of entire directories.

### **2. Git & Target Exclusion**

Files in `.git/` and `target/` are never touched.

### **3. Comments Supported**

```txt
# This is a comment - will be ignored

UserService.java  # This will be removed

# Another comment
OldController.java
```

---

## 🔍 Troubleshooting

### **Problem: File not found in remove.txt**

```
⚠ Not found OldService.java
```

**Solution:**
- Check filename spelling
- File might already be deleted
- Search manually: `find . -name "OldService.java"`

---

### **Problem: Script won't run**

```
bash: ./organize-files.sh: Permission denied
```

**Solution:**
```bash
chmod +x organize-files.sh
./organize-files.sh
```

---

### **Problem: Tree command not showing**

**Solution:**
```bash
# Install tree (optional)
# Windows: Install via Chocolatey or Git Bash
# Linux: sudo apt install tree
# Mac: brew install tree

# Or just use the file list output (works without tree)
```

---

## 📚 Documentation Location Change

### **Important for HLD Tracking:**

All future documentation will be created in:
```
src/main/resources/documents/
```

Instead of project root.

**How I'll handle this:**
All `*.md` files I create will automatically go to this location when you run the script.

---

## 🎯 Best Practices

### **1. Always Run After Downloading Files**

```bash
# Download files → Run organizer → Compile → Test
cd E:\ledgerx
./organize-files.sh
mvn clean compile
mvn test
```

### **2. Use Comments in remove.txt**

```txt
# STEP 3 Correction - Remove over-engineered components
InvariantVerifier.java
TransactionServiceWithInvariants.java

# TODO: Check if these are still needed before removing
# DeprecatedService.java
# OldController.java
```

### **3. Verify Before Committing**

```bash
./organize-files.sh
mvn clean install  # Make sure everything compiles
git status         # Review changes
git add .
git commit -m "Organize: Auto-organized project files"
```

---

## 🔄 Integration with Git Workflow

### **Updated .gitignore**

Add these lines:
```
# Auto-cleanup files
remove.txt
```

### **Recommended Commit Message Format**

```bash
# After organizing
git commit -m "Organize: Auto-organized downloaded files"

# After cleanup
git commit -m "Cleanup: Removed deprecated files via remove.txt"

# After both
git commit -m "Refactor: Organized and cleaned up project structure"
```

---

## ✅ Summary

**New Features:**
1. ✅ `remove.txt` file removal system
2. ✅ Centralized documentation in `resources/documents/`
3. ✅ Improved test organization (categorized by type)
4. ✅ Better output (tree view if available)

**Key Benefits:**
- 🎯 Cleaner project root
- 🧹 Easy cleanup of deprecated files
- 📚 Organized documentation
- 🧪 Categorized tests
- 👀 Better visibility of project structure

**Safe to Use:**
- ✅ Won't touch `.git/` or `target/`
- ✅ Validates project root
- ✅ Rejects paths in remove.txt
- ✅ Warns before skipping

---

**This script is production-ready and safe to use! 🚀**
