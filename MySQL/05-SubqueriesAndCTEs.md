# Exercise 5 — Subqueries & Derived Tables

---

## 1. What Is a Subquery?

A **subquery** (inner query / nested query) is a `SELECT` statement embedded inside another SQL statement. It is evaluated first and its result is used by the outer query.

```sql
-- Simple example: find employees earning above the overall average
SELECT first_name, salary
FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);
-- Inner query runs first: AVG(salary) → e.g. 107,000
-- Outer query: salary > 107,000
```

---

## 2. Types of Subqueries by Return Shape

MySQL's optimizer behaves differently based on what the subquery returns:

| Type | Returns | Used With | Example |
|------|---------|-----------|---------|
| **Scalar** | Single value (1 row, 1 col) | `=`, `>`, `<`, etc. | `WHERE salary > (SELECT AVG(...))` |
| **Column** | Multiple rows, 1 column | `IN`, `NOT IN`, `ANY`, `ALL` | `WHERE dept_id IN (SELECT ...)` |
| **Row** | Single row, multiple columns | `=` row comparison | `WHERE (a,b) = (SELECT a,b ...)` |
| **Table** | Multiple rows + columns | `FROM`, `JOIN` | `FROM (SELECT ...) AS subq` |

---

## 3. Scalar Subquery

Returns **exactly one value** (one row, one column). If it returns more → MySQL throws an error.

```sql
-- Employees earning above average
SELECT first_name, salary
FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);

-- Each employee's salary vs the company average (scalar in SELECT list)
SELECT 
    first_name,
    salary,
    (SELECT AVG(salary) FROM employees) AS company_avg,
    salary - (SELECT AVG(salary) FROM employees) AS diff_from_avg
FROM employees
ORDER BY diff_from_avg DESC;

-- ⚠️ Scalar subquery in SELECT runs once per row — can be slow on large tables
-- Use a JOIN to a derived table for better performance (covered below)
```

---

## 4. Column Subquery — IN / NOT IN / ANY / ALL

Returns a **single column of values** to compare against.

### 4.1 IN

```sql
-- Employees who are assigned to at least one project
SELECT first_name, last_name
FROM employees
WHERE employee_id IN (
    SELECT DISTINCT employee_id FROM employee_projects
);

-- Employees in departments located in Bangalore
SELECT first_name
FROM employees
WHERE department_id IN (
    SELECT department_id FROM departments WHERE location = 'Bangalore'
);
```

### 4.2 NOT IN — And Why It Can Be Dangerous

```sql
-- ✅ Works fine when subquery has no NULLs
SELECT first_name
FROM employees
WHERE department_id NOT IN (
    SELECT department_id FROM departments WHERE location = 'Mumbai'
);

-- ❌ BROKEN: if Vikram's NULL department_id leaks into the subquery result
-- NOT IN with NULL → entire query returns 0 rows
-- Reason: NULL != 2 → UNKNOWN (not FALSE), so the NOT IN condition is UNKNOWN for all rows
SELECT first_name
FROM employees
WHERE department_id NOT IN (2, NULL);
-- → 0 rows! Because NULL poisons the entire comparison.

-- ✅ FIX: use NOT EXISTS (correctly handles NULLs)
SELECT e.first_name
FROM employees e
WHERE NOT EXISTS (
    SELECT 1 FROM departments d 
    WHERE d.department_id = e.department_id 
    AND d.location = 'Mumbai'
);
```

> **Interview Rule:** Prefer `NOT EXISTS` over `NOT IN` whenever the subquery might contain NULLs. `NOT IN` is only safe when you're certain the subquery returns no NULLs (e.g., NOT NULL column without null rows).

### 4.3 ANY / ALL

```sql
-- ANY: true if condition is true for AT LEAST ONE value in subquery
SELECT first_name, salary
FROM employees
WHERE salary > ANY (
    SELECT salary FROM employees WHERE department_id = 3
);
-- True if salary > the minimum salary in Finance dept
-- Equivalent to: salary > (SELECT MIN(salary) FROM employees WHERE department_id = 3)

-- ALL: true if condition is true for EVERY value in subquery
SELECT first_name, salary
FROM employees
WHERE salary > ALL (
    SELECT salary FROM employees WHERE department_id = 2
);
-- True only if salary > the maximum salary in Marketing dept
-- Equivalent to: salary > (SELECT MAX(salary) FROM employees WHERE department_id = 2)
```

---

## 5. Correlated Subquery

A **correlated subquery** references columns from the **outer query**. It re-executes for every row of the outer query — like a nested loop.

