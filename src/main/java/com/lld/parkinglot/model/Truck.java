package com.lld.parkinglot.model;

/** Concrete Vehicle — Truck. Requires a LARGE parking spot. */
public class Truck extends Vehicle {
    public Truck(String licensePlate) {
        super(licensePlate, VehicleType.TRUCK);
    }
}
