package com.lld.hld.spotify;

import com.lld.hld.spotify.model.*;
import com.lld.hld.spotify.repository.*;
import com.lld.hld.spotify.service.*;

import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Spotify — Demo Runner
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Wires all components together and demonstrates the full system:
 *
 * 1. Setup: Seed catalog with songs, artists, users
 * 2. Stream a song (premium user, high bandwidth → 320kbps)
 * 3. Stream a song (free user, limited to 160kbps)
 * 4. Adaptive quality: switch to DataSaver strategy
 * 5. Search for songs (full-text + autocomplete)
 * 6. Cache hit demonstration (second stream of same song)
 * 7. Playlist CRUD: create, add songs, remove
 * 8. Collaborative playlist with OCC conflict detection
 * 9. Recommendations: content-based vs cold start
 * 10. Song skip → analytics + recommendation signal
 *
 * DESIGN PATTERNS DEMONSTRATED:
 * 🔹 STRATEGY → AudioQualityStrategy (swap quality algorithms at runtime)
 * 🔹 OBSERVER → PlaybackEventListener (decouple streaming from analytics/reco)
 * 🔹 STATE → PlaybackSession.PlayerState (IDLE → PLAYING → PAUSED)
 * 🔹 FACTORY → Song.getS3KeyForBitrate() (select correct audio file)
 * 🔹 REPOSITORY → Separation of data access from business logic
 * ═══════════════════════════════════════════════════════════════════════
 */
public class SpotifyMain {