```sql
-- Employees earning above their own department's average (correlated!)
SELECT e.first_name, e.salary, e.department_id
FROM employees e
WHERE e.salary > (
    SELECT AVG(e2.salary)
    FROM employees e2
    WHERE e2.department_id = e.department_id  -- ← references outer query's row
);
-- For each employee row, the subquery recalculates AVG for that specific dept
-- Runs N times (N = number of employee rows) → can be slow
```

**Correlated subquery execution:**
```
Outer row: Arjun (dept=1, salary=180000)
    Inner: SELECT AVG(salary) WHERE dept=1 → 155,000
    Check: 180,000 > 155,000 → TRUE → include Arjun

Outer row: Rahul (dept=1, salary=95000)
    Inner: SELECT AVG(salary) WHERE dept=1 → 155,000
    Check: 95,000 > 155,000 → FALSE → exclude Rahul
```

> **Performance Insight:** Correlated subqueries are O(n × query_cost). For this specific query, a **derived table JOIN** is much faster because the aggregation runs once per department (not once per employee):
> ```sql
> -- ✅ Faster version with derived table
> SELECT e.first_name, e.salary
> FROM employees e
> JOIN (
>     SELECT department_id, AVG(salary) AS dept_avg
>     FROM employees
>     GROUP BY department_id
> ) dept ON e.department_id = dept.department_id
> WHERE e.salary > dept.dept_avg;
> ```

---

## 6. EXISTS / NOT EXISTS

`EXISTS` checks if a subquery returns **any rows at all**. It stops as soon as it finds the first match (**short-circuit evaluation**) — much faster than `IN` for large datasets.

```sql
-- Employees assigned to at least one project
SELECT e.first_name
FROM employees e
WHERE EXISTS (
    SELECT 1  -- ← "1" is a convention — the value doesn't matter, only row existence
    FROM employee_projects ep
    WHERE ep.employee_id = e.employee_id
);

-- Employees NOT assigned to any project
SELECT e.first_name
FROM employees e
WHERE NOT EXISTS (
    SELECT 1
    FROM employee_projects ep
    WHERE ep.employee_id = e.employee_id
);

-- Departments that have at least one active employee
SELECT d.name
FROM departments d
WHERE EXISTS (
    SELECT 1
    FROM employees e
    WHERE e.department_id = d.department_id
    AND e.is_active = TRUE
);
```

**EXISTS vs IN — Performance:**

| Scenario | Winner | Why |
|----------|--------|-----|
| Subquery returns few rows | `IN` | Builds a small lookup set |
| Subquery returns many rows | `EXISTS` | Short-circuits on first match |
| Subquery can return NULLs | `EXISTS` | `IN` fails with NULLs |
| Subquery is correlated | `EXISTS` | Built for correlated checks |

> **Interview Rule:** Use `EXISTS` when checking for the _existence_ of related records. Use `IN` for fixed value lists or small subqueries. Always use `NOT EXISTS` instead of `NOT IN`.

---

## 7. Derived Tables (`FROM` Subquery)

A subquery in the `FROM` clause — acts as a temporary, unnamed table. **Materialized once** (unlike correlated subqueries).

```sql
-- Department salary stats joined back to employees
SELECT e.first_name, e.salary, dept_stats.avg_sal, dept_stats.max_sal
FROM employees e
JOIN (
    SELECT 
        department_id,
        AVG(salary) AS avg_sal,
        MAX(salary) AS max_sal,
        COUNT(*)    AS headcount
    FROM employees
    WHERE is_active = TRUE
    GROUP BY department_id
) dept_stats ON e.department_id = dept_stats.department_id
ORDER BY e.salary DESC;

-- The inner SELECT runs ONCE and produces a virtual table
-- Then it's joined to employees — much more efficient than a correlated subquery
```

> **Interview Insight:** MySQL 8.0 introduced **derived table merging** (`derived_merge`). The optimizer often merges derved tables directly into the outer query instead of materializing them. Use `EXPLAIN` and look for `DERIVED` vs `DERIVED MERGED` in the output.

---

## 8. CTEs — WITH Clause (MySQL 8.0+)

A **Common Table Expression (CTE)** is a named, temporary result set defined before the main query with the `WITH` keyword. It makes complex queries much more readable.

### 8.1 Basic CTE

```sql
-- Same query as above, but with a CTE — much more readable
WITH dept_stats AS (
    SELECT 
        department_id,
        AVG(salary) AS avg_sal,
        MAX(salary) AS max_sal,
        COUNT(*)    AS headcount
    FROM employees
    WHERE is_active = TRUE
    GROUP BY department_id
)
SELECT e.first_name, e.salary, ds.avg_sal, ds.headcount
FROM employees e
JOIN dept_stats ds ON e.department_id = ds.department_id
WHERE e.salary > ds.avg_sal
ORDER BY e.salary DESC;
```

