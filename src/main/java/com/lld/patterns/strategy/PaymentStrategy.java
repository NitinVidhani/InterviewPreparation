package com.lld.patterns.strategy;

/**
 * Strategy Interface — PaymentStrategy.
 *
 * Declares the contract for all payment algorithms.
 * Each concrete strategy encapsulates a different payment method.
 */
public interface PaymentStrategy {

    /**
     * Process a payment of the given amount.
     *
     * @param amount the amount to charge
     * @return true if the payment was successful
     */
    boolean pay(double amount);
}
