package com.lld.hld.spotify.model;

/**
 * Represents a music artist in the Spotify catalog.
 *
 * In production: stored in PostgreSQL `artists` table.
 * Indexed in Elasticsearch for full-text search.
 */
public class Artist {

    private String id; // "artist_001"
    private String name; // "Queen"
    private String bio;
    private String imageUrl;
    private long followerCount;
    private boolean verified;

    public Artist() {
    }

    public Artist(String id, String name, String bio) {
        this.id = id;
        this.name = name;
        this.bio = bio;
        this.followerCount = 0;
        this.verified = false;
    }

    // ── Getters & Setters ─────────────────────────────────────
    public String getId() {
        return id;
    }

    public void setId(String v) {
        this.id = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String v) {
        this.bio = v;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String v) {
        this.imageUrl = v;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long v) {
        this.followerCount = v;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean v) {
        this.verified = v;
    }

    @Override
    public String toString() {
        return "Artist{name='" + name + "', followers=" + followerCount + "}";
    }
}
