# Exercise 4 — Joins Deep Dive

---

## 1. Why Joins Exist — The Relational Model Fundamentals

Relational databases store data in **normalized tables** — each table has a single responsibility. Joins are how you **re-combine** that data at query time without duplicating it at storage time.

```
Without joins (denormalized):                With joins (normalized):
┌───────────────────────────────┐           ┌──────────────────┐   ┌────────────────┐
│ employee_name | dept_name     │           │ employees        │   │ departments    │
│ Arjun         | Engineering   │           │ employee_id      │   │ department_id  │
│ Priya         | Engineering   │  ←joins→  │ name             │   │ name           │
│ Karan         | Marketing     │           │ department_id ───┼──►│ location       │
│ Karan         | Marketing     │           └──────────────────┘   └────────────────┘
│               (redundant!)    │
└───────────────────────────────┘

Problem: "Engineering" stored N times → update anomalies, storage waste
```

---

## 2. How MySQL Executes Joins Internally

Before the types, understand **what MySQL does** when you write a JOIN:

### 2.1 Nested Loop Join (NLJ) — The Default

```
For each row in Table A (outer/driving table):
    For each matching row in Table B (inner table):
        Output the combined row

-- Cost: O(n × m) in worst case
-- With an index on the join column of B: O(n × log m) ← much better
```

```sql
-- MySQL picks the "driving table" (smaller result set after WHERE filtering)
-- and probes the inner table using the join column's index.
EXPLAIN SELECT e.first_name, d.name
FROM employees e
JOIN departments d ON e.department_id = d.department_id;
-- Look at: "type" column — "ref" means index lookup (good), "ALL" means full scan (bad)
```

### 2.2 Hash Join (MySQL 8.0.18+)

For joins without usable indexes, MySQL 8.0.18+ uses **Hash Join**:
```
1. Build phase:    Hash all rows of smaller table into a hash table in memory
2. Probe phase:    Scan larger table; for each row, probe the hash table

-- Cost: O(n + m) — much faster than NLJ without index
-- Memory: requires join_buffer_size memory for the hash table
```

### 2.3 The Join Order Matters

```sql
-- MySQL's optimizer chooses join order automatically
-- But you can influence it with STRAIGHT_JOIN (forces left-to-right order)
SELECT STRAIGHT_JOIN e.first_name, d.name
FROM employees e STRAIGHT_JOIN departments d ON e.department_id = d.department_id;
-- ⚠️ Only do this when you're smarter than the optimizer (rare)
```

> **Interview Insight:** The optimizer uses **cost-based optimization** — it estimates the number of rows each table returns (using statistics from `ANALYZE TABLE`) and picks the join order with the lowest estimated cost. If statistics are stale, it makes bad decisions. Always run `ANALYZE TABLE` after bulk data loads.

---

## 3. INNER JOIN — The Most Common Join

Returns only rows where the join condition is TRUE in **both** tables. Non-matching rows from either table are **excluded**.

```sql
-- Syntax (JOIN and INNER JOIN are identical)
SELECT e.first_name, e.last_name, e.salary, d.name AS department
FROM employees e
INNER JOIN departments d ON e.department_id = d.department_id;

-- Shorthand (same result)
FROM employees e JOIN departments d ON e.department_id = d.department_id;
```

**What happens to Vikram (department_id = NULL)?** → **Excluded**. `NULL = any_value` is always UNKNOWN → the join condition fails.

```
employees              departments
┌─────────────┐        ┌──────────────────┐
│ Arjun  | 1  │───────►│ 1 | Engineering  │  ✅ Match → included
│ Priya  | 1  │───────►│ 1 | Engineering  │  ✅ Match → included
│ Vikram |NULL│        │ 2 | Marketing    │  ❌ No match → excluded
└─────────────┘        └──────────────────┘
```

### USING vs ON

