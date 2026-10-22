package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class SaveCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (session.getServerContext().getAofManager() != null) {
            session.getServerContext().getAofManager().rewriteAof(session.getServerContext().getStore());
            return "+OK\r\n";
        }
        return "-ERR persistence is disabled\r\n";
    }
}
