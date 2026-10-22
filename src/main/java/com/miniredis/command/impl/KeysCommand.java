package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

import java.util.Set;

public class KeysCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        String pattern = args.length > 0 ? args[0] : "*";
        Set<String> keys = session.getServerContext().getStore().keys(pattern);
        if (keys.isEmpty()) {
            return "*0\r\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(keys.size()).append("\r\n");
        for (String k : keys) {
            sb.append("$").append(k.length()).append("\r\n").append(k).append("\r\n");
        }
        return sb.toString();
    }
}