```sql
-- ON: explicit condition (works even if column names differ)
JOIN departments d ON e.department_id = d.department_id

-- USING: shorthand when column name is identical in both tables
JOIN departments d USING (department_id)
-- ✅ Cleaner syntax, but column must have the same name
-- ✅ Output includes department_id only once (not duplicated)
-- ❌ Can't use if column names differ
```

---

## 4. LEFT JOIN (LEFT OUTER JOIN)

Returns **all rows from the left table**, plus matching rows from the right. If no match → right side columns are `NULL`.

```sql
-- Get ALL employees, including those with no department
SELECT e.first_name, e.last_name, d.name AS department
FROM employees e
LEFT JOIN departments d ON e.department_id = d.department_id;
-- Vikram → department = NULL (no match in departments)
-- All other 14 employees → their department names
```

**Visualizing LEFT JOIN:**
```
employees (LEFT)         departments (RIGHT)
┌─────────────────┐      ┌──────────────────┐
│ Arjun    | 1    │─────►│ 1 | Engineering  │ → Arjun + Engineering ✅
│ Priya    | 1    │─────►│ 1 | Engineering  │ → Priya + Engineering ✅
│ Vikram   | NULL │  ✗   │                  │ → Vikram + NULL ✅ (kept!)
└─────────────────┘      └──────────────────┘
```

### Finding Orphan Records with LEFT JOIN

This is the classic **anti-join pattern** — one of the most important interview patterns:

```sql
-- Find employees who don't belong to any department
SELECT e.first_name, e.last_name
FROM employees e
LEFT JOIN departments d ON e.department_id = d.department_id
WHERE d.department_id IS NULL;  -- ← right side is NULL = no match found
-- Result: Vikram Das

-- Find departments with NO employees
SELECT d.name
FROM departments d
LEFT JOIN employees e ON d.department_id = e.department_id
WHERE e.employee_id IS NULL;  -- ← no employee matched this department
```

> **Interview Insight:** `LEFT JOIN ... WHERE right_table.id IS NULL` is the standard pattern to find records in one table that have **no corresponding record** in another table. This is O(n log m) with an index — much faster than `NOT IN (subquery)` or `NOT EXISTS` in many cases.

---

## 5. RIGHT JOIN (RIGHT OUTER JOIN)

The mirror of LEFT JOIN — returns **all rows from the right table**, plus matching left rows.

```sql
-- All departments, with their employees (NULL if no employees)
SELECT d.name AS department, e.first_name
FROM employees e
RIGHT JOIN departments d ON e.department_id = d.department_id;
```

> **Practical Note:** `RIGHT JOIN` is almost never used in practice. Any `RIGHT JOIN` can be rewritten as a `LEFT JOIN` by swapping the tables. Most developers always use `LEFT JOIN` for consistency and readability.
> ```sql
> -- These are identical:
> FROM employees e RIGHT JOIN departments d ON ...
> FROM departments d LEFT JOIN employees e ON ...
> ```

---

## 6. FULL OUTER JOIN — MySQL Workaround

MySQL does **NOT** support `FULL OUTER JOIN` syntax. Simulate it with `UNION`:

```sql
-- All employees + all departments, matched where possible
SELECT e.first_name, d.name
FROM employees e
LEFT JOIN departments d ON e.department_id = d.department_id

UNION

SELECT e.first_name, d.name
FROM employees e
RIGHT JOIN departments d ON e.department_id = d.department_id;

-- This gives:
-- ✅ All employees (including those with no dept)
-- ✅ All departments (including those with no employees)
-- ✅ Matches where both sides have data
```

---

## 7. CROSS JOIN — Cartesian Product

Returns every combination of rows from both tables. **n × m rows** in output.

