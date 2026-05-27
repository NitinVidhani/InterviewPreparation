package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.PlaybackSession;
import com.lld.hld.spotify.model.Song;
import com.lld.hld.spotify.repository.SongRepository;

/**
 * OBSERVER: Analytics Listener
 *
 * Reacts to playback events by recording play counts and logging
 * analytics data. In production, this writes to Kafka → Spark/Flink
 * → ClickHouse for dashboards and royalty calculations.
 */
public class AnalyticsListener implements PlaybackEventListener {

    private final SongRepository songRepo;
    private long totalPlays = 0;
    private long totalSkips = 0;

    public AnalyticsListener(SongRepository songRepo) {
        this.songRepo = songRepo;
    }

    @Override
    public void onSongStarted(PlaybackSession session) {
        System.out.printf("[Analytics] 📊 Stream started: song=%s, user=%s, quality=%dkbps%n",
                session.getSongId(), session.getUserId(), session.getQualityBitrate());
    }

    @Override
    public void onSongCompleted(PlaybackSession session) {
        totalPlays++;
        // Increment play count on the song (in production: async via Kafka)
        songRepo.findById(session.getSongId()).ifPresent(Song::incrementPlayCount);
        System.out.printf("[Analytics] ✅ Play counted: song=%s (total platform plays: %d)%n",
                session.getSongId(), totalPlays);
    }

    @Override
    public void onSongSkipped(PlaybackSession session) {
        totalSkips++;
        System.out.printf("[Analytics] ⏭️  Song skipped at %dms: song=%s (total skips: %d)%n",
                session.getPositionMs(), session.getSongId(), totalSkips);
    }

    public long getTotalPlays() {
        return totalPlays;
    }

    public long getTotalSkips() {
        return totalSkips;
    }
}
