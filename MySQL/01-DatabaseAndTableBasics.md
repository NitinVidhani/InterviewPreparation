# Exercise 1 — Database & Table Basics

---

## 1. How MySQL Stores Data (Architecture Overview)

Before writing a single query, understand what happens under the hood:

```
┌──────────────────────────────────────────┐
│              MySQL Server                │
│  ┌──────────────────────────────────┐    │
│  │        SQL Layer (Parser,        │    │
│  │     Optimizer, Executor)         │    │
│  └──────────┬───────────────────────┘    │
│             │                            │
│  ┌──────────▼───────────────────────┐    │
│  │     Storage Engine (InnoDB)      │    │
│  │  ┌─────────┐  ┌──────────────┐  │    │
│  │  │ Buffer   │  │ Redo Log     │  │    │
│  │  │ Pool     │  │ (WAL)        │  │    │
│  │  └─────────┘  └──────────────┘  │    │
│  └──────────┬───────────────────────┘    │
│             │                            │
│  ┌──────────▼───────────────────────┐    │
│  │     Disk (.ibd files)            │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

**Key concepts:**
- **Database** = a namespace/folder that groups related tables.
- **Table** = a structured collection of rows (also called *relations* in theory).
- **InnoDB** = the default storage engine. All data is stored in B+ Tree structures organized around the **primary key** (this is the *clustered index*).
- Every table has a `.ibd` file on disk (tablespace). MySQL 8.0+ stores metadata in a *data dictionary* (no more `.frm` files).

> **Interview Insight:** When an interviewer asks "how does MySQL store a table?", the answer is: InnoDB stores rows in 16 KB **pages**, organized as a B+ Tree keyed on the primary key. The leaf nodes contain the actual row data. This is why primary key choice matters hugely for performance.

---

## 2. CREATE DATABASE

```sql
-- Create a new database
CREATE DATABASE company_db;

-- Create only if it doesn't exist (idempotent)
CREATE DATABASE IF NOT EXISTS company_db;

-- Specify character set and collation explicitly
CREATE DATABASE company_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### Character Set & Collation — Why It Matters

| Term | Meaning | Example |
|------|---------|---------|
| **Character Set** | Which characters can be stored | `utf8mb4` supports all Unicode including emoji 🎉 |
| **Collation** | Rules for comparing/sorting characters | `utf8mb4_unicode_ci` = case-insensitive comparison |

> **⚠️ Always use `utf8mb4`, not `utf8`.** MySQL's `utf8` is a broken 3-byte encoding that can't store emoji or some Chinese/Japanese characters. `utf8mb4` is true UTF-8 (4 bytes). This is the default since MySQL 8.0.

**Collation naming convention:**
```
utf8mb4_unicode_ci
│        │       └── ci = Case Insensitive (cs = Case Sensitive)
│        └────────── unicode = Unicode sorting rules
└─────────────────── character set
```

### Other Database Commands

```sql
-- List all databases
SHOW DATABASES;

-- Switch to a database
USE company_db;

-- See which database you're currently in
SELECT DATABASE();

-- Drop a database (⚠️ DESTRUCTIVE — deletes all tables inside)
DROP DATABASE company_db;
DROP DATABASE IF EXISTS company_db;

-- Show the CREATE statement that was used
SHOW CREATE DATABASE company_db;
```

---

## 3. Data Types — The Complete Guide

Choosing the right data type is a **critical** senior-level skill. Wrong choices waste storage, hurt performance, and cause subtle bugs.

### 3.1 Numeric Types

#### Integers

| Type | Bytes | Signed Range | Unsigned Range | Use Case |
|------|-------|-------------|----------------|----------|
| `TINYINT` | 1 | -128 to 127 | 0 to 255 | Status codes, booleans |
| `SMALLINT` | 2 | -32,768 to 32,767 | 0 to 65,535 | Year, small counts |
| `MEDIUMINT` | 3 | -8M to 8M | 0 to 16M | Medium IDs |
| `INT` | 4 | -2.1B to 2.1B | 0 to 4.2B | Most IDs, counts |
| `BIGINT` | 8 | -9.2×10¹⁸ to 9.2×10¹⁸ | 0 to 1.8×10¹⁹ | Snowflake IDs, timestamps |

