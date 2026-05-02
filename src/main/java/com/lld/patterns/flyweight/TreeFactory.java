package com.lld.patterns.flyweight;

import java.util.HashMap;
import java.util.Map;

/**
 * Flyweight Factory — TreeFactory.
 *
 * Manages a pool of TreeType flyweights. When a tree type is requested:
 * - If it already exists in the cache → return it (shared).
 * - If it doesn't exist → create it, cache it, and return it.
 *
 * This ensures that each unique (name, color, texture) combination
 * is only instantiated ONCE, regardless of how many trees of that type
 * appear in the forest.
 */
public class TreeFactory {

    // Cache: key → shared TreeType flyweight
    private static final Map<String, TreeType> treeTypes = new HashMap<>();

    /**
     * Returns a shared TreeType for the given parameters.
     * Creates one only if it doesn't exist yet.
     */
    public static TreeType getTreeType(String name, String color, String texture) {
        String key = name + "_" + color + "_" + texture;

        if (!treeTypes.containsKey(key)) {
            treeTypes.put(key, new TreeType(name, color, texture));
        }

        return treeTypes.get(key);
    }

    /**
     * Returns the number of unique tree types created (useful for verification).
     */
    public static int getTypeCount() {
        return treeTypes.size();
    }
}
