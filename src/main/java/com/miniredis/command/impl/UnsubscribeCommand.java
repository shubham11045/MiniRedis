package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.net.ClientSession;

public class UnsubscribeCommand implements Command {
    @Override
    public String execute(ClientSession session, String[] args) {
        if (session.getServerContext().getPubSubManager() == null) {
            return "-ERR pubsub is disabled\r\n";
        }

        StringBuilder sb = new StringBuilder();

        if (args.length == 0) {
            // Unsubscribe from all channels
            session.getServerContext().getPubSubManager().unsubscribeAll(session);
            sb.append("*3\r\n$11\r\nunsubscribe\r\n$-1\r\n:0\r\n");
        } else {
            for (String channel : args) {
                session.getServerContext().getPubSubManager().unsubscribe(session, channel);
                sb.append("*3\r\n$11\r\nunsubscribe\r\n")
                  .append("$").append(channel.length()).append("\r\n").append(channel).append("\r\n")
                  .append(":").append(session.getSubscribedChannels().size()).append("\r\n");
            }
        }
        
        return sb.toString();
    }

    @Override
    public boolean isMutating() {
        return false;
    }
}
