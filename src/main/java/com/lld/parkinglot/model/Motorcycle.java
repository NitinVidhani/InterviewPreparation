package com.lld.parkinglot.model;

/** Concrete Vehicle — Motorcycle. Requires a SMALL parking spot. */
public class Motorcycle extends Vehicle {
    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE);
    }
}
