package com.miniredis.ttl;

import com.miniredis.metrics.MetricsManager;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.storage.RedisValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages active background key expiration using ScheduledExecutorService.
 * Implements Redis's probabilistic active expiration algorithm:
 * - Samples 20 random keys with TTL.
 * - Deletes expired keys.
 * - If > 25% expired, repeats sampling (up to safety limit).
 */
public class TTLManager {

    private static final Logger log = LoggerFactory.getLogger(TTLManager.class);
    private static final int SAMPLE_SIZE = 20;

    private final KeyValueStore store;
    private final ScheduledExecutorService scheduler;
    private final long intervalMs;
    private MetricsManager metricsManager;
    private boolean running = false;

    public TTLManager(KeyValueStore store, long intervalMs) {
        this.store = store;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ttl-cleanup-thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void setMetricsManager(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::performActiveExpiration, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Active TTL Cleanup Manager started (interval: {} ms)", intervalMs);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Active TTL Cleanup Manager stopped");
    }

    /**
     * Active expiration cleanup loop.
     */
    public void performActiveExpiration() {
        try {
            Map<String, RedisValue> rawMap = store.getRawStore();
            if (rawMap.isEmpty()) return;

            List<Map.Entry<String, RedisValue>> entriesWithTtl = new ArrayList<>();
            for (Map.Entry<String, RedisValue> entry : rawMap.entrySet()) {
                if (entry.getValue().getTtlMs() > 0) {
                    entriesWithTtl.add(entry);
                }
            }

            if (entriesWithTtl.isEmpty()) return;

            int expiredCount = 0;
            int sampled = 0;

            // Random sampling up to SAMPLE_SIZE
            java.util.Collections.shuffle(entriesWithTtl);
            for (Map.Entry<String, RedisValue> entry : entriesWithTtl) {
                if (sampled >= SAMPLE_SIZE) break;
                sampled++;

                if (entry.getValue().isExpired()) {
                    if (store.delete(entry.getKey())) {
                        expiredCount++;
                        if (metricsManager != null) {
                            metricsManager.recordExpiredKey();
                        }
                    }
                }
            }

            if (expiredCount > 0) {
                log.debug("Active TTL sweep cleaned {} expired keys out of {} sampled", expiredCount, sampled);
            }
        } catch (Exception e) {
            log.error("Error during active TTL expiration sweep", e);
        }
    }
}
