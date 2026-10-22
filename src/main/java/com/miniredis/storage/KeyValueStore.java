package com.miniredis.storage;

import com.miniredis.metrics.MetricsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * High-performance thread-safe in-memory key-value store using ConcurrentHashMap.
 * Supports memory tracking, automatic LRU eviction, and metrics integration.
 */
public class KeyValueStore {

    private static final Logger log = LoggerFactory.getLogger(KeyValueStore.class);

    private final Map<String, RedisValue> store = new ConcurrentHashMap<>();
    private final LRUEvictionManager lruManager = new LRUEvictionManager();
    private final AtomicLong currentMemoryBytes = new AtomicLong(0);

    private final long maxMemoryBytes;
    private final EvictionPolicy evictionPolicy;
    private MetricsManager metricsManager;

    public KeyValueStore(long maxMemoryBytes, EvictionPolicy evictionPolicy) {
        this.maxMemoryBytes = maxMemoryBytes <= 0 ? Long.MAX_VALUE : maxMemoryBytes;
        this.evictionPolicy = evictionPolicy != null ? evictionPolicy : EvictionPolicy.LRU;
    }

    public void setMetricsManager(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    /**
     * Put key-value pair into store.
     * Enforces memory capacity by evicting LRU keys if memory limit is exceeded.
     */
    public void put(String key, RedisValue value) {
        long newEntrySize = estimateKeySize(key) + value.getEstimatedSizeBytes();

        // Evict keys if memory limit exceeded
        if (evictionPolicy == EvictionPolicy.LRU && maxMemoryBytes < Long.MAX_VALUE) {
            checkAndEvictMemory(newEntrySize);
        }

        RedisValue old = store.put(key, value);
        lruManager.recordAccess(key);

        long delta = newEntrySize;
        if (old != null) {
            delta -= (estimateKeySize(key) + old.getEstimatedSizeBytes());
        }
        currentMemoryBytes.addAndGet(delta);

        if (metricsManager != null) {
            metricsManager.recordWrite();
            metricsManager.setMemoryUsage(currentMemoryBytes.get());
        }
    }

    /**
     * Get value for key with lazy expiration check.
     */
    public RedisValue get(String key) {
        if (metricsManager != null) {
            metricsManager.recordRead();
        }

        RedisValue val = store.get(key);
        if (val == null) {
            if (metricsManager != null) metricsManager.recordMiss();
            return null;
        }

        // Lazy Expiration Check
        if (val.isExpired()) {
            deleteInternal(key);
            if (metricsManager != null) {
                metricsManager.recordMiss();
                metricsManager.recordExpiredKey();
            }
            return null;
        }

        lruManager.recordAccess(key);
        if (metricsManager != null) {
            metricsManager.recordHit();
        }
        return val;
    }

    /**
     * Delete key from store.
     * @return true if key existed and was deleted.
     */
    public boolean delete(String key) {
        RedisValue removed = deleteInternal(key);
        if (removed != null && metricsManager != null) {
            metricsManager.recordWrite();
        }
        return removed != null;
    }

    private RedisValue deleteInternal(String key) {
        RedisValue removed = store.remove(key);
        if (removed != null) {
            lruManager.removeKey(key);
            long removedSize = estimateKeySize(key) + removed.getEstimatedSizeBytes();
            currentMemoryBytes.addAndGet(-removedSize);
            if (metricsManager != null) {
                metricsManager.setMemoryUsage(currentMemoryBytes.get());
            }
        }
        return removed;
    }

    /**
     * Check if key exists (with lazy expiration).
     */
    public boolean exists(String key) {
        return get(key) != null;
    }

    /**
     * Find keys matching a regex or glob pattern (e.g., "*", "user*").
     */
    public Set<String> keys(String globPattern) {
        if (globPattern == null || globPattern.equals("*")) {
            return store.entrySet().stream()
                    .filter(e -> !e.getValue().isExpired())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }

        String regex = globToRegex(globPattern);
        Pattern pattern = Pattern.compile(regex);

        return store.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .map(Map.Entry::getKey)
                .filter(k -> pattern.matcher(k).matches())
                .collect(Collectors.toSet());
    }

    /**
     * Update TTL for an existing key.
     * @return true if TTL was updated, false if key does not exist.
     */
    public boolean expire(String key, long ttlSeconds) {
        RedisValue existing = get(key);
        if (existing == null) {
            return false;
        }
        RedisValue updated = new RedisValue(existing.getValue(), ttlSeconds * 1000L);
        put(key, updated);
        return true;
    }

    /**
     * Get remaining TTL in seconds for key.
     * -1 = key exists with no expire
     * -2 = key does not exist or expired
     */
    public long ttl(String key) {
        RedisValue val = get(key);
        if (val == null) {
            return -2L;
        }
        return val.getRemainingTtlSeconds();
    }

    public long size() {
        return store.size();
    }

    public long getCurrentMemoryBytes() {
        return currentMemoryBytes.get();
    }

    public Map<String, RedisValue> getRawStore() {
        return Collections.unmodifiableMap(store);
    }

    public void clear() {
        store.clear();
        lruManager.clear();
        currentMemoryBytes.set(0);
    }

    private void checkAndEvictMemory(long incomingBytes) {
        while (currentMemoryBytes.get() + incomingBytes > maxMemoryBytes && store.size() > 0) {
            String evictedKey = lruManager.evictLruKey();
            if (evictedKey == null) {
                break;
            }
            RedisValue removed = store.remove(evictedKey);
            if (removed != null) {
                long size = estimateKeySize(evictedKey) + removed.getEstimatedSizeBytes();
                currentMemoryBytes.addAndGet(-size);
                log.info("LRU Evicted key '{}' to free memory (Current: {} bytes, Max: {} bytes)",
                        evictedKey, currentMemoryBytes.get(), maxMemoryBytes);
            }
        }
    }

    private long estimateKeySize(String key) {
        return 32L + (key.length() * 2L);
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append("."); break;
                case '.': sb.append("\\."); break;
                case '\\': sb.append("\\\\"); break;
                default: sb.append(c); break;
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
