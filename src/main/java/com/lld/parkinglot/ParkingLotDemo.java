package com.lld.parkinglot;

import com.lld.parkinglot.model.*;
import com.lld.parkinglot.service.*;

/**
 * ══════════════════════════════════════════════════════════════════════
 * PARKING LOT — LLD DEMO
 * ══════════════════════════════════════════════════════════════════════
 *
 * This demo walks through a complete parking lot scenario, showing
 * each design pattern in action:
 *
 * 1. SINGLETON → ParkingLot.getInstance() (one lot, many gates)
 * 2. FACTORY → ParkingSpotFactory creates spots with generated IDs
 * 3. STRATEGY → PricingStrategy swapped at runtime
 * 4. POLYMORPHISM → Vehicle hierarchy (Car, Motorcycle, Truck)
 *
 * Run this class to see the full flow:
 * Vehicle entry → Ticket issued → Spot assigned → Exit → Fee calculated →
 * Payment
 *
 * ══════════════════════════════════════════════════════════════════════
 */
public class ParkingLotDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║      PARKING LOT — Low Level Design Demo     ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        // ──────────────────────────────────────────────────────────────
        // STEP 1: Initialize the Parking Lot (SINGLETON pattern)
        // ──────────────────────────────────────────────────────────────
        // Reset for clean demo (in production, this wouldn't exist)
        ParkingLot.resetInstance();

        System.out.println("--- STEP 1: Create Parking Lot (Singleton) ---");
        ParkingLot lot = ParkingLot.getInstance("City Center Mall Parking");

        // Subsequent calls return the SAME instance
        ParkingLot sameLot = ParkingLot.getInstance();
        System.out.println("Same instance? " + (lot == sameLot)); // true

        // ──────────────────────────────────────────────────────────────
        // STEP 2: Add floors (FACTORY METHOD creates spots)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 2: Add Floors (Factory creates spots) ---");
        // ParkingFloor constructor uses ParkingSpotFactory internally
        lot.addFloor(new ParkingFloor(1, 5, 10, 3)); // 5 small, 10 medium, 3 large
        lot.addFloor(new ParkingFloor(2, 5, 10, 3));

        // ──────────────────────────────────────────────────────────────
        // STEP 3: Set pricing strategy (STRATEGY pattern)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 3: Set Pricing Strategy ---");
        lot.setPricingStrategy(new HourlyPricingStrategy(10.0)); // $10/hour

        // Display initial availability
        lot.displayAvailability();

        // ──────────────────────────────────────────────────────────────
        // STEP 4: Create entry and exit gates (use SINGLETON internally)
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- STEP 4: Create Gates ---");
        Gate entryGate1 = new Gate("E1", Gate.GateType.ENTRY);
        Gate entryGate2 = new Gate("E2", Gate.GateType.ENTRY);
        Gate exitGate1 = new Gate("X1", Gate.GateType.EXIT);

        // ──────────────────────────────────────────────────────────────
        // STEP 5: Vehicles enter through different gates
        // (POLYMORPHISM — Car, Motorcycle, Truck all treated as Vehicle)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 5: Vehicles Enter ---");

        // A car enters via Gate E1
        Ticket carTicket = entryGate1.vehicleEntry(new Car("KA-01-AB-1234"));

        // A motorcycle enters via Gate E2
        Ticket bikeTicket = entryGate2.vehicleEntry(new Motorcycle("KA-01-CD-5678"));

        // A truck enters via Gate E1
        Ticket truckTicket = entryGate1.vehicleEntry(new Truck("KA-01-EF-9012"));

        // Another car enters via Gate E2
        Ticket car2Ticket = entryGate2.vehicleEntry(new Car("MH-02-GH-3456"));

        // Display availability after entries
        lot.displayAvailability();

        // ──────────────────────────────────────────────────────────────
        // STEP 6: Vehicles exit (fee calculated via STRATEGY)
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- STEP 6: Vehicles Exit (Hourly Pricing) ---");

        // Simulate some parking time (in real system, this would be hours)
        Thread.sleep(100); // small delay for non-zero duration

        double carFee = exitGate1.vehicleExit(carTicket);
        System.out.printf("  Car fee: $%.2f%n", carFee);

        double bikeFee = exitGate1.vehicleExit(bikeTicket);
        System.out.printf("  Bike fee: $%.2f%n", bikeFee);

        // Display availability after some exits
        lot.displayAvailability();

        // ──────────────────────────────────────────────────────────────
        // STEP 7: SWITCH pricing strategy AT RUNTIME (Strategy pattern)
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- STEP 7: Switch to Flat Rate Pricing ---");
        lot.setPricingStrategy(new FlatRatePricingStrategy(25.0)); // $25 flat

        double truckFee = exitGate1.vehicleExit(truckTicket);
        System.out.printf("  Truck fee (flat rate): $%.2f%n", truckFee);

        // ──────────────────────────────────────────────────────────────
        // STEP 8: Try to exit with an already-used ticket
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 8: Edge Case — Re-use ticket ---");
        double invalidFee = exitGate1.vehicleExit(carTicket);
        System.out.println("  Result: " + (invalidFee == -1 ? "REJECTED (as expected)" : "Bug!"));

        // ──────────────────────────────────────────────────────────────
        // STEP 9: Fill up spots to test "lot full" scenario
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 9: Fill Up Large Spots ---");
        // We have 3 large spots per floor × 2 floors = 6 total, 1 already freed
        // Let's fill all remaining large spots
        for (int i = 1; i <= 6; i++) {
            Ticket t = entryGate1.vehicleEntry(new Truck("TRUCK-" + i));
            if (t == null) {
                System.out.println("  → Lot FULL for trucks after " + (i - 1) + " entries\n");
                break;
            }
        }

        // Final availability
        lot.displayAvailability();

        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║            Demo Complete!                    ║");
        System.out.println("║                                              ║");
        System.out.println("║  Patterns demonstrated:                      ║");
        System.out.println("║  ✓ Singleton  — ParkingLot                   ║");
        System.out.println("║  ✓ Factory    — ParkingSpotFactory           ║");
        System.out.println("║  ✓ Strategy   — Hourly → Flat Rate pricing   ║");
        System.out.println("║  ✓ Polymorphism — Car, Motorcycle, Truck     ║");
        System.out.println("╚═══════════════════════════════════════════════╝");
    }
}
