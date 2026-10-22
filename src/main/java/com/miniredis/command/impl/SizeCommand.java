package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class SizeCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        return ":" + session.getServerContext().getStore().size() + "\r\n";
    }
}
