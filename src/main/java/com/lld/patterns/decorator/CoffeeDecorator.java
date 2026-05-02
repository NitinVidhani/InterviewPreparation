package com.lld.patterns.decorator;

/**
 * Base Decorator — CoffeeDecorator.
 *
 * This abstract class implements the same Coffee interface and holds a
 * reference to a wrapped Coffee object. It delegates all calls to the
 * wrapped object by default. Concrete decorators override methods to
 * add their own behavior BEFORE or AFTER delegating.
 *
 * WHY an abstract class instead of just implementing Coffee directly?
 * It provides a common "wrapping" mechanism so concrete decorators
 * don't have to duplicate the delegation boilerplate.
 */
public abstract class CoffeeDecorator implements Coffee {

    // The wrapped component — this is the key to the Decorator pattern.
    // It can be a base coffee OR another decorator (enabling stacking).
    protected final Coffee decoratedCoffee;

    public CoffeeDecorator(Coffee coffee) {
        this.decoratedCoffee = coffee;
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription();
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost();
    }
}
