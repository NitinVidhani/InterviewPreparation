# Exercise 1 — Solutions

---

## Task 1: Create the Database

```sql
-- Create the database with proper character set
CREATE DATABASE IF NOT EXISTS company_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Switch to it
USE company_db;

-- Verify
SELECT DATABASE();
-- Output: company_db
```

---

## Task 2: Create the `departments` Table

```sql
CREATE TABLE departments (
    department_id   INT UNSIGNED AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    location        VARCHAR(100) DEFAULT 'Remote',
    budget          DECIMAL(12,2),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (department_id),
    UNIQUE KEY uq_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Why these choices:**
| Column | Decision | Reasoning |
|--------|----------|-----------|
| `department_id INT UNSIGNED` | Not BIGINT | Departments won't exceed 4.2B. Saves 4 bytes per row + per secondary index entry |
| `name ... UNIQUE` | Unique constraint | Two departments shouldn't have the same name — enforces data integrity at DB level |
| `budget DECIMAL(12,2)` | Not FLOAT | Money must be exact. DECIMAL(12,2) supports up to 9,999,999,999.99 |
| `created_at TIMESTAMP` | Not DATETIME | Auto-sets to `CURRENT_TIMESTAMP`, stores in UTC, and only 4 bytes |

---

## Task 3: Create the `employees` Table

```sql
CREATE TABLE employees (
    employee_id     INT UNSIGNED AUTO_INCREMENT,
    first_name      VARCHAR(50) NOT NULL,
    last_name       VARCHAR(50) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    hire_date       DATE NOT NULL,
    salary          DECIMAL(10,2),
    department_id   INT UNSIGNED,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (employee_id),
    UNIQUE KEY uq_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Why these choices:**
| Column | Decision | Reasoning |
|--------|----------|-----------|
| `first_name VARCHAR(50)` | Not CHAR | Names are variable-length. `CHAR(50)` would pad short names with spaces |
| `email VARCHAR(255)` | 255, not more | RFC 5321 limits email to 254 chars. Also, 255 uses a 1-byte length prefix (256+ uses 2 bytes) |
| `phone VARCHAR(20)` | Not INT | Phone numbers have leading zeros, country codes (+91), dashes. Numeric types lose these |
| `hire_date DATE` | Not DATETIME | We only need the date, not time. Saves space |
| `salary DECIMAL(10,2)` | Smaller than budget | Employee salary fits in 10 digits: up to 99,999,999.99 |
| `department_id INT UNSIGNED` | Matches departments PK type | Must match the referenced column's type exactly for future FK |
| `is_active BOOLEAN` | Not ENUM | Simple true/false. `BOOLEAN` is alias for `TINYINT(1)` — 1 byte |
| `updated_at ... ON UPDATE` | Auto-updates on modification | `ON UPDATE CURRENT_TIMESTAMP` tracks last change without application code |

> **Note:** We're not adding `FOREIGN KEY` here — that's covered in Exercise 7 (Normalization & Relationships). For now `department_id` is just an `INT UNSIGNED` column with no constraint.

---

## Task 4: Inspect Your Work

```sql
-- View structure of both tables
DESCRIBE departments;
```
**Expected output:**
```
+---------------+--------------+------+-----+-------------------+-------------------+
| Field         | Type         | Null | Key | Default           | Extra             |
+---------------+--------------+------+-----+-------------------+-------------------+
| department_id | int unsigned | NO   | PRI | NULL              | auto_increment    |
| name          | varchar(100) | NO   | UNI | NULL              |                   |
| location      | varchar(100) | YES  |     | Remote            |                   |
| budget        | decimal(12,2)| YES  |     | NULL              |                   |
| created_at    | timestamp    | YES  |     | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
+---------------+--------------+------+-----+-------------------+-------------------+
```

```sql
DESCRIBE employees;
```
**Expected output:**
```
+---------------+--------------+------+-----+-------------------+-----------------------------------------------+
| Field         | Type         | Null | Key | Default           | Extra                                         |
+---------------+--------------+------+-----+-------------------+-----------------------------------------------+
| employee_id   | int unsigned | NO   | PRI | NULL              | auto_increment                                |
| first_name    | varchar(50)  | NO   |     | NULL              |                                               |
| last_name     | varchar(50)  | NO   |     | NULL              |                                               |
| email         | varchar(255) | NO   | UNI | NULL              |                                               |
| phone         | varchar(20)  | YES  |     | NULL              |                                               |
| hire_date     | date         | NO   |     | NULL              |                                               |
| salary        | decimal(10,2)| YES  |     | NULL              |                                               |
| department_id | int unsigned | YES  |     | NULL              |                                               |
| is_active     | tinyint(1)   | YES  |     | 1                 |                                               |
| created_at    | timestamp    | YES  |     | CURRENT_TIMESTAMP | DEFAULT_GENERATED                             |
| updated_at    | timestamp    | YES  |     | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |
+---------------+--------------+------+-----+-------------------+-----------------------------------------------+
```

```sql
-- View exact CREATE statements
SHOW CREATE TABLE departments\G
SHOW CREATE TABLE employees\G

-- Check table status (engine, row format, etc.)
SHOW TABLE STATUS LIKE 'departments'\G
SHOW TABLE STATUS LIKE 'employees'\G
```

**Key things to verify in SHOW TABLE STATUS:**
- `Engine: InnoDB` ← correct engine
- `Row_format: Dynamic` ← default for InnoDB in MySQL 8.0
- `Collation: utf8mb4_unicode_ci` ← correct character set

---

## Task 5: Modify the Structure

```sql
-- 1. Add 'middle_name' column after first_name
ALTER TABLE employees ADD COLUMN middle_name VARCHAR(50) AFTER first_name;
```

**Verify:**
```sql
DESCRIBE employees;
-- middle_name should appear right after first_name
```

```sql
-- 2. Change 'location' to NOT NULL with default 'HQ'
ALTER TABLE departments MODIFY COLUMN location VARCHAR(100) NOT NULL DEFAULT 'HQ';
```

**Verify:**
```sql
DESCRIBE departments;
-- location: Null = NO, Default = HQ
```

> **Gotcha:** If the table already had rows with `NULL` in `location`, this `ALTER` would fail with:
> `ERROR 1138: Invalid use of NULL value`
> You'd need to update existing NULLs first:
> ```sql
> UPDATE departments SET location = 'HQ' WHERE location IS NULL;
> ALTER TABLE departments MODIFY COLUMN location VARCHAR(100) NOT NULL DEFAULT 'HQ';
> ```

```sql
-- 3. Add CHECK constraint on salary (must be >= 0)
ALTER TABLE employees ADD CONSTRAINT chk_salary_positive CHECK (salary >= 0);
```

**Verify:**
```sql
-- Try inserting a negative salary — should fail
INSERT INTO employees (first_name, last_name, email, hire_date, salary)
VALUES ('Test', 'User', 'test@test.com', '2026-01-01', -5000);
-- ERROR 3819 (HY000): Check constraint 'chk_salary_positive' is violated.

-- Check that the constraint exists
SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'company_db';
-- Output: chk_salary_positive | (`salary` >= 0)
```

> **Important:** `CHECK` constraints were **parsed but ignored** before MySQL 8.0.16. If you're on an older version, the syntax passes but does nothing — a silent bug. Always verify your MySQL version: `SELECT VERSION();`

---

## Final Schema State

After all tasks, your tables look like this:

```
company_db
├── departments
│   ├── department_id  INT UNSIGNED  PK  AUTO_INCREMENT
│   ├── name           VARCHAR(100)  NOT NULL  UNIQUE
│   ├── location       VARCHAR(100)  NOT NULL  DEFAULT 'HQ'
│   ├── budget         DECIMAL(12,2)
│   └── created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
│
└── employees
    ├── employee_id    INT UNSIGNED  PK  AUTO_INCREMENT
    ├── first_name     VARCHAR(50)   NOT NULL
    ├── middle_name    VARCHAR(50)
    ├── last_name      VARCHAR(50)   NOT NULL
    ├── email          VARCHAR(255)  NOT NULL  UNIQUE
    ├── phone          VARCHAR(20)
    ├── hire_date      DATE          NOT NULL
    ├── salary         DECIMAL(10,2) CHECK (>= 0)
    ├── department_id  INT UNSIGNED
    ├── is_active      BOOLEAN       DEFAULT TRUE
    ├── created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
    └── updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

---

**Ready for Exercise 2 — CRUD Operations? Just say the word!**
