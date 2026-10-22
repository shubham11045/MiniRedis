
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MiniRedisLoadTest {

    private static final byte[] PING =
            "*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII);

    private static final class Result {
        final List<Long> latencies = new ArrayList<>();
        int errors;
    }

    public static void main(String[] args) throws Exception {
        int clients = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int opsPerClient = args.length > 1
                ? Integer.parseInt(args[1]) : 5000;

        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = new ArrayList<>();

        for (int i = 0; i < clients; i++) {
            futures.add(pool.submit(() -> {
                Result result = new Result();

                try (Socket socket = new Socket()) {
                    socket.connect(
                            new InetSocketAddress("127.0.0.1", 6379), 3000);
                    socket.setSoTimeout(5000);

                    BufferedInputStream in =
                            new BufferedInputStream(socket.getInputStream());
                    OutputStream out = socket.getOutputStream();

                    ready.countDown();
                    start.await();

                    for (int j = 0; j < opsPerClient; j++) {
                        long before = System.nanoTime();

                        try {
                            out.write(PING);
                            out.flush();

                            String response = readLine(in);

                            if (!"+PONG".equals(response)) {
                                result.errors++;
                            } else {
                                result.latencies.add(
                                        System.nanoTime() - before);
                            }
                        } catch (IOException e) {
                            result.errors++;
                            break;
                        }
                    }
                } catch (Exception e) {
                    result.errors++;
                    ready.countDown();
                }

                return result;
            }));
        }

        ready.await();
        long begin = System.nanoTime();
        start.countDown();

        List<Long> allLatencies = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();

        for (Future<Result> future : futures) {
            Result result = future.get();
            allLatencies.addAll(result.latencies);
            errors.addAndGet(result.errors);
        }

        long elapsed = System.nanoTime() - begin;
        pool.shutdown();

        Collections.sort(allLatencies);

        double seconds = elapsed / 1_000_000_000.0;
        long successful = allLatencies.size();

        System.out.println("\n=== MiniRedis Load Test ===");
        System.out.println("Clients: " + clients);
        System.out.println("Target operations: "
                + ((long) clients * opsPerClient));
        System.out.println("Successful PINGs: " + successful);
        System.out.println("Errors: " + errors.get());
        System.out.printf("Duration: %.2f seconds%n", seconds);
        System.out.printf("Throughput: %.0f ops/sec%n",
                successful / seconds);

        if (!allLatencies.isEmpty()) {
            System.out.printf("p50 latency: %.3f ms%n",
                    percentile(allLatencies, 50) / 1_000_000.0);
            System.out.printf("p95 latency: %.3f ms%n",
                    percentile(allLatencies, 95) / 1_000_000.0);
            System.out.printf("p99 latency: %.3f ms%n",
                    percentile(allLatencies, 99) / 1_000_000.0);
        }

        System.out.println("===========================\n");
    }

    private static String readLine(BufferedInputStream in)
            throws IOException {
        StringBuilder line = new StringBuilder();

        while (true) {
            int ch = in.read();

            if (ch == -1) {
                throw new IOException("Server closed connection");
            }

            if (ch == '\r') {
                if (in.read() != '\n') {
                    throw new IOException("Invalid response framing");
                }
                return line.toString();
            }

            line.append((char) ch);
        }
    }

    private static long percentile(List<Long> sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
