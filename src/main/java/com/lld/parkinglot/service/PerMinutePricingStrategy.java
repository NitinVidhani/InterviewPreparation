package com.lld.parkinglot.service;

import java.time.Duration;

/**
 * Concrete Strategy — PerMinutePricingStrategy.
 *
 * Charges a rate per minute. Useful for premium downtown lots where
 * every minute counts.
 *
 * This is a third pricing strategy demonstrating extensibility:
 * adding a new pricing model required ZERO changes to ParkingLot,
 * Gate, or any other class — just this new class.
 */
public class PerMinutePricingStrategy implements PricingStrategy {

    private final double ratePerMinute;

    public PerMinutePricingStrategy(double ratePerMinute) {
        this.ratePerMinute = ratePerMinute;
    }

    @Override
    public double calculateFee(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes == 0)
            minutes = 1; // Minimum 1 minute charge
        return minutes * ratePerMinute;
    }
}
