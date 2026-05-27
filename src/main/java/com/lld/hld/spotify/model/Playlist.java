package com.lld.hld.spotify.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a user-created playlist.
 *
 * In production: stored in PostgreSQL `playlists` + `playlist_tracks` tables.
 *
 * Key Design:
 * - snapshot_id: Optimistic Concurrency Control (OCC) for collaborative
 * playlists.
 * Each mutation bumps the snapshot. Clients send their snapshot with edits;
 * if stale, the server rejects the change → client must refresh and retry.
 * - position-based ordering: songs are stored with explicit positions.
 */
public class Playlist {

    private String id; // "playlist_001"
    private String name; // "Chill Vibes"
    private String description;
    private String ownerId; // FK → User
    private boolean isPublic;
    private boolean collaborative;
    private long followerCount;
    private String snapshotId; // Version for OCC conflict detection
    private Instant createdAt;
    private Instant updatedAt;
    private final List<PlaylistTrack> tracks = new ArrayList<>();

    /**
     * Represents one entry in the playlist (song at a position).
     */
    public record PlaylistTrack(String songId, int position, String addedBy, Instant addedAt) {
        @Override
        public String toString() {
            return songId + " @pos=" + position;
        }
    }

    public Playlist() {
    }

    public Playlist(String id, String name, String ownerId, boolean isPublic) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.isPublic = isPublic;
        this.collaborative = false;
        this.followerCount = 0;
        this.snapshotId = generateSnapshotId();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Add a song at the end of the playlist.
     * Bumps the snapshot_id for OCC.
     *
     * @return the new snapshot_id after mutation
     */
    public String addTrack(String songId, String addedBy) {
        int nextPos = tracks.size();
        tracks.add(new PlaylistTrack(songId, nextPos, addedBy, Instant.now()));
        this.snapshotId = generateSnapshotId();
        this.updatedAt = Instant.now();
        return this.snapshotId;
    }

    /**
     * Add a song at a specific position. Shifts existing tracks down.
     */
    public String addTrackAtPosition(String songId, int position, String addedBy) {
        if (position < 0 || position > tracks.size()) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }
        // Shift existing tracks after `position` down by 1
        for (int i = tracks.size() - 1; i >= position; i--) {
            PlaylistTrack t = tracks.get(i);
            tracks.set(i, new PlaylistTrack(t.songId(), t.position() + 1, t.addedBy(), t.addedAt()));
        }
        tracks.add(position, new PlaylistTrack(songId, position, addedBy, Instant.now()));
        this.snapshotId = generateSnapshotId();
        this.updatedAt = Instant.now();
        return this.snapshotId;
    }

    /**
     * Remove a song from the playlist. Re-indexes positions.
     */
    public String removeTrack(String songId) {
        tracks.removeIf(t -> t.songId().equals(songId));
        // Re-index positions
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistTrack t = tracks.get(i);
            tracks.set(i, new PlaylistTrack(t.songId(), i, t.addedBy(), t.addedAt()));
        }
        this.snapshotId = generateSnapshotId();
        this.updatedAt = Instant.now();
        return this.snapshotId;
    }

    /**
     * Shuffle the playlist order (for free-tier mobile playback).
     */
    public void shuffle() {
        Collections.shuffle(tracks);
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistTrack t = tracks.get(i);
            tracks.set(i, new PlaylistTrack(t.songId(), i, t.addedBy(), t.addedAt()));
        }
    }

    private String generateSnapshotId() {
        return "snap_v" + System.nanoTime() % 100000;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String v) {
        this.ownerId = v;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean v) {
        this.isPublic = v;
    }

    public boolean isCollaborative() {
        return collaborative;
    }

    public void setCollaborative(boolean v) {
        this.collaborative = v;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long v) {
        this.followerCount = v;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PlaylistTrack> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    public int getTrackCount() {
        return tracks.size();
    }

    @Override
    public String toString() {
        return "Playlist{name='" + name + "', tracks=" + tracks.size() +
                ", snapshot=" + snapshotId + "}";
    }
}
