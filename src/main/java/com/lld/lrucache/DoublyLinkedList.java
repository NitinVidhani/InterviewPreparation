package com.lld.lrucache;

/**
 * A Doubly Linked List with sentinel head and tail nodes.
 *
 * ──────────────────────────────────────────────────────────────────────
 * SENTINEL NODES (Dummy Head & Tail)
 * ──────────────────────────────────────────────────────────────────────
 * The head and tail are DUMMY nodes that never hold real data.
 * They simplify the code by eliminating null checks:
 *
 * Without sentinels: With sentinels:
 * if (head == null) { ... } // Never null!
 * if (node == head) { ... } // head.next is first real node
 * if (node.prev == null) { ... } // node.prev is always valid
 *
 * Structure:
 * HEAD ↔ [MRU] ↔ [Recent] ↔ ... ↔ [LRU] ↔ TAIL
 * (dummy) (dummy)
 * ↑ ↑
 * addFirst inserts here removeLast evicts here
 *
 * All operations are O(1):
 * - addFirst: O(1) — insert after head sentinel
 * - removeLast: O(1) — remove before tail sentinel
 * - remove: O(1) — unlink using prev/next pointers
 * - moveToHead: O(1) — remove + addFirst
 * ──────────────────────────────────────────────────────────────────────
 *
 * @param <K> key type
 * @param <V> value type
 */
public class DoublyLinkedList<K, V> {

    private final Node<K, V> head; // sentinel — always first
    private final Node<K, V> tail; // sentinel — always last
    private int size;

    public DoublyLinkedList() {
        // Initialize sentinels and link them together
        // head ↔ tail (empty list)
        this.head = new Node<>();
        this.tail = new Node<>();
        head.next = tail;
        tail.prev = head;
        this.size = 0;
    }

    /**
     * Add a node right after the HEAD sentinel (making it the most recent).
     *
     * Before: HEAD ↔ [A] ↔ [B] ↔ TAIL
     * After: HEAD ↔ [NEW] ↔ [A] ↔ [B] ↔ TAIL
     *
     * Steps:
     * 1. NEW.prev = HEAD
     * 2. NEW.next = HEAD.next (which is [A])
     * 3. HEAD.next.prev = NEW (A's prev now points to NEW)
     * 4. HEAD.next = NEW (HEAD's next now points to NEW)
     */
    public void addFirst(Node<K, V> node) {
        // Link the new node between head and head.next
        node.prev = head;
        node.next = head.next;

        // Update the existing links to include the new node
        head.next.prev = node;
        head.next = node;

        size++;
    }

    /**
     * Remove the LAST real node (just before TAIL sentinel) — this is the LRU item.
     *
     * Before: HEAD ↔ [A] ↔ [B] ↔ [C] ↔ TAIL
     * ↑ LRU
     * After: HEAD ↔ [A] ↔ [B] ↔ TAIL
     *
     * @return the removed node (so the caller can get its key for HashMap removal)
     */
    public Node<K, V> removeLast() {
        if (isEmpty()) {
            return null;
        }
        // The LRU node is always tail.prev (thanks to sentinels)
        Node<K, V> lruNode = tail.prev;
        remove(lruNode);
        return lruNode;
    }

    /**
     * Remove a specific node from anywhere in the list — O(1).
     *
     * Before: ... ↔ [PREV] ↔ [NODE] ↔ [NEXT] ↔ ...
     * After: ... ↔ [PREV] ↔ [NEXT] ↔ ...
     *
     * This is O(1) BECAUSE we have a doubly linked list:
     * node.prev.next = node.next; // bypass NODE going forward
     * node.next.prev = node.prev; // bypass NODE going backward
     *
     * With a singly linked list, we'd need to traverse from head to
     * find node's predecessor → O(n). That's why DLL is essential.
     */
    public void remove(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;

        // Clean up the removed node's pointers (good practice)
        node.prev = null;
        node.next = null;

        size--;
    }

    /**
     * Move an existing node to the head (mark as most recently used).
     *
     * This is the key operation called on every get() and put() of an
     * existing key. It's implemented as: remove + addFirst → O(1).
     */
    public void moveToHead(Node<K, V> node) {
        remove(node);
        addFirst(node);
    }

    // --- Utility methods ---

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    /** Print the list from MRU (head) to LRU (tail) — useful for debugging. */
    public String toStringFromHead() {
        StringBuilder sb = new StringBuilder("HEAD");
        Node<K, V> current = head.next;
        while (current != tail) {
            sb.append(" ↔ ").append(current);
            current = current.next;
        }
        sb.append(" ↔ TAIL");
        return sb.toString();
    }
}
