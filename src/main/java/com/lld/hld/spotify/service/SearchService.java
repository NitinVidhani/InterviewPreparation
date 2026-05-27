package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.Song;
import com.lld.hld.spotify.repository.SongRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Search Service — Full-Text Search with Autocomplete
 * ═══════════════════════════════════════════════════════════════════════
 *
 * In production:
 * - Backed by an Elasticsearch cluster (50+ data nodes)
 * - Autocomplete: Redis sorted sets for top-1000 queries + ES completion
 * suggester
 * - Full search: multi-field match with fuzzy matching + BM25 scoring
 * - Personalized ranking: boost songs from followed artists, preferred genres
 *
 * Search Ranking Formula:
 * Score = text_relevance × 0.4 (from Elasticsearch BM25)
 * + popularity × 0.3 (from play_count normalization)
 * + recency × 0.1 (newer releases scored higher)
 * + personalization × 0.2 (followed artists, genre affinity)
 *
 * Optimizations:
 * - Client-side debounce: 200ms between keystrokes reduces queries by ~60%
 * - Autocomplete returns only song_id + title + artist (minimal payload)
 * - Full search returns paginated results (limit=20, offset-based)
 * ═══════════════════════════════════════════════════════════════════════
 */
public class SearchService {

    private final SongRepository songRepo;

    public SearchService(SongRepository songRepo) {
        this.songRepo = songRepo;
    }

    /**
     * Full-text search for songs.
     * In production: Elasticsearch multi-field match query.
     *
     * @param query the search query (e.g., "bohemian rhapsody")
     * @param limit max results to return
     * @return songs matching the query, ranked by popularity
     */
    public List<Song> search(String query, int limit) {
        System.out.printf("[Search] 🔍 Query: '%s' (limit=%d)%n", query, limit);

        List<Song> results = songRepo.search(query).stream()
                .limit(limit)
                .collect(Collectors.toList());

        System.out.printf("[Search] Found %d results%n", results.size());
        for (int i = 0; i < results.size(); i++) {
            Song s = results.get(i);
            System.out.printf("[Search]   %d. '%s' (popularity=%d, genre=%s)%n",
                    i + 1, s.getTitle(), s.getPopularity(), s.getGenre());
        }
        return results;
    }

    /**
     * Autocomplete — returns matches as user types.
     * In production: ES completion suggester + Redis prefix trie.
     *
     * @param prefix partial query (e.g., "bohem")
     * @param limit  max suggestions
     * @return top matches for autocomplete dropdown
     */
    public List<String> autocomplete(String prefix, int limit) {
        System.out.printf("[Search] ⌨️  Autocomplete: '%s'%n", prefix);

        List<String> suggestions = songRepo.search(prefix).stream()
                .limit(limit)
                .map(s -> s.getTitle() + " — " + s.getArtistId())
                .collect(Collectors.toList());

        System.out.printf("[Search] Suggestions: %s%n", suggestions);
        return suggestions;
    }

    /**
     * Search songs by genre — used by browse/explore page.
     */
    public List<Song> searchByGenre(String genre, int limit) {
        System.out.printf("[Search] 🎵 Genre search: '%s'%n", genre);
        return songRepo.findByGenre(genre).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
