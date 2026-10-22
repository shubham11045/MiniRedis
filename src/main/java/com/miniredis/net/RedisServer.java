package com.miniredis.net;

import com.miniredis.config.ServerConfig;
import com.miniredis.command.CommandContext;
import com.miniredis.command.CommandFactory;
import com.miniredis.metrics.MetricsManager;
import com.miniredis.persistence.AofPersistenceManager;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.ttl.TTLManager;
import com.miniredis.pubsub.PubSubManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main TCP Server Bootstrapper.
 * Initializes storage, metrics, TTL scheduler, AOF persistence, and manages thread pool.
 */
public class RedisServer {

    private static final Logger log = LoggerFactory.getLogger(RedisServer.class);

    private final ServerConfig config;
    private final KeyValueStore store;
    private final TTLManager ttlManager;
    private final AofPersistenceManager aofManager;
    private final MetricsManager metricsManager;
    private final PubSubManager pubSubManager;
    private final CommandContext context;
    private final CommandFactory commandFactory;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    public RedisServer(ServerConfig config) {
        this.config = config;
        this.metricsManager = new MetricsManager();
        this.pubSubManager = new PubSubManager();
        this.store = new KeyValueStore(config.getMaxMemoryBytes(), config.getEvictionPolicy());
        this.store.setMetricsManager(metricsManager);

        this.ttlManager = new TTLManager(store, config.getTtlCleanupIntervalMs());
        this.ttlManager.setMetricsManager(metricsManager);

        this.aofManager = new AofPersistenceManager(config.getAofFilePath(), config.isAofEnabled());
        this.context = new CommandContext(store, ttlManager, aofManager, metricsManager, pubSubManager);
        this.commandFactory = new CommandFactory();
    }

    public void start() {
        if (running) return;
        running = true;

        log.info("Starting Mini Redis Server...");
        log.info("Port: {}, MaxMemory: {} MB, AOF: {}", config.getPort(),
                config.getMaxMemoryBytes() / (1024 * 1024), config.isAofEnabled());

        // Replay AOF log file on startup
        if (config.isAofEnabled()) {
            log.info("Replaying AOF persistence log...");
            ClientSession dummySession = new ClientSession(context, new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()));
            int replayed = aofManager.replay(cmd -> commandFactory.executeLine(dummySession, cmd));
            log.info("Successfully replayed {} commands.", replayed);
        }

        // Start active TTL cleanup
        ttlManager.start();

        // Initialize thread pool for client connections
        threadPool = Executors.newFixedThreadPool(config.getThreadPoolSize(), r -> {
            Thread t = new Thread(r);
            t.setName("redis-worker-" + t.threadId());
            return t;
        });

        try {
            serverSocket = new ServerSocket(config.getPort());
            log.info("Mini Redis Server is ready to accept connections on port {}", config.getPort());

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(new ClientHandler(clientSocket, context, commandFactory));
                } catch (IOException e) {
                    if (!running) {
                        log.info("Server socket closed.");
                        break;
                    }
                    log.error("Error accepting client connection", e);
                }
            }
        } catch (IOException e) {
            log.error("Could not start server on port {}", config.getPort(), e);
        } finally {
            stop();
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        log.info("Shutting down Mini Redis Server...");

        ttlManager.stop();
        aofManager.close();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.error("Error closing server socket", e);
            }
        }

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Mini Redis Server shutdown complete.");
    }

    public KeyValueStore getStore() { return store; }
    public CommandContext getContext() { return context; }
    public CommandFactory getCommandFactory() { return commandFactory; }
    public MetricsManager getMetricsManager() { return metricsManager; }
}
