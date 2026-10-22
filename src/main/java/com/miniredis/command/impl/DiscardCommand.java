package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.net.ClientSession;

public class DiscardCommand implements Command {
    @Override
    public String execute(ClientSession session, String[] args) {
        if (!session.isInTransaction()) {
            return "-ERR DISCARD without MULTI\r\n";
        }
        session.setInTransaction(false);
        session.getCommandQueue().clear();
        return "+OK\r\n";
    }

    @Override
    public boolean isMutating() {
        return false;
    }
}
