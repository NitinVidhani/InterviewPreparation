# Exercise 2 — CRUD Operations

---

## 1. INSERT — Adding Data

### 1.1 Basic INSERT

```sql
-- Single row, specifying columns (RECOMMENDED — explicit and safe)
INSERT INTO departments (name, location, budget)
VALUES ('Engineering', 'Bangalore', 5000000.00);

-- Single row, all columns (fragile — breaks if schema changes)
INSERT INTO departments VALUES (DEFAULT, 'Marketing', 'Mumbai', 2000000.00, DEFAULT);
-- DEFAULT tells MySQL to use the default value (auto_increment, CURRENT_TIMESTAMP, etc.)
```

> **Rule:** Always list column names explicitly. If someone adds a column to the table, `INSERT INTO t VALUES (...)` breaks silently or with confusing errors.

### 1.2 Bulk INSERT (Multi-row)

```sql
-- Insert multiple rows in ONE statement — much faster than multiple single inserts
INSERT INTO departments (name, location, budget) VALUES
    ('Engineering', 'Bangalore', 5000000.00),
    ('Marketing', 'Mumbai', 2000000.00),
    ('Finance', 'Delhi', 3000000.00),
    ('HR', 'HQ', 1500000.00),
    ('Product', 'Bangalore', 4000000.00);
```

**Why bulk insert is faster:**

| Approach | What Happens | Network Round Trips | Transaction Overhead |
|----------|-------------|--------------------|--------------------|
| 5 individual INSERTs | 5 SQL parses, 5 commits | 5 | 5 transaction logs |
| 1 bulk INSERT (5 rows) | 1 SQL parse, 1 commit | 1 | 1 transaction log |

> **Interview Insight:** Bulk INSERT can be **10-50x faster** than row-by-row inserts. In production, when loading large datasets, you'd use `LOAD DATA INFILE` (reads directly from a CSV file) which is even faster because it bypasses the SQL parser entirely.

### 1.3 INSERT with Subquery

```sql
-- Copy data from one table to another
INSERT INTO archived_employees (employee_id, first_name, last_name, email)
SELECT employee_id, first_name, last_name, email
FROM employees
WHERE is_active = FALSE;
```

### 1.4 INSERT ... ON DUPLICATE KEY UPDATE (Upsert)

```sql
-- If the row already exists (based on PK or UNIQUE key), update it instead
INSERT INTO departments (name, location, budget)
VALUES ('Engineering', 'Hyderabad', 6000000.00)
ON DUPLICATE KEY UPDATE
    location = VALUES(location),
    budget = VALUES(budget);

-- Since 'name' has a UNIQUE constraint, if 'Engineering' exists:
--   → UPDATE its location to 'Hyderabad' and budget to 6000000
-- If 'Engineering' doesn't exist:
--   → INSERT the new row
```

**How MySQL decides "duplicate":**
1. Checks **PRIMARY KEY** conflict first
2. Then checks all **UNIQUE KEY** conflicts
3. If any conflict → runs the UPDATE part

> **Interview Insight:** `ON DUPLICATE KEY UPDATE` is MySQL-specific. PostgreSQL uses `ON CONFLICT ... DO UPDATE`. The standard SQL equivalent is `MERGE` (not supported in MySQL).

### 1.5 INSERT IGNORE & REPLACE

```sql
-- INSERT IGNORE: silently skips rows that violate a UNIQUE/PK constraint
INSERT IGNORE INTO departments (name, location, budget)
VALUES ('Engineering', 'Pune', 7000000.00);
-- If 'Engineering' exists → silently does nothing (no error, no update)

-- REPLACE: deletes the conflicting row, then inserts the new one
REPLACE INTO departments (name, location, budget)
VALUES ('Engineering', 'Pune', 7000000.00);
-- If 'Engineering' exists → DELETE old row, INSERT new row
-- ⚠️ This gives a NEW auto_increment ID! The old ID is gone.
```

| Behavior | INSERT | INSERT IGNORE | ON DUPLICATE KEY UPDATE | REPLACE |
|----------|--------|--------------|------------------------|---------|
| No conflict | Insert | Insert | Insert | Insert |
| Conflict | **Error** | **Skip silently** | **Update existing** | **Delete old + Insert new** |
| Return ID | New ID | New ID (or 0) | Old ID | **New ID** (old row deleted!) |
| Triggers fired | INSERT | INSERT (if no conflict) | INSERT + UPDATE | DELETE + INSERT |