```sql
age TINYINT UNSIGNED,          -- 0-255, perfect for age
employee_id INT UNSIGNED,       -- 0-4.2 billion
twitter_snowflake_id BIGINT,    -- needs 8 bytes
```

> **Interview Insight:** `INT(11)` — the number in parentheses does NOT limit storage or range. It only affects display width with the `ZEROFILL` attribute (deprecated in MySQL 8.0). `INT(3)` and `INT(11)` store the exact same data. This is a common misconception.

#### Decimal / Float

| Type | Bytes | Precision | Use Case |
|------|-------|-----------|----------|
| `DECIMAL(M,D)` | Varies | Exact | **Money, financial data** |
| `FLOAT` | 4 | ~7 digits | Scientific data (approximate) |
| `DOUBLE` | 8 | ~15 digits | Scientific data (approximate) |

```sql
-- DECIMAL(10,2) = 10 total digits, 2 after decimal point
-- Range: -99999999.99 to 99999999.99
salary DECIMAL(10,2),   -- ✅ ALWAYS use DECIMAL for money

-- ❌ NEVER use FLOAT for money
price FLOAT,   -- 0.1 + 0.2 = 0.30000000000000004
```

> **⚠️ CRITICAL:** Using `FLOAT` or `DOUBLE` for financial data is a **career-ending bug**. Floating-point arithmetic is approximate. `DECIMAL` stores exact values as strings internally.

### 3.2 String Types

| Type | Max Length | Storage | Use Case |
|------|-----------|---------|----------|
| `CHAR(N)` | 255 chars | Fixed N bytes | Country codes (`CHAR(2)`), MD5 hashes (`CHAR(32)`) |
| `VARCHAR(N)` | 65,535 chars* | Variable (actual + 1-2 bytes) | Names, emails, URLs |
| `TEXT` | 65,535 chars | Stored off-page (pointer in row) | Comments, descriptions |
| `MEDIUMTEXT` | 16 MB | Off-page | Blog posts, articles |
| `LONGTEXT` | 4 GB | Off-page | Very large documents |

```sql
-- CHAR vs VARCHAR
country_code CHAR(2),        -- Always exactly 2 chars. Stored as 2 bytes. Padded with spaces.
name VARCHAR(100),           -- Up to 100 chars. Stored as actual_length + 1 byte.
email VARCHAR(255),          -- Standard email length
```

**CHAR vs VARCHAR — When to use which:**

| Scenario | Use | Why |
|----------|-----|-----|
| Fixed-length data (country code, UUID) | `CHAR` | No length prefix overhead, slightly faster |
| Variable-length data (names, emails) | `VARCHAR` | Saves storage for short strings |
| Very long text (comments, posts) | `TEXT` | `VARCHAR` has a 65KB row limit shared with other columns |

> **Interview Insight:** `VARCHAR(255)` vs `VARCHAR(256)` — there's a real difference. Lengths ≤ 255 use a **1-byte** length prefix. Lengths 256+ use a **2-byte** length prefix. Also, when MySQL creates temporary tables for sorting, it allocates the maximum declared length per row. So `VARCHAR(10000)` wastes memory in temp tables even if actual values are short.

### 3.3 Date & Time Types

| Type | Format | Range | Bytes | Use Case |
|------|--------|-------|-------|----------|
| `DATE` | `YYYY-MM-DD` | 1000-01-01 to 9999-12-31 | 3 | Birth dates, event dates |
| `TIME` | `HH:MM:SS` | -838:59:59 to 838:59:59 | 3 | Duration, time of day |
| `DATETIME` | `YYYY-MM-DD HH:MM:SS` | 1000 to 9999 | 8 → **5** (MySQL 5.6.4+) | Event timestamps |
| `TIMESTAMP` | `YYYY-MM-DD HH:MM:SS` | 1970-01-01 to 2038-01-19 | 4 | Row creation/update time |
| `YEAR` | `YYYY` | 1901 to 2155 | 1 | Year-only data |

**DATETIME vs TIMESTAMP — Critical Difference:**

