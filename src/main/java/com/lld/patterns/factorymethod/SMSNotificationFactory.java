package com.lld.patterns.factorymethod;

/**
 * Concrete Creator — SMSNotificationFactory.
 *
 * Overrides the factory method to return an SMSNotification instance.
 */
public class SMSNotificationFactory extends NotificationFactory {

    @Override
    public Notification createNotification() {
        return new SMSNotification();
    }
}