> **⚠️ Warning:** Avoid `REPLACE` in production. It deletes the old row (losing the old PK, firing DELETE triggers, cascade-deleting child rows via FK) and inserts a new one. Use `ON DUPLICATE KEY UPDATE` instead.

### 1.6 What Happens Internally on INSERT

```
1. Parser validates SQL syntax
2. Check table-level permissions
3. Validate data types (implicit casting if needed)
4. Check NOT NULL constraints
5. Check UNIQUE / PRIMARY KEY constraints
6. Check CHECK constraints (MySQL 8.0.16+)
7. Check FOREIGN KEY constraints (if any)
8. Write to redo log (WAL — durability)
9. Write to buffer pool (in-memory page)
10. Return OK to client
11. Background: flush dirty pages to disk
```

---

## 2. SELECT — Reading Data

### 2.1 Basic SELECT

```sql
-- Select all columns (avoid in production — wastes bandwidth, breaks if schema changes)
SELECT * FROM employees;

-- Select specific columns (ALWAYS prefer this)
SELECT first_name, last_name, email, salary FROM employees;

-- Aliases (rename columns in output)
SELECT 
    first_name AS "First Name",
    last_name AS "Last Name",
    salary AS "Annual Salary"
FROM employees;

-- Expressions in SELECT
SELECT 
    first_name,
    last_name,
    salary,
    salary * 12 AS annual_salary,
    salary * 12 * 0.30 AS estimated_tax
FROM employees;
```

> **Interview Insight:** `SELECT *` is bad in production for three reasons:
> 1. **Bandwidth waste** — fetches columns you don't need, including BLOBs
> 2. **Covering index prevention** — can't use index-only scans if you need all columns
> 3. **Fragile code** — adding a column to the table changes your query's result set unexpectedly

### 2.2 WHERE Clause — Filtering Rows

The `WHERE` clause filters rows **before** they're included in the result.

#### Comparison Operators

```sql
-- Equality
SELECT * FROM employees WHERE department_id = 1;

-- Not equal (both are valid, != is more common)
SELECT * FROM employees WHERE department_id != 1;
SELECT * FROM employees WHERE department_id <> 1;

-- Greater than, less than
SELECT * FROM employees WHERE salary > 100000;
SELECT * FROM employees WHERE hire_date < '2024-01-01';

-- Greater/less than or equal
SELECT * FROM employees WHERE salary >= 50000 AND salary <= 100000;
```

#### BETWEEN (Inclusive Range)

```sql
-- Both boundaries are INCLUSIVE
SELECT * FROM employees WHERE salary BETWEEN 50000 AND 100000;
-- Equivalent to: salary >= 50000 AND salary <= 100000

-- Works with dates too
SELECT * FROM employees WHERE hire_date BETWEEN '2024-01-01' AND '2024-12-31';
```

> **Gotcha with BETWEEN and DATETIME:**
> ```sql
> -- This MISSES entries on 2024-12-31 after midnight!
> WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31'
> -- Because '2024-12-31' is interpreted as '2024-12-31 00:00:00'
> -- Fix: use '2024-12-31 23:59:59' or better: created_at < '2025-01-01'
> ```

#### IN (Set Membership)

```sql
-- Instead of multiple OR conditions
SELECT * FROM employees WHERE department_id IN (1, 3, 5);
-- Equivalent to: department_id = 1 OR department_id = 3 OR department_id = 5

-- NOT IN
SELECT * FROM employees WHERE department_id NOT IN (2, 4);
-- ⚠️ If the list contains NULL, NOT IN returns NO RESULTS for any row:
-- WHERE department_id NOT IN (2, NULL) → always UNKNOWN → 0 rows returned!
```

> **Interview Insight:** `NOT IN` with a subquery that might return `NULL` is a classic bug:
> ```sql
> -- Dangerous if subquery can return NULL values
> SELECT * FROM employees WHERE department_id NOT IN (SELECT id FROM departments);
> -- If any department id is NULL → entire query returns 0 rows
> -- Fix: use NOT EXISTS instead (correctly handles NULLs)
> ```

#### LIKE (Pattern Matching)

```sql
-- % = any number of characters (including zero)
SELECT * FROM employees WHERE last_name LIKE 'S%';      -- starts with S
SELECT * FROM employees WHERE email LIKE '%@gmail.com';  -- ends with @gmail.com
SELECT * FROM employees WHERE first_name LIKE '%kumar%'; -- contains 'kumar'

-- _ = exactly one character
SELECT * FROM employees WHERE first_name LIKE '_a%';     -- 2nd char is 'a' (Rahul, Naveen)
SELECT * FROM employees WHERE phone LIKE '____';          -- exactly 4 characters
```

