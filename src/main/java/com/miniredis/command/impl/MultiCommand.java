package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.net.ClientSession;

public class MultiCommand implements Command {
    @Override
    public String execute(ClientSession session, String[] args) {
        if (session.isInTransaction()) {
            return "-ERR MULTI calls can not be nested\r\n";
        }
        session.setInTransaction(true);
        session.getCommandQueue().clear();
        return "+OK\r\n";
    }

    @Override
    public boolean isMutating() {
        return false;
    }
}
