package com.miniredis.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe metrics aggregator for server operations.
 * Uses LongAdder counters to eliminate CAS contention under high concurrent multi-client load.
 */
public class MetricsManager {

    private final LongAdder totalReads = new LongAdder();
    private final LongAdder totalWrites = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder expiredKeys = new LongAdder();
    private final LongAdder activeConnections = new LongAdder();
    private final AtomicLong memoryUsageBytes = new AtomicLong(0);
    private final long startTimeMs = System.currentTimeMillis();

    public void recordRead() {
        totalReads.increment();
    }

    public void recordWrite() {
        totalWrites.increment();
    }

    public void recordHit() {
        cacheHits.increment();
    }

    public void recordMiss() {
        cacheMisses.increment();
    }

    public void recordExpiredKey() {
        expiredKeys.increment();
    }

    public void clientConnected() {
        activeConnections.increment();
    }

    public void clientDisconnected() {
        activeConnections.decrement();
    }

    public void setMemoryUsage(long bytes) {
        memoryUsageBytes.set(bytes);
    }

    public long getTotalReads() { return totalReads.sum(); }
    public long getTotalWrites() { return totalWrites.sum(); }
    public long getCacheHits() { return cacheHits.sum(); }
    public long getCacheMisses() { return cacheMisses.sum(); }
    public long getExpiredKeys() { return expiredKeys.sum(); }
    public long getActiveConnections() { return activeConnections.sum(); }
    public long getMemoryUsageBytes() { return memoryUsageBytes.get(); }
    public long getUptimeSeconds() { return (System.currentTimeMillis() - startTimeMs) / 1000L; }

    public Map<String, Object> getMetricsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uptimeSeconds", getUptimeSeconds());
        map.put("totalReads", getTotalReads());
        map.put("totalWrites", getTotalWrites());
        map.put("cacheHits", getCacheHits());
        map.put("cacheMisses", getCacheMisses());
        long totalOps = getCacheHits() + getCacheMisses();
        double hitRate = totalOps == 0 ? 0.0 : ((double) getCacheHits() / totalOps) * 100.0;
        map.put("hitRatePercent", String.format("%.2f%%", hitRate));
        map.put("expiredKeys", getExpiredKeys());
        map.put("activeConnections", getActiveConnections());
        map.put("memoryUsageBytes", getMemoryUsageBytes());
        map.put("memoryUsageMB", String.format("%.2f MB", getMemoryUsageBytes() / (1024.0 * 1024.0)));
        return map;
    }

    public String toFormattedString() {
        StringBuilder sb = new StringBuilder("# Server Info & Metrics\r\n");
        getMetricsMap().forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        return sb.toString();
    }
}
