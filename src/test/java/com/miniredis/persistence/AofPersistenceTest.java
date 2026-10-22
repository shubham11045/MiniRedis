package com.miniredis.persistence;

import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;
import com.miniredis.command.CommandFactory;
import com.miniredis.storage.EvictionPolicy;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.storage.RedisValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AofPersistenceTest {

    @TempDir
    Path tempDir;

    private File aofFile;
    private AofPersistenceManager aofManager;
    private KeyValueStore store;
    private CommandFactory commandFactory;
    private CommandContext context;
    ClientSession session;

    @BeforeEach
    void setUp() {
        aofFile = tempDir.resolve("test-appendonly.aof").toFile();
        aofManager = new AofPersistenceManager(aofFile.getAbsolutePath(), true);
        store = new KeyValueStore(10 * 1024 * 1024L, EvictionPolicy.LRU);
        context = new CommandContext(store, null, aofManager, null, new com.miniredis.pubsub.PubSubManager());
        commandFactory = new CommandFactory();
        session = new ClientSession(context, new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()));
    }

    @AfterEach
    void tearDown() {
        aofManager.close();
    }

    @Test
    void testAofAppendAndReplay() {
        commandFactory.executeLine(session, "SET key1 Hello");
        commandFactory.executeLine(session, "SET key2 World");
        commandFactory.executeLine(session, "DEL key1");

        assertTrue(aofFile.exists());
        assertTrue(aofFile.length() > 0);

        // Clear store state
        store.clear();
        assertEquals(0, store.size());

        // Replay commands from AOF
        int replayed = aofManager.replay(line -> commandFactory.executeLine(session, line));

        assertEquals(3, replayed);
        assertNull(store.get("key1"));
        assertEquals("World", store.get("key2").getValue());
    }

    @Test
    void testAofRewriteCompaction() {
        store.put("k1", new RedisValue("v1"));
        store.put("k2", new RedisValue("v2"));

        aofManager.rewriteAof(store);

        // Replay on clean store to verify state compaction
        KeyValueStore restoredStore = new KeyValueStore(10 * 1024 * 1024L, EvictionPolicy.LRU);
        CommandContext restoredContext = new CommandContext(restoredStore, null, aofManager, null, new com.miniredis.pubsub.PubSubManager());
        ClientSession restoredSession = new ClientSession(restoredContext, new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()));

        aofManager.replay(line -> commandFactory.executeLine(restoredSession, line));

        assertEquals("v1", restoredStore.get("k1").getValue());
        assertEquals("v2", restoredStore.get("k2").getValue());
    }
}
