package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;

public class InfoCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        String info;
        if (session.getServerContext().getMetricsManager() != null) {
            info = session.getServerContext().getMetricsManager().toFormattedString();
        } else {
            info = "# Server Info\r\nstatus: active\r\n";
        }
        return "$" + info.length() + "\r\n" + info + "\r\n";
    }
}
