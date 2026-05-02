package com.lld.patterns.flyweight;

import java.util.ArrayList;
import java.util.List;

/**
 * Client — Forest.
 *
 * Manages a collection of Tree instances. When planting a tree, it obtains
 * the shared TreeType from the factory — ensuring that thousands of trees
 * of the same type share a single TreeType object.
 */
public class Forest {

    private final List<Tree> trees = new ArrayList<>();

    /**
     * Plant a tree at position (x, y).
     * The TreeFactory returns a shared flyweight for the given type.
     */
    public void plantTree(int x, int y, String name, String color, String texture) {
        TreeType type = TreeFactory.getTreeType(name, color, texture);
        trees.add(new Tree(x, y, type));
    }

    /** Draw all trees in the forest. */
    public void draw() {
        for (Tree tree : trees) {
            tree.draw();
        }
    }

    public int getTreeCount() {
        return trees.size();
    }
}
