package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.Playlist;
import com.lld.hld.spotify.repository.PlaylistRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Playlist Service — CRUD + Collaborative Editing
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Handles playlist lifecycle: create, add/remove tracks, delete.
 *
 * KEY CONCEPT: Optimistic Concurrency Control (OCC)
 * Collaborative playlists can be edited by multiple users.
 * Each playlist has a snapshot_id (version).
 * When editing, the client sends its known snapshot_id.
 * If it doesn't match the server's → CONFLICT, client must refresh.
 *
 * IN PRODUCTION:
 * - Playlists stored in PostgreSQL (metadata + tracks)
 * - Popular playlists cached in Redis (30-min TTL)
 * - Playlist updates published to Kafka for sync across devices
 * - Very large playlists (10K+ tracks) are paginated
 * ═══════════════════════════════════════════════════════════════════════
 */
public class PlaylistService {

    private final PlaylistRepository playlistRepo;
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public PlaylistService(PlaylistRepository playlistRepo) {
        this.playlistRepo = playlistRepo;
    }

    /**
     * Create a new playlist.
     */
    public Playlist createPlaylist(String name, String ownerId, boolean isPublic) {
        String id = "playlist_" + String.format("%03d", idCounter.getAndIncrement());
        Playlist playlist = new Playlist(id, name, ownerId, isPublic);
        playlistRepo.save(playlist);
        System.out.printf("[Playlist] ✅ Created: '%s' (id=%s, owner=%s, public=%s)%n",
                name, id, ownerId, isPublic);
        return playlist;
    }

    /**
     * Add a song to a playlist with OCC validation.
     *
     * @param playlistId     target playlist
     * @param songId         song to add
     * @param userId         who is adding
     * @param clientSnapshot the client's known snapshot_id (for OCC)
     * @return the new snapshot_id after successful mutation
     * @throws ConcurrentModificationException if snapshot is stale
     */
    public String addSong(String playlistId, String songId, String userId, String clientSnapshot) {
        Playlist playlist = playlistRepo.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found: " + playlistId));

        // ── OCC Check ──────────────────────────────────────────
        if (clientSnapshot != null && !clientSnapshot.equals(playlist.getSnapshotId())) {
            throw new java.util.ConcurrentModificationException(
                    "Playlist was modified! Your snapshot=" + clientSnapshot +
                            " but current=" + playlist.getSnapshotId() +
                            ". Please refresh and retry.");
        }

        // Check collaborative access
        if (!playlist.getOwnerId().equals(userId) && !playlist.isCollaborative()) {
            throw new RuntimeException("Only the owner can edit non-collaborative playlists");
        }

        String newSnapshot = playlist.addTrack(songId, userId);
        playlistRepo.save(playlist);
        System.out.printf("[Playlist] ➕ Added song=%s to '%s' (new snapshot=%s)%n",
                songId, playlist.getName(), newSnapshot);
        return newSnapshot;
    }

    /**
     * Remove a song from a playlist.
     */
    public String removeSong(String playlistId, String songId, String userId) {
        Playlist playlist = playlistRepo.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found: " + playlistId));

        if (!playlist.getOwnerId().equals(userId) && !playlist.isCollaborative()) {
            throw new RuntimeException("Only the owner can edit non-collaborative playlists");
        }

        String newSnapshot = playlist.removeTrack(songId);
        playlistRepo.save(playlist);
        System.out.printf("[Playlist] ➖ Removed song=%s from '%s' (new snapshot=%s)%n",
                songId, playlist.getName(), newSnapshot);
        return newSnapshot;
    }

    /**
     * Get a playlist by ID.
     */
    public Playlist getPlaylist(String playlistId) {
        return playlistRepo.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found: " + playlistId));
    }

    /**
     * Get all playlists owned by a user.
     */
    public List<Playlist> getUserPlaylists(String userId) {
        return playlistRepo.findByOwnerId(userId);
    }

    /**
     * Delete a playlist.
     */
    public void deletePlaylist(String playlistId, String userId) {
        Playlist playlist = playlistRepo.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found: " + playlistId));

        if (!playlist.getOwnerId().equals(userId)) {
            throw new RuntimeException("Only the owner can delete a playlist");
        }

        playlistRepo.delete(playlistId);
        System.out.printf("[Playlist] 🗑️  Deleted: '%s' (id=%s)%n", playlist.getName(), playlistId);
    }

    /**
     * Enable collaborative mode on a playlist.
     */
    public void enableCollaboration(String playlistId, String ownerId) {
        Playlist playlist = getPlaylist(playlistId);
        if (!playlist.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Only owner can enable collaboration");
        }
        playlist.setCollaborative(true);
        playlistRepo.save(playlist);
        System.out.printf("[Playlist] 👥 Collaboration enabled on '%s'%n", playlist.getName());
    }
}