| Feature | DATETIME | TIMESTAMP |
|---------|----------|-----------|
| Storage | 5 bytes | 4 bytes |
| Range | 1000-9999 | **1970-2038** (⚠️ Y2038 problem) |
| Timezone | Stores as-is (no conversion) | Converts to UTC on store, back to session timezone on read |
| Default | No auto-behavior | Can auto-set `CURRENT_TIMESTAMP` |

```sql
-- TIMESTAMP auto-updates (perfect for audit columns)
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

-- DATETIME for dates beyond 2038 or when you don't want timezone conversion
event_date DATETIME,

-- For new projects, many teams use DATETIME + application-level UTC
-- to avoid the 2038 overflow of TIMESTAMP
```

> **Interview Insight:** The **Year 2038 problem** — `TIMESTAMP` stores seconds since 1970-01-01 as a 32-bit signed integer. It overflows on **2038-01-19 03:14:07 UTC**. If you're designing a system that stores dates beyond 2038 (mortgages, insurance), use `DATETIME` or `BIGINT` (epoch milliseconds).

### 3.4 Other Important Types

```sql
-- BOOLEAN (alias for TINYINT(1))
is_active BOOLEAN DEFAULT TRUE,     -- stored as 1
is_deleted BOOLEAN DEFAULT FALSE,   -- stored as 0

-- ENUM — stores as integer internally (1 byte for ≤255 values)
status ENUM('active', 'inactive', 'suspended'),
-- ✅ Pros: Type-safe, storage-efficient (1-2 bytes vs VARCHAR)
-- ❌ Cons: ALTER TABLE to add values requires table rebuild (before MySQL 8.0)
--          Cannot be reused across tables (unlike a lookup table)

-- JSON (MySQL 5.7.8+)
metadata JSON,
-- Stored in an optimized binary format (not text)
-- Can be indexed via generated columns

-- BLOB (Binary Large Object)
avatar MEDIUMBLOB,   -- Store binary files (generally avoid — use object storage instead)
```

---

## 4. CREATE TABLE — Full Syntax

```sql
CREATE TABLE departments (
    department_id   INT UNSIGNED AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    location        VARCHAR(100) DEFAULT 'Remote',
    budget          DECIMAL(12,2),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Column Constraints Explained

| Constraint | Meaning | Example |
|-----------|---------|---------|
| `NOT NULL` | Column must have a value (no NULLs allowed) | `name VARCHAR(100) NOT NULL` |
| `DEFAULT value` | Use this value if none is provided on INSERT | `status VARCHAR(20) DEFAULT 'active'` |
| `AUTO_INCREMENT` | MySQL auto-assigns next integer (1, 2, 3…) | `id INT AUTO_INCREMENT` |
| `UNIQUE` | No two rows can have the same value in this column | `email VARCHAR(255) UNIQUE` |
| `PRIMARY KEY` | Uniquely identifies each row. Implies NOT NULL + UNIQUE | `PRIMARY KEY (id)` |
| `CHECK` (MySQL 8.0.16+) | Validates data on insert/update | `CHECK (age >= 0 AND age <= 150)` |

### PRIMARY KEY — The Most Important Decision

The primary key defines the **physical storage order** of rows in InnoDB (clustered index).

**Primary Key strategies:**

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| `AUTO_INCREMENT INT` | `id INT AUTO_INCREMENT` | Sequential → no page splits, compact (4 bytes) | Predictable IDs (security), 4.2B limit |
| `AUTO_INCREMENT BIGINT` | `id BIGINT AUTO_INCREMENT` | Sequential, massive range | 8 bytes per row + per secondary index entry |
| `UUID (CHAR(36))` | `id CHAR(36)` | Globally unique, no coordination | 36 bytes, random → page splits, terrible for clustered index |
| `UUID as BINARY(16)` | `id BINARY(16)` | Globally unique, 16 bytes | Random → page splits (use UUID v7 for ordering) |
| `Snowflake ID (BIGINT)` | `id BIGINT` | Time-ordered, globally unique, 8 bytes | Requires ID generator infrastructure |

> **Interview Answer:** "For most OLTP tables, I'd use `BIGINT AUTO_INCREMENT` — it's sequential (no B+ Tree page splits), compact (8 bytes in every secondary index), and the range is practically unlimited. If I need distributed ID generation without coordination, I'd use UUID v7 (time-ordered) stored as `BINARY(16)` to avoid the random-insert penalty of UUID v4."

### AUTO_INCREMENT Deep Dive

```sql
-- Auto-increment starts at 1 by default
CREATE TABLE test (id INT AUTO_INCREMENT PRIMARY KEY);

