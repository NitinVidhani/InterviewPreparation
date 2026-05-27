package com.lld.hld.spotify.model;

/**
 * Represents a single song/track in the Spotify catalog.
 *
 * In production:
 * - Metadata stored in PostgreSQL `songs` table
 * - Audio files stored on S3: audio/{id}_96.ogg, audio/{id}_160.ogg,
 * audio/{id}_320.ogg
 * - Indexed in Elasticsearch for search
 *
 * Design Pattern: The Song is a pure data model. Quality selection is delegated
 * to the AudioQualityStrategy (Strategy Pattern) and codec selection to
 * AudioCodecFactory (Factory Pattern).
 */
public class Song {

    private String id; // "song_001"
    private String title; // "Bohemian Rhapsody"
    private String artistId; // FK → Artist
    private String albumId; // FK → Album
    private int durationMs; // 354000 = 5:54
    private String genre;
    private long playCount;
    private int popularity; // 0-100 score
    private boolean explicit;

    // S3 keys for each quality level (3 files per song)
    private String s3Key96; // "audio/song_001_96.ogg"
    private String s3Key160; // "audio/song_001_160.ogg"
    private String s3Key320; // "audio/song_001_320.ogg"

    public Song() {
    }

    public Song(String id, String title, String artistId, String albumId,
            int durationMs, String genre) {
        this.id = id;
        this.title = title;
        this.artistId = artistId;
        this.albumId = albumId;
        this.durationMs = durationMs;
        this.genre = genre;
        this.playCount = 0;
        this.popularity = 50;
        this.explicit = false;

        // Simulate S3 key generation (in production, set during upload/transcode)
        this.s3Key96 = "audio/" + id + "_96.ogg";
        this.s3Key160 = "audio/" + id + "_160.ogg";
        this.s3Key320 = "audio/" + id + "_320.ogg";
    }

    /**
     * Returns the S3 key for the requested bitrate.
     * Used by StreamingService to build the CDN URL.
     */
    public String getS3KeyForBitrate(int bitrate) {
        return switch (bitrate) {
            case 96 -> s3Key96;
            case 160 -> s3Key160;
            case 320 -> s3Key320;
            default -> s3Key160; // fallback to normal
        };
    }

    public void incrementPlayCount() {
        this.playCount++;
        // In production: this is done async via Kafka → aggregation pipeline
    }

    public String getFormattedDuration() {
        int totalSec = durationMs / 1000;
        return (totalSec / 60) + ":" + String.format("%02d", totalSec % 60);
    }

    // ── Getters & Setters ─────────────────────────────────────
    public String getId() {
        return id;
    }

    public void setId(String v) {
        this.id = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String v) {
        this.artistId = v;
    }

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String v) {
        this.albumId = v;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int v) {
        this.durationMs = v;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String v) {
        this.genre = v;
    }

    public long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(long v) {
        this.playCount = v;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int v) {
        this.popularity = v;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(boolean v) {
        this.explicit = v;
    }

    public String getS3Key96() {
        return s3Key96;
    }

    public String getS3Key160() {
        return s3Key160;
    }

    public String getS3Key320() {
        return s3Key320;
    }

    @Override
    public String toString() {
        return "Song{id='" + id + "', title='" + title + "', duration=" + getFormattedDuration() +
                ", genre=" + genre + ", plays=" + playCount + "}";
    }
}
