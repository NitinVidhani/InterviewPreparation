package com.lld.parkinglot.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lld.parkinglot.service.ParkingSpotFactory;

/**
 * Represents a single floor in the parking lot.
 *
 * Each floor manages its own collection of parking spots, grouped by SpotType.
 * This allows O(1) lookup by type and efficient searching for available spots.
 *
 * ──────────────────────────────────────────────────────────────────────
 * THREAD SAFETY:
 * findAvailableSpot() is synchronized to prevent two threads from being
 * assigned the same spot on this floor. The lock granularity is per-floor
 * (not per-lot), which improves concurrency — two vehicles can be
 * assigned spots on different floors in parallel.
 * ──────────────────────────────────────────────────────────────────────
 */
public class ParkingFloor {

    private final int floorNumber;
    private final Map<SpotType, List<ParkingSpot>> spotsByType;

    /**
     * Creates a floor and initializes spots using the ParkingSpotFactory.
     *
     * ──────────────────────────────────────────────────────────────
     * FACTORY METHOD pattern used here:
     * Instead of calling 'new ParkingSpot(...)' directly, we delegate
     * to ParkingSpotFactory.createSpot(). This means:
     * - Spot ID generation logic is centralized.
     * - If we add new spot types, only the factory changes.
     * - ParkingFloor doesn't need to know HOW spots are created.
     * ──────────────────────────────────────────────────────────────
     */
    public ParkingFloor(int floorNumber, int smallSpots, int mediumSpots, int largeSpots) {
        this.floorNumber = floorNumber;
        this.spotsByType = new EnumMap<>(SpotType.class);

        // Initialize empty lists for each spot type
        for (SpotType type : SpotType.values()) {
            spotsByType.put(type, new ArrayList<>());
        }

        // Use the FACTORY to create spots — encapsulated creation logic
        for (int i = 1; i <= smallSpots; i++) {
            spotsByType.get(SpotType.SMALL).add(
                    ParkingSpotFactory.createSpot(floorNumber, SpotType.SMALL, i));
        }
        for (int i = 1; i <= mediumSpots; i++) {
            spotsByType.get(SpotType.MEDIUM).add(
                    ParkingSpotFactory.createSpot(floorNumber, SpotType.MEDIUM, i));
        }
        for (int i = 1; i <= largeSpots; i++) {
            spotsByType.get(SpotType.LARGE).add(
                    ParkingSpotFactory.createSpot(floorNumber, SpotType.LARGE, i));
        }
    }

    /**
     * Find the first available spot of the given type on this floor.
     *
     * synchronized → prevents two threads from grabbing the same spot.
     * Lock is per-floor, so different floors can be searched in parallel.
     *
     * @return the available spot, or null if none found on this floor
     */
    public synchronized ParkingSpot findAvailableSpot(SpotType spotType) {
        List<ParkingSpot> spots = spotsByType.get(spotType);
        if (spots == null)
            return null;

        for (ParkingSpot spot : spots) {
            if (spot.isAvailable()) {
                return spot;
            }
        }
        return null; // No available spot of this type on this floor
    }

    /** Count available spots of a given type on this floor. */
    public int getAvailableCount(SpotType spotType) {
        return (int) spotsByType.getOrDefault(spotType, List.of())
                .stream()
                .filter(ParkingSpot::isAvailable)
                .count();
    }

    /** Get total spots of a given type on this floor. */
    public int getTotalCount(SpotType spotType) {
        return spotsByType.getOrDefault(spotType, List.of()).size();
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    /** Display availability summary for this floor. */
    public void displayAvailability() {
        System.out.printf("  Floor %d → ", floorNumber);
        for (SpotType type : SpotType.values()) {
            System.out.printf("%s: %d/%d  ", type,
                    getAvailableCount(type), getTotalCount(type));
        }
        System.out.println();
    }
}