    public static void main(String[] args) {

        // ── Wire up all components (Dependency Injection by hand) ────────────
        SongRepository songRepo = new SongRepository();
        UserRepository userRepo = new UserRepository();
        PlaylistRepository playlistRepo = new PlaylistRepository();
        CacheService cache = new CacheService();

        AudioQualityStrategy adaptiveStrategy = new AdaptiveQualityStrategy();
        StreamingService streaming = new StreamingService(songRepo, userRepo, cache, adaptiveStrategy);
        SearchService search = new SearchService(songRepo);
        RecommendationService reco = new RecommendationService(songRepo, userRepo);
        PlaylistService playlists = new PlaylistService(playlistRepo);

        // Register observers (in production: Kafka consumers)
        AnalyticsListener analytics = new AnalyticsListener(songRepo);
        RecommendationUpdateListener recoUpdater = new RecommendationUpdateListener(userRepo, songRepo);
        streaming.addListener(analytics);
        streaming.addListener(recoUpdater);

        // ── Seed Data ───────────────────────────────────────────────────────
        seedCatalog(songRepo);
        seedUsers(userRepo);

        System.out.println("\n" + "═".repeat(60));
        System.out.println("  🎵 SPOTIFY SYSTEM DEMO");
        System.out.println("═".repeat(60));

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 1: Premium user streams a song (high bandwidth)
        // → Adaptive strategy selects 320kbps
        // ═══════════════════════════════════════════════════════════════
        printHeader("1. Premium User Streams (Adaptive → 320kbps)");
        PlaybackSession session1 = streaming.streamSong("song_001", "user_001", 500);
        System.out.println("   Session: " + session1);

        // Complete the song → triggers analytics + reco update
        streaming.completeSong("user_001");

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 2: Free user streams (capped at 160kbps)
        // → Even with 500kbps bandwidth, free tier caps at 160
        // ═══════════════════════════════════════════════════════════════
        printHeader("2. Free User Streams (Adaptive → 160kbps cap)");
        PlaybackSession session2 = streaming.streamSong("song_003", "user_002", 500);
        System.out.println("   Session: " + session2);
        streaming.completeSong("user_002");

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 3: Switch to Data Saver strategy → 96kbps
        // ═══════════════════════════════════════════════════════════════
        printHeader("3. Data Saver Mode (Strategy Switch → 96kbps)");
        streaming.setQualityStrategy(new DataSaverStrategy());
        PlaybackSession session3 = streaming.streamSong("song_002", "user_001", 500);
        System.out.println("   Session: " + session3);
        streaming.completeSong("user_001");

        // Switch back to adaptive
        streaming.setQualityStrategy(new AdaptiveQualityStrategy());

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 4: Low bandwidth → adaptive downgrades to 96kbps
        // ═══════════════════════════════════════════════════════════════
        printHeader("4. Low Bandwidth (Adaptive → 96kbps)");
        PlaybackSession session4 = streaming.streamSong("song_004", "user_001", 100);
        System.out.println("   Session: " + session4);
        streaming.completeSong("user_001");

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 5: Cache HIT — second stream of same song
        // ═══════════════════════════════════════════════════════════════
        printHeader("5. Cache HIT — Streaming Same Song Again");
        PlaybackSession session5 = streaming.streamSong("song_001", "user_001", 500);
        System.out.println("   Cache stats: hits=" + cache.getHits() + ", misses=" + cache.getMisses()
                + ", ratio=" + cache.getHitRatio());
        streaming.completeSong("user_001");

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 6: Song Skip → negative signal
        // ═══════════════════════════════════════════════════════════════
        printHeader("6. Song Skip (< 30s → doesn't count as play)");
        PlaybackSession session6 = streaming.streamSong("song_005", "user_001", 500);
        session6.updatePosition(5_000); // Only 5 seconds played
        streaming.skipSong("user_001");

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 7: Search — full text + autocomplete
        // ═══════════════════════════════════════════════════════════════
        printHeader("7. Search — Full Text");
        List<Song> searchResults = search.search("bohemian", 5);

        printHeader("7b. Autocomplete");
        List<String> suggestions = search.autocomplete("sta", 3);

        printHeader("7c. Genre Browse");
        List<Song> rockSongs = search.searchByGenre("rock", 5);

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 8: Playlist — create, add songs, remove
        // ═══════════════════════════════════════════════════════════════
        printHeader("8. Playlist CRUD");
        Playlist myPlaylist = playlists.createPlaylist("Chill Vibes", "user_001", true);
        String snap1 = playlists.addSong(myPlaylist.getId(), "song_001", "user_001", myPlaylist.getSnapshotId());
        String snap2 = playlists.addSong(myPlaylist.getId(), "song_003", "user_001", snap1);
        String snap3 = playlists.addSong(myPlaylist.getId(), "song_005", "user_001", snap2);
        System.out.println("   Playlist: " + playlists.getPlaylist(myPlaylist.getId()));
        System.out.println("   Tracks: " + playlists.getPlaylist(myPlaylist.getId()).getTracks());

        // Remove a song
        playlists.removeSong(myPlaylist.getId(), "song_003", "user_001");
        System.out.println("   After removal: " + playlists.getPlaylist(myPlaylist.getId()).getTracks());

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 9: Collaborative Playlist with OCC Conflict
        // ═══════════════════════════════════════════════════════════════
        printHeader("9. Collaborative Playlist — OCC Conflict");
        Playlist collab = playlists.createPlaylist("Party Mix", "user_001", true);
        playlists.enableCollaboration(collab.getId(), "user_001");
        String collabSnap = collab.getSnapshotId();

        // User A adds a song → snapshot changes
        String newSnap = playlists.addSong(collab.getId(), "song_002", "user_001", collabSnap);

        // User B tries to add with OLD snapshot → CONFLICT!
        try {
            playlists.addSong(collab.getId(), "song_004", "user_002", collabSnap);
        } catch (ConcurrentModificationException e) {
            System.out.println("   ⚠️  CONFLICT caught: " + e.getMessage());
        }

        // User B retries with CURRENT snapshot → SUCCESS
        String retrySnap = playlists.addSong(collab.getId(), "song_004", "user_002", newSnap);
        System.out.println("   ✅ Retry succeeded with snapshot=" + retrySnap);
        System.out.println("   Collab playlist: " + playlists.getPlaylist(collab.getId()).getTracks());

        // ═══════════════════════════════════════════════════════════════
        // SCENARIO 10: Recommendations
        // ═══════════════════════════════════════════════════════════════
        printHeader("10a. Recommendations — Content-Based (user with history)");
        List<Song> recoResults = reco.getRecommendations("user_001", 5);

        printHeader("10b. Recommendations — Cold Start (new user)");
        // Create a brand new user with no history
        User newUser = new User("user_003", "newbie", "newbie@mail.com", User.Tier.FREE);
        userRepo.save(newUser);
        List<Song> coldStartReco = reco.getRecommendations("user_003", 5);

        printHeader("10c. Daily Mix");
        List<Song> dailyMix = reco.generateDailyMix("user_001", 5);

        // ═══════════════════════════════════════════════════════════════
        // Summary
        // ═══════════════════════════════════════════════════════════════
        printHeader("Summary");
        System.out.println("   Total songs in catalog : " + songRepo.size());
        System.out.println("   Total users            : " + userRepo.size());
        System.out.println("   Total playlists        : " + playlistRepo.size());
        System.out.println("   Cache entries          : " + cache.size());
        System.out.println("   Cache hit ratio        : " + cache.getHitRatio());
        System.out.println("   Total plays recorded   : " + analytics.getTotalPlays());
        System.out.println("   Total skips recorded   : " + analytics.getTotalSkips());

        System.out.println("\n   Design Patterns Used:");
        System.out.println("   🔹 STRATEGY  → AudioQualityStrategy (High/DataSaver/Adaptive)");
        System.out.println("   🔹 OBSERVER  → PlaybackEventListener (Analytics + Reco listeners)");
        System.out.println("   🔹 STATE     → PlaybackSession.PlayerState (IDLE/PLAYING/PAUSED/BUFFERING)");
        System.out.println("   🔹 FACTORY   → Song.getS3KeyForBitrate() (select audio file by quality)");
        System.out.println("   🔹 OCC       → Playlist snapshot_id (collaborative conflict detection)");

        System.out.println("\n" + "═".repeat(60));
        System.out.println("  Demo complete.");
        System.out.println("═".repeat(60) + "\n");
    }

