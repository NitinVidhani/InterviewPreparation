package com.lld.parkinglot.model;

/**
 * Enum representing the types of vehicles the parking lot supports.
 *
 * Used throughout the system to determine which spot type a vehicle needs.
 * Adding a new vehicle type here (e.g., ELECTRIC_CAR) is the first step
 * in extending the system — the Factory and ParkingFloor will also need
 * updates.
 */
public enum VehicleType {
    MOTORCYCLE,
    CAR,
    TRUCK
}
