package com.miniredis.persistence;

import com.miniredis.storage.KeyValueStore;
import com.miniredis.storage.RedisValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
/**
 * Append-Only File (AOF) Persistence Manager.
 * Log mutating commands sequentially to disk and replays them on startup to reconstruct state.
 */
public class AofPersistenceManager {

    private static final Logger log = LoggerFactory.getLogger(AofPersistenceManager.class);

    private final File aofFile;
    private final boolean enabled;
    private final ReentrantLock fileLock = new ReentrantLock();
    private BufferedWriter writer;

    public AofPersistenceManager(String filePath, boolean enabled) {
        this.aofFile = new File(filePath);
        this.enabled = enabled;

        if (enabled) {
            initWriter();
        }
    }

    private void initWriter() {
        try {
            File parent = aofFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            this.writer = new BufferedWriter(new FileWriter(aofFile, StandardCharsets.UTF_8, true));
            log.info("AOF Persistence enabled. Log file: {}", aofFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize AOF writer", e);
            throw new RuntimeException("Could not initialize AOF file", e);
        }
    }

    private volatile boolean replaying = false;

    /**
     * Log a mutating command to AOF.
     */
    public void appendCommand(String commandLine) {
        if (!enabled || replaying) return;

        fileLock.lock();
        try {
            writer.write(commandLine);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to append command to AOF: {}", commandLine, e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Replay command history on server startup.
     * Passes raw command lines to command executor consumer.
     */
    public int replay(Consumer<String> commandExecutor) {
        if (!enabled || !aofFile.exists() || aofFile.length() == 0) {
            log.info("No AOF file found or file empty. Starting clean.");
            return 0;
        }

        int replayedCount = 0;
        fileLock.lock();
        replaying = true;
        try (BufferedReader reader = new BufferedReader(new FileReader(aofFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    commandExecutor.accept(line);
                    replayedCount++;
                }
            }
            log.info("Successfully replayed {} commands from AOF log file", replayedCount);
        } catch (IOException e) {
            log.error("Error replaying AOF file", e);
        } finally {
            replaying = false;
            fileLock.unlock();
        }
        return replayedCount;
    }

    /**
     * Compacts AOF file by taking a snapshot of current non-expired KeyValueStore state.
     */
    public synchronized void rewriteAof(KeyValueStore store) {
        if (!enabled) return;

        fileLock.lock();
        try {
            if (writer != null) {
                writer.close();
            }

            File tempFile = new File(aofFile.getAbsolutePath() + ".tmp");
            try (BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempFile, StandardCharsets.UTF_8))) {
                for (Map.Entry<String, RedisValue> entry : store.getRawStore().entrySet()) {
                    String key = entry.getKey();
                    RedisValue val = entry.getValue();

                    if (!val.isExpired()) {
                        long remainingTtl = val.getRemainingTtlSeconds();
                        if (remainingTtl > 0) {
                            tempWriter.write(String.format("SET %s %s EX %d", key, val.getValue(), remainingTtl));
                        } else {
                            tempWriter.write(String.format("SET %s %s", key, val.getValue()));
                        }
                        tempWriter.newLine();
                    }
                }
                tempWriter.flush();
            }

            // Replace original file with compacted AOF
            Files.move(
                    tempFile.toPath(),
                    aofFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            log.info("AOF compact rewrite completed successfully");

            initWriter();
        } catch (IOException e) {
            log.error("Error performing AOF rewrite", e);
        } finally {
            fileLock.unlock();
        }
    }

    public void close() {
        if (!enabled) return;
        fileLock.lock();
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            log.error("Error closing AOF writer", e);
        } finally {
            fileLock.unlock();
        }
    }
}
