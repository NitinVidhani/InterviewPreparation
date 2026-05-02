# MySQL — Exercise-Based Learning for Senior SWE Interviews

---

## Curriculum Overview

Each exercise below is a standalone learning module. Before you attempt each exercise, I will teach you **every concept in depth** that is needed for that exercise. The exercises are ordered from fundamentals → advanced, mirroring what senior SWE interviews expect.

---

## Phase 1: Foundations

### Exercise 1 — Database & Table Basics
**Concepts Covered:** CREATE DATABASE, USE, CREATE TABLE, data types (INT, VARCHAR, TEXT, DATE, DATETIME, DECIMAL, BOOLEAN, ENUM), PRIMARY KEY, AUTO_INCREMENT, DEFAULT, NOT NULL, DROP, ALTER TABLE, SHOW/DESCRIBE.

**Task:** Design and create a database `company_db` with an `employees` table and a `departments` table.

---

### Exercise 2 — CRUD Operations
**Concepts Covered:** INSERT (single & bulk), SELECT, UPDATE, DELETE, WHERE clause, operators (=, !=, <, >, BETWEEN, IN, LIKE, IS NULL), ORDER BY, LIMIT, OFFSET.

**Task:** Populate the tables from Exercise 1 with sample data. Write queries to insert, read, update, and delete employees.

---

### Exercise 3 — Filtering, Sorting & Aggregation
**Concepts Covered:** COUNT, SUM, AVG, MIN, MAX, GROUP BY, HAVING, DISTINCT, CASE WHEN, IFNULL/COALESCE, aliases.

**Task:** Answer business questions like "salary per department", "highest paid employee", "departments with >5 people".

---

## Phase 2: Joins & Relationships

### Exercise 4 — Joins Deep Dive
**Concepts Covered:** INNER JOIN, LEFT JOIN, RIGHT JOIN, FULL OUTER JOIN (MySQL workaround), CROSS JOIN, SELF JOIN, NATURAL JOIN, USING vs ON, join algorithms (Nested Loop, Hash Join, Sort-Merge), how MySQL optimizer picks join order.

**Task:** Query data across employees, departments, and a new `projects` table using every type of join.

---

### Exercise 5 — Subqueries & Derived Tables
**Concepts Covered:** Scalar subqueries, column subqueries, row subqueries, correlated subqueries, EXISTS vs IN, derived tables (FROM subquery), CTEs (WITH clause), subquery vs join performance.

**Task:** Solve complex queries like "employees earning above department average" and "departments with no projects".

---

### Exercise 6 — Set Operations
**Concepts Covered:** UNION, UNION ALL, INTERSECT (MySQL 8.0.31+), EXCEPT (MySQL 8.0.31+), when to use each, deduplication cost of UNION vs UNION ALL.

**Task:** Combine results from multiple queries (e.g., all managers UNION all project leads).

---

## Phase 3: Schema Design & Constraints

### Exercise 7 — Normalization & Relationships
**Concepts Covered:** 1NF, 2NF, 3NF, BCNF, denormalization trade-offs, one-to-one, one-to-many, many-to-many (junction tables), FOREIGN KEY, CASCADE (ON DELETE/UPDATE), referential integrity.

**Task:** Redesign the company schema to 3NF. Add `projects`, `employee_projects` (M:N), and `addresses` (1:1) tables.

---

### Exercise 8 — Indexes Deep Dive
**Concepts Covered:** B+ Tree internals, clustered index (InnoDB primary key), secondary indexes, composite indexes, covering indexes, prefix indexes, index selectivity/cardinality, EXPLAIN and query plans, index hints (USE INDEX, FORCE INDEX, IGNORE INDEX), when NOT to index.

**Task:** Add indexes to optimize slow queries. Use EXPLAIN to compare before/after.

---

## Phase 4: Advanced Queries

### Exercise 9 — Window Functions
**Concepts Covered:** ROW_NUMBER(), RANK(), DENSE_RANK(), NTILE(), LAG(), LEAD(), FIRST_VALUE(), LAST_VALUE(), SUM/AVG/COUNT OVER(), PARTITION BY, ORDER BY within OVER, frame specification (ROWS BETWEEN / RANGE BETWEEN).

**Task:** Compute running totals, rank employees by salary within department, compare each row with previous/next.

---

### Exercise 10 — Recursive CTEs & Hierarchical Data
**Concepts Covered:** WITH RECURSIVE, anchor member, recursive member, termination condition, adjacency list model, nested set model, closure table, org chart traversal.

**Task:** Build an org hierarchy (employee → manager). Query all reports under a given manager at any depth.

---

