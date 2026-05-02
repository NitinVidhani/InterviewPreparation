package com.lld.patterns.strategy;

/**
 * Concrete Strategy — CreditCardPayment.
 *
 * Encapsulates the algorithm for processing a credit card payment.
 */
public class CreditCardPayment implements PaymentStrategy {

    private final String cardNumber;
    private final String cardHolderName;

    public CreditCardPayment(String cardNumber, String cardHolderName) {
        this.cardNumber = cardNumber;
        this.cardHolderName = cardHolderName;
    }

    @Override
    public boolean pay(double amount) {
        // In a real system, this would call a payment gateway API
        String maskedCard = "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
        System.out.printf("[Credit Card] Charged $%.2f to card %s (%s)%n",
                amount, maskedCard, cardHolderName);
        return true;
    }
}
