package com.lld.hld.urlshortener.service;

import com.lld.hld.urlshortener.repository.KGSRepository;

import java.security.SecureRandom;
import java.util.concurrent.*;

/**
 * Key Generation Service (KGS)
 *
 * Responsibility: Pre-generate random, unique Base62 short codes and hand
 * them out to the Write Service on demand — without any collision risk.
 *
 * Design Decisions:
 * - Keys are generated randomly (not sequential) → not guessable
 * - Keys are marked as "used" in DB the moment they enter the memory buffer
 * → If two KGS instances run, they can never hand out the same key
 * - Buffer is refilled automatically in the background when it runs low
 */
public class KeyGenerationService {

    // Base62 alphabet: digits + lowercase + uppercase = 62 chars
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6; // 62^6 = 56 Billion unique codes
    private static final int BUFFER_CAPACITY = 10_000;
    private static final int REFILL_THRESHOLD = 1_000; // Refill when buffer < 1000

    private final BlockingQueue<String> keyBuffer; // Thread-safe in-memory pool
    private final SecureRandom random;
    private final KGSRepository kgsRepository;
    private final ScheduledExecutorService scheduler;

    public KeyGenerationService(KGSRepository kgsRepository) {
        this.kgsRepository = kgsRepository;
        this.keyBuffer = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        this.random = new SecureRandom();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Load initial batch of keys into memory buffer on startup
        refillBuffer();

        // Start background watcher: check every 5 seconds if refill is needed
        startRefillWatcher();
    }

    /**
     * Called by WriteService to get a unique short code.
     * This is a BLOCKING call only if the buffer is empty (extremely rare).
     * Normally returns in nanoseconds from the in-memory buffer.
     */
    public String getNextKey() throws InterruptedException {
        return keyBuffer.take(); // Atomic pop — thread-safe
    }

    // ── Private Methods ──────────────────────────────────────────────────────

    /**
     * Generate a single random 6-character Base62 code.
     *
     * How it works:
     * For each of 6 positions → pick random index 0-61 → map to ALPHABET char
     *
     * Example:
     * random indices [33, 46, 9, 41, 52, 2]
     * → ALPHABET chars ['x', 'K', '9', 'p', 'Q', '2']
     * → "xK9pQ2"
     */
    private String generateRandomCode() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }

    /**
     * Fetch a batch of unique keys:
     * 1. Generate random code
     * 2. Try INSERT into KGS DB (unique constraint protects against duplicates)
     * 3. If saved → mark as used → add to buffer
     * 4. If collision → retry with a new random code (very rare)
     */
    private void refillBuffer() {
        int needed = BUFFER_CAPACITY - keyBuffer.size();
        int added = 0;

        System.out.printf("[KGS] Refilling buffer: need %d keys%n", needed);

        while (added < needed) {
            String code = generateRandomCode();

            // saveIfUnique → simulates: INSERT IGNORE INTO kgs_keys (key) VALUES (?)
            // Returns false if the code already exists in DB (collision)
            boolean saved = kgsRepository.saveIfUnique(code);

            if (saved) {
                // Mark as used in DB BEFORE adding to buffer
                // → Prevents another KGS server from handing out the same key
                kgsRepository.markAsUsed(code);
                keyBuffer.offer(code);
                added++;
            }
            // If collision → loop again and generate a fresh random code
        }

        System.out.printf("[KGS] Buffer refilled. Total keys issued: %d%n",
                kgsRepository.getTotalKeysIssued());
    }

    /** Background task: auto-refill when buffer gets low */
    private void startRefillWatcher() {
        scheduler.scheduleAtFixedRate(() -> {
            if (keyBuffer.size() < REFILL_THRESHOLD) {
                System.out.println("[KGS] Buffer low → triggering refill...");
                refillBuffer();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
