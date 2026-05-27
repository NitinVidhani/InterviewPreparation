package com.lld.hld.spotify.model;

import java.time.Instant;
import java.util.*;

/**
 * Represents a user account on Spotify.
 *
 * In production: stored in PostgreSQL `users` table.
 * User's liked songs in `user_liked_songs`, followed artists in `user_follows`.
 */
public class User {

    public enum Tier {
        FREE, PREMIUM
    }

    private String id; // "user_001"
    private String username;
    private String email;
    private String displayName;
    private Tier tier;
    private String country;
    private Instant createdAt;

    // User library — in production these are separate tables
    private final Set<String> likedSongIds = new LinkedHashSet<>();
    private final Set<String> followedArtistIds = new LinkedHashSet<>();
    private final List<String> recentlyPlayed = new ArrayList<>(); // last N song IDs

    // Listening stats for recommendations
    private final Map<String, Integer> genrePlayCounts = new HashMap<>();

    public User() {
    }

    public User(String id, String username, String email, Tier tier) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = username;
        this.tier = tier;
        this.country = "US";
        this.createdAt = Instant.now();
    }

    public void likeSong(String songId) {
        likedSongIds.add(songId);
    }

    public void unlikeSong(String songId) {
        likedSongIds.remove(songId);
    }

    public boolean hasLiked(String songId) {
        return likedSongIds.contains(songId);
    }

    public void followArtist(String artistId) {
        followedArtistIds.add(artistId);
    }

    public void recordPlay(String songId, String genre) {
        recentlyPlayed.add(0, songId); // prepend
        if (recentlyPlayed.size() > 50) {
            recentlyPlayed.remove(recentlyPlayed.size() - 1);
        }
        if (genre != null) {
            genrePlayCounts.merge(genre, 1, Integer::sum);
        }
    }

    public boolean isPremium() {
        return tier == Tier.PREMIUM;
    }

    /**
     * Returns the user's top genre by play count — used by recommendations.
     */
    public String getTopGenre() {
        return genrePlayCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ── Getters & Setters ─────────────────────────────────────
    public String getId() {
        return id;
    }

    public void setId(String v) {
        this.id = v;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String v) {
        this.username = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        this.email = v;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String v) {
        this.displayName = v;
    }

    public Tier getTier() {
        return tier;
    }

    public void setTier(Tier v) {
        this.tier = v;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String v) {
        this.country = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<String> getLikedSongIds() {
        return Collections.unmodifiableSet(likedSongIds);
    }

    public Set<String> getFollowedArtistIds() {
        return Collections.unmodifiableSet(followedArtistIds);
    }

    public List<String> getRecentlyPlayed() {
        return Collections.unmodifiableList(recentlyPlayed);
    }

    public Map<String, Integer> getGenrePlayCounts() {
        return Collections.unmodifiableMap(genrePlayCounts);
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', tier=" + tier +
                ", liked=" + likedSongIds.size() + " songs}";
    }
}
