package com.lld.patterns.factorymethod;

/**
 * Concrete Product — SMSNotification.
 *
 * Implements the Notification interface for sending notifications via SMS.
 */
public class SMSNotification implements Notification {

    @Override
    public void send(String recipient, String message) {
        System.out.println("[SMS] Sending to " + recipient + ": " + message);
    }
}
