
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MiniRedisFunctionalTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        passed = 0;
        failed = 0;

        try (Socket socket = new Socket("127.0.0.1", 6379)) {
            socket.setSoTimeout(5000);

            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            // Basic functional tests
            test(output, input, "PING", "+PONG", "PING");

            test(output, input, "SET", "+OK",
                    "SET", "test_key", "hello123");

            test(output, input, "GET", "hello123",
                    "GET", "test_key");

            test(output, input, "EXISTS existing key", ":1",
                    "EXISTS", "test_key");

            test(output, input, "DEL", ":1",
                    "DEL", "test_key");

            test(output, input, "EXISTS deleted key", ":0",
                    "EXISTS", "test_key");

            // Values containing spaces
            test(output, input, "SET value with spaces", "+OK",
                    "SET", "spaced_key", "Shubham Gupta");

            test(output, input, "GET value with spaces", "Shubham Gupta",
                    "GET", "spaced_key");

            test(output, input, "Cleanup spaced key", ":1",
                    "DEL", "spaced_key");

            // TTL and expiration tests
            test(output, input, "SET TTL key", "+OK",
                    "SET", "ttl_key", "temporary");

            test(output, input, "EXPIRE key after 2 seconds", ":1",
                    "EXPIRE", "ttl_key", "2");

            testTtlRange(output, input, "TTL remaining time",
                    "ttl_key", 0, 2);

            test(output, input, "TTL key exists before expiry", ":1",
                    "EXISTS", "ttl_key");

            System.out.println("\nWaiting 2.5 seconds for key expiration...");
            Thread.sleep(2500);

            test(output, input, "TTL key expires", "(nil)",
                    "GET", "ttl_key");

            test(output, input, "EXISTS expired key", ":0",
                    "EXISTS", "ttl_key");

            // Persistent key tests
            test(output, input, "SET persistent key", "+OK",
                    "SET", "persistent_key", "permanent");

            test(output, input, "Persistent key has no expiry", ":-1",
                    "TTL", "persistent_key");

            test(output, input, "Persistent key remains available", "permanent",
                    "GET", "persistent_key");

            test(output, input, "Cleanup persistent key", ":1",
                    "DEL", "persistent_key");

        } catch (Exception e) {
            System.err.println("Test error: " + e.getMessage());
            throw e;
        }

        System.out.println("\n=== Functional Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("===============================");
    }

    private static void test(
            OutputStream output,
            InputStream input,
            String name,
            String expected,
            String... args) throws IOException {

        sendCommand(output, args);
        String actual = readResponse(input);

        if (expected.equals(actual)) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name
                    + " | expected: " + expected
                    + " | actual: " + actual);
        }
    }

    private static void testTtlRange(
            OutputStream output,
            InputStream input,
            String name,
            String key,
            int min,
            int max) throws IOException {

        sendCommand(output, "TTL", key);
        String response = readResponse(input);

        try {
            if (response.startsWith(":")) {
                int ttl = Integer.parseInt(response.substring(1));

                if (ttl >= min && ttl <= max) {
                    passed++;
                    System.out.println("[PASS] " + name
                            + " (" + ttl + " seconds)");
                    return;
                }
            }

            failed++;
            System.out.println("[FAIL] " + name
                    + " | expected TTL between " + min + " and " + max
                    + " | actual: " + response);

        } catch (NumberFormatException e) {
            failed++;
            System.out.println("[FAIL] " + name
                    + " | invalid TTL response: " + response);
        }
    }

    private static void sendCommand(
            OutputStream output,
            String... args) throws IOException {

        StringBuilder header = new StringBuilder();
        header.append("*").append(args.length).append("\r\n");

        output.write(header.toString().getBytes(StandardCharsets.UTF_8));

        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);

            String bulkHeader = "$" + bytes.length + "\r\n";
            output.write(bulkHeader.getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write('\r');
            output.write('\n');
        }

        output.flush();
    }

    private static String readResponse(InputStream input)
            throws IOException {

        String line = readLine(input);

        if (line.startsWith("$")) {
            int length = Integer.parseInt(line.substring(1));

            if (length == -1) {
                return "(nil)";
            }

            if (length < -1) {
                throw new IOException("Invalid bulk response length");
            }

            byte[] bytes = input.readNBytes(length);

            if (bytes.length != length) {
                throw new IOException("Incomplete bulk response");
            }

            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("Invalid bulk response framing");
            }

            return new String(bytes, StandardCharsets.UTF_8);
        }

        return line;
    }

    private static String readLine(InputStream input)
            throws IOException {

        StringBuilder line = new StringBuilder();

        while (true) {
            int ch = input.read();

            if (ch == -1) {
                throw new IOException("Server closed connection");
            }

            if (ch == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("Invalid response framing");
                }

                return line.toString();
            }

            line.append((char) ch);
        }
    }
}
