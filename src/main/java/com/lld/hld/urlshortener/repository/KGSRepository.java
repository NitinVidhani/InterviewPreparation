package com.lld.hld.urlshortener.repository;

import java.util.HashSet;
import java.util.Set;

/**
 * In-memory simulation of the KGS (Key Generation Service) database.
 *
 * In production this would be a separate DB with a table:
 * kgs_keys (key VARCHAR(6) PRIMARY KEY, is_used BOOLEAN)
 * Pre-populated with 56 Billion Base62 codes.
 */
public class KGSRepository {

    // Simulates the "used keys" set (in production: a proper DB with a unique
    // index)
    private final Set<String> usedKeys = new HashSet<>();

    /**
     * Tries to INSERT a new key into the KGS DB.
     * Returns true if saved successfully (unique), false if duplicate.
     *
     * Simulates: INSERT IGNORE INTO kgs_keys (key, is_used) VALUES (?, false)
     */
    public synchronized boolean saveIfUnique(String key) {
        return usedKeys.add(key); // add() returns false if already present
    }

    /**
     * Marks a key as used in the DB the moment it is loaded into the in-memory
     * buffer.
     * This prevents two KGS instances from handing out the same key.
     *
     * Simulates: UPDATE kgs_keys SET is_used = true WHERE key = ?
     * (In our simulation, saveIfUnique already handles this via the Set.)
     */
    public synchronized void markAsUsed(String key) {
        // Already tracked in usedKeys set. In a real DB this would be an UPDATE call.
        usedKeys.add(key);
    }

    /** How many keys have been issued so far (for monitoring/metrics). */
    public int getTotalKeysIssued() {
        return usedKeys.size();
    }
}