```sql
-- Every employee paired with every department
SELECT e.first_name, d.name
FROM employees e
CROSS JOIN departments d;
-- 15 employees × 5 departments = 75 rows

-- Practical uses:
-- 1. Generate date/time series
-- 2. Create test data
-- 3. Compute all combinations for a matrix

-- Example: Generate all months for a report (even months with 0 sales)
SELECT months.m, COALESCE(SUM(sales.amount), 0) AS total
FROM (SELECT 1 m UNION SELECT 2 UNION SELECT 3 ... UNION SELECT 12) months
LEFT JOIN sales ON MONTH(sales.sale_date) = months.m
GROUP BY months.m;
```

> **Warning:** A CROSS JOIN without a WHERE clause on large tables will produce **billions of rows** and kill your database. Always verify CROSS JOIN result row count before running.

---

## 8. SELF JOIN — A Table Joining Itself

A table joined to itself using different aliases. Classic use case: **hierarchical data** (manager-employee relationships).

```sql
-- Add a manager_id column to employees for this concept
-- manager_id references employee_id in the same table

-- Find each employee's manager name
SELECT 
    e.first_name  AS employee,
    m.first_name  AS manager
FROM employees e
LEFT JOIN employees m ON e.manager_id = m.employee_id;
-- LEFT JOIN because the CEO has no manager (manager_id = NULL)

-- Find all employees reporting to 'Arjun'
SELECT e.first_name AS report
FROM employees e
JOIN employees m ON e.manager_id = m.employee_id
WHERE m.first_name = 'Arjun';
```

---

## 9. Multi-Table Joins

```sql
-- Join 3 tables: employees + departments + projects
SELECT 
    e.first_name,
    d.name AS department,
    p.title AS project
FROM employees e
JOIN departments d ON e.department_id = d.department_id
JOIN employee_projects ep ON e.employee_id = ep.employee_id
JOIN projects p ON ep.project_id = p.project_id
WHERE d.name = 'Engineering'
ORDER BY e.first_name;
```

**Join order best practices:**
1. Start with the most restrictive table (adds the most filtering early)
2. Use indexes on all join columns
3. Use `EXPLAIN` to verify the optimizer's chosen order

---

## 10. JOIN Performance — Index Rules

```sql
-- ✅ FAST: join column has an index on the inner table
CREATE INDEX idx_emp_dept ON employees(department_id);
-- Lookup cost: O(log n) per outer row

-- ❌ SLOW: no index on join column
-- Full scan of inner table for every outer row
-- Cost: O(n × m) = catastrophic on large tables

-- Check if your join uses an index:
EXPLAIN SELECT e.first_name, d.name
FROM employees e JOIN departments d ON e.department_id = d.department_id;
-- "type: ref" → uses index ✅
-- "type: ALL" → full table scan ❌ — add an index
```

**When to index join columns:**
- ✅ **Foreign key columns** — always index them (MySQL does NOT auto-index FK columns!)
- ✅ **Frequently joined columns** even without a formal FK
- ❌ **Very low cardinality** columns (e.g., is_active with 2 values) — index not selective enough

---

## 11. Common Interview Patterns

### Pattern 1: Second-Highest Salary per Department

```sql
-- Using self-join
SELECT e1.department_id, e1.salary
FROM employees e1
WHERE 1 = (
    SELECT COUNT(DISTINCT e2.salary)
    FROM employees e2
    WHERE e2.department_id = e1.department_id
    AND e2.salary > e1.salary
);
```

### Pattern 2: Employees Earning More Than Their Department Average

```sql
SELECT e.first_name, e.salary, dept_avg.avg_sal
FROM employees e
JOIN (
    SELECT department_id, AVG(salary) AS avg_sal
    FROM employees
    GROUP BY department_id
) dept_avg ON e.department_id = dept_avg.department_id
WHERE e.salary > dept_avg.avg_sal;
```

### Pattern 3: Departments with All Employees Active

```sql
-- Departments where NO inactive employee exists
SELECT d.name
FROM departments d
WHERE d.department_id NOT IN (
    SELECT department_id FROM employees WHERE is_active = FALSE
);
```

---

## 🗄️ Dataset Setup

Run this first. Adds a `projects` table and `employee_projects` linking table for multi-table join exercises.

