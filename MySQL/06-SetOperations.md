# Exercise 6 — Set Operations

---

## 1. What Are Set Operations?

Set operations combine the results of **two or more SELECT statements** into a single result set. They operate on **result sets as a whole** — not on individual rows with a join condition.

```
Query A result:     Query B result:     UNION result:
┌───────────┐       ┌───────────┐       ┌───────────┐
│ Alice     │       │ Bob       │       │ Alice     │
│ Bob       │  ∪    │ Charlie   │   =   │ Bob       │
│ Charlie   │       │ Dave      │       │ Charlie   │
└───────────┘       └───────────┘       │ Dave      │
                                        └───────────┘
```

### The Golden Rules of Set Operations

1. **Same number of columns** in both SELECT statements
2. **Compatible data types** in each column position (MySQL does implicit casting)
3. **Column names** come from the **first** SELECT statement
4. Set operations apply to the **full row** when deduplicating

---

## 2. UNION — Combine & Deduplicate

`UNION` merges results from two queries and **removes duplicate rows**.

```sql
-- All unique locations: from departments OR from a hypothetical offices table
SELECT location FROM departments
UNION
SELECT city FROM offices;
-- Result: unique set of all locations

-- Real example with our dataset:
-- Get a unified list of all people who are either managers OR project leads
SELECT e.first_name, e.last_name, 'Manager' AS role
FROM employees e
WHERE e.manager_id IS NULL AND e.employee_id IS NOT NULL

UNION

SELECT e.first_name, e.last_name, 'Project Lead' AS role
FROM employees e
JOIN employee_projects ep ON e.employee_id = ep.employee_id
WHERE ep.role = 'Lead';
```

### How Deduplication Works in UNION

```sql
-- If the same person is both a manager AND a project lead,
-- UNION keeps only ONE row because all columns must match for deduplication.
-- In the query above: role differs ('Manager' vs 'Project Lead') → both rows kept
-- If you had identical rows, only one would appear.
```

> **Performance Cost:** `UNION` sorts or hashes the **entire combined result set** to find and remove duplicates. On large datasets, this is expensive. Always prefer `UNION ALL` if you know there are no duplicates or don't care about them.

---

## 3. UNION ALL — Combine Without Deduplication

`UNION ALL` merges results and **keeps every row including duplicates**. Always faster than `UNION`.

```sql
-- Combine active and inactive employees into one list (may have overlapping columns)
SELECT first_name, last_name, 'Active' AS status
FROM employees WHERE is_active = TRUE

UNION ALL

SELECT first_name, last_name, 'Inactive' AS status
FROM employees WHERE is_active = FALSE;
-- Result: ALL rows from both queries, no deduplication
-- 13 active + 2 inactive = 15 rows (no duplicates possible since status differs)
```

**UNION vs UNION ALL — Decision Rule:**

| Use | When |
|-----|------|
| `UNION ALL` | You know results don't overlap **OR** duplicates are acceptable — always faster |
| `UNION` | You need deduplication and the queries can return the same rows |

> **Interview Insight:** In production, `UNION ALL` is almost always preferred over `UNION`. If you're combining two queries where the `WHERE` clauses are mutually exclusive (e.g., `is_active = TRUE` vs `is_active = FALSE`), use `UNION ALL` — there can't be duplicates, and you avoid the sort/hash overhead.

---

## 4. INTERSECT — Rows in BOTH Results (MySQL 8.0.31+)

`INTERSECT` returns only rows that appear in **both** query results.

```sql
-- Employees who are both: in Engineering AND assigned to a project
SELECT employee_id FROM employees WHERE department_id = 1
INTERSECT
SELECT employee_id FROM employee_projects;
-- Returns: {1, 2, 3} — Engineering employees who have project assignments
-- Excludes Sneha (id=4) who is in Engineering but has no project assignment
```

**Before MySQL 8.0.31 — simulate INTERSECT with JOIN or EXISTS:**

```sql
-- INTERSECT simulation using JOIN (equivalent)
SELECT DISTINCT e.employee_id
FROM employees e
JOIN employee_projects ep ON e.employee_id = ep.employee_id
WHERE e.department_id = 1;

-- INTERSECT simulation using EXISTS
SELECT employee_id FROM employees
WHERE department_id = 1
AND EXISTS (
    SELECT 1 FROM employee_projects ep 
    WHERE ep.employee_id = employees.employee_id
);
```

---

## 5. EXCEPT / MINUS — Rows in First but NOT in Second (MySQL 8.0.31+)

`EXCEPT` (also called `MINUS` in Oracle/older syntax) returns rows from the **first query that do not appear in the second query**.

```sql
-- Employees in Engineering who are NOT assigned to any project
SELECT employee_id FROM employees WHERE department_id = 1
EXCEPT
SELECT employee_id FROM employee_projects;
-- Returns: {4} — Sneha (inactive, in Engineering but no project)
```

**Before MySQL 8.0.31 — simulate EXCEPT:**

```sql
-- Simulation using LEFT JOIN anti-join pattern
SELECT e.employee_id
FROM employees e
LEFT JOIN employee_projects ep ON e.employee_id = ep.employee_id
WHERE e.department_id = 1
  AND ep.employee_id IS NULL;

-- Simulation using NOT IN
SELECT employee_id FROM employees WHERE department_id = 1
AND employee_id NOT IN (SELECT employee_id FROM employee_projects);
-- ⚠️ Danger: if employee_projects has NULL employee_id values, NOT IN returns 0 rows

-- Simulation using NOT EXISTS (safest)
SELECT employee_id FROM employees e
WHERE e.department_id = 1
AND NOT EXISTS (
    SELECT 1 FROM employee_projects ep WHERE ep.employee_id = e.employee_id
);
```

