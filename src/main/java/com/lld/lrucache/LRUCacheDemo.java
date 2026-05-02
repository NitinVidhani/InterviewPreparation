package com.lld.lrucache;

/**
 * ══════════════════════════════════════════════════════════════════════
 * LRU CACHE — Full Demo
 * ══════════════════════════════════════════════════════════════════════
 *
 * This demo walks through every scenario an interviewer would ask about:
 *
 * 1. Basic put + get
 * 2. Eviction when capacity is reached
 * 3. Updating an existing key (moves it to MRU)
 * 4. Accessing a key (moves it to MRU, changes eviction order)
 * 5. Multiple evictions in sequence
 * 6. Edge cases (null lookups, delete)
 *
 * Each step prints the cache state so you can trace the algorithm.
 *
 * ══════════════════════════════════════════════════════════════════════
 */
public class LRUCacheDemo {

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║        LRU CACHE — Low Level Design Demo     ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        // Create a cache with capacity 3
        LRUCache<String, String> cache = new LRUCache<>(3);

        // ──────────────────────────────────────────────────────────────
        // STEP 1: Basic put operations
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- STEP 1: Basic PUT (fill the cache) ---");
        cache.put("user:1", "Alice");
        cache.put("user:2", "Bob");
        cache.put("user:3", "Charlie");
        cache.displayState();
        // State: HEAD ↔ (user:3=Charlie) ↔ (user:2=Bob) ↔ (user:1=Alice) ↔ TAIL
        // Most Recent Least Recent (LRU)

        // ──────────────────────────────────────────────────────────────
        // STEP 2: Access a key → moves it to MRU position
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 2: GET 'user:1' (moves to MRU) ---");
        cache.get("user:1");
        cache.displayState();
        // Before: HEAD ↔ user:3 ↔ user:2 ↔ user:1 ↔ TAIL
        // After: HEAD ↔ user:1 ↔ user:3 ↔ user:2 ↔ TAIL
        // ↑ now user:2 is LRU

        // ──────────────────────────────────────────────────────────────
        // STEP 3: EVICTION — cache is full, adding new item evicts LRU
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 3: PUT 'user:4' (triggers eviction of LRU 'user:2') ---");
        cache.put("user:4", "Diana");
        cache.displayState();
        // user:2 (Bob) was LRU → EVICTED
        // State: HEAD ↔ user:4 ↔ user:1 ↔ user:3 ↔ TAIL

        // ──────────────────────────────────────────────────────────────
        // STEP 4: Verify evicted key returns null
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 4: GET evicted key 'user:2' → should be MISS ---");
        String result = cache.get("user:2");
        System.out.println("  Result: " + (result == null ? "null (evicted as expected!)" : result));

        // ──────────────────────────────────────────────────────────────
        // STEP 5: Update existing key — value changes, moves to MRU
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 5: UPDATE 'user:3' with new value ---");
        cache.put("user:3", "Charlie Updated");
        cache.displayState();
        // user:3 was near tail, now it's at HEAD (most recent)

        // ──────────────────────────────────────────────────────────────
        // STEP 6: Multiple evictions
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 6: Add 2 more items (triggers 2 evictions) ---");
        cache.put("user:5", "Eve");
        cache.displayState();
        cache.put("user:6", "Frank");
        cache.displayState();
        // After both: only user:3, user:5, user:6 should remain

        // ──────────────────────────────────────────────────────────────
        // STEP 7: Delete a specific key
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 7: DELETE 'user:5' ---");
        cache.delete("user:5");
        cache.displayState();

        // ──────────────────────────────────────────────────────────────
        // STEP 8: Integer key-value example (Generics)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 8: Integer Cache (Generics) ---");
        LRUCache<Integer, Integer> intCache = new LRUCache<>(2);
        intCache.put(1, 100);
        intCache.put(2, 200);
        intCache.get(1); // Access key 1 → moves to MRU
        intCache.put(3, 300); // Evicts key 2 (LRU)
        intCache.displayState();
        intCache.get(2); // Should be MISS

        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║            Demo Complete!                    ║");
        System.out.println("║                                              ║");
        System.out.println("║  Key concepts demonstrated:                  ║");
        System.out.println("║  ✓ HashMap + DLL → O(1) get() and put()     ║");
        System.out.println("║  ✓ Sentinel nodes → no null edge cases      ║");
        System.out.println("║  ✓ Strategy pattern → pluggable eviction    ║");
        System.out.println("║  ✓ Generics → type-safe, reusable cache     ║");
        System.out.println("║  ✓ Thread safety → synchronized methods     ║");
        System.out.println("╚═══════════════════════════════════════════════╝");
    }
}