**Performance of LIKE:**

| Pattern | Index Usable? | Performance |
|---------|--------------|-------------|
| `LIKE 'abc%'` | ✅ Yes (prefix match) | Fast — uses B+ Tree range scan |
| `LIKE '%abc'` | ❌ No (suffix match) | **Full table scan** — very slow |
| `LIKE '%abc%'` | ❌ No (contains) | **Full table scan** — very slow |

> **Interview Insight:** If you need fast `LIKE '%search%'` (contains search), MySQL's B+ Tree index can't help. Solutions:
> 1. **Full-Text Index** — `CREATE FULLTEXT INDEX` + `MATCH ... AGAINST`
> 2. **Elasticsearch/OpenSearch** — for complex text search at scale
> 3. **Generated column + index** — if the pattern is predictable (e.g., reverse the string for suffix search)

#### IS NULL / IS NOT NULL

```sql
-- Find employees with no phone number
SELECT * FROM employees WHERE phone IS NULL;

-- Find employees who HAVE a phone number
SELECT * FROM employees WHERE phone IS NOT NULL;

-- ❌ WRONG — this never works, NULL cannot be compared with =
SELECT * FROM employees WHERE phone = NULL;   -- always returns 0 rows
```

#### Combining Conditions (AND, OR, NOT)

```sql
-- AND: both conditions must be true
SELECT * FROM employees 
WHERE department_id = 1 AND salary > 100000;

-- OR: at least one condition must be true
SELECT * FROM employees 
WHERE department_id = 1 OR department_id = 3;

-- NOT: negate a condition
SELECT * FROM employees 
WHERE NOT (salary > 100000);

-- ⚠️ Operator precedence: AND binds tighter than OR
-- This query is NOT what you might expect:
SELECT * FROM employees 
WHERE department_id = 1 OR department_id = 2 AND salary > 100000;
-- Parsed as: dept = 1 OR (dept = 2 AND salary > 100000)
-- Fix: use parentheses
SELECT * FROM employees 
WHERE (department_id = 1 OR department_id = 2) AND salary > 100000;
```

### 2.3 ORDER BY — Sorting Results

```sql
-- Ascending (default)
SELECT * FROM employees ORDER BY salary ASC;
SELECT * FROM employees ORDER BY salary;  -- ASC is default

-- Descending
SELECT * FROM employees ORDER BY salary DESC;

-- Multiple columns (sort by dept first, then by salary within each dept)
SELECT * FROM employees ORDER BY department_id ASC, salary DESC;

-- Sort by expression
SELECT first_name, last_name, salary FROM employees ORDER BY salary * 12 DESC;

-- Sort by column position (avoid in production — fragile)
SELECT first_name, last_name, salary FROM employees ORDER BY 3 DESC;
-- 3 = third column in SELECT = salary
```

**How MySQL sorts internally:**

| Scenario | Method | Performance |
|----------|--------|-------------|
| ORDER BY matches an index | **Index scan** | Fast (already sorted) |
| No matching index, small dataset | **In-memory sort** (sort buffer) | OK |
| No matching index, large dataset | **Filesort** (disk-based sort) | Slow ⚠️ |

> **Interview Insight:** When you see `Using filesort` in `EXPLAIN`, it doesn't necessarily mean it's sorting on disk. It just means MySQL is using its own sort algorithm (not an index). But if the dataset exceeds `sort_buffer_size` (default 256KB), it spills to disk → very slow.

### 2.4 LIMIT & OFFSET — Pagination

```sql
-- Get first 10 rows
SELECT * FROM employees ORDER BY employee_id LIMIT 10;

-- Get rows 11-20 (page 2)
SELECT * FROM employees ORDER BY employee_id LIMIT 10 OFFSET 10;
-- Shorthand: LIMIT offset, count
SELECT * FROM employees ORDER BY employee_id LIMIT 10, 10;

-- Get the single highest paid employee
SELECT * FROM employees ORDER BY salary DESC LIMIT 1;
```

**⚠️ The OFFSET Performance Problem:**

```sql
-- Page 1: Fast
SELECT * FROM employees ORDER BY employee_id LIMIT 10 OFFSET 0;

-- Page 1000: SLOW — MySQL reads and discards 10,000 rows!
SELECT * FROM employees ORDER BY employee_id LIMIT 10 OFFSET 10000;
```

