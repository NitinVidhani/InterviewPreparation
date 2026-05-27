package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.PlaybackSession;
import com.lld.hld.spotify.model.Song;
import com.lld.hld.spotify.model.User;
import com.lld.hld.spotify.repository.SongRepository;
import com.lld.hld.spotify.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Streaming Service — The Core of Spotify
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Handles the critical path: user presses play → music streams.
 *
 * DESIGN PATTERNS USED:
 * 1. STRATEGY → AudioQualityStrategy for bitrate selection
 * 2. OBSERVER → PlaybackEventListener for decoupled event handling
 * 3. STATE → PlaybackSession.PlayerState for player state machine
 *
 * FLOW (mirrors the HLD sequence diagram):
 * 1. Client: GET /songs/{id}/stream?quality=high
 * 2. API Gateway: validate JWT, rate limit
 * 3. StreamingService:
 * a. Lookup song metadata (cache → DB)
 * b. Check user tier (free caps at 160kbps)
 * c. Select quality via Strategy Pattern
 * d. Generate pre-signed CDN URL
 * e. Create PlaybackSession
 * f. Notify observers (analytics, recommendations)
 * g. Return CDN URL to client
 * 4. Client streams directly from CDN (not from this service!)
 *
 * IN PRODUCTION:
 * - Song metadata is cached in Redis (1-hour TTL)
 * - CDN URL is a pre-signed CloudFront URL with 1-hour expiry
 * - Play events are published to Kafka topic "play-events"
 * - Only 1 active stream per account (enforced via session tracking)
 * ═══════════════════════════════════════════════════════════════════════
 */
public class StreamingService {

    private static final String CDN_BASE_URL = "https://cdn.spotify-edge.com/";

    private final SongRepository songRepo;
    private final UserRepository userRepo;
    private final CacheService cache;
    private AudioQualityStrategy qualityStrategy;

    // Observer pattern: list of listeners notified on playback events
    private final List<PlaybackEventListener> listeners = new ArrayList<>();

    // Active sessions: userId → session (only 1 stream per user)
    private final java.util.Map<String, PlaybackSession> activeSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public StreamingService(SongRepository songRepo, UserRepository userRepo,
            CacheService cache, AudioQualityStrategy qualityStrategy) {
        this.songRepo = songRepo;
        this.userRepo = userRepo;
        this.cache = cache;
        this.qualityStrategy = qualityStrategy;
    }

    // ── Observer Management ───────────────────────────────────────

    public void addListener(PlaybackEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PlaybackEventListener listener) {
        listeners.remove(listener);
    }

    // ── Strategy Swap ─────────────────────────────────────────────

    /**
     * Switch the quality selection strategy at runtime.
     * e.g., user toggles "Data Saver" mode → swap to DataSaverStrategy
     */
    public void setQualityStrategy(AudioQualityStrategy strategy) {
        System.out.printf("[Streaming] ⚙️  Quality strategy changed: %s → %s%n",
                this.qualityStrategy.getName(), strategy.getName());
        this.qualityStrategy = strategy;
    }

    // ── Core: Stream a Song ───────────────────────────────────────

    /**
     * Stream a song for the given user.
     *
     * @param songId           the song to stream
     * @param userId           the user requesting the stream
     * @param networkBandwidth estimated bandwidth in kbps (0 = unknown)
     * @return the PlaybackSession with CDN URL and quality info
     */
    public PlaybackSession streamSong(String songId, String userId, int networkBandwidth) {
        System.out.println("\n" + "─".repeat(50));
        System.out.printf("[Streaming] ▶️  Stream request: song=%s, user=%s%n", songId, userId);

        // 1. Lookup user
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // 2. End any existing session for this user (1 stream per account)
        PlaybackSession existing = activeSessions.get(userId);
        if (existing != null) {
            endSession(existing);
        }

        // 3. Lookup song metadata: Cache → DB
        Song song = cache.getSong(songId);
        if (song == null) {
            // Cache miss → fetch from DB → populate cache
            song = songRepo.findById(songId)
                    .orElseThrow(() -> new RuntimeException("Song not found: " + songId));
            cache.putSong(songId, song);
        }

        // 4. Select quality via STRATEGY PATTERN
        int bitrate = qualityStrategy.selectBitrate(user, networkBandwidth);
        System.out.printf("[Streaming] 🎚️  Quality selected: %dkbps (strategy=%s, tier=%s, bandwidth=%dkbps)%n",
                bitrate, qualityStrategy.getName(), user.getTier(), networkBandwidth);

        // 5. Build CDN URL (in production: pre-signed CloudFront URL with expiry)
        String s3Key = song.getS3KeyForBitrate(bitrate);
        String cdnUrl = CDN_BASE_URL + s3Key + "?token=" + UUID.randomUUID().toString().substring(0, 8);
        System.out.printf("[Streaming] 🌐 CDN URL generated: %s%n", cdnUrl);

        // 6. Create PlaybackSession (STATE PATTERN for player states)
        PlaybackSession session = new PlaybackSession(
                UUID.randomUUID().toString().substring(0, 8),
                userId, songId, bitrate, cdnUrl);
        session.play(); // Transition: IDLE → PLAYING
        activeSessions.put(userId, session);

        // 7. Notify all observers (OBSERVER PATTERN)
        for (PlaybackEventListener listener : listeners) {
            listener.onSongStarted(session);
        }

        System.out.printf("[Streaming] ✅ Now playing: '%s' @ %dkbps%n", song.getTitle(), bitrate);
        return session;
    }

    /**
     * Simulate skipping a song (played < 30 seconds).
     */
    public void skipSong(String userId) {
        PlaybackSession session = activeSessions.get(userId);
        if (session == null)
            return;

        System.out.printf("[Streaming] ⏭️  Skip: song=%s at %dms%n",
                session.getSongId(), session.getPositionMs());

        // Notify observers of skip
        for (PlaybackEventListener listener : listeners) {
            listener.onSongSkipped(session);
        }
        activeSessions.remove(userId);
    }

    /**
     * Simulate completing a song (played > 30 seconds → counts as a play).
     */
    public void completeSong(String userId) {
        PlaybackSession session = activeSessions.get(userId);
        if (session == null)
            return;

        // Simulate that the song played long enough (~80% of duration)
        session.updatePosition(180_000); // 3 minutes in

        if (session.countsAsPlay()) {
            for (PlaybackEventListener listener : listeners) {
                listener.onSongCompleted(session);
            }
        }
        activeSessions.remove(userId);
    }

    private void endSession(PlaybackSession session) {
        if (session.countsAsPlay()) {
            for (PlaybackEventListener listener : listeners) {
                listener.onSongCompleted(session);
            }
        }
        activeSessions.remove(session.getUserId());
    }

    public PlaybackSession getActiveSession(String userId) {
        return activeSessions.get(userId);
    }
}
