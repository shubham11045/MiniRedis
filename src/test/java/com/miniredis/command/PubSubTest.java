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

class PubSubTest {
    private CommandFactory commandFactory;
    private ClientSession subSession;
    private ClientSession pubSession;
    private StringWriter subWriter;

    @BeforeEach
    void setUp() {
        KeyValueStore store = new KeyValueStore(1000L, com.miniredis.storage.EvictionPolicy.LRU);
        TTLManager ttlManager = new TTLManager(store, 1000);
        MetricsManager metricsManager = new MetricsManager();
        PubSubManager pubSubManager = new PubSubManager();
        CommandContext context = new CommandContext(store, ttlManager, null, metricsManager, pubSubManager);
        commandFactory = new CommandFactory();

        subWriter = new StringWriter();
        PrintWriter pwSub = new PrintWriter(subWriter);
        subSession = new ClientSession(context, pwSub);

        StringWriter pubWriter = new StringWriter();
        PrintWriter pwPub = new PrintWriter(pubWriter);
        pubSession = new ClientSession(context, pwPub);
    }

    @Test
    void testSubscribeAndPublish() {
        String subRes = commandFactory.executeLine(subSession, "SUBSCRIBE news");
        assertTrue(subSession.isInPubSubMode());
        assertTrue(subRes.contains("subscribe"));

        String pubRes = commandFactory.executeLine(pubSession, "PUBLISH news hello");
        assertEquals(":1\r\n", pubRes);

        String pushedMsg = subWriter.toString();
        assertTrue(pushedMsg.contains("message"));
        assertTrue(pushedMsg.contains("hello"));
    }

    @Test
    void testUnsubscribe() {
        commandFactory.executeLine(subSession, "SUBSCRIBE news");
        commandFactory.executeLine(subSession, "UNSUBSCRIBE news");
        
        assertFalse(subSession.isInPubSubMode());

        String pubRes = commandFactory.executeLine(pubSession, "PUBLISH news hello");
        assertEquals(":0\r\n", pubRes);
    }
}
