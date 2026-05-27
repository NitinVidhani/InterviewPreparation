package com.lld.hld.urlshortener.service;

import com.lld.hld.urlshortener.model.ClickEvent;
import com.lld.hld.urlshortener.model.UrlMapping;
import com.lld.hld.urlshortener.repository.UrlMappingRepository;

/**
 * URL Read/Redirect Service — THE HOT PATH
 *
 * Handles: GET /{shortCode}
 *
 * This is called on every single redirect → must be FAST.
 * Target latency: < 10ms
 *
 * Full flow:
 * 1. Check Redis cache → HIT: return redirect immediately (~1ms)
 * 2. Cache miss → DB lookup
 * 3. Validate: not deleted, not expired
 * 4. Populate cache for next time
 * 5. Fire analytics event ASYNCHRONOUSLY (don't block redirect!)
 * 6. Return redirect URL
 *
 * HTTP Response codes:
 * - 302 Found → valid redirect (use 302 so analytics requests hit server)
 * - 404 Not Found → short code doesn't exist
 * - 410 Gone → URL was deleted or expired
 */
public class UrlReadService {

    private final UrlMappingRepository urlRepo;
    private final CacheService cache;
    private final AnalyticsPublisher analyticsPublisher;

    public UrlReadService(UrlMappingRepository urlRepo,
            CacheService cache,
            AnalyticsPublisher analyticsPublisher) {
        this.urlRepo = urlRepo;
        this.cache = cache;
        this.analyticsPublisher = analyticsPublisher;
    }

    /**
     * Core redirect lookup.
     *
     * @param shortCode e.g. "xK9pQ2"
     * @param ipAddress caller's IP (for analytics)
     * @param userAgent caller's User-Agent header (for device detection)
     * @return RedirectResult containing status and destination URL
     */
    public RedirectResult redirect(String shortCode, String ipAddress, String userAgent) {

        // ── Step 1: Cache lookup (fastest path) ──────────────────────────────
        String longUrl = cache.get(shortCode);

        if (longUrl != null) {
            // Cache HIT — fire analytics async and return immediately
            fireAnalyticsAsync(shortCode, ipAddress, userAgent);
            return RedirectResult.found(longUrl);
        }

        // ── Step 2: Cache MISS — go to DB ────────────────────────────────────
        System.out.printf("[ReadService] Cache MISS → querying DB for: %s%n", shortCode);
        UrlMapping mapping = urlRepo.findByShortCode(shortCode);

        // ── Step 3: Validate ─────────────────────────────────────────────────
        if (mapping == null) {
            return RedirectResult.notFound("Short code '" + shortCode + "' does not exist.");
        }

        if (mapping.isDeleted()) {
            return RedirectResult.gone("URL '" + shortCode + "' has been deleted.");
        }

        if (mapping.isExpired()) {
            return RedirectResult.gone("URL '" + shortCode + "' has expired.");
        }

        longUrl = mapping.getLongUrl();

        // ── Step 4: Populate cache (so next request is a HIT) ────────────────
        cache.put(shortCode, longUrl, mapping.getExpiresAt());

        // ── Step 5: Fire analytics asynchronously ────────────────────────────
        fireAnalyticsAsync(shortCode, ipAddress, userAgent);

        // ── Step 6: Return redirect URL ───────────────────────────────────────
        return RedirectResult.found(longUrl);
    }

    /** Get analytics info (click count) for a short code */
    public UrlMapping getUrlInfo(String shortCode) {
        return urlRepo.findByShortCode(shortCode);
    }

    private void fireAnalyticsAsync(String shortCode, String ipAddress, String userAgent) {
        // Async → returns immediately, doesn't block redirect
        ClickEvent event = new ClickEvent(shortCode, ipAddress, userAgent);
        analyticsPublisher.publishAsync(event);
        // Also increment count in DB (in production: async batch update)
        urlRepo.incrementClickCount(shortCode);
    }

    // ── Inner Result Class ───────────────────────────────────────────────────

    /**
     * Represents the outcome of a redirect lookup.
     * Maps to HTTP response codes:
     * FOUND → 302
     * NOT_FOUND → 404
     * GONE → 410
     */
    public static class RedirectResult {
        public enum Status {
            FOUND, NOT_FOUND, GONE
        }

        private final Status status;
        private final String url; // destination URL (only set on FOUND)
        private final String message; // error message (set on NOT_FOUND / GONE)

        private RedirectResult(Status status, String url, String message) {
            this.status = status;
            this.url = url;
            this.message = message;
        }

        public static RedirectResult found(String url) {
            return new RedirectResult(Status.FOUND, url, null);
        }

        public static RedirectResult notFound(String msg) {
            return new RedirectResult(Status.NOT_FOUND, null, msg);
        }

        public static RedirectResult gone(String msg) {
            return new RedirectResult(Status.GONE, null, msg);
        }

        public Status getStatus() {
            return status;
        }

        public String getUrl() {
            return url;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return status == Status.FOUND
                    ? "RedirectResult{FOUND → " + url + "}"
                    : "RedirectResult{" + status + ": " + message + "}";
        }
    }
}
