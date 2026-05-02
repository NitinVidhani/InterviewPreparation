package com.lld.patterns.flyweight;

/**
 * Context Object — Tree.
 *
 * Represents a single tree instance in the forest. It stores:
 * - EXTRINSIC state: x, y (position — unique per tree)
 * - Reference to the shared FLYWEIGHT: TreeType (shared among many trees)
 *
 * Each Tree instance is lightweight because the heavy data (textures, colors)
 * is shared via the TreeType flyweight.
 */
public class Tree {

    // Extrinsic state — unique per tree instance
    private final int x;
    private final int y;

    // Reference to the shared flyweight (intrinsic state)
    private final TreeType type;

    public Tree(int x, int y, TreeType type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

    public void draw() {
        type.draw(x, y);
    }
}
