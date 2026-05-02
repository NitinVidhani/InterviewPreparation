package com.lld.parkinglot.model;

import java.time.Duration;

/**
 * Represents a payment for a completed parking session.
 *
 * In a real system, this would integrate with a payment gateway.
 * For this LLD, it simply records the amount and the associated ticket.
 */
public class Payment {

    private final Ticket ticket;
    private final double amount;

    public Payment(Ticket ticket, double amount) {
        this.ticket = ticket;
        this.amount = amount;
    }

    /** Process the payment (simulated). */
    public boolean processPayment() {
        // In production: call Stripe/PayPal/UPI API here
        System.out.printf("    [Payment] $%.2f charged for ticket %s%n",
                amount, ticket.getTicketId());
        return true;
    }

    public double getAmount() {
        return amount;
    }

    public Ticket getTicket() {
        return ticket;
    }
}
