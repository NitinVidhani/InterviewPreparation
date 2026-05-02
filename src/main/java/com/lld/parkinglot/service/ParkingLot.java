package com.lld.parkinglot.service;

import com.lld.parkinglot.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Singleton
 * ══════════════════════════════════════════════════════════════════════
 * ParkingLot is a SINGLETON because:
 * 1. There is only ONE physical parking lot.
 * 2. All gates (entry/exit) must share the same state (spots, floors).
 * 3. Global access is needed from any gate or display panel.
 *
 * We use the Double-Checked Locking approach for thread-safe lazy init:
 * - 'volatile' on the instance field prevents instruction reordering.
 * - First null check avoids unnecessary synchronization.
 * - Second null check (inside synchronized) prevents race conditions.
 *
 * INTERVIEW TIP: The interviewer will often ask "Why Singleton here?"
 * Answer: A parking lot is a shared resource. All entry/exit points and
 * display boards must operate on the same set of floors and spots.
 * A Singleton ensures consistency and prevents duplicate state.
 * ══════════════════════════════════════════════════════════════════════
 *
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Strategy (Pricing)
 * ══════════════════════════════════════════════════════════════════════
 * The ParkingLot holds a PricingStrategy reference. When calculating
 * fees at exit, it delegates to the strategy:
 * pricingStrategy.calculateFee(duration)
 *
 * This allows the lot to switch between HourlyPricing, FlatRatePricing,
 * or PerMinutePricing WITHOUT modifying ParkingLot code.
 *
 * You can even change pricing at runtime:
 * parkingLot.setPricingStrategy(new FlatRatePricingStrategy(25.0));
 * ══════════════════════════════════════════════════════════════════════
 */
public class ParkingLot {

    // ──────────────────────────────────────────────────────────────────
    // SINGLETON: volatile instance + private constructor + getInstance()
    // ──────────────────────────────────────────────────────────────────
    private static volatile ParkingLot instance;

    private final String name;
    private final List<ParkingFloor> floors;
    private PricingStrategy pricingStrategy;

    /** Private constructor — prevents external instantiation (Singleton). */
    private ParkingLot(String name) {
        this.name = name;
        this.floors = new ArrayList<>();
    }

    /**
     * SINGLETON: Double-Checked Locking getInstance().
     *
     * Thread A and Thread B both call getInstance() simultaneously:
     * 1. Both check instance == null → true (no lock yet)
     * 2. Thread A acquires lock, checks again → still null → creates instance
     * 3. Thread B acquires lock, checks again → NOT null → returns existing
     */
    public static ParkingLot getInstance(String name) {
        if (instance == null) { // 1st check (no lock — fast path)
            synchronized (ParkingLot.class) {
                if (instance == null) { // 2nd check (with lock — safe)
                    instance = new ParkingLot(name);
                    System.out.println("[ParkingLot] Singleton created: " + name);
                }
            }
        }
        return instance;
    }

    /** Convenience overload for getting the existing instance. */
    public static ParkingLot getInstance() {
        return getInstance("Default Parking Lot");
    }

    /** Reset for testing purposes only (not part of production code). */
    public static void resetInstance() {
        instance = null;
    }

    // ──────────────────────────────────────────────────────────────────
    // Configuration methods
    // ──────────────────────────────────────────────────────────────────

    /** Add a floor to the parking lot. */
    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
        System.out.printf("[ParkingLot] Added Floor %d%n", floor.getFloorNumber());
    }

    /**
     * STRATEGY PATTERN: Set the pricing strategy at runtime.
     * This is how the Strategy pattern is "plugged in" — the lot doesn't
     * know which pricing algorithm it's using, just that it can calculate fees.
     */
    public void setPricingStrategy(PricingStrategy strategy) {
        this.pricingStrategy = strategy;
        System.out.printf("[ParkingLot] Pricing set to: %s%n",
                strategy.getClass().getSimpleName());
    }

    // ──────────────────────────────────────────────────────────────────
    // Core operations
    // ──────────────────────────────────────────────────────────────────

    /**
     * Find an available parking spot for the given vehicle type.
     *
     * Searches floors sequentially (lowest floor first) to place vehicles
     * as close to the entrance as possible.
     *
     * @return the first available spot, or null if parking lot is full
     */
    public ParkingSpot findAvailableSpot(VehicleType vehicleType) {
        SpotType requiredType = ParkingSpotFactory.getRequiredSpotType(vehicleType);

        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(requiredType);
            if (spot != null) {
                return spot;
            }
        }
        return null; // Parking lot is full for this vehicle type
    }

    /**
     * Issue a parking ticket for a vehicle entering the lot.
     *
     * Steps:
     * 1. Find an available spot (via Factory's type mapping).
     * 2. Park the vehicle in the spot (thread-safe).
     * 3. Create and return a Ticket.
     */
    public Ticket issueTicket(Vehicle vehicle) {
        ParkingSpot spot = findAvailableSpot(vehicle.getType());
        if (spot == null) {
            System.out.printf("[ParkingLot] FULL! Cannot park %s%n", vehicle);
            return null;
        }

        // Park the vehicle (synchronized inside ParkingSpot)
        spot.park(vehicle);

        // Create a ticket linking the vehicle to its spot
        Ticket ticket = new Ticket(vehicle, spot);
        System.out.printf("[ParkingLot] Ticket issued: %s%n", ticket);
        return ticket;
    }

    /**
     * Process a vehicle exiting the lot.
     *
     * Steps:
     * 1. Calculate the fee using the STRATEGY pattern.
     * 2. Process the payment.
     * 3. Unpark the vehicle (free the spot).
     * 4. Mark the ticket as PAID.
     *
     * @return the payment amount, or -1 if exit failed
     */
    public double processExit(Ticket ticket) {
        if (ticket == null || ticket.getStatus() != TicketStatus.ACTIVE) {
            System.out.println("[ParkingLot] Invalid or already processed ticket!");
            return -1;
        }

        // Calculate duration and fee using the STRATEGY pattern
        Duration duration = Duration.between(ticket.getEntryTime(),
                java.time.LocalDateTime.now());

        // ──────────────────────────────────────────────────────────
        // STRATEGY PATTERN in action:
        // The ParkingLot delegates fee calculation to the strategy.
        // It calls the SAME method regardless of whether the strategy
        // is Hourly, FlatRate, or PerMinute. Polymorphism at work!
        // ──────────────────────────────────────────────────────────
        double fee = pricingStrategy.calculateFee(duration);

        // Process payment
        Payment payment = new Payment(ticket, fee);
        payment.processPayment();

        // Free the parking spot
        ticket.getSpot().unpark();

        // Mark ticket as paid
        ticket.markPaid(fee);

        return fee;
    }

    // ──────────────────────────────────────────────────────────────────
    // Display / Status
    // ──────────────────────────────────────────────────────────────────

    /** Display real-time availability across all floors. */
    public void displayAvailability() {
        System.out.println("\n╔═══════════════════════════════════════╗");
        System.out.println("║     " + name + " — Availability       ║");
        System.out.println("╠═══════════════════════════════════════╣");
        for (ParkingFloor floor : floors) {
            floor.displayAvailability();
        }
        System.out.println("╚═══════════════════════════════════════╝\n");
    }

    public String getName() {
        return name;
    }

    public List<ParkingFloor> getFloors() {
        return floors;
    }
}
