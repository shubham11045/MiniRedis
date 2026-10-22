package com.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LRUEvictionManagerTest {

    private LRUEvictionManager lruManager;

    @BeforeEach
    void setUp() {
        lruManager = new LRUEvictionManager();
    }

    @Test
    void testLruEvictionOrder() {
        lruManager.recordAccess("A");
        lruManager.recordAccess("B");
        lruManager.recordAccess("C");

        // A is LRU
        assertEquals("A", lruManager.evictLruKey());
        assertEquals("B", lruManager.evictLruKey());
        assertEquals("C", lruManager.evictLruKey());
        assertNull(lruManager.evictLruKey());
    }

    @Test
    void testAccessMovesToMru() {
        lruManager.recordAccess("A");
        lruManager.recordAccess("B");
        lruManager.recordAccess("C");

        // Access A so it becomes MRU
        lruManager.recordAccess("A");

        // Now B is LRU
        assertEquals("B", lruManager.evictLruKey());
        assertEquals("C", lruManager.evictLruKey());
        assertEquals("A", lruManager.evictLruKey());
    }

    @Test
    void testStoreEvictsWhenMemoryExceeded() {
        // Small memory limit: 200 bytes (~1 item capacity)
        KeyValueStore smallStore = new KeyValueStore(200L, EvictionPolicy.LRU);

        smallStore.put("k1", new RedisValue("LargeValueStringOver200BytesLongAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        smallStore.put("k2", new RedisValue("LargeValueStringOver200BytesLongBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"));

        // k1 should be evicted by LRU policy
        assertNull(smallStore.get("k1"));
        assertNotNull(smallStore.get("k2"));
    }
}
