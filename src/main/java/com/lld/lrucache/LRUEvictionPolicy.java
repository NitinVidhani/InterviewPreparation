package com.lld.lrucache;

import java.util.HashMap;
import java.util.Map;

/**
 * ──────────────────────────────────────────────────────────────────────
 * DESIGN PATTERN: Strategy (Concrete Implementation)
 * ──────────────────────────────────────────────────────────────────────
 * LRUEvictionPolicy implements the LRU eviction algorithm using a
 * HashMap + Doubly Linked List, exactly as described in the requirements.
 *
 * This class is a CONCRETE STRATEGY. The Cache class doesn't know or
 * care about the DLL or any LRU-specific logic — it just calls
 * keyAccessed() and evict().
 *
 * If you needed LFU, you'd create LFUEvictionPolicy with a frequency
 * map + min-heap — completely different internals, same interface.
 * ──────────────────────────────────────────────────────────────────────
 *
 * Internal Structure:
 * map: { key → Node } for O(1) lookup of any node
 * dll: HEAD ↔ [MRU] ↔ ... ↔ [LRU] ↔ TAIL for O(1) ordering
 *
 * @param <K> key type
 */
public class LRUEvictionPolicy<K> implements EvictionPolicy<K> {

    // Maps keys to their DLL nodes — gives O(1) access to any node
    private final Map<K, Node<K, ?>> nodeMap;

    // Doubly linked list maintaining access order (MRU at head, LRU at tail)
    private final DoublyLinkedList<K, Object> dll;

    public LRUEvictionPolicy() {
        this.nodeMap = new HashMap<>();
        this.dll = new DoublyLinkedList<>();
    }

    /**
     * Called on every access (get/put).
     *
     * ──────────────────────────────────────────────────────────────
     * FLOW:
     * Key already tracked? → Move its node to the HEAD (most recent)
     * New key? → Create a node, add it to the HEAD
     *
     * Time complexity: O(1) for both cases
     * - HashMap get/put: O(1)
     * - DLL moveToHead: O(1)
     * - DLL addFirst: O(1)
     * ──────────────────────────────────────────────────────────────
     */
    @Override
    @SuppressWarnings("unchecked")
    public void keyAccessed(K key) {
        if (nodeMap.containsKey(key)) {
            // Key exists → move to head (mark as most recently used)
            Node<K, Object> node = (Node<K, Object>) nodeMap.get(key);
            dll.moveToHead(node);
        } else {
            // New key → create node and add to head
            Node<K, Object> newNode = new Node<>(key, null);
            dll.addFirst(newNode);
            nodeMap.put(key, newNode);
        }
    }

    /**
     * Evict the least recently used key.
     *
     * ──────────────────────────────────────────────────────────────
     * FLOW:
     * 1. Remove the TAIL node from the DLL (that's the LRU item)
     * 2. Remove its key from the nodeMap
     * 3. Return the key so the caller (Cache) can also remove it
     * from its own storage map
     *
     * Time complexity: O(1)
     * - DLL removeLast: O(1)
     * - HashMap remove: O(1)
     * ──────────────────────────────────────────────────────────────
     */
    @Override
    @SuppressWarnings("unchecked")
    public K evict() {
        Node<K, Object> lruNode = dll.removeLast();
        if (lruNode == null) {
            return null;
        }
        nodeMap.remove(lruNode.key);
        return lruNode.key;
    }
}
