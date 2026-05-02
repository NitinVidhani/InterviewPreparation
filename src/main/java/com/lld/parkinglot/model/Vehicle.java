package com.lld.parkinglot.model;

/**
 * Abstract base class for all vehicles.
 *
 * ──────────────────────────────────────────────────────────────────────
 * DESIGN PATTERN: Polymorphism / Inheritance Hierarchy
 * ──────────────────────────────────────────────────────────────────────
 * By using an abstract Vehicle class, the parking lot logic can work with
 * ANY vehicle type through the base class reference. When a new vehicle
 * type is added (e.g., ElectricCar), existing code doesn't change — you
 * only create a new subclass. This follows the Open/Closed Principle.
 *
 * The ParkingLot, ParkingSpot, and Ticket classes all refer to Vehicle
 * (the abstraction), never to Car, Motorcycle, or Truck directly.
 * ──────────────────────────────────────────────────────────────────────
 */
public abstract class Vehicle {

    private final String licensePlate;
    private final VehicleType type;

    protected Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + " [" + licensePlate + "]";
    }
}
