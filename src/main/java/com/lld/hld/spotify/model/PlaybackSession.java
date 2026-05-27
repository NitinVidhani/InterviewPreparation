package com.lld.hld.spotify.model;

import java.time.Instant;

/**
 * Represents an active playback session.
 *
 * Tracks what a user is currently listening to, including position within the
 * song.
 * Used by the PlaybackService (which uses the State Pattern for player states).
 *
 * In production: stored in Redis as a session object for real-time state
 * tracking.
 * After the session ends, the play event is published to Kafka for analytics.
 */
public class PlaybackSession {

    /**
     * ── STATE PATTERN ──────────────────────────────────────────────
     * The player can be in one of 4 states:
     * IDLE → PLAYING → PAUSED → PLAYING (toggle)
     * → BUFFERING → PLAYING (auto-resume after buffer)
     *
     * State transitions are enforced by PlaybackService.
     */
    public enum PlayerState {
        IDLE, PLAYING, PAUSED, BUFFERING
    }

    private String sessionId; // unique per playback
    private String userId;
    private String songId;
    private PlayerState state;
    private int positionMs; // current position in the song
    private int qualityBitrate; // 96, 160, or 320
    private String cdnUrl; // pre-signed CDN URL being streamed
    private Instant startedAt;
    private Instant lastUpdated;

    public PlaybackSession() {
    }

    public PlaybackSession(String sessionId, String userId, String songId,
            int qualityBitrate, String cdnUrl) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.songId = songId;
        this.state = PlayerState.IDLE;
        this.positionMs = 0;
        this.qualityBitrate = qualityBitrate;
        this.cdnUrl = cdnUrl;
        this.startedAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * Transition to PLAYING state.
     * Valid from: IDLE, PAUSED, BUFFERING
     */
    public void play() {
        if (state == PlayerState.PLAYING)
            return; // already playing
        this.state = PlayerState.PLAYING;
        this.lastUpdated = Instant.now();
    }

    /**
     * Transition to PAUSED state.
     * Valid from: PLAYING
     */
    public void pause() {
        if (state != PlayerState.PLAYING)
            return;
        this.state = PlayerState.PAUSED;
        this.lastUpdated = Instant.now();
    }

    /**
     * Seek to a specific position in the song.
     * Uses HTTP Range requests under the hood.
     */
    public void seek(int positionMs) {
        this.positionMs = positionMs;
        this.lastUpdated = Instant.now();
    }

    /**
     * Update current playback position (called periodically by client).
     */
    public void updatePosition(int positionMs) {
        this.positionMs = positionMs;
        this.lastUpdated = Instant.now();
    }

    /**
     * Check if the song has been played long enough to count as a "play"
     * for analytics/royalty purposes (Spotify counts after 30 seconds).
     */
    public boolean countsAsPlay() {
        return positionMs >= 30_000; // 30 seconds
    }

    // ── Getters & Setters ─────────────────────────────────────
    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSongId() {
        return songId;
    }

    public PlayerState getState() {
        return state;
    }

    public void setState(PlayerState v) {
        this.state = v;
    }

    public int getPositionMs() {
        return positionMs;
    }

    public int getQualityBitrate() {
        return qualityBitrate;
    }

    public void setQualityBitrate(int v) {
        this.qualityBitrate = v;
    }

    public String getCdnUrl() {
        return cdnUrl;
    }

    public void setCdnUrl(String v) {
        this.cdnUrl = v;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public String toString() {
        return "PlaybackSession{song='" + songId + "', state=" + state +
                ", position=" + positionMs + "ms, quality=" + qualityBitrate + "kbps}";
    }
}
