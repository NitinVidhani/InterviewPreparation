package com.lld.hld.urlshortener.model;

import java.time.Instant;

/**
 * Represents a single short URL → long URL mapping stored in the database.
 */
public class UrlMapping {

    private String shortCode; // e.g. "xK9pQ2"
    private String longUrl; // e.g. "https://example.com/very/long/path"
    private String userId; // null if anonymous
    private boolean customAlias;
    private boolean deleted;
    private Instant createdAt;
    private Instant expiresAt; // null = never expires
    private long clickCount;

    public UrlMapping() {
    }

    public UrlMapping(String shortCode, String longUrl, String userId,
            boolean customAlias, Instant expiresAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.userId = userId;
        this.customAlias = customAlias;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.deleted = false;
        this.clickCount = 0;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String v) {
        this.shortCode = v;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String v) {
        this.longUrl = v;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String v) {
        this.userId = v;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(boolean v) {
        this.customAlias = v;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean v) {
        this.deleted = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant v) {
        this.createdAt = v;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant v) {
        this.expiresAt = v;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long v) {
        this.clickCount = v;
    }

    @Override
    public String toString() {
        return "UrlMapping{shortCode='" + shortCode + "', longUrl='" + longUrl +
                "', expiresAt=" + expiresAt + ", deleted=" + deleted + "}";
    }
}
