package com.miniredis.command;

import com.miniredis.net.ClientSession;

import com.miniredis.metrics.MetricsManager;
import com.miniredis.storage.EvictionPolicy;
import com.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {

    private CommandFactory commandFactory;
    private CommandContext context;
    ClientSession session;
    private KeyValueStore store;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore(10 * 1024 * 1024L, EvictionPolicy.LRU);
        MetricsManager metricsManager = new MetricsManager();
        store.setMetricsManager(metricsManager);
        context = new CommandContext(store, null, null, metricsManager, new com.miniredis.pubsub.PubSubManager());
        commandFactory = new CommandFactory();
        session = new ClientSession(context, new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()));
    }

    @Test
    void testTokenizeWithQuotes() {
        List<String> tokens = CommandFactory.tokenize("SET msg \"Hello World Redis\"");
        assertEquals(3, tokens.size());
        assertEquals("SET", tokens.get(0));
        assertEquals("msg", tokens.get(1));
        assertEquals("Hello World Redis", tokens.get(2));
    }

    @Test
    void testSetAndGetCommands() {
        String setRes = commandFactory.executeLine(session, "SET greeting \"Hello Java\"");
        assertEquals("+OK\r\n", setRes);

        String getRes = commandFactory.executeLine(session, "GET greeting");
        assertEquals("$10\r\nHello Java\r\n", getRes);
    }

    @Test
    void testDelCommand() {
        commandFactory.executeLine(session, "SET a 1");
        commandFactory.executeLine(session, "SET b 2");

        String delRes = commandFactory.executeLine(session, "DEL a b c");
        assertEquals(":2\r\n", delRes);
    }

    @Test
    void testPingAndInfoCommands() {
        assertEquals("+PONG\r\n", commandFactory.executeLine(session, "PING"));
        assertEquals("+CUSTOM\r\n", commandFactory.executeLine(session, "PING CUSTOM"));

        String info = commandFactory.executeLine(session, "INFO");
        assertTrue(info.contains("uptimeSeconds"));
        assertTrue(info.startsWith("$"));
    }
}
