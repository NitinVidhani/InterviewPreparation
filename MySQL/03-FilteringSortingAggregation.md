# Exercise 3 — Filtering, Sorting & Aggregation

---

## 1. Aggregate Functions

Aggregate functions compute a **single value** from a set of rows.

### 1.1 The Big Five

```sql
-- COUNT — number of rows
SELECT COUNT(*) FROM employees;              -- count ALL rows (including NULLs)
SELECT COUNT(phone) FROM employees;          -- count rows where phone IS NOT NULL
SELECT COUNT(DISTINCT department_id) FROM employees;  -- count unique departments

-- SUM — total of a numeric column
SELECT SUM(salary) FROM employees;           -- total payroll
SELECT SUM(salary) FROM employees WHERE department_id = 1;

-- AVG — arithmetic mean
SELECT AVG(salary) FROM employees;           -- average salary
-- ⚠️ AVG ignores NULLs. If 8/10 employees have salary, AVG = SUM/8, not SUM/10

-- MIN / MAX — smallest / largest value
SELECT MIN(salary), MAX(salary) FROM employees;
SELECT MIN(hire_date), MAX(hire_date) FROM employees;  -- works with dates too
```

**NULL Handling in Aggregates — Critical to Understand:**

| Function | NULL Behavior | Example (values: 100, 200, NULL) |
|----------|--------------|----------------------------------|
| `COUNT(*)` | Counts all rows | 3 |
| `COUNT(col)` | **Skips NULLs** | 2 |
| `SUM(col)` | **Skips NULLs** | 300 (not NULL) |
| `AVG(col)` | **Skips NULLs** | 150 (300/2, not 300/3) |
| `MIN(col)` | **Skips NULLs** | 100 |
| `MAX(col)` | **Skips NULLs** | 200 |

> **Interview Insight:** `AVG(salary)` vs `SUM(salary)/COUNT(*)` give **different results** if any salary is NULL. `AVG` divides by only non-NULL count. `SUM/COUNT(*)` divides by total rows. Be explicit about which behavior you want:
> ```sql
> -- True average (ignoring NULLs)
> SELECT AVG(salary) FROM employees;
>
> -- Average treating NULL as 0
> SELECT SUM(IFNULL(salary, 0)) / COUNT(*) FROM employees;
> ```

### 1.2 COUNT(*) vs COUNT(col) vs COUNT(1)

```sql
SELECT COUNT(*) FROM employees;   -- counts all rows (fastest — InnoDB has a special optimization)
SELECT COUNT(1) FROM employees;   -- identical to COUNT(*) in MySQL
SELECT COUNT(phone) FROM employees;  -- counts only rows where phone IS NOT NULL
```

> **Interview Insight:** In InnoDB, `COUNT(*)` doesn't scan the primary key index. It scans the **smallest secondary index** (fewer pages to read). If you have no secondary indexes, it scans the clustered index. This is why `COUNT(*)` on a 100M-row table can still take seconds — there's no pre-computed count stored.

Want an instant count? Use:
```sql
-- Approximate (from table statistics — may be off by 10-20%)
SELECT TABLE_ROWS FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'company_db' AND TABLE_NAME = 'employees';
```

---

## 2. GROUP BY — Grouping Rows

`GROUP BY` collapses multiple rows into **one row per group** and lets you run aggregate functions on each group.

### Execution Order

```
FROM        → which table(s)
WHERE       → filter rows BEFORE grouping
GROUP BY    → group remaining rows
HAVING      → filter groups AFTER grouping
SELECT      → compute output columns
ORDER BY    → sort results
LIMIT       → restrict output count
```

> **Interview Insight:** This execution order is why you can't use a column alias from SELECT in WHERE (SELECT runs after WHERE), but you CAN use it in ORDER BY (ORDER BY runs after SELECT). MySQL actually allows aliases in HAVING as a non-standard extension.

### 2.1 Basic GROUP BY

```sql
-- Count employees per department
SELECT department_id, COUNT(*) AS employee_count
FROM employees
GROUP BY department_id;

-- Result:
-- department_id | employee_count
-- 1             | 4
-- 2             | 3
-- 3             | 2
-- NULL          | 1   ← employees with no department are grouped together
```

### 2.2 GROUP BY with Multiple Aggregates

```sql
-- Department-level salary statistics
SELECT 
    department_id,
    COUNT(*) AS headcount,
    SUM(salary) AS total_salary,
    AVG(salary) AS avg_salary,
    MIN(salary) AS min_salary,
    MAX(salary) AS max_salary
FROM employees
WHERE is_active = TRUE
GROUP BY department_id
ORDER BY avg_salary DESC;
```

