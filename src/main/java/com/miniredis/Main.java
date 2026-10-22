package com.miniredis;

import com.miniredis.config.ServerConfig;
import com.miniredis.net.RedisServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("==========================================");
        log.info("           MINI REDIS SERVER              ");
        log.info("==========================================");

        ServerConfig config = ServerConfig.loadFromProperties("server.properties");
        RedisServer server = new RedisServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received.");
            server.stop();
        }));

        server.start();
    }
}
