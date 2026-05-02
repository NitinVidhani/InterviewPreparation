package com.lld.patterns.factorymethod;

/**
 * Creator (Abstract) — NotificationFactory.
 *
 * This abstract class declares the factory method createNotification().
 * Subclasses will override this method to produce different Notification types.
 *
 * IMPORTANT: The Creator is NOT only responsible for creating products.
 * It usually contains core business logic that relies on the Product objects.
 * The factory method decouples this logic from the concrete product classes.
 */
public abstract class NotificationFactory {

    // -----------------------------------------------------------------------
    // The FACTORY METHOD — subclasses must implement this.
    // It returns a Notification; the Creator doesn't need to know which one.
    // -----------------------------------------------------------------------
    public abstract Notification createNotification();

    /**
     * Template method that uses the factory method.
     * Notice that the business logic (notify) is INDEPENDENT of the concrete
     * product class. This is the key benefit of the Factory Method pattern.
     */
    public void notify(String recipient, String message) {
        // Step 1: Use the factory method to get a Notification
        Notification notification = createNotification();

        // Step 2: Delegate the sending to the product
        notification.send(recipient, message);
    }
}
