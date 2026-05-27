package com.lld.hld.urlshortener;

import com.lld.hld.urlshortener.exception.AliasAlreadyTakenException;
import com.lld.hld.urlshortener.exception.InvalidUrlException;
import com.lld.hld.urlshortener.model.UrlMapping;
import com.lld.hld.urlshortener.repository.KGSRepository;
import com.lld.hld.urlshortener.repository.UrlMappingRepository;
import com.lld.hld.urlshortener.service.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * ═══════════════════════════════════════════════════════════════════
 * URL Shortener — Demo Runner
 *
 * Wires all components together and demonstrates the full flow:
 *
 * 1. Shorten a URL (KGS assigns a random code)
 * 2. Redirect — cache MISS (first request hits DB)
 * 3. Redirect — cache HIT (second request served from cache)
 * 4. Shorten same URL again (dedup returns existing code)
 * 5. Shorten with a custom alias
 * 6. Try a duplicate custom alias → exception
 * 7. Delete a URL and try to redirect → 410 Gone
 * 8. Redirect to non-existent code → 404 Not Found
 * 9. Shorten URL with expiry in the past → 410 Gone on redirect
 * 10. Invalid URL → exception
 * ═══════════════════════════════════════════════════════════════════
 */
public class UrlShortenerMain {

    public static void main(String[] args) throws InterruptedException {

        // ── Wire up all components (Dependency Injection by hand) ────────────
        KGSRepository kgsRepo = new KGSRepository();
        UrlMappingRepository urlRepo = new UrlMappingRepository();
        CacheService cache = new CacheService();
        AnalyticsPublisher analytics = new AnalyticsPublisher();
        KeyGenerationService kgs = new KeyGenerationService(kgsRepo);
        UrlWriteService writer = new UrlWriteService(kgs, urlRepo, cache);
        UrlReadService reader = new UrlReadService(urlRepo, cache, analytics);

        // Give KGS a moment to pre-fill its buffer
        Thread.sleep(200);

        System.out.println("\n" + "═".repeat(60));

        // ════════════════════════════════════════════════════════════
        // SCENARIO 1: Shorten a URL (random KGS code)
        // ════════════════════════════════════════════════════════════
        printHeader("1. Shorten a URL");
        String longUrl1 = "https://www.example.com/articles/hld-system-design-2024";
        String shortUrl1 = writer.shorten(longUrl1, null, "user_001", null);
        System.out.println("   Long URL  : " + longUrl1);
        System.out.println("   Short URL : " + shortUrl1);

        // Extract the code from the returned URL for use in redirects
        String code1 = shortUrl1.replace("https://short.ly/", "");

        // ════════════════════════════════════════════════════════════
        // SCENARIO 2: Redirect — first request (cache MISS → DB)
        // ════════════════════════════════════════════════════════════
        printHeader("2. Redirect — Cache MISS (first visit)");
        // Manually remove from cache to simulate a cold-start miss
        cache.invalidate(code1);
        UrlReadService.RedirectResult result1 = reader.redirect(code1, "192.168.1.1", "Mozilla/5.0 Desktop");
        System.out.println("   Status  : " + result1.getStatus());
        System.out.println("   Redirect: " + result1.getUrl());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 3: Redirect — second request (cache HIT)
        // ════════════════════════════════════════════════════════════
        printHeader("3. Redirect — Cache HIT (second visit)");
        UrlReadService.RedirectResult result2 = reader.redirect(code1, "10.0.0.5", "Mozilla/5.0 Mobile");
        System.out.println("   Status  : " + result2.getStatus());
        System.out.println("   Redirect: " + result2.getUrl());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 4: Dedup — shorten same URL again (same user)
        // ════════════════════════════════════════════════════════════
        printHeader("4. Dedup — same URL by same user");
        String shortUrl1Again = writer.shorten(longUrl1, null, "user_001", null);
        System.out.println("   Returned same code? " + shortUrl1Again.equals(shortUrl1));
        System.out.println("   Short URL: " + shortUrl1Again);

        // ════════════════════════════════════════════════════════════
        // SCENARIO 5: Custom alias
        // ════════════════════════════════════════════════════════════
        printHeader("5. Custom alias");
        String shortUrlCustom = writer.shorten(
                "https://www.google.com", "g-search", "user_002", null);
        System.out.println("   Short URL: " + shortUrlCustom);
        UrlReadService.RedirectResult resultCustom = reader.redirect("g-search", "1.2.3.4", "curl/7.0");
        System.out.println("   Redirect : " + resultCustom);

        // ════════════════════════════════════════════════════════════
        // SCENARIO 6: Duplicate custom alias → exception
        // ════════════════════════════════════════════════════════════
        printHeader("6. Duplicate custom alias → AliasAlreadyTakenException");
        try {
            writer.shorten("https://www.bing.com", "g-search", "user_003", null);
        } catch (AliasAlreadyTakenException e) {
            System.out.println("   Caught: " + e.getMessage());
        }

        // ════════════════════════════════════════════════════════════
        // SCENARIO 7: Delete a URL → 410 Gone on redirect
        // ════════════════════════════════════════════════════════════
        printHeader("7. Delete URL → 410 Gone");
        writer.delete(code1);
        UrlReadService.RedirectResult resultDeleted = reader.redirect(code1, "5.6.7.8", "Agent");
        System.out.println("   Status : " + resultDeleted.getStatus()); // GONE
        System.out.println("   Message: " + resultDeleted.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 8: Non-existent short code → 404 Not Found
        // ════════════════════════════════════════════════════════════
        printHeader("8. Non-existent code → 404 Not Found");
        UrlReadService.RedirectResult resultNotFound = reader.redirect("XXXXXX", "9.9.9.9", "Agent");
        System.out.println("   Status : " + resultNotFound.getStatus());
        System.out.println("   Message: " + resultNotFound.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 9: URL with expiry in the past → 410 Gone
        // ════════════════════════════════════════════════════════════
        printHeader("9. Expired URL → 410 Gone");
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        String shortExpired = writer.shorten(
                "https://www.expired-deal.com/sale", null, "user_004", pastExpiry);
        String expiredCode = shortExpired.replace("https://short.ly/", "");
        // Clear from cache so we do a real DB lookup (which checks expiry)
        cache.invalidate(expiredCode);
        UrlReadService.RedirectResult resultExpired = reader.redirect(expiredCode, "1.1.1.1", "Agent");
        System.out.println("   Status : " + resultExpired.getStatus());
        System.out.println("   Message: " + resultExpired.getMessage());

        // ════════════════════════════════════════════════════════════
        // SCENARIO 10: Invalid URL → exception
        // ════════════════════════════════════════════════════════════
        printHeader("10. Invalid URL → InvalidUrlException");
        try {
            writer.shorten("not-a-valid-url", null, "user_005", null);
        } catch (InvalidUrlException e) {
            System.out.println("   Caught: " + e.getMessage());
        }

        // ════════════════════════════════════════════════════════════
        // Summary
        // ════════════════════════════════════════════════════════════
        printHeader("Summary");
        UrlMapping infoCustom = reader.getUrlInfo("g-search");
        System.out.println("   'g-search' click count: " + infoCustom.getClickCount());
        System.out.println("   Cache size            : " + cache.size() + " entries");
        System.out.println("   Total keys issued     : " + kgsRepo.getTotalKeysIssued());

        // Cleanup background threads
        Thread.sleep(100); // Let async analytics events flush
        kgs.shutdown();
        analytics.shutdown();

        System.out.println("\n" + "═".repeat(60));
        System.out.println("  Demo complete.");
        System.out.println("═".repeat(60) + "\n");
    }

    private static void printHeader(String title) {
        System.out.println("\n── " + title + " " + "─".repeat(Math.max(0, 55 - title.length())));
    }
}
