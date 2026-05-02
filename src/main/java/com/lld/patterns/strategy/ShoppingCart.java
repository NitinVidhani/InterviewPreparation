package com.lld.patterns.strategy;

/**
 * Context — ShoppingCart.
 *
 * The context maintains a reference to a Strategy object and delegates
 * the payment to it. The context doesn't know which concrete strategy
 * it's working with — it only interacts with the PaymentStrategy interface.
 *
 * KEY BENEFITS:
 * - The cart is DECOUPLED from all payment methods.
 * - You can switch payment strategies AT RUNTIME via setPaymentStrategy().
 * - Adding a new payment method (e.g., CryptoPayment) requires ZERO
 * changes to this class — just create a new strategy and plug it in.
 */
public class ShoppingCart {

    private double totalAmount;
    private PaymentStrategy paymentStrategy;

    public ShoppingCart() {
        this.totalAmount = 0;
    }

    /** Add an item to the cart. */
    public void addItem(String item, double price) {
        totalAmount += price;
        System.out.printf("  Added '%s' ($%.2f) → Cart total: $%.2f%n", item, price, totalAmount);
    }

    /**
     * Set the payment strategy AT RUNTIME.
     * This is the key to the Strategy pattern — the algorithm can be swapped
     * without modifying the context class.
     */
    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }

    /** Checkout: delegate the payment to the current strategy. */
    public boolean checkout() {
        if (paymentStrategy == null) {
            System.out.println("[Cart] No payment strategy set!");
            return false;
        }
        System.out.printf("[Cart] Checking out $%.2f...%n", totalAmount);
        return paymentStrategy.pay(totalAmount);
    }
}
