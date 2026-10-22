package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class PingCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length > 0) {
            return "+" + args[0] + "\r\n";
        }
        return "+PONG\r\n";
    }
}
