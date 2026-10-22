package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.net.ClientSession;

public class SubscribeCommand implements Command {
    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length < 1) {
            return "-ERR wrong number of arguments for 'subscribe' command\r\n";
        }
        
        if (session.getServerContext().getPubSubManager() == null) {
            return "-ERR pubsub is disabled\r\n";
        }

        StringBuilder sb = new StringBuilder();
        int subCount = session.getSubscribedChannels().size();
        
        for (String channel : args) {
            session.getServerContext().getPubSubManager().subscribe(session, channel);
            subCount++;
            
            // Append RESP response for each subscribed channel
            sb.append("*3\r\n")
              .append("$9\r\nsubscribe\r\n")
              .append("$").append(channel.length()).append("\r\n").append(channel).append("\r\n")
              .append(":").append(subCount).append("\r\n");
        }
        
        return sb.toString();
    }

    @Override
    public boolean isMutating() {
        return false;
    }
}
