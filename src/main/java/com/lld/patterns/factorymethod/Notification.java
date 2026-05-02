package com.lld.patterns.factorymethod;

/**
 * Product Interface — defines the contract that all notifications must follow.
 *
 * Every concrete notification type (Email, SMS, Push) will implement this
 * interface, ensuring they all provide a send() method.
 */
public interface Notification {

    /** Send the notification to the given recipient with the given message. */
    void send(String recipient, String message);
}
