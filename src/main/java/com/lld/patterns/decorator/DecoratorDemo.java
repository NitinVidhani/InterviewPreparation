package com.lld.patterns.decorator;

/**
 * Driver class that demonstrates the Decorator pattern.
 *
 * Notice how decorators are STACKED:
 * SimpleCoffee → wrapped by MilkDecorator → wrapped by SugarDecorator
 *
 * Each decorator adds its own description and cost ON TOP of what's already
 * there. You can combine them in any order, and add/remove them at runtime.
 */
public class DecoratorDemo {

    public static void main(String[] args) {
        System.out.println("=== Decorator Pattern Demo ===\n");

        // 1. A plain coffee
        Coffee coffee = new SimpleCoffee();
        System.out.println(coffee.getDescription() + " → $" + coffee.getCost());

        // 2. Add milk (wrap the plain coffee in a MilkDecorator)
        coffee = new MilkDecorator(coffee);
        System.out.println(coffee.getDescription() + " → $" + coffee.getCost());

        // 3. Add sugar (wrap the milk-coffee in a SugarDecorator)
        coffee = new SugarDecorator(coffee);
        System.out.println(coffee.getDescription() + " → $" + coffee.getCost());

        // 4. Add whipped cream (wrap again)
        coffee = new WhippedCreamDecorator(coffee);
        System.out.println(coffee.getDescription() + " → $" + coffee.getCost());

        // 5. A completely different combo — decorators are independent
        System.out.println("\n--- Another order ---");
        Coffee fancyCoffee = new WhippedCreamDecorator(
                new WhippedCreamDecorator( // double whipped cream!
                        new MilkDecorator(
                                new SimpleCoffee())));
        System.out.println(fancyCoffee.getDescription() + " → $" + fancyCoffee.getCost());
    }
}
