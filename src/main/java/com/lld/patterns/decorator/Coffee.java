package com.lld.patterns.decorator;

/**
 * Component Interface — Coffee.
 *
 * Defines the common interface for both the base coffee and all decorators.
 * Both concrete coffees and decorators implement this, so they are
 * interchangeable from the client's perspective.
 */
public interface Coffee {

    /** Get a human-readable description of this coffee. */
    String getDescription();

    /** Get the total cost of this coffee (including all add-ons). */
    double getCost();
}
