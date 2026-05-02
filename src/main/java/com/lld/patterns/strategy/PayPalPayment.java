package com.lld.patterns.strategy;

/**
 * Concrete Strategy — PayPalPayment.
 *
 * Encapsulates the algorithm for processing a PayPal payment.
 */
public class PayPalPayment implements PaymentStrategy {

    private final String email;

    public PayPalPayment(String email) {
        this.email = email;
    }

    @Override
    public boolean pay(double amount) {
        System.out.printf("[PayPal] Charged $%.2f to account %s%n", amount, email);
        return true;
    }
}
