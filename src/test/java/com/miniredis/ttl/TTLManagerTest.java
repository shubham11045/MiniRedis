package com.miniredis.ttl;

import com.miniredis.storage.EvictionPolicy;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.storage.RedisValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TTLManagerTest {

    private KeyValueStore store;
    private TTLManager ttlManager;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore(10 * 1024 * 1024L, EvictionPolicy.LRU);
        ttlManager = new TTLManager(store, 100L);
    }

    @AfterEach
    void tearDown() {
        ttlManager.stop();
    }

    @Test
    void testActiveExpirationSweep() throws InterruptedException {
        store.put("expired1", new RedisValue("val1", 50L));
        store.put("expired2", new RedisValue("val2", 50L));
        store.put("permanent", new RedisValue("val3", -1L));

        assertEquals(3, store.size());

        // Wait for key expiration
        Thread.sleep(100);

        // Execute active expiration sweep manually
        ttlManager.performActiveExpiration();

        // Expired keys should be removed from underlying store
        assertNull(store.get("expired1"));
        assertNull(store.get("expired2"));
        assertNotNull(store.get("permanent"));
    }
}
