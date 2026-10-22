package com.miniredis.net;

import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;
import com.miniredis.command.CommandFactory;
import com.miniredis.metrics.MetricsManager;
import com.miniredis.storage.EvictionPolicy;
import com.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentAccessTest {

    private KeyValueStore store;
    private CommandFactory commandFactory;
    private CommandContext context;
    ClientSession session;
    private MetricsManager metricsManager;

    @BeforeEach
    void setUp() {
        metricsManager = new MetricsManager();
        store = new KeyValueStore(50 * 1024 * 1024L, EvictionPolicy.LRU);
        store.setMetricsManager(metricsManager);
        context = new CommandContext(store, null, null, metricsManager, new com.miniredis.pubsub.PubSubManager());
        commandFactory = new CommandFactory();
        session = new ClientSession(context, new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()));
    }

    @Test
    void testConcurrentWritesAndReads() throws InterruptedException {
        int threadCount = 10;
        int opsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        String val = "val-" + j;
                        commandFactory.executeLine(session, "SET " + key + " " + val);
                        commandFactory.executeLine(session, "GET " + key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "Concurrent tasks should complete without hanging or deadlock");

        executor.shutdown();
        assertEquals(threadCount * opsPerThread, store.size());
        assertEquals(threadCount * opsPerThread, metricsManager.getTotalWrites());
        assertEquals(threadCount * opsPerThread, metricsManager.getTotalReads());
    }
}
