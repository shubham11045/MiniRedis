package com.miniredis.command;

import com.miniredis.net.ClientSession;

/**
 * Represents a command that can be executed on the key-value store.
 */
public interface Command {
    /**
     * Executes the command.
     * @param session the client session wrapping the context
     * @param args command arguments
     * @return String response to send back to client
     */
    String execute(ClientSession session, String[] args);

    /**
     * Returns true if command mutates state (requiring AOF logging).
     */
    default boolean isMutating() {
        return false;
    }
}
