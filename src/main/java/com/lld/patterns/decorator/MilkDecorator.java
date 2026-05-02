package com.lld.patterns.decorator;

/**
 * Concrete Decorator — MilkDecorator.
 *
 * Adds milk to the wrapped coffee. Notice how it:
 * 1. Calls super to delegate to the wrapped coffee.
 * 2. Adds its own contribution (description + cost).
 */
public class MilkDecorator extends CoffeeDecorator {

    public MilkDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Milk";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 1.50;
    }
}
