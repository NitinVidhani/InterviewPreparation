package com.lld.hld.spotify.repository;

import com.lld.hld.spotify.model.Song;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simulates a combined PostgreSQL + Elasticsearch data layer for songs.
 *
 * In production:
 * - PostgreSQL stores the authoritative song record
 * - Elasticsearch indexes title, artist_name, album_name, genre for full-text
 * search
 * - A Kafka consumer keeps ES in sync when songs are added/updated
 */
public class SongRepository {

    // Simulates PostgreSQL: id → Song
    private final Map<String, Song> store = new ConcurrentHashMap<>();

    // Simulates Elasticsearch: searchable fields (lowercase)
    // In production, ES handles this with analyzers and tokenizers.

    public void save(Song song) {
        store.put(song.getId(), song);
    }

    public Optional<Song> findById(String songId) {
        return Optional.ofNullable(store.get(songId));
    }

    /**
     * Full-text search — simulates Elasticsearch query.
     * In production: multi-field match with fuzzy + autocomplete + ranking.
     */
    public List<Song> search(String query) {
        String q = query.toLowerCase();
        return store.values().stream()
                .filter(s -> s.getTitle().toLowerCase().contains(q)
                        || s.getGenre().toLowerCase().contains(q))
                .sorted(Comparator.comparingInt(Song::getPopularity).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Find songs by genre — used by recommendation engine.
     */
    public List<Song> findByGenre(String genre) {
        return store.values().stream()
                .filter(s -> s.getGenre().equalsIgnoreCase(genre))
                .sorted(Comparator.comparingInt(Song::getPopularity).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Find songs by artist — used for artist page.
     */
    public List<Song> findByArtistId(String artistId) {
        return store.values().stream()
                .filter(s -> s.getArtistId().equals(artistId))
                .sorted(Comparator.comparingInt(Song::getPopularity).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get all songs — for batch recommendation processing.
     */
    public Collection<Song> findAll() {
        return store.values();
    }

    public int size() {
        return store.size();
    }
}
