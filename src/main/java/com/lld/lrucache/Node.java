package com.lld.lrucache;

/**
 * Node in a Doubly Linked List — the core building block of LRU Cache.
 *
 * ──────────────────────────────────────────────────────────────────────
 * WHY store BOTH key AND value in the node?
 * ──────────────────────────────────────────────────────────────────────
 * When we evict the tail node (LRU item), we need to ALSO remove it
 * from the HashMap. To do that, we need the KEY. If the node only stored
 * the value, we'd have no way to find the corresponding HashMap entry
 * without a reverse lookup (O(n)).
 *
 * By storing the key in the node, eviction becomes O(1):
 * Node lru = dll.removeLast();
 * map.remove(lru.key); ← we need the key here!
 * ──────────────────────────────────────────────────────────────────────
 *
 * WHY Doubly Linked (prev + next)?
 * ──────────────────────────────────────────────────────────────────────
 * With a singly linked list, removing a node requires finding its
 * predecessor by traversing from the head → O(n).
 *
 * With a doubly linked list, removal is O(1):
 * node.prev.next = node.next;
 * node.next.prev = node.prev;
 *
 * This is critical for the "move to head" operation on every get().
 * ──────────────────────────────────────────────────────────────────────
 *
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
public class Node<K, V> {

    K key;
    V value;
    Node<K, V> prev; // link to previous node (towards head)
    Node<K, V> next; // link to next node (towards tail)

    /** Constructor for data nodes. */
    public Node(K key, V value) {
        this.key = key;
        this.value = value;
    }

    /** Constructor for sentinel (dummy) nodes — no key/value. */
    public Node() {
        this(null, null);
    }

    @Override
    public String toString() {
        return String.format("(%s=%s)", key, value);
    }
}
