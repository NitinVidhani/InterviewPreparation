package com.lld.patterns.factorymethod;

/**
 * Driver class that demonstrates the Factory Method pattern.
 *
 * Notice how the client code (this main method) works with creators
 * and products via their ABSTRACT types, not concrete classes.
 * To add a new notification type you would:
 * 1. Create a new ConcreteProduct (e.g., SlackNotification).
 * 2. Create a new ConcreteCreator (e.g., SlackNotificationFactory).
 * No existing classes need to be modified — this is the Open/Closed Principle.
 */
public class FactoryMethodDemo {

    public static void main(String[] args) {
        System.out.println("=== Factory Method Pattern Demo ===\n");

        // Client code works with the abstract NotificationFactory type.
        // It doesn't know (or care) which concrete notification is created.
        NotificationFactory emailFactory = new EmailNotificationFactory();
        NotificationFactory smsFactory = new SMSNotificationFactory();
        NotificationFactory pushFactory = new PushNotificationFactory();

        // Each factory creates its own type of notification internally
        emailFactory.notify("user@example.com", "Your order has been shipped!");
        smsFactory.notify("+1-555-0100", "Your OTP is 483920");
        pushFactory.notify("device-token-abc", "New message from John");
    }
}
