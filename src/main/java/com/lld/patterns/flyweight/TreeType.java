package com.lld.patterns.flyweight;

/**
 * Flyweight — TreeType.
 *
 * Represents the INTRINSIC (shared) state of a tree: name, color, and texture.
 * These properties are the same for all trees of the same type.
 *
 * IMPORTANT: Flyweight objects must be IMMUTABLE. Once created, their
 * intrinsic state should never change. This is what makes sharing safe.
 */
public class TreeType {

    // All fields are final — immutable intrinsic state
    private final String name;
    private final String color;
    private final String texture;

    public TreeType(String name, String color, String texture) {
        this.name = name;
        this.color = color;
        this.texture = texture;
        System.out.println("[Flyweight] Created new TreeType: " + name);
    }

    /**
     * Renders the tree at the given position.
     *
     * @param x extrinsic state — x coordinate (unique per tree instance)
     * @param y extrinsic state — y coordinate (unique per tree instance)
     *
     *          The extrinsic state is NOT stored in the flyweight — it's passed in.
     */
    public void draw(int x, int y) {
        System.out.printf("  Drawing [%s] tree (%s, %s) at (%d, %d)%n",
                name, color, texture, x, y);
    }

    public String getName() {
        return name;
    }
}
