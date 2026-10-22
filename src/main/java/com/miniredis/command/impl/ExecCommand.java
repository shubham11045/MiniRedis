package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandFactory;
import com.miniredis.net.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ExecCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(ExecCommand.class);
    private final CommandFactory commandFactory;

    public ExecCommand(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
    }

    @Override
    public String execute(ClientSession session, String[] args) {
        if (!session.isInTransaction()) {
            return "-ERR EXEC without MULTI\r\n";
        }

        List<String> queue = new ArrayList<>(session.getCommandQueue());
        session.setInTransaction(false);
        session.getCommandQueue().clear();

        if (queue.isEmpty()) {
            return "*0\r\n"; // Empty transaction
        }

        ReentrantLock globalLock = session.getServerContext().getGlobalTransactionLock();
        globalLock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("*").append(queue.size()).append("\r\n");

            for (String rawCmd : queue) {
                // Execute command immediately without queuing it again since inTransaction is false
                String result = commandFactory.executeLine(session, rawCmd);
                
                // executeLine returns responses like "+OK\r\n" or "$3\r\nval\r\n".
                // In RESP, the transaction array just bundles these raw RESP values together.
                if (result != null) {
                    sb.append(result);
                } else {
                    sb.append("$-1\r\n");
                }
            }
            return sb.toString();
        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public boolean isMutating() {
        return false;
    }
}