### 2.3 GROUP BY Multiple Columns

```sql
-- Count employees by department AND active status
SELECT department_id, is_active, COUNT(*) AS count
FROM employees
GROUP BY department_id, is_active;

-- Result:
-- department_id | is_active | count
-- 1             | 1         | 3
-- 1             | 0         | 1
-- 2             | 1         | 3
-- 3             | 1         | 2
```

### 2.4 The Non-Aggregated Column Rule

```sql
-- ❌ WRONG in strict SQL mode (default in MySQL 8.0):
SELECT department_id, first_name, COUNT(*) 
FROM employees 
GROUP BY department_id;
-- ERROR 1055: 'first_name' isn't in GROUP BY or an aggregate function

-- Why? If dept 1 has 4 employees, which first_name should MySQL show? It's ambiguous.
```

> **Interview Insight:** MySQL 5.7 and below had `ONLY_FULL_GROUP_BY` disabled by default — it would silently pick a random value for non-aggregated columns. MySQL 8.0 enables it by default (correct behavior). If an interviewer asks about this, explain that the old behavior was a **data correctness bug** masquerading as a feature.

```sql
-- ✅ FIX: either aggregate the column or add it to GROUP BY
SELECT department_id, GROUP_CONCAT(first_name) AS names, COUNT(*)
FROM employees
GROUP BY department_id;
-- GROUP_CONCAT joins all first_names into a comma-separated string per group
```

---

## 3. HAVING — Filtering Groups

`WHERE` filters **rows before grouping**. `HAVING` filters **groups after grouping**.

```sql
-- Departments with more than 3 employees
SELECT department_id, COUNT(*) AS headcount
FROM employees
GROUP BY department_id
HAVING headcount > 3;
-- You MUST use HAVING, not WHERE, because COUNT(*) doesn't exist yet during WHERE

-- Departments where average salary exceeds 100000
SELECT department_id, AVG(salary) AS avg_salary
FROM employees
GROUP BY department_id
HAVING avg_salary > 100000;

-- Combine WHERE and HAVING
SELECT department_id, AVG(salary) AS avg_salary
FROM employees
WHERE is_active = TRUE          -- filter rows FIRST (only active employees)
GROUP BY department_id          -- then group
HAVING avg_salary > 80000       -- then filter groups
ORDER BY avg_salary DESC;       -- then sort
```

**WHERE vs HAVING:**

| | WHERE | HAVING |
|-|-------|--------|
| Filters | Individual rows | Grouped results |
| Runs | Before GROUP BY | After GROUP BY |
| Can use aggregates? | ❌ No | ✅ Yes |
| Performance | Faster (filters early, less data to group) | Runs after grouping |

> **Interview Insight:** Always prefer `WHERE` over `HAVING` when possible. `WHERE department_id = 1` is faster than `HAVING department_id = 1` because WHERE reduces the dataset before the expensive GROUP BY operation.

---

## 4. DISTINCT — Removing Duplicates

```sql
-- Unique departments
SELECT DISTINCT department_id FROM employees;

-- Unique combinations
SELECT DISTINCT department_id, is_active FROM employees;
-- Returns unique (department_id, is_active) pairs

-- DISTINCT with COUNT
SELECT COUNT(DISTINCT department_id) AS unique_depts FROM employees;
```

**How DISTINCT works internally:**
1. MySQL executes the query
2. Sorts the entire result set (or uses a hash table)  
3. Removes duplicate rows

> **Interview Insight:** `DISTINCT` is often a code smell. If you need `DISTINCT`, ask yourself: "Why am I getting duplicates?" Usually it means you have an incorrect JOIN or missing WHERE clause. Fix the root cause rather than masking it with `DISTINCT`.

---

## 5. CASE WHEN — Conditional Logic

`CASE` is SQL's version of if/else. It returns a value based on conditions.

### 5.1 Simple CASE

```sql
-- Categorize salary into bands
SELECT 
    first_name,
    last_name,
    salary,
    CASE
        WHEN salary >= 150000 THEN 'Senior'
        WHEN salary >= 100000 THEN 'Mid-Level'
        WHEN salary >= 60000  THEN 'Junior'
        ELSE 'Entry Level'
    END AS salary_band
FROM employees
ORDER BY salary DESC;
```

### 5.2 CASE in Aggregation (Pivot-like Queries)

```sql
-- Count active vs inactive per department in a single row
SELECT 
    department_id,
    COUNT(*) AS total,
    SUM(CASE WHEN is_active = TRUE THEN 1 ELSE 0 END) AS active_count,
    SUM(CASE WHEN is_active = FALSE THEN 1 ELSE 0 END) AS inactive_count
FROM employees
GROUP BY department_id;

-- Result:
-- department_id | total | active_count | inactive_count
-- 1             | 4     | 3            | 1
-- 2             | 3     | 3            | 0
```

