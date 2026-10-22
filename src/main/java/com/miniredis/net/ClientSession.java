package com.miniredis.net;

import com.miniredis.command.CommandContext;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds per-client state such as transactions and pub/sub subscriptions.
 */
public class ClientSession {
    private final CommandContext serverContext;
    private final PrintWriter writer;

    // Transaction state
    private boolean inTransaction = false;
    private final List<String> commandQueue = new ArrayList<>();

    // Pub/Sub state
    private boolean inPubSubMode = false;
    private final Set<String> subscribedChannels = new HashSet<>();

    public ClientSession(CommandContext serverContext, PrintWriter writer) {
        this.serverContext = serverContext;
        this.writer = writer;
    }

    public CommandContext getServerContext() {
        return serverContext;
    }

    public PrintWriter getWriter() {
        return writer;
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void setInTransaction(boolean inTransaction) {
        this.inTransaction = inTransaction;
    }

    public List<String> getCommandQueue() {
        return commandQueue;
    }

    public boolean isInPubSubMode() {
        return inPubSubMode;
    }

    public void setInPubSubMode(boolean inPubSubMode) {
        this.inPubSubMode = inPubSubMode;
    }

    public Set<String> getSubscribedChannels() {
        return subscribedChannels;
    }

    /**
     * Send an asynchronous message (e.g. from Pub/Sub) to the client.
     */
    public void sendMessage(String message) {
        synchronized (writer) {
            writer.print(message);
            writer.flush();
        }
    }
}
