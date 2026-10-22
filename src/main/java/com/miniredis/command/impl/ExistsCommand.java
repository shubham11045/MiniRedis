package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class ExistsCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length < 1) {
            return "-ERR wrong number of arguments for 'exists' command\r\n";
        }

        int count = 0;
        for (String key : args) {
            if (session.getServerContext().getStore().exists(key)) {
                count++;
            }
        }
        return ":" + count + "\r\n";
    }
}
