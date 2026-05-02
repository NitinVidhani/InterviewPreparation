package com.lld.parkinglot.service;

import java.time.Duration;

/**
 * ──────────────────────────────────────────────────────────────────────
 * DESIGN PATTERN: Strategy
 * ──────────────────────────────────────────────────────────────────────
 * PricingStrategy is the STRATEGY INTERFACE for calculating parking fees.
 *
 * WHY Strategy?
 * Different parking lots (or the same lot at different times) may use
 * different pricing models:
 * - Hourly rate (most common)
 * - Flat rate (airports, events)
 * - Per-minute (premium lots)
 * - Time-of-day based (peak/off-peak)
 *
 * The Strategy pattern lets us:
 * 1. Encapsulate each pricing algorithm in its own class.
 * 2. Switch pricing at runtime (e.g., apply event pricing on weekends).
 * 3. Add new pricing models without modifying existing code.
 *
 * The ParkingLot or Gate holds a reference to a PricingStrategy and
 * delegates fee calculation to it — it doesn't know or care WHICH
 * pricing algorithm is being used.
 * ──────────────────────────────────────────────────────────────────────
 */
public interface PricingStrategy {

    /**
     * Calculate the parking fee based on the duration parked.
     *
     * @param duration how long the vehicle was parked
     * @return the fee in dollars
     */
    double calculateFee(Duration duration);
}