```sql
CREATE DATABASE IF NOT EXISTS company_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE company_db;

-- Drop in correct order (child tables first)
DROP TABLE IF EXISTS employee_projects;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS departments;

-- Departments
CREATE TABLE departments (
    department_id INT UNSIGNED AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    location      VARCHAR(100) NOT NULL DEFAULT 'HQ',
    budget        DECIMAL(12,2),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (department_id),
    UNIQUE KEY uq_dept_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Employees (with manager_id for self-join exercises)
CREATE TABLE employees (
    employee_id   INT UNSIGNED AUTO_INCREMENT,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    hire_date     DATE         NOT NULL,
    salary        DECIMAL(10,2),
    department_id INT UNSIGNED,
    manager_id    INT UNSIGNED,          -- self-reference for org hierarchy
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (employee_id),
    UNIQUE KEY uq_email (email),
    KEY idx_dept (department_id),
    KEY idx_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Projects
CREATE TABLE projects (
    project_id  INT UNSIGNED AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL,
    status      ENUM('active', 'completed', 'cancelled') DEFAULT 'active',
    start_date  DATE,
    end_date    DATE,
    PRIMARY KEY (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Employee ↔ Project (many-to-many junction table)
CREATE TABLE employee_projects (
    employee_id INT UNSIGNED NOT NULL,
    project_id  INT UNSIGNED NOT NULL,
    role        VARCHAR(50)  DEFAULT 'Member',
    joined_date DATE,
    PRIMARY KEY (employee_id, project_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Seed departments ──────────────────────────────────────────────────────────
INSERT INTO departments (name, location, budget) VALUES
    ('Engineering', 'Bangalore', 5000000.00),
    ('Marketing',   'Mumbai',    2000000.00),
    ('Finance',     'Delhi',     3000000.00),
    ('HR',          'HQ',        1500000.00),
    ('Product',     'Bangalore', 4000000.00);

-- ── Seed employees (with manager hierarchy) ───────────────────────────────────
-- Arjun (id=1) and Lakshmi (id=12) are top-level managers (manager_id = NULL)
INSERT INTO employees (employee_id, first_name, last_name, email, phone, hire_date, salary, department_id, manager_id, is_active) VALUES
(1,  'Arjun',   'Sharma',   'arjun.sharma@gmail.com',   '9876543210', '2021-03-15', 180000.00, 1, NULL, TRUE),
(2,  'Priya',   'Nair',     'priya.nair@company.com',   '9876543211', '2022-07-01', 145000.00, 1, 1,    TRUE),
(3,  'Rahul',   'Verma',    'rahul.verma@gmail.com',    NULL,         '2023-01-10', 95000.00,  1, 1,    TRUE),
(4,  'Sneha',   'Iyer',     'sneha.iyer@company.com',   '9876543213', '2020-05-20', 200000.00, 1, NULL, FALSE),
(5,  'Karan',   'Mehta',    'karan.mehta@gmail.com',    '9876543214', '2023-06-01', 85000.00,  2, NULL, TRUE),
(6,  'Divya',   'Pillai',   'divya.pillai@company.com', '9876543215', '2024-02-14', 75000.00,  2, 5,    TRUE),
(7,  'Nikhil',  'Gupta',    'nikhil.gupta@gmail.com',   NULL,         '2024-08-01', 65000.00,  2, 5,    TRUE),
(8,  'Pooja',   'Reddy',    'pooja.reddy@company.com',  '9876543217', '2022-11-01', 110000.00, 3, NULL, TRUE),
(9,  'Aditya',  'Kumar',    'aditya.kumar@gmail.com',   '9876543218', '2021-09-15', 125000.00, 3, 8,    TRUE),
(10, 'Meena',   'Krishnan', 'meena.k@company.com',      '9876543219', '2023-03-01', 70000.00,  4, NULL, TRUE),
(11, 'Suresh',  'Bhat',     'suresh.bhat@gmail.com',    NULL,         '2019-12-01', 55000.00,  4, 10,   FALSE),
(12, 'Lakshmi', 'Venkat',   'lakshmi.v@company.com',    '9876543221', '2022-04-01', 155000.00, 5, NULL, TRUE),
(13, 'Rohit',   'Joshi',    'rohit.joshi@gmail.com',    '9876543222', '2023-10-01', 120000.00, 5, 12,   TRUE),
(14, 'Anjali',  'Singh',    'anjali.singh@company.com', '9876543223', '2024-05-15', 90000.00,  5, 12,   TRUE),
(15, 'Vikram',  'Das',      'vikram.das@gmail.com',     NULL,         '2025-01-10', 45000.00,  NULL, NULL, TRUE);

-- ── Seed projects ─────────────────────────────────────────────────────────────
INSERT INTO projects (project_id, title, status, start_date, end_date) VALUES
(1, 'Payment Gateway',    'active',    '2024-01-01', NULL),
(2, 'Mobile App v2',      'active',    '2024-03-01', NULL),
(3, 'Data Pipeline',      'completed', '2023-01-01', '2023-12-31'),
(4, 'Brand Refresh',      'active',    '2024-06-01', NULL),
(5, 'Finance Automation', 'cancelled', '2023-06-01', '2023-09-01');

-- ── Seed employee_projects ────────────────────────────────────────────────────
INSERT INTO employee_projects (employee_id, project_id, role, joined_date) VALUES
(1,  1, 'Lead',   '2024-01-01'),
(2,  1, 'Member', '2024-01-15'),
(3,  1, 'Member', '2024-02-01'),
(1,  2, 'Member', '2024-03-01'),
(12, 2, 'Lead',   '2024-03-01'),
(13, 2, 'Member', '2024-03-15'),
(9,  3, 'Lead',   '2023-01-01'),
(8,  3, 'Member', '2023-01-01'),
(5,  4, 'Lead',   '2024-06-01'),
(6,  4, 'Member', '2024-06-15'),
(8,  5, 'Lead',   '2023-06-01'),
(9,  5, 'Member', '2023-06-01');
-- Note: dept 4 (HR) and employee 15 (Vikram) have NO projects → useful for anti-join tasks
```

