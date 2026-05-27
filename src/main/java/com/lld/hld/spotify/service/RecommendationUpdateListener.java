package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.PlaybackSession;
import com.lld.hld.spotify.model.Song;
import com.lld.hld.spotify.model.User;
import com.lld.hld.spotify.repository.SongRepository;
import com.lld.hld.spotify.repository.UserRepository;

/**
 * OBSERVER: Recommendation Listener
 *
 * Reacts to playback events by updating the user's listening profile.
 * This data feeds into the recommendation engine to improve
 * personalized suggestions (Discover Weekly, Daily Mixes).
 *
 * In production: writes to a separate Kafka consumer group that feeds
 * into the ML pipeline for model training.
 */
public class RecommendationUpdateListener implements PlaybackEventListener {

    private final UserRepository userRepo;
    private final SongRepository songRepo;

    public RecommendationUpdateListener(UserRepository userRepo, SongRepository songRepo) {
        this.userRepo = userRepo;
        this.songRepo = songRepo;
    }

    @Override
    public void onSongStarted(PlaybackSession session) {
        // No action on start — wait for completion/skip signal
    }

    @Override
    public void onSongCompleted(PlaybackSession session) {
        // Strong positive signal → update user's genre preferences
        userRepo.findById(session.getUserId()).ifPresent(user -> {
            songRepo.findById(session.getSongId()).ifPresent(song -> {
                user.recordPlay(song.getId(), song.getGenre());
                System.out.printf("[Reco] 🎯 Profile updated: user=%s, genre=%s (completed)%n",
                        user.getUsername(), song.getGenre());
            });
        });
    }

    @Override
    public void onSongSkipped(PlaybackSession session) {
        // Negative signal — in production, reduces weight for this genre/artist
        System.out.printf("[Reco] 👎 Negative signal: user=%s skipped song=%s%n",
                session.getUserId(), session.getSongId());
    }
}
