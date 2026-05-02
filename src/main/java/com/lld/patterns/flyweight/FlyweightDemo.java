package com.lld.patterns.flyweight;

/**
 * Driver class that demonstrates the Flyweight pattern.
 *
 * We plant 10 trees of 3 types. Without Flyweight, that's 10 full objects.
 * With Flyweight, only 3 TreeType objects are created; the remaining 7
 * reuse existing ones. In a real game with 1,000,000 trees, the savings
 * are enormous.
 */
public class FlyweightDemo {

    public static void main(String[] args) {
        System.out.println("=== Flyweight Pattern Demo ===\n");

        Forest forest = new Forest();

        // Plant several trees — same type will share a TreeType flyweight
        forest.plantTree(10, 20, "Oak", "Green", "oak_texture.png");
        forest.plantTree(30, 40, "Pine", "Dark Green", "pine_texture.png");
        forest.plantTree(50, 60, "Oak", "Green", "oak_texture.png"); // reuses Oak
        forest.plantTree(70, 80, "Birch", "White", "birch_texture.png");
        forest.plantTree(90, 10, "Pine", "Dark Green", "pine_texture.png"); // reuses Pine
        forest.plantTree(15, 25, "Oak", "Green", "oak_texture.png"); // reuses Oak
        forest.plantTree(35, 45, "Birch", "White", "birch_texture.png");// reuses Birch
        forest.plantTree(55, 65, "Oak", "Green", "oak_texture.png"); // reuses Oak
        forest.plantTree(75, 85, "Pine", "Dark Green", "pine_texture.png"); // reuses Pine
        forest.plantTree(95, 15, "Oak", "Green", "oak_texture.png"); // reuses Oak

        System.out.println("\nDrawing the forest:");
        forest.draw();

        // Proof of memory savings
        System.out.println("\n--- Statistics ---");
        System.out.println("Total trees planted: " + forest.getTreeCount()); // 10
        System.out.println("Unique TreeType flyweights created: " + TreeFactory.getTypeCount()); // 3
        System.out.println("Memory saved by sharing: " +
                (forest.getTreeCount() - TreeFactory.getTypeCount()) + " duplicate objects avoided");
    }
}
