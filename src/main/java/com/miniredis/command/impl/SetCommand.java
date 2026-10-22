package com.miniredis.command.impl;

import com.miniredis.command.Command;
import com.miniredis.command.CommandContext;
import com.miniredis.net.ClientSession;
import com.miniredis.storage.RedisValue;

public class SetCommand implements Command {

    @Override
    public String execute(ClientSession session, String[] args) {
        if (args.length < 2) {
            return "-ERR wrong number of arguments for 'set' command\r\n";
        }

        String key = args[0];
        String value = args[1];
        long ttlMs = -1L;

        if (args.length >= 4) {
            if (args[2].equalsIgnoreCase("EX")) {
                try {
                    long ttlSec = Long.parseLong(args[3]);
                    if (ttlSec <= 0) {
                        return "-ERR invalid expire time in 'set' command\r\n";
                    }
                    ttlMs = ttlSec * 1000L;
                } catch (NumberFormatException e) {
                    return "-ERR value is not an integer or out of range\r\n";
                }
            }
        }

        RedisValue redisValue = new RedisValue(value, ttlMs);
        session.getServerContext().getStore().put(key, redisValue);
        return "+OK\r\n";
    }

    @Override
    public boolean isMutating() {
        return true;
    }
}
