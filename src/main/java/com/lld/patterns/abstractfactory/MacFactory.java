package com.lld.patterns.abstractfactory;

/**
 * Concrete Factory — MacFactory.
 *
 * Creates the macOS family of UI components.
 */
public class MacFactory implements UIFactory {

    @Override
    public Button createButton() {
        return new MacButton();
    }

    @Override
    public Checkbox createCheckbox() {
        return new MacCheckbox();
    }
}
