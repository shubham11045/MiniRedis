package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class ExpireCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length != 2) {
            return "-ERR wrong number of arguments for 'expire' command\r\n";
        }
        String key = args[0];
        try {
            long seconds = Long.parseLong(args[1]);
            boolean updated = session.getServerContext().getStore().expire(key, seconds);
            return ":" + (updated ? "1" : "0") + "\r\n";
        } catch (NumberFormatException e) {
            return "-ERR value is not an integer or out of range\r\n";
        }
    }

    @Override
    public boolean isMutating() {
        return true;
    }
}
