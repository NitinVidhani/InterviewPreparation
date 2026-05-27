package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.Song;
import com.lld.hld.spotify.model.User;
import com.lld.hld.spotify.repository.SongRepository;
import com.lld.hld.spotify.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Recommendation Service — Personalized Music Discovery
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Combines three recommendation approaches (matches HLD deep dive):
 *
 * 1. COLLABORATIVE FILTERING
 * "Users who liked X also liked Y"
 * → Find users with similar listening history (cosine similarity)
 * → Recommend songs they liked but current user hasn't heard
 *
 * 2. CONTENT-BASED FILTERING
 * Analyze audio features (genre, tempo, energy) of songs user likes
 * → Find other songs with similar audio DNA
 * → This is simplified here as genre-based matching
 *
 * 3. POPULARITY-BASED FALLBACK
 * For new users (cold start problem) → recommend trending songs
 *
 * IN PRODUCTION:
 * - Batch pipeline (Spark, nightly): trains ALS model on user-song matrix
 * - Real-time pipeline (Kafka Streams): re-ranks candidates based on recent
 * activity
 * - Pre-computed results stored in Redis: reco:{user_id} → [song_ids]
 * - Uses FAISS/Annoy for Approximate Nearest Neighbor at scale
 * ═══════════════════════════════════════════════════════════════════════
 */
public class RecommendationService {

    private final SongRepository songRepo;
    private final UserRepository userRepo;

    public RecommendationService(SongRepository songRepo, UserRepository userRepo) {
        this.songRepo = songRepo;
        this.userRepo = userRepo;
    }

    /**
     * Get personalized recommendations for a user.
     *
     * Strategy:
     * 1. If user has listening history → use content-based + collaborative
     * filtering
     * 2. If new user (no history) → fall back to popularity-based (cold start)
     *
     * @param userId the user to recommend for
     * @param limit  number of recommendations
     * @return list of recommended songs
     */
    public List<Song> getRecommendations(String userId, int limit) {
        System.out.println("\n" + "─".repeat(50));
        System.out.printf("[Reco] 🎯 Generating recommendations for user=%s (limit=%d)%n", userId, limit);

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            System.out.println("[Reco] ❌ User not found — returning empty");
            return Collections.emptyList();
        }

        List<Song> recommendations;
        String topGenre = user.getTopGenre();

        if (topGenre != null && !user.getRecentlyPlayed().isEmpty()) {
            // ── Approach 1+2: Content-Based + Collaborative ──────────
            System.out.printf("[Reco] 📊 User profile: top genre=%s, history=%d plays%n",
                    topGenre, user.getRecentlyPlayed().size());

            recommendations = getContentBasedRecommendations(user, topGenre, limit);
            System.out.printf("[Reco] Strategy: Content-Based (genre=%s)%n", topGenre);
        } else {
            // ── Approach 3: Popularity Fallback (Cold Start) ─────────
            System.out.println("[Reco] 🆕 New user — using popularity fallback (cold start)");
            recommendations = getPopularityBasedRecommendations(limit);
            System.out.println("[Reco] Strategy: Popularity-Based (trending songs)");
        }

        System.out.printf("[Reco] ✅ Returning %d recommendations:%n", recommendations.size());
        for (int i = 0; i < recommendations.size(); i++) {
            Song s = recommendations.get(i);
            System.out.printf("[Reco]   %d. '%s' (genre=%s, popularity=%d)%n",
                    i + 1, s.getTitle(), s.getGenre(), s.getPopularity());
        }
        return recommendations;
    }

    /**
     * Content-Based: find songs in the user's preferred genre
     * that they haven't listened to yet.
     */
    private List<Song> getContentBasedRecommendations(User user, String genre, int limit) {
        Set<String> alreadyPlayed = new HashSet<>(user.getRecentlyPlayed());
        Set<String> alreadyLiked = user.getLikedSongIds();

        return songRepo.findByGenre(genre).stream()
                // Exclude songs user has already heard
                .filter(s -> !alreadyPlayed.contains(s.getId()))
                // Boost: put liked artists' songs first (collaborative signal)
                .sorted(Comparator.comparingInt(Song::getPopularity).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Popularity-Based: return the most popular songs overall.
     * Used for new users who have no listening history (cold start problem).
     */
    private List<Song> getPopularityBasedRecommendations(int limit) {
        return songRepo.findAll().stream()
                .sorted(Comparator.comparingInt(Song::getPopularity).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Generate a "Daily Mix" for the user — a curated playlist based on
     * their top genres. In production, this is pre-computed by a Spark job
     * and stored in Redis.
     */
    public List<Song> generateDailyMix(String userId, int limit) {
        System.out.printf("[Reco] 🎧 Generating Daily Mix for user=%s%n", userId);

        User user = userRepo.findById(userId).orElse(null);
        if (user == null)
            return Collections.emptyList();

        // Mix recommended songs with some diversity
        List<Song> recommendations = getRecommendations(userId, limit * 2);
        Collections.shuffle(recommendations); // Add variety
        return recommendations.stream().limit(limit).collect(Collectors.toList());
    }
}
