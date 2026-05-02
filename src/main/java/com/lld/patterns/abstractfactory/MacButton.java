package com.lld.patterns.abstractfactory;

/** Concrete Product — macOS-styled Button. */
public class MacButton implements Button {

    @Override
    public void render() {
        System.out.println("[Mac Button] Rendering a rounded, glossy button");
    }

    @Override
    public void onClick() {
        System.out.println("[Mac Button] Click handled with Cocoa event system");
    }
}