-- Set the starting value
ALTER TABLE test AUTO_INCREMENT = 1000;

-- Check current auto_increment value
SELECT AUTO_INCREMENT 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'company_db' AND TABLE_NAME = 'test';
```

**Important behaviors:**
- If you insert id=100, next auto_increment will be 101.
- If you delete the row with id=100, the id is **NOT reused** (gaps are normal).
- In InnoDB, auto_increment counter is stored **in memory** and recalculated on restart by: `SELECT MAX(id) FROM table`. This means after a crash, the counter might reuse IDs from uncommitted transactions (fixed in MySQL 8.0 — counter is now persisted in redo log).

---

## 5. ALTER TABLE — Modifying Structure

```sql
-- Add a column
ALTER TABLE employees ADD COLUMN phone VARCHAR(20) AFTER email;

-- Drop a column
ALTER TABLE employees DROP COLUMN phone;

-- Modify column type
ALTER TABLE employees MODIFY COLUMN name VARCHAR(200) NOT NULL;

-- Rename a column (MySQL 8.0+)
ALTER TABLE employees RENAME COLUMN name TO full_name;

-- Add a constraint
ALTER TABLE employees ADD CONSTRAINT uq_email UNIQUE (email);

-- Drop a constraint
ALTER TABLE employees DROP INDEX uq_email;

-- Rename the table
ALTER TABLE employees RENAME TO staff;
```

> **Interview Insight:** `ALTER TABLE` on large tables is **dangerous in production**. In older MySQL versions, many ALTER operations copy the entire table (locking it). Tools like **pt-online-schema-change** (Percona) or **gh-ost** (GitHub) perform online schema changes by creating a shadow table, copying data, and swapping. MySQL 8.0 supports `ALGORITHM=INSTANT` for some operations (adding columns at the end) which is O(1).

```sql
-- Check if an ALTER can be done instantly (MySQL 8.0+)
ALTER TABLE employees ADD COLUMN notes TEXT, ALGORITHM=INSTANT;
-- If MySQL can't do it instantly, it will throw an error instead of blocking.
```

---

## 6. SHOW & DESCRIBE — Inspecting Structure

```sql
-- Show all tables in the current database
SHOW TABLES;

-- Show the full CREATE statement for a table (extremely useful for debugging)
SHOW CREATE TABLE employees\G

-- Describe table structure (columns, types, nullability)
DESCRIBE employees;
-- or
DESC employees;
-- or (most detailed)
SHOW FULL COLUMNS FROM employees;

-- Show table status (engine, row count, avg row length, etc.)
SHOW TABLE STATUS LIKE 'employees'\G

