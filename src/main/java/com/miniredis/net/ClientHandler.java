
package com.miniredis.net;

import com.miniredis.command.CommandContext;
import com.miniredis.command.CommandFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {

    private static final Logger log =
            LoggerFactory.getLogger(ClientHandler.class);

    private final Socket clientSocket;
    private final CommandContext context;
    private final CommandFactory commandFactory;

    public ClientHandler(
            Socket clientSocket,
            CommandContext context,
            CommandFactory commandFactory) {
        this.clientSocket = clientSocket;
        this.context = context;
        this.commandFactory = commandFactory;
    }

    @Override
    public void run() {
        String clientAddress =
                clientSocket.getRemoteSocketAddress().toString();

        log.info("Client connected: {}", clientAddress);

        if (context.getMetricsManager() != null) {
            context.getMetricsManager().clientConnected();
        }

        ClientSession session = null;

        try (Socket socket = clientSocket;
             BufferedInputStream input =
                     new BufferedInputStream(socket.getInputStream());
             OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(
                     output, true, StandardCharsets.UTF_8)) {

            session = new ClientSession(context, writer);

            while (true) {
                List<String> args = readCommand(input);

                if (args == null) {
                    break;
                }

                if (args.isEmpty()) {
                    continue;
                }

                String commandName = args.get(0);

                if (commandName.equalsIgnoreCase("QUIT")
                        || commandName.equalsIgnoreCase("EXIT")) {
                    writer.print("+OK\r\n");
                    writer.flush();
                    break;
                }

                String response = commandFactory.executeArgs(
                        session,
                        args.toArray(new String[0])
                );
                if (response != null && !response.isEmpty()) {
                    writer.print(response);
                    writer.flush();
                }
            }

        } catch (IOException e) {
            log.debug("Client connection ended: {}", clientAddress);
        } catch (Exception e) {
            log.error("Client handler failed: {}", clientAddress, e);
        } finally {
            if (session != null && context.getPubSubManager() != null) {
                context.getPubSubManager().unsubscribeAll(session);
            }

            if (context.getMetricsManager() != null) {
                context.getMetricsManager().clientDisconnected();
            }

            log.info("Client disconnected: {}", clientAddress);
        }
    }

    private List<String> readCommand(InputStream input) throws IOException {
        int first = input.read();

        if (first == -1) {
            return null;
        }

        // Standard RESP command: array of bulk strings.
        if (first == '*') {
            int count = Integer.parseInt(readProtocolLine(input));

            if (count < 1 || count > 1024) {
                throw new IOException("Invalid RESP array length");
            }

            List<String> args = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                if (input.read() != '$') {
                    throw new IOException(
                            "Expected RESP bulk string");
                }

                int length =
                        Integer.parseInt(readProtocolLine(input));

                if (length < 0 || length > 16 * 1024 * 1024) {
                    throw new IOException(
                            "Invalid RESP bulk string length");
                }

                byte[] bytes = input.readNBytes(length);

                if (bytes.length != length) {
                    throw new IOException(
                            "Incomplete RESP bulk string");
                }

                if (input.read() != '\r' || input.read() != '\n') {
                    throw new IOException(
                            "Invalid RESP bulk string terminator");
                }

                args.add(new String(bytes, StandardCharsets.UTF_8));
            }

            return args;
        }

        // Existing plain-text command support.
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        line.write(first);

        int ch;
        while ((ch = input.read()) != -1 && ch != '\n') {
            if (ch != '\r') {
                line.write(ch);
            }
        }

        return CommandFactory.tokenize(
                line.toString(StandardCharsets.UTF_8).trim());
    }

    private String readProtocolLine(InputStream input)
            throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        while (true) {
            int ch = input.read();

            if (ch == -1) {
                throw new IOException(
                        "Unexpected end of protocol input");
            }

            if (ch == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("Invalid line ending");
                }

                return line.toString(StandardCharsets.US_ASCII);
            }

            line.write(ch);
        }
    }
}
