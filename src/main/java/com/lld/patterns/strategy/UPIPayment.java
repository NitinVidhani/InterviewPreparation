package com.lld.patterns.strategy;

/**
 * Concrete Strategy — UPIPayment.
 *
 * Encapsulates the algorithm for processing a UPI payment.
 */
public class UPIPayment implements PaymentStrategy {

    private final String upiId;

    public UPIPayment(String upiId) {
        this.upiId = upiId;
    }

    @Override
    public boolean pay(double amount) {
        System.out.printf("[UPI] Charged ₹%.2f via UPI ID %s%n", amount, upiId);
        return true;
    }
}