`OFFSET N` means MySQL scans N + LIMIT rows, discards the first N, and returns the rest. For page 10,000 with 10 rows per page, MySQL scans **100,010 rows** to return 10.

> **Interview Answer — How to fix pagination at scale:**
> ```sql
> -- ❌ Slow: OFFSET-based pagination
> SELECT * FROM employees ORDER BY employee_id LIMIT 10 OFFSET 100000;
> 
> -- ✅ Fast: Keyset/cursor pagination (seek method)
> -- "Give me 10 rows after the last ID I saw"
> SELECT * FROM employees 
> WHERE employee_id > 100000   -- last seen ID from previous page
> ORDER BY employee_id 
> LIMIT 10;
> -- Uses the PK index directly. Always fast regardless of page number.
> ```

---

## 3. UPDATE — Modifying Data

```sql
-- Update a single column
UPDATE employees SET salary = 120000 WHERE employee_id = 1;

-- Update multiple columns
UPDATE employees 
SET salary = 120000, 
    department_id = 2,
    is_active = TRUE
WHERE employee_id = 1;

-- Update with an expression
UPDATE employees SET salary = salary * 1.10 WHERE department_id = 1;
-- Give everyone in department 1 a 10% raise

-- Update using another table's value (MySQL syntax)
UPDATE employees e
JOIN departments d ON e.department_id = d.department_id
SET e.salary = e.salary * 1.15
WHERE d.name = 'Engineering';
-- 15% raise for all engineers

-- Update with LIMIT (update only first 100 matches)
UPDATE employees SET is_active = FALSE 
WHERE hire_date < '2020-01-01' 
ORDER BY hire_date 
LIMIT 100;
```

### ⚠️ Critical Safety Rules for UPDATE

```sql
-- ❌ DANGEROUS: no WHERE clause = updates ALL rows
UPDATE employees SET salary = 0;
-- Every employee now has salary 0. No undo.*

-- ✅ SAFE: always include a WHERE clause
UPDATE employees SET salary = 0 WHERE employee_id = 42;

-- ✅ SAFER: use SQL_SAFE_UPDATES mode
SET SQL_SAFE_UPDATES = 1;
-- Now MySQL rejects UPDATE/DELETE without a WHERE clause that uses a key column
UPDATE employees SET salary = 0;
-- ERROR 1175: You are using safe update mode...
```

> **Interview Insight:** In production, always wrap mass updates in a transaction so you can verify before committing:
> ```sql
> START TRANSACTION;
> UPDATE employees SET salary = salary * 1.10 WHERE department_id = 1;
> -- Check the results
> SELECT * FROM employees WHERE department_id = 1;
> -- Happy? Commit. Not happy? Rollback.
> COMMIT;   -- or ROLLBACK;
> ```

### What Happens Internally on UPDATE

```
1. Find rows matching WHERE (using index or full scan)
2. Check constraints (FK, CHECK, NOT NULL) on new values
3. Write old row values to undo log (for MVCC + rollback)
4. Write change to redo log (WAL)
5. Modify row in buffer pool (in-memory)
6. If the row has secondary indexes on modified columns → update those indexes too
7. Update updated_at if ON UPDATE CURRENT_TIMESTAMP is set
8. Return "Rows matched: N  Changed: M  Warnings: 0"
```

> `Rows matched` vs `Changed`: If you `SET salary = 100000` and the salary is already 100000, the row is *matched* but not *changed*. InnoDB skips the actual write for unchanged rows.

---

## 4. DELETE — Removing Data

```sql
-- Delete specific rows
DELETE FROM employees WHERE employee_id = 42;

-- Delete with complex conditions
DELETE FROM employees 
WHERE is_active = FALSE 
  AND hire_date < '2020-01-01';

-- Delete with JOIN (delete employees in a specific department by name)
DELETE e FROM employees e
JOIN departments d ON e.department_id = d.department_id
WHERE d.name = 'Temp Workers';

-- Delete with LIMIT (batch deletion to avoid long locks)
DELETE FROM employees WHERE is_active = FALSE ORDER BY employee_id LIMIT 1000;
-- Run this in a loop to delete in batches of 1000
```

### DELETE vs TRUNCATE (Recap)