### 8.2 Multiple CTEs

```sql
-- Chain multiple CTEs — each can reference the ones before it
WITH 
active_emps AS (
    SELECT * FROM employees WHERE is_active = TRUE
),
dept_totals AS (
    SELECT department_id, SUM(salary) AS total_salary
    FROM active_emps
    GROUP BY department_id
),
high_budget_depts AS (
    SELECT department_id
    FROM departments
    WHERE budget > 3000000
)
SELECT d.name, dt.total_salary
FROM dept_totals dt
JOIN departments d ON dt.department_id = d.department_id
JOIN high_budget_depts hbd ON dt.department_id = hbd.department_id
ORDER BY dt.total_salary DESC;
```

### 8.3 CTE vs Derived Table vs Subquery — When to Use Which

| Feature | Subquery | Derived Table | CTE |
|---------|--------|----------------|-----|
| Named / reusable? | ❌ No | ❌ No | ✅ Yes |
| Readable on complex queries? | ❌ Poor | 🟡 OK | ✅ Best |
| Can reference itself (recursive)? | ❌ No | ❌ No | ✅ Yes |
| Performance | Variable | Same as CTE | Same as derived |
| Materialized? | Per execution | Usually not (merged) | Usually not (merged) |
| MySQL version | Any | Any | 8.0+ |

> **Use CTEs** when the logic is complex enough to benefit from a name, or when you need to **reuse the same subquery multiple times** in a query (subquery/derived table would repeat code and potentially run twice).

---

## 9. Subquery in UPDATE and DELETE

```sql
-- Give a 5% bonus to employees working on active projects
UPDATE employees
SET salary = salary * 1.05
WHERE employee_id IN (
    SELECT DISTINCT ep.employee_id
    FROM employee_projects ep
    JOIN projects p ON ep.project_id = p.project_id
    WHERE p.status = 'active'
);

-- ⚠️ MySQL limitation: you can't directly use the same table in a subquery for UPDATE/DELETE
-- ❌ This fails:
DELETE FROM employees WHERE employee_id IN (
    SELECT employee_id FROM employees WHERE is_active = FALSE
);
-- ERROR 1093: Can't specify target table 'employees' for update in FROM clause

-- ✅ Workaround: wrap in another subquery to create a temporary copy
DELETE FROM employees WHERE employee_id IN (
    SELECT emp_id FROM (
        SELECT employee_id AS emp_id FROM employees WHERE is_active = FALSE
    ) AS tmp
);
```

---

## 🗄️ Dataset Setup

Uses the same expanded dataset from Exercise 4 (with `projects` and `employee_projects`). Re-run if needed:

```sql
-- (Same script as Exercise 4 — see 04-JoinsDeepDive.md Dataset Setup section)
-- Quick re-seed if tables already exist:
USE company_db;
SELECT COUNT(*) FROM employees;   -- should be 15
SELECT COUNT(*) FROM projects;    -- should be 5
SELECT COUNT(*) FROM employee_projects;  -- should be 12
```

---

## 🏋️ Exercise Tasks

### Task 1: Scalar Subqueries
1. Find all employees earning above the company-wide average salary
2. Show each employee's salary AND the difference from their department's average (use scalar subquery in SELECT list)
3. Find the employee with the highest salary in the Finance department (use a scalar subquery, not ORDER BY LIMIT)

### Task 2: IN / NOT IN
1. Find employees who are working on at least one project — using `IN`
2. Find employees NOT assigned to any project — using `NOT IN` (compare results with Task 3.2)
3. Find employees in departments located in Bangalore

### Task 3: EXISTS / NOT EXISTS
1. Find employees who are assigned to at least one `active` project — using `EXISTS`
2. Find employees who have NO project assignments — using `NOT EXISTS`
3. Find departments that currently have no employees (use `NOT EXISTS`)

### Task 4: Correlated Subqueries
1. Find employees who earn more than the average salary of their own department (correlated subquery)
2. For each employee, show whether their salary is `Above Avg`, `At Avg`, or `Below Avg` relative to their department (use correlated scalar subquery with CASE WHEN)

### Task 5: Derived Tables & CTEs
1. Without a correlated subquery, rewrite Task 4.1 using a derived table JOIN (more efficient)
2. Using a CTE, find the top-earning employee in each department
3. Write a multi-CTE query that: (a) calculates per-department stats, (b) filters to high-budget departments (>3M), and (c) shows employees in those departments above the dept average

---

**Done? Tell me and we'll move to Exercise 6 — Set Operations!**
