package com.lld.hld.pastebin.service;

import com.lld.hld.pastebin.model.PasteMetadata;
import com.lld.hld.pastebin.repository.PasteMetadataRepository;

import java.util.List;

/**
 * Cleanup Service — Background job that removes expired pastes.
 *
 * In production: runs as a scheduled cron job (e.g., every hour)
 *
 * Flow:
 * 1. Query DB for all pastes where expires_at < NOW()
 * 2. For each expired paste:
 * a. Delete content from S3
 * b. Soft-delete metadata in DB
 * c. Invalidate cache
 * 3. Log summary metrics
 */
public class CleanupService {

    private final PasteMetadataRepository metaRepo;
    private final ObjectStorageService s3;
    private final PasteCacheService cache;

    public CleanupService(PasteMetadataRepository metaRepo,
            ObjectStorageService s3,
            PasteCacheService cache) {
        this.metaRepo = metaRepo;
        this.s3 = s3;
        this.cache = cache;
    }

    /**
     * Execute cleanup of all expired pastes.
     * 
     * @return number of pastes cleaned up
     */
    public int cleanupExpiredPastes() {
        System.out.println("\n[Cleanup] Starting expired paste cleanup...");

        List<PasteMetadata> expiredPastes = metaRepo.findExpiredPastes();

        if (expiredPastes.isEmpty()) {
            System.out.println("[Cleanup] No expired pastes found.");
            return 0;
        }

        int cleaned = 0;
        for (PasteMetadata meta : expiredPastes) {
            try {
                // 1. Delete from S3
                s3.delete(meta.getS3ObjectKey());
                // 2. Soft-delete in DB
                metaRepo.softDelete(meta.getPasteKey());
                // 3. Invalidate cache
                cache.invalidate(meta.getPasteKey());
                cleaned++;
                System.out.printf("[Cleanup] Cleaned: %s (expired at %s)%n",
                        meta.getPasteKey(), meta.getExpiresAt());
            } catch (Exception e) {
                System.err.printf("[Cleanup] FAILED to clean %s: %s%n",
                        meta.getPasteKey(), e.getMessage());
            }
        }

        System.out.printf("[Cleanup] Done. Cleaned %d / %d expired pastes.%n",
                cleaned, expiredPastes.size());
        return cleaned;
    }
}
