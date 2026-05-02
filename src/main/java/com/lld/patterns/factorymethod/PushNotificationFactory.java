package com.lld.patterns.factorymethod;

/**
 * Concrete Creator — PushNotificationFactory.
 *
 * Overrides the factory method to return a PushNotification instance.
 */
public class PushNotificationFactory extends NotificationFactory {

    @Override
    public Notification createNotification() {
        return new PushNotification();
    }
}
