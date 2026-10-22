package com.miniredis.storage;

public enum EvictionPolicy {
    LRU,
    NO_EVICTION;

    public static EvictionPolicy fromString(String policy) {
        if (policy == null) return LRU;
        try {
            return EvictionPolicy.valueOf(policy.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LRU;
        }
    }
}
