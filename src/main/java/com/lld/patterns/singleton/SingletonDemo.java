package com.lld.patterns.singleton;

/**
 * Driver class that demonstrates the Singleton pattern.
 *
 * Running this class will show that:
 *  - The constructor is called only once.
 *  - Both references point to the exact same object (same hash code).
 */
public class SingletonDemo {

    public static void main(String[] args) {
        System.out.println("=== Singleton Pattern Demo ===\n");

        // First call — creates the instance
        DatabaseConnection conn1 = DatabaseConnection.getInstance();
        conn1.query("SELECT * FROM users");

        // Second call — reuses the existing instance
        DatabaseConnection conn2 = DatabaseConnection.getInstance();
        conn2.query("SELECT * FROM orders");

        // Proof: both references point to the same object
        System.out.println("\nconn1 hashCode: " + conn1.hashCode());
        System.out.println("conn2 hashCode: " + conn2.hashCode());
        System.out.println("Same instance?  " + (conn1 == conn2));  // true
    }
}
