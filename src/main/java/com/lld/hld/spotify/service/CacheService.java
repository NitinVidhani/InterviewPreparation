package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.Song;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates a Redis cache layer for song metadata.
 *
 * In production: Redis Cluster (AWS ElastiCache)
 * - Key: "song_meta:{song_id}" → Song metadata (title, artist, S3 keys)
 * - Key: "reco:{user_id}" → Pre-computed recommendation list
 * - Key: "session:{token}" → User session data
 * - TTL: 1 hour for metadata, 6 hours for recommendations
 *
 * Strategy: Write-Through for metadata, Write-Behind for play counts
 */
public class CacheService {

    private record CacheEntry(Song song, Instant expiresAt) {
    }

    private final Map<String, CacheEntry> songCache = new ConcurrentHashMap<>();
    private long hits = 0;
    private long misses = 0;

    /**
     * Cache song metadata. TTL = 1 hour.
     */
    public void putSong(String songId, Song song) {
        Instant expiry = Instant.now().plusSeconds(3600); // 1-hour TTL
        songCache.put(songId, new CacheEntry(song, expiry));
        System.out.printf("[Cache] PUT  song_meta:%s → '%s'%n", songId, song.getTitle());
    }

    /**
     * Retrieve song metadata from cache. Returns null on miss or expiry.
     */
    public Song getSong(String songId) {
        CacheEntry entry = songCache.get(songId);
        if (entry == null) {
            misses++;
            System.out.printf("[Cache] MISS song_meta:%s%n", songId);
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            songCache.remove(songId);
            misses++;
            System.out.printf("[Cache] EXPIRED song_meta:%s%n", songId);
            return null;
        }
        hits++;
        System.out.printf("[Cache] HIT  song_meta:%s → '%s'%n", songId, entry.song().getTitle());
        return entry.song();
    }

    public void invalidate(String songId) {
        songCache.remove(songId);
    }

    public int size() {
        return songCache.size();
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public String getHitRatio() {
        long total = hits + misses;
        if (total == 0)
            return "N/A";
        return String.format("%.1f%%", (hits * 100.0) / total);
    }
}
