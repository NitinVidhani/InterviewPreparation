package com.lld.patterns.abstractfactory;

/**
 * Concrete Factory — WindowsFactory.
 *
 * Creates the Windows family of UI components.
 * All products from this factory are guaranteed to be compatible with each
 * other.
 */
public class WindowsFactory implements UIFactory {

    @Override
    public Button createButton() {
        return new WindowsButton();
    }

    @Override
    public Checkbox createCheckbox() {
        return new WindowsCheckbox();
    }
}
