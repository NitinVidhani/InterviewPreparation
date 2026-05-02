package com.lld.parkinglot.model;

/**
 * Enum representing parking spot sizes.
 *
 * Each SpotType maps to one or more VehicleTypes that can fit in it.
 * SMALL → MOTORCYCLE only
 * MEDIUM → CAR only
 * LARGE → TRUCK (and technically smaller vehicles, but we keep 1:1 for
 * simplicity)
 */
public enum SpotType {
    SMALL,
    MEDIUM,
    LARGE
}