**Dataset quick reference:**

| Table | Rows | Key facts |
|-------|------|-----------|
| departments | 5 | All have employees except none intentionally left out |
| employees | 15 | Vikram has NULL dept, Sneha+Suresh inactive, 6 managers |
| projects | 5 | 3 active, 1 completed, 1 cancelled |
| employee_projects | 12 | HR employees and Vikram have no project assignments |

---

## 🏋️ Exercise Tasks

### Task 1: INNER JOIN
1. List all employees with their department name (exclude employees without a department)
2. List all employees working on the 'Payment Gateway' project with their role
3. List employees with their manager's name (self-join) — show `employee_name | manager_name`

### Task 2: LEFT JOIN & Anti-Join
1. List ALL employees including those without a department — show `NULL` for department if missing
2. Find employees who are **not assigned to any project** (anti-join pattern)
3. Find departments that have **no employees** (reverse anti-join)
4. Find projects with **no employees assigned**

### Task 3: Multi-Table Joins
1. Show every active employee, their department name, and all projects they're assigned to. If an employee has no project, still show them with NULL project.
2. Show each project with the number of employees working on it

### Task 4: CROSS JOIN
Create a query that generates every possible pairing of departments (useful for planning cross-department collaboration). Exclude self-pairings (dept A with dept A).

### Task 5: Aggregation with JOINs (Common Interview Queries)
1. For each department, show: `department name | headcount | total salary | average salary`
2. Show the highest-paid employee per department (their name and salary)
3. List employees who earn more than the average salary of their own department
4. Show each manager's name and how many direct reports they have

---

**Once done, tell me and we move to Exercise 5 — Subqueries & CTEs!**
