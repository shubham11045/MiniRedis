
import com.miniredis.storage.EvictionPolicy;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.storage.RedisValue;

public class MiniRedisLRUTest {

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

    public static void main(String[] args) {
        KeyValueStore store =
                new KeyValueStore(350, EvictionPolicy.LRU);

        store.put("A", new RedisValue("one"));
        store.put("B", new RedisValue("two"));

        check("Both initial keys fit",
                store.get("A") != null && store.get("B") != null);

        // A is now more recently used than B.
        store.get("A");

        // This entry is large enough to force eviction.
        store.put("C", new RedisValue("x".repeat(60)));

        check("Recently accessed key A survives",
                store.get("A") != null);

        check("New key C survives",
                store.get("C") != null);

        check("Least recently used key B is evicted",
                store.get("B") == null);

        check("Store remains within configured memory limit",
                store.getCurrentMemoryBytes() <= 350);

        System.out.println("\n=== LRU Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }
}
