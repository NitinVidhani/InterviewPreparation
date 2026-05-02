package com.lld.patterns.abstractfactory;

/** Concrete Product — Windows-styled Checkbox. */
public class WindowsCheckbox implements Checkbox {

    @Override
    public void render() {
        System.out.println("[Windows Checkbox] Rendering a square checkbox");
    }

    @Override
    public void toggle() {
        System.out.println("[Windows Checkbox] Toggled with Windows UI framework");
    }
}
