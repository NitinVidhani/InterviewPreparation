package com.lld.hld.urlshortener.service;

import com.lld.hld.urlshortener.exception.AliasAlreadyTakenException;
import com.lld.hld.urlshortener.exception.InvalidUrlException;
import com.lld.hld.urlshortener.model.UrlMapping;
import com.lld.hld.urlshortener.repository.UrlMappingRepository;

import java.time.Instant;

/**
 * URL Write Service
 *
 * Handles: POST /api/v1/shorten
 *
 * Full flow:
 * 1. Validate the long URL format
 * 2. Dedup check — return existing short code if same user already shortened it
 * 3. Determine short code: custom alias OR get from KGS
 * 4. Persist to DB
 * 5. Warm cache (write-through)
 * 6. Return short URL to caller
 */
public class UrlWriteService {

    private static final String BASE_URL = "https://short.ly/";

    private final KeyGenerationService kgs;
    private final UrlMappingRepository urlRepo;
    private final CacheService cache;

    public UrlWriteService(KeyGenerationService kgs,
            UrlMappingRepository urlRepo,
            CacheService cache) {
        this.kgs = kgs;
        this.urlRepo = urlRepo;
        this.cache = cache;
    }

    /**
     * Main method: shorten a long URL.
     *
     * @param longUrl     The original URL to shorten
     * @param customAlias Optional custom code (e.g., "my-sale")
     * @param userId      Optional user ID (null for anonymous)
     * @param expiresAt   Optional expiry time (null = never expires)
     * @return Full short URL (e.g., "https://short.ly/xK9pQ2")
     */
    public String shorten(String longUrl, String customAlias, String userId, Instant expiresAt)
            throws InterruptedException {

        // ── Step 1: Validate URL ─────────────────────────────────────────────
        validateUrl(longUrl);

        // ── Step 2: Dedup check ──────────────────────────────────────────────
        // If this user already shortened this exact URL → return existing code
        String existingCode = urlRepo.findShortCodeByLongUrlAndUserId(longUrl, userId);
        if (existingCode != null) {
            System.out.printf("[WriteService] Dedup hit! Returning existing code: %s%n", existingCode);
            return BASE_URL + existingCode;
        }

        // ── Step 3: Determine short code ─────────────────────────────────────
        String shortCode;
        if (customAlias != null && !customAlias.isBlank()) {
            // Custom alias requested — must be unique
            if (urlRepo.existsByShortCode(customAlias)) {
                throw new AliasAlreadyTakenException("Alias '" + customAlias + "' is already taken.");
            }
            shortCode = customAlias;
            System.out.printf("[WriteService] Using custom alias: %s%n", shortCode);
        } else {
            // Get next pre-generated unique key from KGS buffer
            shortCode = kgs.getNextKey();
            System.out.printf("[WriteService] KGS assigned key: %s%n", shortCode);
        }

        // ── Step 4: Persist to DB ────────────────────────────────────────────
        UrlMapping mapping = new UrlMapping(
                shortCode, longUrl, userId,
                customAlias != null,
                expiresAt);
        urlRepo.save(mapping);
        System.out.printf("[WriteService] Saved → %s%n", mapping);

        // ── Step 5: Write-through cache ──────────────────────────────────────
        cache.put(shortCode, longUrl, expiresAt);

        // ── Step 6: Return the full short URL ────────────────────────────────
        return BASE_URL + shortCode;
    }

    /**
     * Delete a shortened URL (soft delete — keeps record, marks as deleted).
     * Also invalidates cache.
     */
    public boolean delete(String shortCode) {
        boolean deleted = urlRepo.softDelete(shortCode);
        if (deleted) {
            cache.invalidate(shortCode);
            System.out.printf("[WriteService] Deleted short code: %s%n", shortCode);
        }
        return deleted;
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL cannot be null or empty.");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new InvalidUrlException("URL must start with http:// or https://. Got: " + url);
        }
        if (url.length() > 2048) {
            throw new InvalidUrlException("URL exceeds maximum length of 2048 characters.");
        }
    }
}
