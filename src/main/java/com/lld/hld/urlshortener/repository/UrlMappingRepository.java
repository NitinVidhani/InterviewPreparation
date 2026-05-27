package com.lld.hld.urlshortener.repository;

import com.lld.hld.urlshortener.model.UrlMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory simulation of the URL Mappings database table.
 *
 * In production this would be: PostgreSQL / DynamoDB
 * Table: url_mappings (short_code PK, long_url, user_id, expires_at, ...)
 */
public class UrlMappingRepository {

    // Simulates: SELECT * FROM url_mappings WHERE short_code = ?
    private final Map<String, UrlMapping> byShortCode = new HashMap<>();

    // Simulates: SELECT short_code FROM url_mappings WHERE long_url = ? AND user_id
    // = ?
    // Used for deduplication
    private final Map<String, String> longUrlToShortCode = new HashMap<>();

    /** INSERT INTO url_mappings */
    public void save(UrlMapping mapping) {
        byShortCode.put(mapping.getShortCode(), mapping);
        String dedupKey = mapping.getLongUrl() + "|" + mapping.getUserId();
        longUrlToShortCode.put(dedupKey, mapping.getShortCode());
    }

    /** SELECT * FROM url_mappings WHERE short_code = ? */
    public UrlMapping findByShortCode(String shortCode) {
        return byShortCode.get(shortCode);
    }

    /** Dedup check: has this user already shortened this exact URL? */
    public String findShortCodeByLongUrlAndUserId(String longUrl, String userId) {
        String dedupKey = longUrl + "|" + userId;
        return longUrlToShortCode.get(dedupKey);
    }

    /** SELECT 1 FROM url_mappings WHERE short_code = ? */
    public boolean existsByShortCode(String shortCode) {
        return byShortCode.containsKey(shortCode);
    }

    /** UPDATE url_mappings SET is_deleted = true WHERE short_code = ? */
    public boolean softDelete(String shortCode) {
        UrlMapping mapping = byShortCode.get(shortCode);
        if (mapping == null)
            return false;
        mapping.setDeleted(true);
        return true;
    }

    /**
     * UPDATE url_mappings SET click_count = click_count + 1 WHERE short_code = ?
     */
    public void incrementClickCount(String shortCode) {
        UrlMapping mapping = byShortCode.get(shortCode);
        if (mapping != null) {
            mapping.setClickCount(mapping.getClickCount() + 1);
        }
    }
}
