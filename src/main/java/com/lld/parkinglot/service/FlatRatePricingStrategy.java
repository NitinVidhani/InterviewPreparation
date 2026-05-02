package com.lld.parkinglot.service;

import java.time.Duration;

/**
 * Concrete Strategy — FlatRatePricingStrategy.
 *
 * Charges a fixed flat fee regardless of how long the vehicle is parked.
 * Common at airports, event venues, and stadium parking.
 *
 * This demonstrates how easily you can swap pricing algorithms.
 * parkingLot.setPricingStrategy(new FlatRatePricingStrategy(25.0))
 * → all future fees are $25, no matter the duration.
 */
public class FlatRatePricingStrategy implements PricingStrategy {

    private final double flatRate;

    public FlatRatePricingStrategy(double flatRate) {
        this.flatRate = flatRate;
    }

    @Override
    public double calculateFee(Duration duration) {
        return flatRate; // Same fee regardless of duration
    }
}
