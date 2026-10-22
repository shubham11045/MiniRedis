package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;
import com.miniredis.storage.RedisValue;

public class GetCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length != 1) {
            return "-ERR wrong number of arguments for 'get' command\r\n";
        }

        String key = args[0];
        RedisValue redisValue = session.getServerContext().getStore().get(key);
        if (redisValue == null) {
            return "$-1\r\n";
        }
        String val = redisValue.getValue();
        return "$" + val.length() + "\r\n" + val + "\r\n";
    }
}
