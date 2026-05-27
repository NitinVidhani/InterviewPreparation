package com.lld.hld.pastebin.model;

import java.time.Instant;

/**
 * Paste Metadata — stored in the Metadata DB (PostgreSQL / DynamoDB).
 *
 * Key Design Decision:
 * The actual paste CONTENT is NOT stored here.
 * Content lives on Object Storage (S3) at the path in s3ObjectKey.
 * This keeps DB rows small and fast to query.
 */
public class PasteMetadata {

    private String pasteKey; // e.g. "aB3xY9" — Primary Key
    private String title; // e.g. "My Python Script"
    private String s3ObjectKey; // e.g. "pastes/aB3xY9.txt"
    private String syntax; // e.g. "python", "java", "plaintext"
    private String userId; // null if anonymous
    private Visibility visibility;
    private String passwordHash; // bcrypt hash, null if no password
    private long sizeBytes; // original content size
    private long viewCount;
    private Instant createdAt;
    private Instant expiresAt; // null = never expires
    private boolean deleted;

    public enum Visibility {
        PUBLIC, UNLISTED, PRIVATE
    }

    public PasteMetadata() {
    }

    public PasteMetadata(String pasteKey, String title, String syntax,
            String userId, Visibility visibility,
            String passwordHash, long sizeBytes, Instant expiresAt) {
        this.pasteKey = pasteKey;
        this.title = title;
        this.s3ObjectKey = "pastes/" + pasteKey + ".txt";
        this.syntax = syntax != null ? syntax : "plaintext";
        this.userId = userId;
        this.visibility = visibility != null ? visibility : Visibility.UNLISTED;
        this.passwordHash = passwordHash;
        this.sizeBytes = sizeBytes;
        this.viewCount = 0;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.deleted = false;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public boolean isPasswordProtected() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getPasteKey() {
        return pasteKey;
    }

    public void setPasteKey(String v) {
        this.pasteKey = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v;
    }

    public String getS3ObjectKey() {
        return s3ObjectKey;
    }

    public void setS3ObjectKey(String v) {
        this.s3ObjectKey = v;
    }

    public String getSyntax() {
        return syntax;
    }

    public void setSyntax(String v) {
        this.syntax = v;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String v) {
        this.userId = v;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility v) {
        this.visibility = v;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String v) {
        this.passwordHash = v;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long v) {
        this.sizeBytes = v;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long v) {
        this.viewCount = v;
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean v) {
        this.deleted = v;
    }

    @Override
    public String toString() {
        return "PasteMetadata{key='" + pasteKey + "', title='" + title +
                "', syntax='" + syntax + "', size=" + sizeBytes +
                "B, views=" + viewCount + ", visibility=" + visibility + "}";
    }
}
