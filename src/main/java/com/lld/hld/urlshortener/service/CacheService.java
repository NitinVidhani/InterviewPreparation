package com.lld.hld.urlshortener.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache Service — simulates a Redis cache layer.
 *
 * In production: Redis Cluster (AWS ElastiCache)
 * - Key: short_code (e.g., "xK9pQ2")
 * - Value: long_url (e.g., "https://example.com/...")
 * - TTL: min(URL expiry, 24 hours) — set via Redis EXPIRE command
 *
 * Strategy: Write-Through
 * → Cache is populated at write time (when URL is shortened)
 * → If cache miss on read → fetch from DB → populate cache
 *
 * Eviction: LRU — handled by Redis automatically with `maxmemory-policy
 * allkeys-lru`
 */
public class CacheService {

    private record CacheEntry(String longUrl, Instant expiresAt) {
    }

    // ConcurrentHashMap → thread-safe, simulates Redis key-value store
    private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();

    /**
     * Cache the mapping.
     *
     * @param shortCode the short code key
     * @param longUrl   the destination URL
     * @param expiresAt optional expiry (null = no expiry)
     */
    public void put(String shortCode, String longUrl, Instant expiresAt) {
        store.put(shortCode, new CacheEntry(longUrl, expiresAt));
        System.out.printf("[Cache] PUT  key=%s → value=%s%n", shortCode, longUrl);
    }

    /**
     * Lookup a short code.
     * Returns null on cache miss OR if the cached URL has expired.
     */
    public String get(String shortCode) {
        CacheEntry entry = store.get(shortCode);
        if (entry == null) {
            System.out.printf("[Cache] MISS key=%s%n", shortCode);
            return null;
        }

        // Simulate TTL expiry check (Redis does this automatically)
        if (entry.expiresAt() != null && entry.expiresAt().isBefore(Instant.now())) {
            store.remove(shortCode); // Evict expired entry
            System.out.printf("[Cache] EXPIRED key=%s%n", shortCode);
            return null;
        }

        System.out.printf("[Cache] HIT  key=%s → %s%n", shortCode, entry.longUrl());
        return entry.longUrl();
    }

    /**
     * Remove a key from cache (called on URL deletion).
     * Simulates: Redis DEL <shortCode>
     */
    public void invalidate(String shortCode) {
        store.remove(shortCode);
        System.out.printf("[Cache] DEL  key=%s%n", shortCode);
    }

    public int size() {
        return store.size();
    }
}
