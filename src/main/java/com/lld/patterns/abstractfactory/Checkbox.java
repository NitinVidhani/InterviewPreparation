package com.lld.patterns.abstractfactory;

/**
 * Abstract Product — Checkbox.
 *
 * Every platform-specific checkbox will implement this interface.
 */
public interface Checkbox {
    void render();

    void toggle();
}
