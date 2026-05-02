package com.lld.patterns.singleton;

/**
 * Singleton Pattern - Database Connection Example
 *
 * This class simulates a database connection pool using the Singleton pattern.
 * We use the Double-Checked Locking approach to ensure thread safety while
 * keeping synchronization overhead low.
 *
 * KEY CONCEPTS:
 * 1. Private constructor — prevents external instantiation.
 * 2. A private static volatile instance — holds the one and only instance.
 *    'volatile' ensures visibility of changes across threads and prevents
 *    instruction reordering during object creation.
 * 3. A public static getInstance() method — the global access point.
 *    Uses double-checked locking: checks without a lock first (fast path),
 *    then synchronizes and checks again before creating.
 */
public class DatabaseConnection {

    // -----------------------------------------------------------------------
    // 'volatile' is CRITICAL here. Without it, the JVM may reorder
    // instructions such that another thread sees a partially constructed
    // object from the reference before the constructor finishes.
    // -----------------------------------------------------------------------
    private static volatile DatabaseConnection instance;

    private final String connectionUrl;

    // -----------------------------------------------------------------------
    // Private constructor — the cornerstone of the Singleton pattern.
    // No external code can call 'new DatabaseConnection()'.
    // -----------------------------------------------------------------------
    private DatabaseConnection(String url) {
        this.connectionUrl = url;
        System.out.println("[Singleton] DatabaseConnection created with URL: " + url);
    }

    // -----------------------------------------------------------------------
    // Double-Checked Locking (DCL) getInstance()
    //
    // WHY two null checks?
    //  • First check (outside sync block):  avoids acquiring a lock every time.
    //  • Second check (inside sync block):  prevents a race condition where
    //    two threads both pass the first check before one acquires the lock.
    // -----------------------------------------------------------------------
    public static DatabaseConnection getInstance(String url) {
        if (instance == null) {                     // 1st check (no lock)
            synchronized (DatabaseConnection.class) {
                if (instance == null) {             // 2nd check (with lock)
                    instance = new DatabaseConnection(url);
                }
            }
        }
        return instance;
    }

    /** Convenience overload — uses a default URL. */
    public static DatabaseConnection getInstance() {
        return getInstance("jdbc:mysql://localhost:3306/mydb");
    }

    // -----------------------------------------------------------------------
    // Business methods
    // -----------------------------------------------------------------------
    public void query(String sql) {
        System.out.println("[Singleton] Executing query on " + connectionUrl + ": " + sql);
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }
}
