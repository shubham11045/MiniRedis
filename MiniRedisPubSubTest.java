
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MiniRedisPubSubTest {
    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        if (ok) passed++; else failed++;
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev = -1, c;
        while ((c = in.read()) != -1) {
            if (prev == '\r' && c == '\n') {
                byte[] bytes = b.toByteArray();
                return new String(bytes, 0, bytes.length - 1,
                        StandardCharsets.UTF_8);
            }
            b.write(c);
            prev = c;
        }
        throw new EOFException("Connection closed");
    }

    static String readResponse(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix == -1) throw new EOFException("No response received");
        String line = readLine(in);

        switch (prefix) {
            case '+': return "+" + line;
            case '-': return "-" + line;
            case ':': return ":" + line;
            case '$': {
                int len = Integer.parseInt(line);
                if (len == -1) return "(nil)";
                byte[] data = in.readNBytes(len);
                if (data.length != len) throw new EOFException("Incomplete bulk response");
                in.readNBytes(2);
                return "$" + new String(data, StandardCharsets.UTF_8);
            }
            case '*': {
                int count = Integer.parseInt(line);
                StringBuilder result = new StringBuilder("*" + count);
                for (int i = 0; i < count; i++)
                    result.append("\n").append(readResponse(in));
                return result.toString();
            }
            default:
                throw new IOException("Unexpected RESP prefix: " + (char) prefix);
        }
    }

    static String command(Socket socket, String... args) throws IOException {
        OutputStream out = socket.getOutputStream();
        StringBuilder request = new StringBuilder("*" + args.length + "\r\n");

        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            request.append("$").append(bytes.length).append("\r\n")
                    .append(arg).append("\r\n");
        }

        out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        return readResponse(socket.getInputStream());
    }

    public static void main(String[] args) throws Exception {
        try (
                Socket subscriber = new Socket("127.0.0.1", 6379);
                Socket publisher = new Socket("127.0.0.1", 6379)
        ) {
            subscriber.setSoTimeout(5000);
            publisher.setSoTimeout(5000);

            String channel = "test_channel";
            String message = "hello_pubsub";

            String sub = command(subscriber, "SUBSCRIBE", channel);
            check("SUBSCRIBE acknowledges channel",
                    sub.contains("subscribe") && sub.contains(channel));

            String pub1 = command(publisher, "PUBLISH", channel, message);
            check("PUBLISH reports one receiver", pub1.equals(":1"));

            String received = readResponse(subscriber.getInputStream());
            check("Subscriber receives published message",
                    received.contains("message")
                            && received.contains(channel)
                            && received.contains(message));

            String unsub = command(subscriber, "UNSUBSCRIBE", channel);
            check("UNSUBSCRIBE acknowledges removal",
                    unsub.contains("unsubscribe") && unsub.contains(channel));

            String pub2 = command(publisher, "PUBLISH", channel, "after_unsubscribe");
            check("Publish after unsubscribe reports zero receivers",
                    pub2.equals(":0"));
        }

        System.out.println("\n=== Pub/Sub Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }
}
