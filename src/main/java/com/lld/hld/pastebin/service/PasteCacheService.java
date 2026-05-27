package com.lld.hld.pastebin.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-level cache for Pastebin.
 *
 * Level 1: Metadata cache — caches ALL metadata (tiny, ~1 KB each)
 * Level 2: Content cache — caches ONLY pastes < 256 KB to avoid Redis memory
 * bloat
 *
 * In production:
 * Level 1: Redis hash "meta:{paste_key}" → {title, s3Key, syntax, ...}
 * Level 2: Redis string "content:{paste_key}" → compressed text
 * Level 3: CDN (CloudFront) — handles large paste delivery at edge
 */
public class PasteCacheService {

    private static final int MAX_CONTENT_CACHE_SIZE = 256 * 1024; // 256 KB

    private record MetaCacheEntry(String s3ObjectKey, String title, String syntax,
            long sizeBytes, Instant expiresAt) {
    }

    private record ContentCacheEntry(String content, Instant expiresAt) {
    }

    // Level 1: Metadata cache — always cached
    private final Map<String, MetaCacheEntry> metaCache = new ConcurrentHashMap<>();

    // Level 2: Content cache — only for small pastes < 256 KB
    private final Map<String, ContentCacheEntry> contentCache = new ConcurrentHashMap<>();

    // ── Level 1: Metadata ────────────────────────────────────────────────────

    public void putMetadata(String pasteKey, String s3Key, String title,
            String syntax, long sizeBytes, Instant expiresAt) {
        metaCache.put(pasteKey, new MetaCacheEntry(s3Key, title, syntax, sizeBytes, expiresAt));
        System.out.printf("[Cache-L1] PUT META  key=%s title=%s%n", pasteKey, title);
    }

    public String getS3Key(String pasteKey) {
        MetaCacheEntry entry = metaCache.get(pasteKey);
        if (entry == null) {
            System.out.printf("[Cache-L1] MISS META key=%s%n", pasteKey);
            return null;
        }
        if (entry.expiresAt() != null && entry.expiresAt().isBefore(Instant.now())) {
            metaCache.remove(pasteKey);
            System.out.printf("[Cache-L1] EXPIRED META key=%s%n", pasteKey);
            return null;
        }
        System.out.printf("[Cache-L1] HIT META  key=%s → s3=%s%n", pasteKey, entry.s3ObjectKey());
        return entry.s3ObjectKey();
    }

    // ── Level 2: Content (small pastes only) ─────────────────────────────────

    public void putContent(String pasteKey, String content, long sizeBytes, Instant expiresAt) {
        if (sizeBytes > MAX_CONTENT_CACHE_SIZE) {
            System.out.printf("[Cache-L2] SKIP CONTENT key=%s (size=%dB > 256KB → use CDN)%n",
                    pasteKey, sizeBytes);
            return;
        }
        contentCache.put(pasteKey, new ContentCacheEntry(content, expiresAt));
        System.out.printf("[Cache-L2] PUT CONTENT key=%s (%dB cached)%n", pasteKey, sizeBytes);
    }

    public String getContent(String pasteKey) {
        ContentCacheEntry entry = contentCache.get(pasteKey);
        if (entry == null) {
            System.out.printf("[Cache-L2] MISS CONTENT key=%s%n", pasteKey);
            return null;
        }
        if (entry.expiresAt() != null && entry.expiresAt().isBefore(Instant.now())) {
            contentCache.remove(pasteKey);
            System.out.printf("[Cache-L2] EXPIRED CONTENT key=%s%n", pasteKey);
            return null;
        }
        System.out.printf("[Cache-L2] HIT CONTENT key=%s (%d chars)%n",
                pasteKey, entry.content().length());
        return entry.content();
    }

    // ── Invalidation ─────────────────────────────────────────────────────────

    public void invalidate(String pasteKey) {
        metaCache.remove(pasteKey);
        contentCache.remove(pasteKey);
        System.out.printf("[Cache] INVALIDATED key=%s (both levels)%n", pasteKey);
    }

    public int metaCacheSize() {
        return metaCache.size();
    }

    public int contentCacheSize() {
        return contentCache.size();
    }
}
