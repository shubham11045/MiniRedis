package com.miniredis.command;

import com.miniredis.metrics.MetricsManager;
import com.miniredis.persistence.AofPersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.ttl.TTLManager;

import java.util.concurrent.locks.ReentrantLock;

public class CommandContext {
    private final KeyValueStore store;
    private final TTLManager ttlManager;
    private final AofPersistenceManager aofManager;
    private final MetricsManager metricsManager;
    private final PubSubManager pubSubManager;
    private final ReentrantLock globalTransactionLock = new ReentrantLock(true); // fair lock

    public CommandContext(KeyValueStore store,
                          TTLManager ttlManager,
                          AofPersistenceManager aofManager,
                          MetricsManager metricsManager,
                          PubSubManager pubSubManager) {
        this.store = store;
        this.ttlManager = ttlManager;
        this.aofManager = aofManager;
        this.metricsManager = metricsManager;
        this.pubSubManager = pubSubManager;
    }

    public KeyValueStore getStore() { return store; }
    public TTLManager getTtlManager() { return ttlManager; }
    public AofPersistenceManager getAofManager() { return aofManager; }
    public MetricsManager getMetricsManager() { return metricsManager; }
    public PubSubManager getPubSubManager() { return pubSubManager; }
    public ReentrantLock getGlobalTransactionLock() { return globalTransactionLock; }
}
