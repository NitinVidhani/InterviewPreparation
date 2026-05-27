package com.lld.hld.pastebin.repository;

import com.lld.hld.pastebin.model.PasteMetadata;

import java.util.*;

/**
 * In-memory simulation of the Paste Metadata database.
 *
 * In production: PostgreSQL / DynamoDB
 * Table: paste_metadata (paste_key PK, title, s3_object_key, syntax, ...)
 */
public class PasteMetadataRepository {

    private final Map<String, PasteMetadata> byPasteKey = new HashMap<>();
    private final Map<String, List<PasteMetadata>> byUserId = new HashMap<>();

    /** INSERT INTO paste_metadata */
    public void save(PasteMetadata meta) {
        byPasteKey.put(meta.getPasteKey(), meta);
        if (meta.getUserId() != null) {
            byUserId.computeIfAbsent(meta.getUserId(), k -> new ArrayList<>()).add(meta);
        }
    }

    /** SELECT * FROM paste_metadata WHERE paste_key = ? */
    public PasteMetadata findByPasteKey(String pasteKey) {
        return byPasteKey.get(pasteKey);
    }

    /** SELECT * FROM paste_metadata WHERE user_id = ? ORDER BY created_at DESC */
    public List<PasteMetadata> findByUserId(String userId) {
        return byUserId.getOrDefault(userId, Collections.emptyList());
    }

    /** SELECT 1 FROM paste_metadata WHERE paste_key = ? */
    public boolean existsByPasteKey(String pasteKey) {
        return byPasteKey.containsKey(pasteKey);
    }

    /** UPDATE paste_metadata SET is_deleted = true WHERE paste_key = ? */
    public boolean softDelete(String pasteKey) {
        PasteMetadata meta = byPasteKey.get(pasteKey);
        if (meta == null)
            return false;
        meta.setDeleted(true);
        return true;
    }

    /** UPDATE paste_metadata SET view_count = view_count + 1 */
    public void incrementViewCount(String pasteKey) {
        PasteMetadata meta = byPasteKey.get(pasteKey);
        if (meta != null) {
            meta.setViewCount(meta.getViewCount() + 1);
        }
    }

    /**
     * SELECT paste_key, s3_object_key FROM paste_metadata WHERE expires_at < NOW()
     */
    public List<PasteMetadata> findExpiredPastes() {
        List<PasteMetadata> expired = new ArrayList<>();
        for (PasteMetadata meta : byPasteKey.values()) {
            if (!meta.isDeleted() && meta.isExpired()) {
                expired.add(meta);
            }
        }
        return expired;
    }
}
