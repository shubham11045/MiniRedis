package com.miniredis.storage;

import java.util.Objects;

/**
 * Value object stored inside KeyValueStore.
 * Holds string data, creation timestamp, and optional TTL.
 */
public class RedisValue {
    private final String value;
    private final long createdAtMs;
    private final long ttlMs; // -1 means no expiration

    public RedisValue(String value) {
        this(value, -1L);
    }

    public RedisValue(String value, long ttlMs) {
        this.value = Objects.requireNonNull(value, "Value cannot be null");
        this.createdAtMs = System.currentTimeMillis();
        this.ttlMs = ttlMs;
    }

    // Constructor with explicit creation timestamp (useful for persistent reload)
    public RedisValue(String value, long createdAtMs, long ttlMs) {
        this.value = Objects.requireNonNull(value, "Value cannot be null");
        this.createdAtMs = createdAtMs;
        this.ttlMs = ttlMs;
    }

    public String getValue() {
        return value;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    /**
     * Checks whether this key-value pair has expired based on current time.
     */
    public boolean isExpired() {
        if (ttlMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() > (createdAtMs + ttlMs);
    }

    /**
     * Returns remaining TTL in seconds.
     * -1 = no expiration set
     * -2 = already expired
     */
    public long getRemainingTtlSeconds() {
        if (ttlMs <= 0) {
            return -1L;
        }
        long expireAtMs = createdAtMs + ttlMs;
        long now = System.currentTimeMillis();
        if (now >= expireAtMs) {
            return -2L;
        }
        return (expireAtMs - now) / 1000L;
    }

    /**
     * Rough memory estimation in bytes.
     * Object header (24 bytes) + String char array (value.length * 2 + 24) + primitive fields (16 bytes).
     */
    public long getEstimatedSizeBytes() {
        return 64L + (value.length() * 2L);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedisValue that = (RedisValue) o;
        return createdAtMs == that.createdAtMs && ttlMs == that.ttlMs && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, createdAtMs, ttlMs);
    }

    @Override
    public String toString() {
        return "RedisValue{" +
                "value='" + value + '\'' +
                ", ttlMs=" + ttlMs +
                ", remainingTtlSec=" + getRemainingTtlSeconds() +
                '}';
    }
}
