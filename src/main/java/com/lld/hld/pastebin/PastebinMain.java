package com.lld.hld.pastebin;

import com.lld.hld.pastebin.model.PasteMetadata;
import com.lld.hld.pastebin.model.PasteMetadata.Visibility;
import com.lld.hld.pastebin.repository.PasteMetadataRepository;
import com.lld.hld.pastebin.service.*;
import com.lld.hld.urlshortener.repository.KGSRepository;
import com.lld.hld.urlshortener.service.KeyGenerationService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * ═══════════════════════════════════════════════════════════════════
 * Pastebin — Demo Runner
 *
 * Demonstrates the full Pastebin system including:
 *
 * 1. Create a paste (KGS assigns key, content uploaded to S3 with GZIP)
 * 2. Read paste — cache MISS (fetches from S3)
 * 3. Read paste — cache HIT (served from L2 content cache)
 * 4. Create paste with custom alias
 * 5. Create a password-protected paste
 * 6. Read protected paste — no password → 403
 * 7. Read protected paste — correct password → 200
 * 8. Create paste with past expiry → read returns 410 Gone
 * 9. Delete a paste → read returns 410 Gone
 * 10. Read non-existent paste → 404
 * 11. Large paste (skips L2 content cache → "served by CDN")
 * 12. Cleanup service runs and removes expired pastes
 * ═══════════════════════════════════════════════════════════════════
 */
public class PastebinMain {