---

## 6. Operator Precedence

When chaining multiple set operations, `INTERSECT` has **higher precedence** than `UNION` and `EXCEPT`:

```sql
-- This:
A UNION B INTERSECT C
-- Is evaluated as:
A UNION (B INTERSECT C)   -- NOT (A UNION B) INTERSECT C

-- Use parentheses to make intent explicit:
(A UNION B) INTERSECT C
```

---

## 7. ORDER BY and LIMIT with Set Operations

`ORDER BY` and `LIMIT` apply to the **entire combined result**, not individual queries:

```sql
-- ❌ Wrong: can't ORDER BY inside individual queries in a UNION
SELECT first_name, salary FROM employees WHERE department_id = 1 ORDER BY salary   -- ERROR
UNION ALL
SELECT first_name, salary FROM employees WHERE department_id = 2;

-- ✅ Correct: ORDER BY goes at the very end
SELECT first_name, salary FROM employees WHERE department_id = 1
UNION ALL
SELECT first_name, salary FROM employees WHERE department_id = 2
ORDER BY salary DESC
LIMIT 5;
-- Returns the top 5 salaries across both departments combined
```

---

## 8. Real-World Use Cases

### 8.1 Audit / Change Report

```sql
-- Show all salary changes: both who got raises AND who got cuts
SELECT first_name, 'Raised' AS change_type, salary
FROM employees WHERE salary > 100000
UNION ALL
SELECT first_name, 'Below Threshold' AS change_type, salary
FROM employees WHERE salary <= 100000
ORDER BY change_type, salary DESC;
```

### 8.2 Full Coverage Report (Simulated FULL OUTER JOIN)

```sql
-- All employees + all departments, matched where possible (Full Outer Join pattern)
SELECT e.first_name, d.name AS dept_name
FROM employees e LEFT JOIN departments d ON e.department_id = d.department_id

UNION

SELECT e.first_name, d.name AS dept_name
FROM employees e RIGHT JOIN departments d ON e.department_id = d.department_id;
```

### 8.3 Multi-Source Data Consolidation

```sql
-- Unified contact list: all employees + all external clients
SELECT first_name, last_name, email, 'Employee' AS source FROM employees
UNION ALL
SELECT first_name, last_name, email, 'Client' AS source FROM clients;
```

### 8.4 Finding Items Present in One Period but Not Another

```sql
-- Projects that were active in 2023 but NOT in 2024
SELECT project_id FROM projects WHERE YEAR(start_date) = 2023
EXCEPT
SELECT project_id FROM projects WHERE YEAR(start_date) = 2024;
```

---

## 9. Set Operations vs JOIN vs Subquery — When to Use Which

| Need | Best Tool |
|------|-----------|
| Combine rows from two queries (same columns) | `UNION ALL` / `UNION` |
| Rows matching across two tables | `INNER JOIN` or `INTERSECT` |
| Rows in one table but not another | `LEFT JOIN ... IS NULL` or `NOT EXISTS` or `EXCEPT` |
| Rows from one table filtered by another | Subquery with `IN` / `EXISTS` |

---

## 🗄️ Dataset Setup

Uses the same expanded dataset from Exercise 4. Run this to verify or re-seed:

```sql
USE company_db;
-- Quick check (should return 15, 5, 12)
SELECT
    (SELECT COUNT(*) FROM employees)        AS emp_count,
    (SELECT COUNT(*) FROM projects)         AS proj_count,
    (SELECT COUNT(*) FROM employee_projects) AS ep_count;

-- If not matching, re-run the full setup from 04-JoinsDeepDive.md
```

---

## 🏋️ Exercise Tasks

### Task 1: UNION
1. Get a unified list of all unique locations — from `departments.location` combined with a literal list of custom offices `('London', 'Singapore', 'Bangalore')` — deduplicated
2. Combine active and inactive employees into one list with a computed `status` column (`'Active'` / `'Inactive'`), ordered by last name

### Task 2: UNION ALL vs UNION
1. Write a query using `UNION ALL` that shows every employee with their project count, unioning "employees with projects" and "employees without projects" (0 count). Verify the total row count equals 15.
2. Run the same query with `UNION` instead. Do you get different results? Why or why not?

### Task 3: Simulating INTERSECT (Before 8.0.31)
1. Find employees who are both: **in the Product department** AND **assigned to at least one project** — using a JOIN (simulating INTERSECT)
2. Write the same query using `EXISTS`
3. If your MySQL version supports it (8.0.31+), write it using native `INTERSECT`

### Task 4: Simulating EXCEPT (Before 8.0.31)
1. Find employees who are in **Engineering or Product** but have **no project assignments at all** — using `NOT EXISTS`
2. Write the same query using a `LEFT JOIN ... IS NULL` anti-join
3. If your MySQL version supports it, write it using native `EXCEPT`

### Task 5: Combined Set Operations
1. Build a full "people directory" report that combines:
   - Active employees (source = 'Employee')
   - A hardcoded list of 3 external consultants (source = 'Consultant'): `('John', 'Smith'), ('Aisha', 'Khan'), ('Carlos', 'Ruiz')` with fake emails
   - Ordered by last_name
2. Find the `employee_id` values that appear in **both** the top-5-salaried employees AND the employees on active projects

---

**All done? I'll create the Phase 2 solutions or jump straight into Phase 3 (Normalization & Indexes) — your call!**
