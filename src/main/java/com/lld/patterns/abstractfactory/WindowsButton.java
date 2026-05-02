package com.lld.patterns.abstractfactory;

/** Concrete Product — Windows-styled Button. */
public class WindowsButton implements Button {

    @Override
    public void render() {
        System.out.println("[Windows Button] Rendering a flat, rectangular button");
    }

    @Override
    public void onClick() {
        System.out.println("[Windows Button] Click handled with Windows event system");
    }
}