    public static void main(String[] args) throws InterruptedException {

        // ── Wire up all components ───────────────────────────────────────────
        // Reusing KGS from URL Shortener (shared service)
        KGSRepository kgsRepo = new KGSRepository();
        KeyGenerationService kgs = new KeyGenerationService(kgsRepo);
        PasteMetadataRepository metaRepo = new PasteMetadataRepository();
        ObjectStorageService s3 = new ObjectStorageService();
        PasteCacheService cache = new PasteCacheService();
        PasteWriteService writer = new PasteWriteService(kgs, metaRepo, s3, cache);
        PasteReadService reader = new PasteReadService(metaRepo, s3, cache);
        CleanupService cleanup = new CleanupService(metaRepo, s3, cache);

        Thread.sleep(200); // Let KGS fill buffer

        System.out.println("\n" + "═".repeat(65));
        System.out.println("  PASTEBIN — System Design Demo");
        System.out.println("═".repeat(65));

        // ════════════════════════════════════════════════════════════
        // SCENARIO 1: Create a simple paste
        // ════════════════════════════════════════════════════════════
        printHeader("1. Create Paste (KGS key + S3 upload with GZIP)");
        String pythonCode = "def fibonacci(n):\n" +
                "    if n <= 1:\n" +
                "        return n\n" +
                "    a, b = 0, 1\n" +
                "    for _ in range(2, n + 1):\n" +
                "        a, b = b, a + b\n" +
                "    return b\n" +
                "\n" +
                "print(fibonacci(10))  # Output: 55\n";

        String pasteUrl1 = writer.createPaste(
                pythonCode, "Fibonacci in Python", "python",
                null, "user_001", Visibility.PUBLIC, null, null);
        String key1 = pasteUrl1.replace("https://paste.ly/", "");
        System.out.println("   Paste URL: " + pasteUrl1);

        // ════════════════════════════════════════════════════════════
        // SCENARIO 2: Read paste — cache MISS → S3
        // ════════════════════════════════════════════════════════════
        printHeader("2. Read Paste — Cache MISS (first read from S3)");
        cache.invalidate(key1); // Simulate cold start
        PasteReadService.ReadResult r1 = reader.readPaste(key1, null);
        System.out.println("   Status  : " + r1.getStatus());
        System.out.println("   Title   : " + r1.getMetadata().getTitle());
        System.out.println("   Syntax  : " + r1.getMetadata().getSyntax());
        System.out.println("   Content :\n" + indent(r1.getContent()));

        // ════════════════════════════════════════════════════════════
        // SCENARIO 3: Read paste — cache HIT
        // ════════════════════════════════════════════════════════════
        printHeader("3. Read Paste — Cache HIT (served from Redis L2)");
        PasteReadService.ReadResult r2 = reader.readPaste(key1, null);
        System.out.println("   Status  : " + r2.getStatus());
        System.out.println("   Views   : " + metaRepo.findByPasteKey(key1).getViewCount());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 4: Custom alias paste
        // ════════════════════════════════════════════════════════════
        printHeader("4. Custom Alias Paste");
        String pasteUrlCustom = writer.createPaste(
                "SELECT * FROM users WHERE active = true;",
                "Active Users Query", "sql",
                "sql-query", "user_002", Visibility.UNLISTED, null, null);
        System.out.println("   Paste URL: " + pasteUrlCustom);

        // ════════════════════════════════════════════════════════════
        // SCENARIO 5: Password-protected paste
        // ════════════════════════════════════════════════════════════
        printHeader("5. Create Password-Protected Paste");
        String secretUrl = writer.createPaste(
                "API_KEY=sk_live_abc123xyz\nDB_PASSWORD=super_secret_42",
                "My Secrets", "plaintext",
                null, "user_001", Visibility.PRIVATE, "mypassword123", null);
        String secretKey = secretUrl.replace("https://paste.ly/", "");
        System.out.println("   Paste URL: " + secretUrl);

        // ════════════════════════════════════════════════════════════
        // SCENARIO 6: Read protected paste — NO password → 403
        // ════════════════════════════════════════════════════════════
        printHeader("6. Read Protected Paste — No Password → 403 FORBIDDEN");
        PasteReadService.ReadResult rNoPass = reader.readPaste(secretKey, null);
        System.out.println("   Status : " + rNoPass.getStatus());
        System.out.println("   Message: " + rNoPass.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 7: Read protected paste — correct password → 200
        // ════════════════════════════════════════════════════════════
        printHeader("7. Read Protected Paste — Correct Password → 200 FOUND");
        PasteReadService.ReadResult rWithPass = reader.readPaste(secretKey, "mypassword123");
        System.out.println("   Status : " + rWithPass.getStatus());
        System.out.println("   Content:\n" + indent(rWithPass.getContent()));

        // ════════════════════════════════════════════════════════════
        // SCENARIO 8: Expired paste → 410 Gone
        // ════════════════════════════════════════════════════════════
        printHeader("8. Expired Paste → 410 GONE");
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        String expiredUrl = writer.createPaste(
                "This paste has already expired!", "Expired Paste", "plaintext",
                null, "user_003", Visibility.PUBLIC, null, pastExpiry);
        String expiredKey = expiredUrl.replace("https://paste.ly/", "");
        cache.invalidate(expiredKey); // Force DB check
        PasteReadService.ReadResult rExpired = reader.readPaste(expiredKey, null);
        System.out.println("   Status : " + rExpired.getStatus());
        System.out.println("   Message: " + rExpired.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 9: Delete a paste → 410 Gone
        // ════════════════════════════════════════════════════════════
        printHeader("9. Delete Paste → 410 GONE on Read");
        writer.deletePaste("sql-query");
        PasteReadService.ReadResult rDeleted = reader.readPaste("sql-query", null);
        System.out.println("   Status : " + rDeleted.getStatus());
        System.out.println("   Message: " + rDeleted.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 10: Non-existent paste → 404
        // ════════════════════════════════════════════════════════════
        printHeader("10. Non-existent Paste → 404 NOT FOUND");
        PasteReadService.ReadResult rNotFound = reader.readPaste("XXXXXX", null);
        System.out.println("   Status : " + rNotFound.getStatus());
        System.out.println("   Message: " + rNotFound.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 11: Large paste — skips L2 content cache
        // ════════════════════════════════════════════════════════════
        printHeader("11. Large Paste (> 256 KB → skips Redis, served by CDN/S3)");
        StringBuilder largePaste = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largePaste.append("Line ").append(i)
                    .append(": This is a long line of content to simulate a large paste file. ")
                    .append("More data follows here to make this a realistic test.\n");
        }
        String largeUrl = writer.createPaste(
                largePaste.toString(), "Large Log File", "plaintext",
                null, "user_004", Visibility.PUBLIC, null, null);
        String largeKey = largeUrl.replace("https://paste.ly/", "");
        System.out.println("   Size: " + largePaste.length() + " chars");
        System.out.println("   Note: L2 content cache was SKIPPED (too large → use CDN in prod)");

        // ════════════════════════════════════════════════════════════
        // SCENARIO 12: Cleanup Service
        // ════════════════════════════════════════════════════════════
        printHeader("12. Cleanup Service — Remove Expired Pastes");
        int cleaned = cleanup.cleanupExpiredPastes();
        System.out.println("   Cleaned: " + cleaned + " expired pastes");

        // ════════════════════════════════════════════════════════════
        // Summary
        // ════════════════════════════════════════════════════════════
        printHeader("Summary");
        System.out.println("   S3 objects remaining   : " + s3.objectCount());
        System.out.println("   Metadata cache entries : " + cache.metaCacheSize());
        System.out.println("   Content cache entries  : " + cache.contentCacheSize());
        System.out.println("   Total KGS keys issued  : " + kgsRepo.getTotalKeysIssued());

        kgs.shutdown();

        System.out.println("\n" + "═".repeat(65));
        System.out.println("  Demo complete.");
        System.out.println("═".repeat(65) + "\n");
    }

    private static void printHeader(String title) {
        System.out.println("\n── " + title + " " + "─".repeat(Math.max(0, 60 - title.length())));
    }

    private static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            sb.append("     ").append(line).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
