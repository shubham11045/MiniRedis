package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class DelCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length < 1) {
            return "-ERR wrong number of arguments for 'del' command\r\n";
        }

        int count = 0;
        for (String key : args) {
            if (session.getServerContext().getStore().delete(key)) {
                count++;
            }
        }
        return ":" + count + "\r\n";
    }

    @Override
    public boolean isMutating() {
        return true;
    }
}
