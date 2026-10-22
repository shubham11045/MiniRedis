package com.miniredis.command;

import com.miniredis.metrics.MetricsManager;
import com.miniredis.net.ClientSession;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.ttl.TTLManager;
import com.miniredis.pubsub.PubSubManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {
    private CommandFactory commandFactory;
    private ClientSession session;
    private KeyValueStore store;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore(1000L, com.miniredis.storage.EvictionPolicy.LRU);
        TTLManager ttlManager = new TTLManager(store, 1000);
        MetricsManager metricsManager = new MetricsManager();
        PubSubManager pubSubManager = new PubSubManager();
        CommandContext context = new CommandContext(store, ttlManager, null, metricsManager, pubSubManager);
        commandFactory = new CommandFactory();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        session = new ClientSession(context, pw);
    }

    @Test
    void testMultiExec() {
        assertEquals("+OK\r\n", commandFactory.executeLine(session, "MULTI"));
        assertTrue(session.isInTransaction());

        assertEquals("+QUEUED\r\n", commandFactory.executeLine(session, "SET a 1"));
        assertEquals("+QUEUED\r\n", commandFactory.executeLine(session, "SET b 2"));
        assertEquals("+QUEUED\r\n", commandFactory.executeLine(session, "GET a"));

        String execRes = commandFactory.executeLine(session, "EXEC");
        assertFalse(session.isInTransaction());
        assertTrue(execRes.startsWith("*3\r\n"));
        assertTrue(execRes.contains("+OK\r\n"));
        assertTrue(execRes.contains("$1\r\n1\r\n"));
    }

    @Test
    void testDiscard() {
        commandFactory.executeLine(session, "MULTI");
        commandFactory.executeLine(session, "SET a 1");
        
        assertEquals("+OK\r\n", commandFactory.executeLine(session, "DISCARD"));
        assertFalse(session.isInTransaction());
        assertEquals(0, session.getCommandQueue().size());
        
        assertEquals("-ERR EXEC without MULTI\r\n", commandFactory.executeLine(session, "EXEC"));
    }
}