    // ── Seed Data ─────────────────────────────────────────────────────

    private static void seedCatalog(SongRepository songRepo) {
        // Rock songs
        Song s1 = new Song("song_001", "Bohemian Rhapsody", "artist_001", "album_001", 354000, "rock");
        s1.setPopularity(95);
        Song s2 = new Song("song_002", "Stairway to Heaven", "artist_002", "album_002", 482000, "rock");
        s2.setPopularity(92);
        Song s3 = new Song("song_003", "Hotel California", "artist_003", "album_003", 391000, "rock");
        s3.setPopularity(90);

        // Pop songs
        Song s4 = new Song("song_004", "Shape of You", "artist_004", "album_004", 233000, "pop");
        s4.setPopularity(96);
        Song s5 = new Song("song_005", "Blinding Lights", "artist_005", "album_005", 200000, "pop");
        s5.setPopularity(94);
        Song s6 = new Song("song_006", "Starboy", "artist_005", "album_005", 230000, "pop");
        s6.setPopularity(88);

        // Hip-Hop songs
        Song s7 = new Song("song_007", "Lose Yourself", "artist_006", "album_006", 326000, "hip-hop");
        s7.setPopularity(91);
        Song s8 = new Song("song_008", "Sicko Mode", "artist_007", "album_007", 312000, "hip-hop");
        s8.setPopularity(89);

        // Electronic
        Song s9 = new Song("song_009", "Strobe", "artist_008", "album_008", 637000, "electronic");
        s9.setPopularity(85);
        Song s10 = new Song("song_010", "Levels", "artist_009", "album_009", 209000, "electronic");
        s10.setPopularity(87);

        songRepo.save(s1);
        songRepo.save(s2);
        songRepo.save(s3);
        songRepo.save(s4);
        songRepo.save(s5);
        songRepo.save(s6);
        songRepo.save(s7);
        songRepo.save(s8);
        songRepo.save(s9);
        songRepo.save(s10);

        System.out.println("[Setup] 📀 Seeded " + songRepo.size() + " songs across 4 genres");
    }

    private static void seedUsers(UserRepository userRepo) {
        User u1 = new User("user_001", "nitin", "nitin@mail.com", User.Tier.PREMIUM);
        u1.likeSong("song_001");
        u1.likeSong("song_002");
        u1.followArtist("artist_001");

        User u2 = new User("user_002", "alex", "alex@mail.com", User.Tier.FREE);
        u2.likeSong("song_004");
        u2.followArtist("artist_005");

        userRepo.save(u1);
        userRepo.save(u2);

        System.out.println("[Setup] 👤 Seeded " + userRepo.size() + " users (1 premium, 1 free)");
    }

    private static void printHeader(String title) {
        System.out.println("\n── " + title + " " + "─".repeat(Math.max(0, 55 - title.length())));
    }
}
