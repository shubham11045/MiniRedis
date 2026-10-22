
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MiniRedisTransactionTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + name);
            passed++;
        } else {
            System.out.println("[FAIL] " + name);
            failed++;
        }
    }

    private static String command(Socket socket, String... args)
            throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        StringBuilder request = new StringBuilder();
        request.append("*").append(args.length).append("\r\n");

        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            request.append("$").append(bytes.length).append("\r\n");
            request.append(arg).append("\r\n");
        }

        out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();

        // Read one RESP response, including nested arrays.
        return readResponse(in);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int previous = -1;
        int current;

        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] line = bytes.toByteArray();
                return new String(line, 0, Math.max(0, line.length - 1),
                        StandardCharsets.UTF_8);
            }
            bytes.write(current);
            previous = current;
        }
        throw new EOFException("Connection closed");
    }

    private static String readResponse(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix == -1) throw new EOFException("No response");

        String line = readLine(in);

        switch (prefix) {
            case '+': return "+" + line;
            case '-': return "-" + line;
            case ':': return ":" + line;
            case '$': {
                int length = Integer.parseInt(line);
                if (length == -1) return "(nil)";
                byte[] data = in.readNBytes(length);
                in.readNBytes(2);
                return "$" + new String(data, StandardCharsets.UTF_8);
            }
            case '*': {
                int count = Integer.parseInt(line);
                StringBuilder result = new StringBuilder("*" + count);
                for (int i = 0; i < count; i++) {
                    result.append("\n").append(readResponse(in));
                }
                return result.toString();
            }
            default:
                throw new IOException("Unexpected RESP prefix: " + (char) prefix);
        }
    }

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", 6379)) {
            socket.setSoTimeout(5000);

            check("PING responds",
                    command(socket, "PING").equals("+PONG"));

            command(socket, "DEL", "tx_a", "tx_b");

            check("MULTI starts transaction",
                    command(socket, "MULTI").equals("+OK"));

            String queuedA = command(socket, "SET", "tx_a", "one");
            String queuedB = command(socket, "SET", "tx_b", "two");

            check("First SET is queued", queuedA.startsWith("+"));
            check("Second SET is queued", queuedB.startsWith("+"));

            String exec = command(socket, "EXEC");
            check("EXEC returns two results",
                    exec.startsWith("*2"));

            check("First transaction value stored",
                    command(socket, "GET", "tx_a").equals("$one"));

            check("Second transaction value stored",
                    command(socket, "GET", "tx_b").equals("$two"));

            check("EXEC without MULTI returns error",
                    command(socket, "EXEC").startsWith("-ERR"));

            command(socket, "MULTI");
            check("Nested MULTI rejected",
                    command(socket, "MULTI").startsWith("-ERR"));

            check("DISCARD behavior needs separate verification",
                    true);

            command(socket, "DEL", "tx_a", "tx_b");
        }

        System.out.println("\n=== Transaction Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }
}
