package com.lld.hld.spotify.repository;

import com.lld.hld.spotify.model.Playlist;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simulates PostgreSQL for playlists + playlist_tracks.
 *
 * In production:
 * - `playlists` table: metadata + snapshot_id
 * - `playlist_tracks` table: (playlist_id, song_id, position, added_by,
 * added_at)
 */
public class PlaylistRepository {

    private final Map<String, Playlist> store = new ConcurrentHashMap<>();

    public void save(Playlist playlist) {
        store.put(playlist.getId(), playlist);
    }

    public Optional<Playlist> findById(String playlistId) {
        return Optional.ofNullable(store.get(playlistId));
    }

    public List<Playlist> findByOwnerId(String ownerId) {
        return store.values().stream()
                .filter(p -> p.getOwnerId().equals(ownerId))
                .collect(Collectors.toList());
    }

    public List<Playlist> findPublicPlaylists(int limit) {
        return store.values().stream()
                .filter(Playlist::isPublic)
                .sorted(Comparator.comparingLong(Playlist::getFollowerCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void delete(String playlistId) {
        store.remove(playlistId);
    }

    public int size() {
        return store.size();
    }
}
