package com.lld.lrucache;

/**
 * ──────────────────────────────────────────────────────────────────────
 * DESIGN PATTERN: Strategy
 * ──────────────────────────────────────────────────────────────────────
 * EvictionPolicy defines the STRATEGY interface for cache eviction.
 *
 * WHY Strategy?
 * Different caches may need different eviction policies:
 * - LRU (Least Recently Used) — evict oldest by access time
 * - LFU (Least Frequently Used) — evict least accessed
 * - FIFO (First In, First Out) — evict oldest by insertion time
 *
 * The Strategy pattern lets us:
 * 1. Encapsulate each eviction algorithm in its own class.
 * 2. Swap policies at design time (or even runtime).
 * 3. Add new eviction policies without modifying the Cache class.
 *
 * The Cache holds a reference to an EvictionPolicy and delegates
 * eviction decisions to it.
 *
 * INTERVIEW TIP: If asked "How would you support LFU?", answer:
 * "Create LFUEvictionPolicy implementing this same interface.
 * The Cache class doesn't change at all — only the policy does."
 * ──────────────────────────────────────────────────────────────────────
 *
 * @param <K> key type
 */
public interface EvictionPolicy<K> {

    /**
     * Called when a key is accessed (get or put).
     * The policy should mark it as "recently used" in its tracking structure.
     */
    void keyAccessed(K key);

    /**
     * Called when an eviction is needed.
     * The policy decides WHICH key to evict based on its algorithm.
     *
     * @return the key that should be evicted
     */
    K evict();
}
