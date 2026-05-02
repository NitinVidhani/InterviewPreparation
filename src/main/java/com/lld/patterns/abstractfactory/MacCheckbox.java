package com.lld.patterns.abstractfactory;

/** Concrete Product — macOS-styled Checkbox. */
public class MacCheckbox implements Checkbox {

    @Override
    public void render() {
        System.out.println("[Mac Checkbox] Rendering a rounded checkbox with animation");
    }

    @Override
    public void toggle() {
        System.out.println("[Mac Checkbox] Toggled with Cocoa UI framework");
    }
}