-- Query the information_schema for programmatic access
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'company_db' AND TABLE_NAME = 'employees';
```

---

## 7. DROP & TRUNCATE

```sql
-- DROP TABLE — deletes the table and all its data permanently
DROP TABLE employees;
DROP TABLE IF EXISTS employees;  -- idempotent (no error if table doesn't exist)

-- TRUNCATE — deletes all rows but keeps the table structure
TRUNCATE TABLE employees;
```

**DROP vs TRUNCATE vs DELETE:**

| Feature | DROP | TRUNCATE | DELETE |
|---------|------|----------|--------|
| Removes table structure? | ✅ Yes | ❌ No | ❌ No |
| Removes all data? | ✅ Yes | ✅ Yes | Can use WHERE |
| Can be rolled back? | ❌ No (DDL) | ❌ No (DDL) | ✅ Yes (DML) |
| Resets AUTO_INCREMENT? | N/A | ✅ Yes | ❌ No |
| Speed on large tables | Instant | Instant (drops and recreates) | Slow (row-by-row delete) |
| Fires triggers? | ❌ No | ❌ No | ✅ Yes |
| Logged? | Minimal | Minimal (deallocates pages) | Full (logs every row) |

> **Interview Insight:** `TRUNCATE` is faster than `DELETE FROM table` because it drops and recreates the table internally (deallocation, not row-by-row deletion). But it's a DDL operation — it causes an implicit commit and cannot be rolled back.

---

## 8. Storage Engine Basics

```sql
-- Check the default storage engine
SHOW ENGINES;

-- Set engine per table
CREATE TABLE logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    message TEXT
) ENGINE=InnoDB;  -- default; always use this unless you have a specific reason
```

| Engine | Transactions | Locking | Use Case |
|--------|-------------|---------|----------|
| **InnoDB** | ✅ Yes (ACID) | Row-level | **Default. Use for everything.** |
| **MyISAM** | ❌ No | Table-level | Legacy. Full-text search (before MySQL 5.6) |
| **MEMORY** | ❌ No | Table-level | Temporary lookup tables (data lost on restart) |
| **ARCHIVE** | ❌ No | Row-level (insert only) | Write-once audit/log tables |

> **Interview Rule:** If someone asks "which storage engine?", the answer is **always InnoDB** unless you have a very specific edge case. InnoDB gives you transactions, row-level locking, crash recovery, foreign keys, and MVCC.

---

## 9. NULL — The Billion Dollar Mistake

`NULL` means **unknown/missing/not applicable**. It is NOT zero, not empty string, not false.

```sql
-- NULL comparisons are NEVER true or false — they are UNKNOWN
SELECT NULL = NULL;      -- NULL (not TRUE!)
SELECT NULL != NULL;     -- NULL (not TRUE!)
SELECT NULL > 0;         -- NULL
SELECT NULL + 5;         -- NULL (any arithmetic with NULL = NULL)

-- Use IS NULL / IS NOT NULL
SELECT * FROM employees WHERE manager_id IS NULL;

-- IFNULL / COALESCE for default values
SELECT IFNULL(phone, 'N/A') FROM employees;
SELECT COALESCE(phone, mobile, 'N/A') FROM employees;  -- first non-null value
```

**NULL's impact on queries:**

| Operation | Effect of NULL |
|-----------|---------------|
| `WHERE col = value` | NULLs are excluded (comparison is UNKNOWN) |
| `WHERE col != value` | NULLs are **still excluded** (common bug!) |
| `COUNT(col)` | Skips NULLs |
| `COUNT(*)` | Counts all rows including NULLs |
| `SUM(col)` | Ignores NULLs |
| `GROUP BY` | NULLs are grouped together |
| `UNIQUE index` | Multiple NULLs allowed (NULL ≠ NULL) |
| `ORDER BY` | NULLs sort first (ASC) in MySQL |

> **Interview Insight:** A common bug: `SELECT * FROM employees WHERE department_id != 5` does **NOT** return rows where `department_id IS NULL`. You need: `WHERE department_id != 5 OR department_id IS NULL`. This catches many people off guard.

---

## 🏋️ Exercise Tasks

Now apply everything you've learned. **Run these in a MySQL shell or workbench:**

### Task 1: Create the database
```sql
-- Create the company_db database with proper character set
-- Switch to it
```

### Task 2: Create the `departments` table
Design it with:
- `department_id` — auto-incrementing primary key
- `name` — required, max 100 chars, must be unique
- `location` — optional, defaults to 'Remote'
- `budget` — decimal for money
- `created_at` — auto-set on insert

### Task 3: Create the `employees` table
Design it with:
- `employee_id` — auto-incrementing primary key
- `first_name`, `last_name` — required
- `email` — required, must be unique
- `phone` — optional
- `hire_date` — required, DATE type
- `salary` — decimal
- `department_id` — references departments (FOREIGN KEY — just use INT for now, we'll add FK in Exercise 7)
- `is_active` — boolean, defaults to TRUE
- `created_at`, `updated_at` — auto timestamps

### Task 4: Inspect your work
```sql
-- View the structure of both tables using DESCRIBE
-- View the exact CREATE statements using SHOW CREATE TABLE
-- Check the table status to see the engine being used
```

### Task 5: Modify the structure
```sql
-- Add a 'middle_name' column to employees (after first_name)
-- Change the 'location' column in departments to NOT NULL with default 'HQ'
-- Add a CHECK constraint on salary (must be >= 0)
```

---

**Once you've completed these tasks, tell me and I'll review your solutions. Then we move to Exercise 2 — CRUD Operations!**
