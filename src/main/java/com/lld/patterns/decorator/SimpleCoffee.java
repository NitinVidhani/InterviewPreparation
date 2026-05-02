package com.lld.patterns.decorator;

/**
 * Concrete Component — SimpleCoffee.
 *
 * The base object that decorators will wrap. Represents a plain coffee
 * with no extras.
 */
public class SimpleCoffee implements Coffee {

    @Override
    public String getDescription() {
        return "Simple Coffee";
    }

    @Override
    public double getCost() {
        return 5.00;
    }
}
