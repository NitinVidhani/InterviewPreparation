package com.lld.patterns.factorymethod;

/**
 * Concrete Product — EmailNotification.
 *
 * Implements the Notification interface for sending notifications via email.
 */
public class EmailNotification implements Notification {

    @Override
    public void send(String recipient, String message) {
        System.out.println("[Email] Sending to " + recipient + ": " + message);
    }
}
