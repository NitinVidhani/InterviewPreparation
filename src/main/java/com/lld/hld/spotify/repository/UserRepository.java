package com.lld.hld.spotify.repository;

import com.lld.hld.spotify.model.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates a PostgreSQL data store for user accounts.
 *
 * In production:
 * - PostgreSQL `users` table for core data
 * - `user_liked_songs` and `user_follows` as join tables
 * - Redis for session management
 */
public class UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    public void save(User user) {
        store.put(user.getId(), user);
    }

    public Optional<User> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    public Optional<User> findByUsername(String username) {
        return store.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    public int size() {
        return store.size();
    }
}
