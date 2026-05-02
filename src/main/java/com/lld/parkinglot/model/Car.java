package com.lld.parkinglot.model;

/** Concrete Vehicle — Car. Requires a MEDIUM parking spot. */
public class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }
}
