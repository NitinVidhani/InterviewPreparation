package com.lld.patterns.abstractfactory;

/**
 * Abstract Product — Button.
 *
 * Every platform-specific button (Windows, Mac) will implement this interface.
 */
public interface Button {
    void render();

    void onClick();
}
