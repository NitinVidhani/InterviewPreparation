package com.lld.lrucache;

import java.util.HashMap;
import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════════════
 * LRU CACHE — Core Implementation
 * ══════════════════════════════════════════════════════════════════════
 *
 * A generic, thread-safe LRU Cache that combines:
 * 1. HashMap<K, Node<K,V>> → O(1) key lookup
 * 2. DoublyLinkedList → O(1) ordering (MRU at head, LRU at tail)
 *
 * BOTH get() and put() run in O(1) time complexity.
 *
 * ──────────────────────────────────────────────────────────────────────
 * DESIGN PATTERN: Strategy (EvictionPolicy)
 * ──────────────────────────────────────────────────────────────────────
 * The cache delegates eviction decisions to an EvictionPolicy strategy.
 * Currently wired with LRUEvictionPolicy, but could be swapped for
 * LFU, FIFO, etc. without any changes to this class.
 *
 * ──────────────────────────────────────────────────────────────────────
 * THREAD SAFETY
 * ──────────────────────────────────────────────────────────────────────
 * All public methods are synchronized to ensure thread safety.
 * In a production system, you'd use finer-grained locking (e.g.,
 * ReentrantReadWriteLock or Striped locks) for better concurrency.
 * ──────────────────────────────────────────────────────────────────────
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LRUCache<K, V> {

    private final int capacity;

    /**
     * Primary storage: maps keys to DLL nodes.
     *
     * WHY store Nodes (not just values)?
     * Because on get(), we need to move the node to the head of the DLL.
     * If we only stored the value, we'd need to search the DLL for the
     * node → O(n). By storing the Node, we have a direct reference → O(1).
     */
    private final Map<K, Node<K, V>> map;

    /**
     * Ordering structure: maintains access order.
     *
     * HEAD ↔ [MRU] ↔ [Recent] ↔ ... ↔ [LRU] ↔ TAIL
     *
     * - get() → moves accessed node to HEAD
     * - put() → new node added at HEAD; if full, TAIL node evicted
     */
    private final DoublyLinkedList<K, V> dll;

    /**
     * The eviction strategy — currently LRU, but pluggable via Strategy pattern.
     */
    private final EvictionPolicy<K> evictionPolicy;

    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.dll = new DoublyLinkedList<>();
        this.evictionPolicy = new LRUEvictionPolicy<>();
    }

    /**
     * GET a value from the cache.
     *
     * ──────────────────────────────────────────────────────────────
     * ALGORITHM:
     * 1. Look up key in HashMap → O(1)
     * 2. Key not found? → return null (cache miss)
     * 3. Key found? →
     * a. Move the node to the HEAD of the DLL (mark as MRU) → O(1)
     * b. Notify the eviction policy → O(1)
     * c. Return the value
     *
     * TOTAL: O(1)
     * ──────────────────────────────────────────────────────────────
     *
     * @param key the key to look up
     * @return the value, or null if not found (cache miss)
     */
    public synchronized V get(K key) {
        Node<K, V> node = map.get(key);

        if (node == null) {
            // CACHE MISS — key not in cache
            System.out.printf("  [Cache MISS] key=%s%n", key);
            return null;
        }

        // CACHE HIT — move to head (most recently used)
        dll.moveToHead(node);

        // Notify eviction policy that this key was accessed
        evictionPolicy.keyAccessed(key);

        System.out.printf("  [Cache HIT]  key=%s → value=%s%n", key, node.value);
        return node.value;
    }

    /**
     * PUT a key-value pair into the cache.
     *
     * ──────────────────────────────────────────────────────────────
     * ALGORITHM:
     * 1. Key already exists?
     * YES → Update value, move to HEAD (mark as MRU) → O(1)
     * NO → Continue to step 2
     *
     * 2. Is cache at capacity?
     * YES → Evict LRU item:
     * a. Remove TAIL node from DLL → O(1)
     * b. Remove its key from HashMap → O(1)
     * c. Notify eviction policy → O(1)
     * NO → Continue
     *
     * 3. Create new node, add to HEAD of DLL, add to HashMap → O(1)
     *
     * TOTAL: O(1)
     * ──────────────────────────────────────────────────────────────
     *
     * @param key   the key
     * @param value the value to store
     */
    public synchronized void put(K key, V value) {
        // CASE 1: Key already exists → UPDATE
        if (map.containsKey(key)) {
            Node<K, V> existingNode = map.get(key);
            existingNode.value = value; // update value
            dll.moveToHead(existingNode); // mark as most recently used
            evictionPolicy.keyAccessed(key); // notify eviction policy
            System.out.printf("  [Cache UPDATE] key=%s → value=%s%n", key, value);
            return;
        }

        // CASE 2: Cache is full → EVICT LRU item
        if (map.size() >= capacity) {
            // Remove the TAIL node (LRU) from the DLL
            Node<K, V> lruNode = dll.removeLast();
            if (lruNode != null) {
                // Remove from HashMap using the node's key
                // (This is WHY we store the key in the node!)
                map.remove(lruNode.key);
                System.out.printf("  [Cache EVICT] key=%s (least recently used)%n", lruNode.key);
            }
            // Also evict from the eviction policy's tracking
            evictionPolicy.evict();
        }

        // CASE 3: Insert new entry
        Node<K, V> newNode = new Node<>(key, value);
        dll.addFirst(newNode); // add to HEAD (most recent)
        map.put(key, newNode); // add to HashMap
        evictionPolicy.keyAccessed(key); // notify eviction policy
        System.out.printf("  [Cache PUT]   key=%s → value=%s%n", key, value);
    }

    /**
     * Delete a key from the cache.
     *
     * @return the removed value, or null if not found
     */
    public synchronized V delete(K key) {
        Node<K, V> node = map.remove(key);
        if (node == null)
            return null;

        dll.remove(node);
        System.out.printf("  [Cache DELETE] key=%s%n", key);
        return node.value;
    }

    // --- Utility methods ---

    public synchronized int size() {
        return map.size();
    }

    public int capacity() {
        return capacity;
    }

    /** Display the current cache state from MRU to LRU. */
    public synchronized void displayState() {
        System.out.printf("  [Cache State] size=%d/%d | %s%n",
                map.size(), capacity, dll.toStringFromHead());
    }
}
