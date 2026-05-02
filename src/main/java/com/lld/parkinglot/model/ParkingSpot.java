package com.lld.parkinglot.model;

/**
 * Represents a single parking spot in the parking lot.
 *
 * ──────────────────────────────────────────────────────────────────────
 * THREAD SAFETY CONSIDERATION
 * ──────────────────────────────────────────────────────────────────────
 * The park() and unpark() methods are SYNCHRONIZED because multiple
 * entry/exit gates may try to assign/release the same spot concurrently.
 *
 * Without synchronization, two vehicles could be assigned the same spot:
 * Thread A: checks isAvailable → true
 * Thread B: checks isAvailable → true (race condition!)
 * Thread A: parks vehicle
 * Thread B: parks vehicle → CONFLICT!
 *
 * 'synchronized' ensures that only one thread can execute park()/unpark()
 * on the same ParkingSpot instance at a time.
 * ──────────────────────────────────────────────────────────────────────
 */
public class ParkingSpot {

    private final String spotId;
    private final SpotType spotType;
    private boolean isAvailable;
    private Vehicle parkedVehicle;

    public ParkingSpot(String spotId, SpotType spotType) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.isAvailable = true;
        this.parkedVehicle = null;
    }

    /**
     * Attempt to park a vehicle in this spot.
     *
     * synchronized → ensures atomic check-and-set for thread safety.
     *
     * @return true if the vehicle was successfully parked, false if spot is
     *         occupied
     */
    public synchronized boolean park(Vehicle vehicle) {
        if (!isAvailable) {
            return false;
        }
        this.parkedVehicle = vehicle;
        this.isAvailable = false;
        System.out.printf("    [Spot %s] %s parked %s%n", spotId, spotType, vehicle);
        return true;
    }

    /**
     * Remove the vehicle from this spot, making it available again.
     *
     * @return the vehicle that was parked, or null if spot was empty
     */
    public synchronized Vehicle unpark() {
        Vehicle vehicle = this.parkedVehicle;
        this.parkedVehicle = null;
        this.isAvailable = true;
        if (vehicle != null) {
            System.out.printf("    [Spot %s] %s unparked%n", spotId, vehicle);
        }
        return vehicle;
    }

    // --- Getters ---
    public String getSpotId() {
        return spotId;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public Vehicle getParkedVehicle() {
        return parkedVehicle;
    }
}
