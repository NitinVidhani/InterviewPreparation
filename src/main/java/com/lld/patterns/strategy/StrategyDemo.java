package com.lld.patterns.strategy;

/**
 * Driver class that demonstrates the Strategy pattern.
 *
 * Notice how the ShoppingCart delegates payment to different strategies
 * without any if-else logic. The strategy is injected from outside.
 */
public class StrategyDemo {

    public static void main(String[] args) {
        System.out.println("=== Strategy Pattern Demo ===\n");

        // --- Order 1: Pay with Credit Card ---
        System.out.println("--- Order 1 ---");
        ShoppingCart cart1 = new ShoppingCart();
        cart1.addItem("Laptop", 999.99);
        cart1.addItem("Mouse", 29.99);
        cart1.setPaymentStrategy(new CreditCardPayment("1234567890121234", "Nitin Vidhani"));
        cart1.checkout();

        // --- Order 2: Pay with PayPal ---
        System.out.println("\n--- Order 2 ---");
        ShoppingCart cart2 = new ShoppingCart();
        cart2.addItem("Book", 15.00);
        cart2.setPaymentStrategy(new PayPalPayment("nitin@example.com"));
        cart2.checkout();

        // --- Order 3: Pay with UPI ---
        System.out.println("\n--- Order 3 ---");
        ShoppingCart cart3 = new ShoppingCart();
        cart3.addItem("Headphones", 59.99);
        cart3.addItem("Phone Case", 12.50);
        cart3.setPaymentStrategy(new UPIPayment("nitin@upi"));
        cart3.checkout();

        // --- Switch strategy at runtime ---
        System.out.println("\n--- Runtime Strategy Switch ---");
        ShoppingCart cart4 = new ShoppingCart();
        cart4.addItem("Keyboard", 75.00);
        cart4.setPaymentStrategy(new CreditCardPayment("9876543210985678", "John Doe"));
        System.out.println("Switching strategy to PayPal...");
        cart4.setPaymentStrategy(new PayPalPayment("john@example.com"));
        cart4.checkout();
    }
}
