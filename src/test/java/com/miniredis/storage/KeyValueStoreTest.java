package com.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueStoreTest {

    private KeyValueStore store;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore(10 * 1024 * 1024L, EvictionPolicy.LRU); // 10MB limit
    }

    @Test
    void testBasicPutAndGet() {
        store.put("name", new RedisValue("Hruday"));
        RedisValue val = store.get("name");
        assertNotNull(val);
        assertEquals("Hruday", val.getValue());
    }

    @Test
    void testDelete() {
        store.put("key1", new RedisValue("val1"));
        assertTrue(store.delete("key1"));
        assertNull(store.get("key1"));
        assertFalse(store.delete("key1"));
    }

    @Test
    void testExists() {
        store.put("k", new RedisValue("v"));
        assertTrue(store.exists("k"));
        assertFalse(store.exists("unknown"));
    }

    @Test
    void testKeysPatternMatching() {
        store.put("user:1", new RedisValue("Alice"));
        store.put("user:2", new RedisValue("Bob"));
        store.put("product:1", new RedisValue("Laptop"));

        Set<String> userKeys = store.keys("user:*");
        assertEquals(2, userKeys.size());
        assertTrue(userKeys.contains("user:1"));
        assertTrue(userKeys.contains("user:2"));

        Set<String> allKeys = store.keys("*");
        assertEquals(3, allKeys.size());
    }

    @Test
    void testLazyExpiration() throws InterruptedException {
        store.put("temp", new RedisValue("value", 100L)); // 100ms TTL
        assertNotNull(store.get("temp"));

        Thread.sleep(150);
        assertNull(store.get("temp"));
        assertFalse(store.exists("temp"));
    }

    @Test
    void testExpireAndUpdateTtl() {
        store.put("k1", new RedisValue("v1"));
        assertEquals(-1L, store.ttl("k1"));

        assertTrue(store.expire("k1", 60L));
        assertTrue(store.ttl("k1") > 0);
    }
}
