package com.lld.parkinglot.service;

import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.SpotType;
import com.lld.parkinglot.model.VehicleType;

/**
 * ──────────────────────────────────────────────────────────────────────
 * DESIGN PATTERN: Factory Method
 * ──────────────────────────────────────────────────────────────────────
 * ParkingSpotFactory encapsulates the creation of ParkingSpot objects.
 *
 * WHY a Factory?
 * 1. The client (ParkingFloor) doesn't need to know the details of spot
 * creation — it just asks for "5 SMALL spots on floor 1".
 * 2. If we later add new spot types (HANDICAPPED, ELECTRIC_CHARGING),
 * we only update this factory — not every place that creates spots.
 * 3. Spot ID generation logic is centralized here.
 *
 * In an interview, the interviewer might ask:
 * "How would you add electric vehicle spots?"
 * Answer: Add ELECTRIC to SpotType enum, update this factory method,
 * and create any specialized subclass if needed. Existing code
 * remains untouched → Open/Closed Principle.
 * ──────────────────────────────────────────────────────────────────────
 */
public class ParkingSpotFactory {

    /**
     * Factory method to create a parking spot with a generated ID.
     *
     * @param floorNumber the floor where this spot is located
     * @param spotType    the size/type of the spot
     * @param spotNumber  the sequential number of this spot on the floor
     * @return a new ParkingSpot instance
     */
    public static ParkingSpot createSpot(int floorNumber, SpotType spotType, int spotNumber) {
        // Generate a human-readable ID like "F1-S-001", "F2-M-015", "F3-L-003"
        String prefix = switch (spotType) {
            case SMALL -> "S";
            case MEDIUM -> "M";
            case LARGE -> "L";
        };
        String spotId = String.format("F%d-%s-%03d", floorNumber, prefix, spotNumber);

        return new ParkingSpot(spotId, spotType);
    }

    /**
     * Maps a VehicleType to the required SpotType.
     *
     * This mapping is centralized in the factory so that the rest of the
     * system doesn't need to know the vehicle-to-spot mapping rules.
     */
    public static SpotType getRequiredSpotType(VehicleType vehicleType) {
        return switch (vehicleType) {
            case MOTORCYCLE -> SpotType.SMALL;
            case CAR -> SpotType.MEDIUM;
            case TRUCK -> SpotType.LARGE;
        };
    }
}
