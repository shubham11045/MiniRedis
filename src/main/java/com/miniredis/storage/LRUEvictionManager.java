package com.miniredis.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Custom O(1) LRU (Least Recently Used) Eviction Manager.
 * Built using a HashMap combined with a custom Doubly Linked List.
 * 
 * Node Order:
 * HEAD (Dummy) <-> [MRU Key] <-> ... <-> [LRU Key] <-> TAIL (Dummy)
 * 
 * All list operations are protected by a fine-grained ReentrantLock ensuring O(1) time complexity
 * and thread safety across concurrent reads and writes.
 */
public class LRUEvictionManager {

    private static class Node {
        final String key;
        Node prev;
        Node next;

        Node(String key) {
            this.key = key;
        }
    }

    private final Map<String, Node> nodeMap = new ConcurrentHashMap<>();
    private final Node head = new Node(null); // Dummy head (MRU side)
    private final Node tail = new Node(null); // Dummy tail (LRU side)
    private final ReentrantLock lock = new ReentrantLock();

    public LRUEvictionManager() {
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Record key access or insertion.
     * Moves existing node to head (MRU), or creates new node at head.
     */
    public void recordAccess(String key) {
        lock.lock();
        try {
            Node node = nodeMap.get(key);
            if (node != null) {
                // Move existing node to head
                removeNode(node);
                addAtHead(node);
            } else {
                // Add new node at head
                node = new Node(key);
                nodeMap.put(key, node);
                addAtHead(node);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a key from the LRU cache tracking (e.g. on DEL command).
     */
    public void removeKey(String key) {
        lock.lock();
        try {
            Node node = nodeMap.remove(key);
            if (node != null) {
                removeNode(node);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Evict the Least Recently Used (LRU) key from the tracking list.
     * @return key of evicted item, or null if list is empty.
     */
    public String evictLruKey() {
        lock.lock();
        try {
            if (tail.prev == head) {
                return null; // Empty cache
            }
            Node lruNode = tail.prev;
            removeNode(lruNode);
            nodeMap.remove(lruNode.key);
            return lruNode.key;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clear all tracked keys.
     */
    public void clear() {
        lock.lock();
        try {
            nodeMap.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        return nodeMap.size();
    }

    // Helper: Add node immediately after dummy head (MRU position)
    private void addAtHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    // Helper: Unlink node from doubly linked list
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}
