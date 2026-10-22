package com.miniredis.config;

import com.miniredis.storage.EvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Server configuration holding network, memory, TTL, and persistence settings.
 * Implements Builder Pattern.
 */
public class ServerConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    private final int port;
    private final long maxMemoryBytes;
    private final int threadPoolSize;
    private final boolean aofEnabled;
    private final String aofFilePath;
    private final EvictionPolicy evictionPolicy;
    private final long ttlCleanupIntervalMs;

    private ServerConfig(Builder builder) {
        this.port = builder.port;
        this.maxMemoryBytes = builder.maxMemoryBytes;
        this.threadPoolSize = builder.threadPoolSize;
        this.aofEnabled = builder.aofEnabled;
        this.aofFilePath = builder.aofFilePath;
        this.evictionPolicy = builder.evictionPolicy;
        this.ttlCleanupIntervalMs = builder.ttlCleanupIntervalMs;
    }

    public int getPort() { return port; }
    public long getMaxMemoryBytes() { return maxMemoryBytes; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public boolean isAofEnabled() { return aofEnabled; }
    public String getAofFilePath() { return aofFilePath; }
    public EvictionPolicy getEvictionPolicy() { return evictionPolicy; }
    public long getTtlCleanupIntervalMs() { return ttlCleanupIntervalMs; }

    public static Builder builder() {
        return new Builder();
    }

    public static ServerConfig loadFromProperties(String resourceName) {
        Properties props = new Properties();
        try (InputStream input = ServerConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input != null) {
                props.load(input);
                log.info("Loaded server configuration from {}", resourceName);
            } else {
                log.warn("Configuration file {} not found, using default values", resourceName);
            }
        } catch (Exception e) {
            log.error("Failed to read server configuration file", e);
        }

        int port = Integer.parseInt(props.getProperty("server.port", "6379"));
        long maxMemoryMB = Long.parseLong(props.getProperty("server.maxMemoryMB", "100"));
        int threadPoolSize = Integer.parseInt(props.getProperty("server.threadPoolSize", "10"));
        boolean aofEnabled = Boolean.parseBoolean(props.getProperty("server.aofEnabled", "true"));
        String aofFilePath = props.getProperty("server.aofFilePath", "appendonly.aof");
        String evictionPolicy = props.getProperty("server.evictionPolicy", "LRU");
        long ttlInterval = Long.parseLong(props.getProperty("server.ttlCleanupIntervalMs", "1000"));

        return builder()
                .port(port)
                .maxMemoryBytes(maxMemoryMB * 1024L * 1024L)
                .threadPoolSize(threadPoolSize)
                .aofEnabled(aofEnabled)
                .aofFilePath(aofFilePath)
                .evictionPolicy(EvictionPolicy.fromString(evictionPolicy))
                .ttlCleanupIntervalMs(ttlInterval)
                .build();
    }

    public static class Builder {
        private int port = 6379;
        private long maxMemoryBytes = 100 * 1024 * 1024L; // 100 MB
        private int threadPoolSize = 10;
        private boolean aofEnabled = true;
        private String aofFilePath = "appendonly.aof";
        private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
        private long ttlCleanupIntervalMs = 1000L;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder maxMemoryBytes(long maxMemoryBytes) {
            this.maxMemoryBytes = maxMemoryBytes;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public Builder aofEnabled(boolean aofEnabled) {
            this.aofEnabled = aofEnabled;
            return this;
        }

        public Builder aofFilePath(String aofFilePath) {
            this.aofFilePath = aofFilePath;
            return this;
        }

        public Builder evictionPolicy(EvictionPolicy evictionPolicy) {
            this.evictionPolicy = evictionPolicy;
            return this;
        }

        public Builder ttlCleanupIntervalMs(long ttlCleanupIntervalMs) {
            this.ttlCleanupIntervalMs = ttlCleanupIntervalMs;
            return this;
        }

        public ServerConfig build() {
            return new ServerConfig(this);
        }
    }
}
