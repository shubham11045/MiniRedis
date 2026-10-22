
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AofWriteTest {
    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", 6379)) {
            socket.setSoTimeout(5000);
            // First run: use SET to store the test key and value in AOF.
// Restart the Redis server, then switch to GET to verify
// that the value was recovered from AOF persistence.
//
// String[] argsToSend = {
//     "SET", "aof_restart_test", "persistence_verified_2026"
// };


            String[] argsToSend = {"GET", "aof_restart_test"};

            StringBuilder request = new StringBuilder();
            request.append("*").append(argsToSend.length).append("\r\n");

            for (String arg : argsToSend) {
                int length = arg.getBytes(StandardCharsets.UTF_8).length;
                request.append("$").append(length).append("\r\n")
                        .append(arg).append("\r\n");
            }

            socket.getOutputStream().write(
                    request.toString().getBytes(StandardCharsets.UTF_8)
            );
            socket.getOutputStream().flush();

            byte[] response = new byte[256];
            int count = socket.getInputStream().read(response);

            System.out.println("Server response: " +
                    new String(response, 0, count, StandardCharsets.UTF_8)
                            .replace("\r\n", "\\r\\n"));
        }
    }
}
