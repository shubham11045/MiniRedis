package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.net.ClientSession;

public class PublishCommand implements Command {
    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length != 2) {
            return "-ERR wrong number of arguments for 'publish' command\r\n";
        }

        if (session.getServerContext().getPubSubManager() == null) {
            return "-ERR pubsub is disabled\r\n";
        }

        String channel = args[0];
        String message = args[1];

        int receivers = session.getServerContext().getPubSubManager().publish(channel, message);
        return ":" + receivers + "\r\n";
    }

    @Override
    public boolean isMutating() {
        return false; // Publish doesn't mutate store state, so it shouldn't go to AOF
    }
}
