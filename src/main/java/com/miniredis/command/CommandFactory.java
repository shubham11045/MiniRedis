package com.miniredis.command;

import com.miniredis.command.impl.*;
import com.miniredis.net.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses raw input lines and executes the corresponding Command.
 */
public class CommandFactory {
    private static final Logger log = LoggerFactory.getLogger(CommandFactory.class);
    private final Map<String, Command> commandRegistry = new HashMap<>();

    public CommandFactory() {
        // Register commands
        commandRegistry.put("SET", new SetCommand());
        commandRegistry.put("GET", new GetCommand());
        commandRegistry.put("DEL", new DelCommand());
        commandRegistry.put("EXISTS", new ExistsCommand());
        commandRegistry.put("KEYS", new KeysCommand());
        commandRegistry.put("SIZE", new SizeCommand());
        commandRegistry.put("TTL", new TtlCommand());
        commandRegistry.put("EXPIRE", new ExpireCommand());
        commandRegistry.put("PING", new PingCommand());
        commandRegistry.put("INFO", new InfoCommand());
        commandRegistry.put("SAVE", new SaveCommand());
        
        // Transaction commands
        commandRegistry.put("MULTI", new MultiCommand());
        commandRegistry.put("EXEC", new ExecCommand(this));
        commandRegistry.put("DISCARD", new DiscardCommand());

        // Pub/Sub commands
        commandRegistry.put("SUBSCRIBE", new SubscribeCommand());
        commandRegistry.put("UNSUBSCRIBE", new UnsubscribeCommand());
        commandRegistry.put("PUBLISH", new PublishCommand());
    }

    /**
     * Parse and execute a raw input string.
     */

    public String executeLine(ClientSession session, String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        List<String> tokens = tokenize(rawLine.trim());
        if (tokens.isEmpty()) {
            return null;
        }

        String[] args = tokens.subList(1, tokens.size())
                .toArray(new String[0]);

        return executeCommand(
                session, tokens.get(0), args, rawLine
        );
    }

    public String executeArgs(ClientSession session, String[] allArgs) {
        if (allArgs == null || allArgs.length == 0) {
            return null;
        }

        String[] args = java.util.Arrays.copyOfRange(
                allArgs, 1, allArgs.length
        );

        return executeCommand(
                session, allArgs[0], args, String.join(" ", allArgs)
        );
    }

    private String executeCommand(
            ClientSession session,
            String command,
            String[] args,
            String rawLine) {

        String commandName = command.toUpperCase();
        Command cmd = commandRegistry.get(commandName);

        if (cmd == null) {
            return "-ERR unknown command '" + commandName + "'\r\n";
        }

        if (session.isInPubSubMode()
                && !commandName.equals("SUBSCRIBE")
                && !commandName.equals("UNSUBSCRIBE")
                && !commandName.equals("QUIT")) {
            return "-ERR only (P)SUBSCRIBE / (P)UNSUBSCRIBE / QUIT allowed in this context\r\n";
        }

        if (session.isInTransaction()
                && !commandName.equals("EXEC")
                && !commandName.equals("DISCARD")
                && !commandName.equals("MULTI")
                && !commandName.equals("QUIT")) {
            session.getCommandQueue().add(rawLine);
            return "+QUEUED\r\n";
        }

        String result;
        try {
            result = cmd.execute(session, args);
        } catch (Exception e) {
            log.error("Unhandled exception executing command: {}", rawLine, e);
            return "-ERR internal server error: " + e.getMessage() + "\r\n";
        }

        if (cmd.isMutating()
                && result != null
                && !result.startsWith("-ERR")
                && session.getServerContext().getAofManager() != null) {
            session.getServerContext().getAofManager()
                    .appendCommand(rawLine.trim());
        }

        return result;
    }


    /**
     * Tokenize raw line with support for quoted strings.
     * Example: SET message "Hello World" -> ["SET", "message", "Hello World"]
     */



    public static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}