| | DELETE FROM employees | TRUNCATE TABLE employees |
|-|----------------------|--------------------------|
| Can use WHERE? | ✅ Yes | ❌ No (removes all rows) |
| Rollback? | ✅ Yes (within transaction) | ❌ No (implicit commit) |
| Fires triggers? | ✅ Yes | ❌ No |
| Resets AUTO_INCREMENT? | ❌ No | ✅ Yes |
| Speed | Slow (row-by-row, logged) | Fast (drops + recreates table) |
| FK check | ✅ Checks foreign keys | ❌ Fails if FK references exist |

### Soft Delete Pattern (Industry Standard)

In production, most companies **never physically delete** data. Instead, they use a soft delete:

```sql
-- Instead of DELETE:
UPDATE employees SET is_active = FALSE, updated_at = NOW() WHERE employee_id = 42;

-- All SELECT queries add this filter:
SELECT * FROM employees WHERE is_active = TRUE AND department_id = 1;

-- Or use a view to hide soft-deleted rows:
CREATE VIEW active_employees AS
SELECT * FROM employees WHERE is_active = TRUE;

SELECT * FROM active_employees WHERE department_id = 1;
```

**Why soft delete:**
- **Audit trail** — you know who was deleted and when
- **Undo** — can restore with a simple UPDATE
- **Data integrity** — other tables referencing this row don't break
- **Legal compliance** — GDPR/SOX may require keeping records

> **Interview Insight:** Soft delete has downsides too — every query needs `WHERE is_active = TRUE`, indexes grow with dead data, and unique constraints get tricky (`email` must be unique among active users but allow reuse after soft delete). Solve with a partial unique index: `CREATE UNIQUE INDEX uq_active_email ON employees(email) WHERE is_active = TRUE;` — but MySQL doesn't support partial indexes! Workaround: use a composite unique index `UNIQUE(email, is_active)` or add `deleted_at` and use `UNIQUE(email, deleted_at)`.

---

## 5. Implicit Type Casting — Silent Bugs

MySQL performs **implicit type conversion** when comparing different types. This is a source of hard-to-find bugs and performance problems.

```sql
-- Column 'phone' is VARCHAR. What happens with numeric comparison?
SELECT * FROM employees WHERE phone = 1234567890;
-- MySQL converts EVERY phone value to a number for comparison!
-- '  1234567890' → 1234567890 ✓ (unexpected match)
-- '1234567890abc' → 1234567890 ✓ (unexpected match!)
-- 'abc' → 0
-- This also PREVENTS index usage on phone column!

-- ✅ Always compare with the correct type:
SELECT * FROM employees WHERE phone = '1234567890';
```

> **Interview Insight:** If you compare a VARCHAR column with a number (`WHERE varchar_col = 123`), MySQL converts **every row's value** to a number. This:
> 1. Prevents index usage → **full table scan**
> 2. Can match unexpected rows
> 3. Returns wrong results silently
>
> Always match the data type exactly.

---

## 🏋️ Exercise Tasks

Use the `company_db` with `departments` and `employees` tables from Exercise 1.

### Task 1: Populate departments
Insert these departments in a single statement:
| name | location | budget |
|------|----------|--------|
| Engineering | Bangalore | 5000000.00 |
| Marketing | Mumbai | 2000000.00 |
| Finance | Delhi | 3000000.00 |
| HR | HQ | 1500000.00 |
| Product | Bangalore | 4000000.00 |

### Task 2: Populate employees
Insert at least 10 employees spread across the departments. Include:
- At least 2 employees with NULL phone numbers
- At least 1 employee with `is_active = FALSE`
- Employees with varying hire dates (past 5 years)
- A range of salaries from 40,000 to 200,000

### Task 3: SELECT queries
Write queries to:
1. List all employees sorted by salary (highest first)
2. Find all employees in the Engineering department (use department_id)
3. Find employees hired in 2025
4. Find employees whose email ends with `@gmail.com`
5. Find employees with no phone number
6. Find the top 3 highest-paid employees
7. Find employees with salary between 60,000 and 120,000

### Task 4: UPDATE queries
1. Give a 10% raise to all employees in the Engineering department
2. Change the location of the HR department to 'Bangalore'
3. Deactivate all employees hired before 2022

### Task 5: DELETE queries
1. Delete all inactive employees (or soft-delete them — your choice)
2. Try to delete a department and observe what happens (no FK yet, so it should work — but discuss why this is problematic)

---

**Complete the tasks, then tell me and we'll review your solutions and move to Exercise 3 (Filtering, Sorting & Aggregation)!**
