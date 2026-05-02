package com.lld.patterns.factorymethod;

/**
 * Concrete Creator — EmailNotificationFactory.
 *
 * Overrides the factory method to return an EmailNotification instance.
 * To add a new notification channel (e.g., Slack), you create a NEW
 * subclass — you never modify existing code. This is the Open/Closed Principle.
 */
public class EmailNotificationFactory extends NotificationFactory {

    @Override
    public Notification createNotification() {
        return new EmailNotification();
    }
}
