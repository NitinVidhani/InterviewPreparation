package com.lld.hld.pastebin.service;

import com.lld.hld.pastebin.model.PasteMetadata;
import com.lld.hld.pastebin.repository.PasteMetadataRepository;

/**
 * Paste Read Service — THE HOT PATH (must be fast!)
 *
 * GET /api/v1/pastes/{pasteKey} Flow:
 * 1. Metadata: check L1 cache → miss → DB → populate cache
 * 2. Validate: not deleted, not expired, password check
 * 3. Content: check L2 cache → miss → S3 download → populate cache (if small)
 * 4. Increment view count (async in production)
 * 5. Return metadata + content
 */
public class PasteReadService {

    private final PasteMetadataRepository metaRepo;
    private final ObjectStorageService s3;
    private final PasteCacheService cache;

    public PasteReadService(PasteMetadataRepository metaRepo,
            ObjectStorageService s3,
            PasteCacheService cache) {
        this.metaRepo = metaRepo;
        this.s3 = s3;
        this.cache = cache;
    }

    /**
     * Read a paste: metadata + content.
     *
     * @param pasteKey e.g. "aB3xY9"
     * @param password optional password for private pastes (null if public)
     * @return ReadResult with status, metadata, and content
     */
    public ReadResult readPaste(String pasteKey, String password) {

        // ── Step 1: Resolve metadata ─────────────────────────────────────────
        PasteMetadata meta = metaRepo.findByPasteKey(pasteKey);

        if (meta == null) {
            return ReadResult.notFound("Paste '" + pasteKey + "' does not exist.");
        }

        // ── Step 2: Validate ─────────────────────────────────────────────────
        if (meta.isDeleted()) {
            return ReadResult.gone("Paste '" + pasteKey + "' has been deleted.");
        }
        if (meta.isExpired()) {
            return ReadResult.gone("Paste '" + pasteKey + "' has expired.");
        }
        if (meta.isPasswordProtected()) {
            if (password == null || password.isBlank()) {
                return ReadResult.forbidden("This paste is password-protected. Provide X-Paste-Password header.");
            }
            if (!PasteWriteService.verifyPassword(password, meta.getPasswordHash())) {
                return ReadResult.forbidden("Incorrect password for paste '" + pasteKey + "'.");
            }
        }

        // ── Step 3: Fetch content (L2 cache → S3) ───────────────────────────
        String content = cache.getContent(pasteKey);

        if (content == null) {
            // Cache miss → fetch from S3
            System.out.printf("[ReadService] Content cache MISS → fetching from S3: %s%n",
                    meta.getS3ObjectKey());
            content = s3.download(meta.getS3ObjectKey());

            if (content == null) {
                // S3 object missing (should not happen — data integrity issue!)
                return ReadResult.notFound("Content not found in storage for '" + pasteKey + "'.");
            }

            // Populate L2 content cache (only if small < 256 KB)
            cache.putContent(pasteKey, content, meta.getSizeBytes(), meta.getExpiresAt());
        }

        // ── Step 4: Increment view count (sync here, async in production) ────
        metaRepo.incrementViewCount(pasteKey);

        // ── Step 5: Return result ────────────────────────────────────────────
        return ReadResult.found(meta, content);
    }

    /**
     * Get raw content only (for /raw/{pasteKey} endpoint).
     * Returns plain text with Content-Type: text/plain
     */
    public String getRawContent(String pasteKey) {
        ReadResult result = readPaste(pasteKey, null);
        if (result.getStatus() == ReadResult.Status.FOUND) {
            return result.getContent();
        }
        return null;
    }

    // ── Inner Result Class ───────────────────────────────────────────────────

    public static class ReadResult {
        public enum Status {
            FOUND, NOT_FOUND, GONE, FORBIDDEN
        }

        private final Status status;
        private final PasteMetadata metadata; // set on FOUND
        private final String content; // set on FOUND
        private final String message; // set on error

        private ReadResult(Status status, PasteMetadata meta, String content, String msg) {
            this.status = status;
            this.metadata = meta;
            this.content = content;
            this.message = msg;
        }

        public static ReadResult found(PasteMetadata meta, String content) {
            return new ReadResult(Status.FOUND, meta, content, null);
        }

        public static ReadResult notFound(String msg) {
            return new ReadResult(Status.NOT_FOUND, null, null, msg);
        }

        public static ReadResult gone(String msg) {
            return new ReadResult(Status.GONE, null, null, msg);
        }

        public static ReadResult forbidden(String msg) {
            return new ReadResult(Status.FORBIDDEN, null, null, msg);
        }

        public Status getStatus() {
            return status;
        }

        public PasteMetadata getMetadata() {
            return metadata;
        }

        public String getContent() {
            return content;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return status == Status.FOUND
                    ? "ReadResult{FOUND, key=" + metadata.getPasteKey() +
                            ", content=" + content.length() + " chars}"
                    : "ReadResult{" + status + ": " + message + "}";
        }
    }
}