### Exercise 11 — JSON in MySQL
**Concepts Covered:** JSON data type, JSON_OBJECT(), JSON_ARRAY(), JSON_EXTRACT() / -> / ->> operators, JSON_SET(), JSON_REMOVE(), JSON_CONTAINS(), JSON_TABLE() (convert JSON to rows), indexing JSON (generated columns + index).

**Task:** Store flexible metadata as JSON and write queries that filter/aggregate over JSON fields.

---

## Phase 5: Transactions & Concurrency

### Exercise 12 — Transactions & ACID
**Concepts Covered:** ACID properties (deep dive into each), START TRANSACTION, COMMIT, ROLLBACK, SAVEPOINT, autocommit, transaction log (redo log, undo log), write-ahead logging (WAL), crash recovery.

**Task:** Implement a money transfer between two accounts with proper transaction handling.

---

### Exercise 13 — Isolation Levels & Locking
**Concepts Covered:** READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ (InnoDB default), SERIALIZABLE, dirty reads, non-repeatable reads, phantom reads, MVCC (Multi-Version Concurrency Control), gap locks, next-key locks, record locks, deadlocks (detection & prevention), SELECT ... FOR UPDATE, SELECT ... LOCK IN SHARE MODE.

**Task:** Demonstrate each isolation level's behavior. Simulate and debug a deadlock.

---

## Phase 6: Performance & Internals

### Exercise 14 — Query Optimization
**Concepts Covered:** EXPLAIN / EXPLAIN ANALYZE (MySQL 8.0.18+), query execution pipeline (parser → optimizer → executor), cost-based optimizer, index merge, filesort vs index sort, Using temporary, Using filesort, derived_merge, subquery materialization, query rewriting techniques, slow query log.

**Task:** Take 5 intentionally slow queries and optimize them to < 10ms using EXPLAIN-driven analysis.

---

### Exercise 15 — InnoDB Architecture
**Concepts Covered:** InnoDB page structure (16KB), buffer pool (LRU, midpoint insertion), change buffer, adaptive hash index, doublewrite buffer, redo log (WAL), undo log (MVCC), tablespace files (.ibd), row format (COMPACT, DYNAMIC, COMPRESSED), page splits, fragmentation.

**Task:** Monitor and tune buffer pool hit ratio. Analyze table fragmentation and optimize.

---

### Exercise 16 — Partitioning
**Concepts Covered:** RANGE partitioning, LIST partitioning, HASH partitioning, KEY partitioning, sub-partitioning, partition pruning, when to partition (and when NOT to), partitioning vs sharding.

**Task:** Partition a large `orders` table by date range. Compare query performance with/without partitioning.

---

## Phase 7: Real-World Patterns

### Exercise 17 — Stored Procedures, Functions & Triggers
**Concepts Covered:** CREATE PROCEDURE, IN/OUT/INOUT parameters, CREATE FUNCTION, DETERMINISTIC, DELIMITER, cursors, handlers (DECLARE HANDLER), triggers (BEFORE/AFTER INSERT/UPDATE/DELETE), event scheduler, pros/cons of stored procedures in modern architectures.

**Task:** Write a procedure for monthly payroll processing with error handling. Create an audit trigger.

---

### Exercise 18 — Views & Materialized Views
**Concepts Covered:** CREATE VIEW, updatable views, WITH CHECK OPTION, view merge vs temptable algorithm, MySQL doesn't have native materialized views — how to simulate with tables + triggers/events.

**Task:** Create views for common reporting queries. Simulate a materialized view for a dashboard summary.

---

### Exercise 19 — Replication & High Availability
**Concepts Covered:** Binary log (binlog) formats (STATEMENT, ROW, MIXED), source-replica replication, GTID (Global Transaction ID), semi-synchronous replication, group replication, InnoDB Cluster, read/write splitting, replication lag monitoring.

**Task:** (Theory + config exercise) Design a replication topology for a read-heavy application.

---

### Exercise 20 — Sharding & Scaling Patterns
**Concepts Covered:** Vertical vs horizontal scaling, application-level sharding, shard key selection, cross-shard queries/joins, rebalancing, Vitess/ProxySQL, connection pooling, read replicas, caching layer (Redis + MySQL).

**Task:** (Design exercise) Design a sharding strategy for a multi-tenant SaaS application with 100M+ rows.

---

## How to Use This Curriculum

1. **Tell me which exercise you want to start with** (recommended: Exercise 1).
2. I will create a detailed concept document covering everything for that exercise.
3. You solve the exercise.
4. I review and we discuss edge cases & interview angles.

**Let's begin! Which exercise do you want to start?**
