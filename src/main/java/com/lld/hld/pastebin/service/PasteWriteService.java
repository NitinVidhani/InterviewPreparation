package com.lld.hld.pastebin.service;

import com.lld.hld.pastebin.model.PasteMetadata;
import com.lld.hld.pastebin.model.PasteMetadata.Visibility;
import com.lld.hld.pastebin.repository.PasteMetadataRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Paste Write Service — handles creating and deleting pastes.
 *
 * POST /api/v1/pastes Flow:
 * 1. Validate input (content non-empty, size ≤ 10 MB, custom URL uniqueness)
 * 2. If password → hash with SHA-256 (bcrypt in production)
 * 3. Get unique paste key from KGS
 * 4. Upload content to S3 (GZIP compressed) — must succeed BEFORE DB write!
 * 5. Save metadata to DB
 * 6. Warm cache (write-through) — both L1 metadata + L2 content if small
 * 7. Return paste URL
 */
public class PasteWriteService {

    private static final String BASE_URL = "https://paste.ly/";
    private static final int MAX_SIZE = 10 * 1024 * 1024; // 10 MB

    // Reusing the same KGS from URL Shortener (shared microservice)
    private final com.lld.hld.urlshortener.service.KeyGenerationService kgs;
    private final PasteMetadataRepository metaRepo;
    private final ObjectStorageService s3;
    private final PasteCacheService cache;

    public PasteWriteService(
            com.lld.hld.urlshortener.service.KeyGenerationService kgs,
            PasteMetadataRepository metaRepo,
            ObjectStorageService s3,
            PasteCacheService cache) {
        this.kgs = kgs;
        this.metaRepo = metaRepo;
        this.s3 = s3;
        this.cache = cache;
    }

    /**
     * Create a new paste.
     *
     * @return Full paste URL (e.g., "https://paste.ly/aB3xY9")
     */
    public String createPaste(String content, String title, String syntax,
            String customAlias, String userId,
            Visibility visibility, String password,
            Instant expiresAt) throws InterruptedException {

        // ── Step 1: Validate ─────────────────────────────────────────────────
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Paste content cannot be empty.");
        }
        long sizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (sizeBytes > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Paste size " + sizeBytes + "B exceeds max of " + MAX_SIZE + "B (10 MB).");
        }

        // ── Step 2: Hash password if provided ────────────────────────────────
        String passwordHash = null;
        if (password != null && !password.isBlank()) {
            passwordHash = hashPassword(password);
            if (visibility == null)
                visibility = Visibility.PRIVATE;
        }

        // ── Step 3: Get unique paste key ─────────────────────────────────────
        String pasteKey;
        if (customAlias != null && !customAlias.isBlank()) {
            if (metaRepo.existsByPasteKey(customAlias)) {
                throw new IllegalArgumentException("Alias '" + customAlias + "' is already taken.");
            }
            pasteKey = customAlias;
            System.out.printf("[WriteService] Using custom alias: %s%n", pasteKey);
        } else {
            pasteKey = kgs.getNextKey();
            System.out.printf("[WriteService] KGS assigned key: %s%n", pasteKey);
        }

        // ── Step 4: Upload to S3 FIRST (before DB write!) ────────────────────
        // Why S3 first? If S3 fails, we don't save orphan metadata in DB.
        // If DB fails after S3 upload, we have an orphan S3 object (cleaned up later).
        String s3Key = "pastes/" + pasteKey + ".txt";
        s3.upload(s3Key, content);

        // ── Step 5: Save metadata to DB ──────────────────────────────────────
        PasteMetadata meta = new PasteMetadata(
                pasteKey, title, syntax, userId,
                visibility, passwordHash, sizeBytes, expiresAt);
        metaRepo.save(meta);
        System.out.printf("[WriteService] Saved metadata → %s%n", meta);

        // ── Step 6: Warm cache (write-through) ──────────────────────────────
        cache.putMetadata(pasteKey, s3Key, title, syntax, sizeBytes, expiresAt);
        cache.putContent(pasteKey, content, sizeBytes, expiresAt); // Only caches if < 256 KB

        // ── Step 7: Return URL ───────────────────────────────────────────────
        return BASE_URL + pasteKey;
    }

    /**
     * Delete a paste: soft-delete metadata + remove from S3 + invalidate cache.
     */
    public boolean deletePaste(String pasteKey) {
        PasteMetadata meta = metaRepo.findByPasteKey(pasteKey);
        if (meta == null || meta.isDeleted())
            return false;

        // Delete from S3
        s3.delete(meta.getS3ObjectKey());
        // Soft-delete in DB
        metaRepo.softDelete(pasteKey);
        // Invalidate all cache levels
        cache.invalidate(pasteKey);

        System.out.printf("[WriteService] Deleted paste: %s%n", pasteKey);
        return true;
    }

    // Simple password hash (use bcrypt in production!)
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    /** Exposed for ReadService to verify password */
    public static boolean verifyPassword(String password, String hash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] computed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            String computedHash = Base64.getEncoder().encodeToString(computed);
            return computedHash.equals(hash);
        } catch (Exception e) {
            return false;
        }
    }
}
