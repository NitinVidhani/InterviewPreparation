package com.lld.hld.spotify.model;

/**
 * Represents an album containing multiple songs.
 *
 * In production: stored in PostgreSQL `albums` table.
 */
public class Album {

    public enum AlbumType {
        ALBUM, SINGLE, COMPILATION
    }

    private String id; // "album_001"
    private String title; // "A Night at the Opera"
    private String artistId;
    private String coverUrl;
    private int totalTracks;
    private AlbumType albumType;

    public Album() {
    }

    public Album(String id, String title, String artistId, AlbumType albumType, int totalTracks) {
        this.id = id;
        this.title = title;
        this.artistId = artistId;
        this.albumType = albumType;
        this.totalTracks = totalTracks;
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

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String v) {
        this.coverUrl = v;
    }

    public int getTotalTracks() {
        return totalTracks;
    }

    public void setTotalTracks(int v) {
        this.totalTracks = v;
    }

    public AlbumType getAlbumType() {
        return albumType;
    }

    public void setAlbumType(AlbumType v) {
        this.albumType = v;
    }

    @Override
    public String toString() {
        return "Album{title='" + title + "', type=" + albumType + ", tracks=" + totalTracks + "}";
    }
}
