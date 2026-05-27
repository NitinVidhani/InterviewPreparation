package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.PlaybackSession;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * OBSERVER PATTERN — Playback Event Listener
 * ═══════════════════════════════════════════════════════════════════════
 *
 * WHY OBSERVER PATTERN?
 * When a user plays a song, MANY subsystems need to react:
 * - Analytics: record the play event, compute royalties
 * - Recommendations: update user's listening profile
 * - Social: "Friend is listening to X"
 * - Ad service: decrement song count until next ad (free tier)
 * - Lyrics: fetch synced lyrics for the song
 *
 * Instead of StreamingService calling each one directly (tight coupling),
 * we use the Observer pattern: StreamingService publishes events,
 * any number of listeners can subscribe independently.
 *
 * IN PRODUCTION:
 * This is implemented via Kafka topics. The StreamingService publishes
 * a "play-event" to Kafka, and multiple consumer groups subscribe:
 * - analytics-consumer
 * - recommendation-consumer
 * - social-consumer
 * - etc.
 *
 * PATTERN STRUCTURE:
 * PlaybackEventListener (Observer interface)
 * ├── AnalyticsListener → logs play event, updates counters
 * ├── RecommendationListener → updates user's genre preferences
 * └── AdInjectionListener → counts plays until next ad
 * ═══════════════════════════════════════════════════════════════════════
 */
public interface PlaybackEventListener {

    /**
     * Called when a song starts playing.
     */
    void onSongStarted(PlaybackSession session);

    /**
     * Called when a song finishes (played > 80% or completed).
     */
    void onSongCompleted(PlaybackSession session);

    /**
     * Called when a song is skipped (played < 30 seconds).
     */
    void onSongSkipped(PlaybackSession session);
}