### 5.3 CASE in ORDER BY

```sql
-- Custom sort order: show Engineering first, then Product, then others alphabetically
SELECT d.name, e.first_name, e.salary
FROM employees e
JOIN departments d ON e.department_id = d.department_id
ORDER BY 
    CASE d.name
        WHEN 'Engineering' THEN 1
        WHEN 'Product' THEN 2
        ELSE 3
    END,
    d.name;
```

### 5.4 CASE in UPDATE

```sql
-- Different raise percentages by department
UPDATE employees SET salary = salary * 
    CASE department_id
        WHEN 1 THEN 1.15  -- Engineering: 15%
        WHEN 2 THEN 1.10  -- Marketing: 10%
        WHEN 3 THEN 1.08  -- Finance: 8%
        ELSE 1.05          -- Others: 5%
    END;
```

---

## 6. IFNULL, COALESCE, NULLIF — NULL Handling Functions

```sql
-- IFNULL(expr, default) — if expr is NULL, return default
SELECT first_name, IFNULL(phone, 'Not Provided') AS phone FROM employees;

-- COALESCE(expr1, expr2, ...) — return first non-NULL value
SELECT COALESCE(phone, mobile, home_phone, 'No Contact') AS contact FROM employees;
-- More flexible than IFNULL (handles multiple columns)

-- NULLIF(expr1, expr2) — returns NULL if expr1 = expr2, otherwise returns expr1
-- Useful to prevent division by zero:
SELECT department_id, 
    SUM(salary) / NULLIF(COUNT(*), 0) AS avg_salary  -- returns NULL instead of error if count is 0
FROM employees
GROUP BY department_id;
```

---

## 7. GROUP_CONCAT — Aggregating Strings

```sql
-- List all employee names per department in one row
SELECT 
    department_id,
    GROUP_CONCAT(first_name ORDER BY first_name SEPARATOR ', ') AS employees
FROM employees
GROUP BY department_id;

-- Result:
-- department_id | employees
-- 1             | Alice, Bob, Charlie, Dave
-- 2             | Eve, Frank, Grace

-- With DISTINCT (remove duplicate names)
GROUP_CONCAT(DISTINCT first_name ORDER BY first_name SEPARATOR ', ')
```

> **⚠️ Warning:** `GROUP_CONCAT` has a default max length of **1024 bytes** (`group_concat_max_len`). If your concatenated string exceeds this, it's silently truncated! Increase it if needed:
> ```sql
> SET SESSION group_concat_max_len = 100000;
> ```

---

## 8. Date Functions (Preview for Aggregation)

```sql
-- Extract parts of a date
SELECT YEAR(hire_date), MONTH(hire_date), DAY(hire_date) FROM employees;

-- Group by year of hire
SELECT YEAR(hire_date) AS hire_year, COUNT(*) AS hired_count
FROM employees
GROUP BY YEAR(hire_date)
ORDER BY hire_year;

-- Group by month (across all years)
SELECT DATE_FORMAT(hire_date, '%Y-%m') AS hire_month, COUNT(*)
FROM employees
GROUP BY hire_month
ORDER BY hire_month;

-- Employees hired in the last 90 days
SELECT * FROM employees 
WHERE hire_date >= DATE_SUB(CURDATE(), INTERVAL 90 DAY);
```

---

## 🗄️ Dataset Setup

Run this script to create and seed the dataset for this exercise. It's safe to re-run.

> If you already ran the setup from Exercise 2, you can skip straight to the tasks — the same `company_db` dataset is used.

