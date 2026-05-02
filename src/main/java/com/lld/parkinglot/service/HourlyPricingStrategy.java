package com.lld.parkinglot.service;

import java.time.Duration;

/**
 * Concrete Strategy — HourlyPricingStrategy.
 *
 * Charges a fixed rate per hour (rounded up).
 * Example: 2.5 hours at $10/hour → ceil(2.5) = 3 hours → $30.
 *
 * ──────────────────────────────────────────────────────────────────────
 * STRATEGY PATTERN in action:
 * This class knows ONLY about hourly pricing. It doesn't know about
 * ParkingLot, Gates, or Tickets. The ParkingLot just calls
 * calculateFee(duration) — the algorithm is encapsulated here.
 * ──────────────────────────────────────────────────────────────────────
 */
public class HourlyPricingStrategy implements PricingStrategy {

    private final double ratePerHour;

    public HourlyPricingStrategy(double ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public double calculateFee(Duration duration) {
        // Round up to the next full hour (partial hours are charged fully)
        long hours = (long) Math.ceil(duration.toMinutes() / 60.0);
        if (hours == 0)
            hours = 1; // Minimum 1 hour charge
        return hours * ratePerHour;
    }
}
