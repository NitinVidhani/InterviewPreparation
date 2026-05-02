package com.lld.patterns.abstractfactory;

/**
 * Abstract Factory Interface — UIFactory.
 *
 * Declares creation methods for each distinct product type (Button, Checkbox).
 * Each concrete factory (WindowsFactory, MacFactory) produces a FAMILY of
 * products that belong together (all Windows-styled, or all Mac-styled).
 *
 * KEY INSIGHT: The client code calls these methods and works with the returned
 * abstract types — it never references WindowsButton or MacCheckbox directly.
 */
public interface UIFactory {

    Button createButton();

    Checkbox createCheckbox();
}