```sql
-- ─── 0. Create & switch database ─────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS company_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE company_db;

-- ─── 1. Tables ────────────────────────────────────────────────────────────────
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS departments;

CREATE TABLE departments (
    department_id INT UNSIGNED AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    location      VARCHAR(100) NOT NULL DEFAULT 'HQ',
    budget        DECIMAL(12,2),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (department_id),
    UNIQUE KEY uq_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employees (
    employee_id   INT UNSIGNED AUTO_INCREMENT,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    hire_date     DATE         NOT NULL,
    salary        DECIMAL(10,2),
    department_id INT UNSIGNED,
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (employee_id),
    UNIQUE KEY uq_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─── 2. Seed departments ──────────────────────────────────────────────────────
INSERT INTO departments (name, location, budget) VALUES
    ('Engineering', 'Bangalore', 5000000.00),
    ('Marketing',   'Mumbai',    2000000.00),
    ('Finance',     'Delhi',     3000000.00),
    ('HR',          'HQ',        1500000.00),
    ('Product',     'Bangalore', 4000000.00);

-- ─── 3. Seed employees (15 rows) ─────────────────────────────────────────────
INSERT INTO employees (first_name, last_name, email, phone, hire_date, salary, department_id, is_active) VALUES
-- Engineering (dept 1) — 4 employees
('Arjun',   'Sharma',   'arjun.sharma@gmail.com',   '9876543210', '2021-03-15', 180000.00, 1, TRUE),
('Priya',   'Nair',     'priya.nair@company.com',   '9876543211', '2022-07-01', 145000.00, 1, TRUE),
('Rahul',   'Verma',    'rahul.verma@gmail.com',    NULL,         '2023-01-10', 95000.00,  1, TRUE),
('Sneha',   'Iyer',     'sneha.iyer@company.com',   '9876543213', '2020-05-20', 200000.00, 1, FALSE),
-- Marketing (dept 2) — 3 employees
('Karan',   'Mehta',    'karan.mehta@gmail.com',    '9876543214', '2023-06-01', 85000.00,  2, TRUE),
('Divya',   'Pillai',   'divya.pillai@company.com', '9876543215', '2024-02-14', 75000.00,  2, TRUE),
('Nikhil',  'Gupta',    'nikhil.gupta@gmail.com',   NULL,         '2024-08-01', 65000.00,  2, TRUE),
-- Finance (dept 3) — 2 employees
('Pooja',   'Reddy',    'pooja.reddy@company.com',  '9876543217', '2022-11-01', 110000.00, 3, TRUE),
('Aditya',  'Kumar',    'aditya.kumar@gmail.com',   '9876543218', '2021-09-15', 125000.00, 3, TRUE),
-- HR (dept 4) — 2 employees
('Meena',   'Krishnan', 'meena.k@company.com',      '9876543219', '2023-03-01', 70000.00,  4, TRUE),
('Suresh',  'Bhat',     'suresh.bhat@gmail.com',    NULL,         '2019-12-01', 55000.00,  4, FALSE),
-- Product (dept 5) — 3 employees
('Lakshmi', 'Venkat',   'lakshmi.v@company.com',    '9876543221', '2022-04-01', 155000.00, 5, TRUE),
('Rohit',   'Joshi',    'rohit.joshi@gmail.com',    '9876543222', '2023-10-01', 120000.00, 5, TRUE),
('Anjali',  'Singh',    'anjali.singh@company.com', '9876543223', '2024-05-15', 90000.00,  5, TRUE),
-- No department (NULL dept) — 1 employee
('Vikram',  'Das',       'vikram.das@gmail.com',    NULL,         '2025-01-10', 45000.00,  NULL, TRUE);
```

**Quick reference — dataset overview for writing your queries:**

| Department | dept_id | Headcount | Salary Range | Active |
|-----------|---------|-----------|-------------|--------|
| Engineering | 1 | 4 | 95K – 200K | 3 active, 1 inactive |
| Marketing | 2 | 3 | 65K – 85K | 3 active |
| Finance | 3 | 2 | 110K – 125K | 2 active |
| HR | 4 | 2 | 55K – 70K | 1 active, 1 inactive |
| Product | 5 | 3 | 90K – 155K | 3 active |
| (No dept) | NULL | 1 | 45K | 1 active |

---

## 🏋️ Exercise Tasks

### Task 1: Basic Aggregation
Write queries to find:
1. Total number of employees
2. Total number of employees who have a phone number
3. Total salary expenditure across all employees
4. Average salary of all employees
5. Minimum and maximum salary

### Task 2: GROUP BY
1. Count of employees per department
2. Average salary per department, sorted highest to lowest
3. Number of employees hired per year
4. Total salary budget per department (SUM of salaries)

### Task 3: HAVING
1. Find departments with more than 2 employees
2. Find departments where the average salary is above 80,000
3. Find hire years where more than 3 people were hired

### Task 4: CASE WHEN
1. Categorize all employees into salary bands: 'Entry' (<60K), 'Junior' (60K-100K), 'Mid' (100K-150K), 'Senior' (>150K). Show the count per band.
2. Create a report showing each department with columns: `department_id`, `total`, `active`, `inactive`

### Task 5: Complex Queries
1. Show each department with: department name, headcount, avg salary, the name of the highest-paid person (hint: this is tricky with just GROUP BY — try subquery)
2. For each department, show a comma-separated list of employee names using GROUP_CONCAT
3. Find departments where the highest salary is more than double the lowest salary

---

**Complete the tasks, then tell me and we'll review and move to Phase 2 (Joins)!**
