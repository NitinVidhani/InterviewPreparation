package com.lld.patterns.factorymethod;

/**
 * Concrete Product — PushNotification.
 *
 * Implements the Notification interface for sending push notifications.
 */
public class PushNotification implements Notification {

    @Override
    public void send(String recipient, String message) {
        System.out.println("[Push] Sending to " + recipient + ": " + message);
    }
}
