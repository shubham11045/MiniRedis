package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class TtlCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length != 1) {
            return "-ERR wrong number of arguments for 'ttl' command\r\n";
        }
        String key = args[0];
        long seconds = session.getServerContext().getStore().ttl(key);
        return ":" + seconds + "\r\n";
    }
}
