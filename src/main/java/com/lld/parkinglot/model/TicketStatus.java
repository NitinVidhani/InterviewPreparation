package com.lld.parkinglot.model;

/**
 * Enum representing the lifecycle status of a parking ticket.
 *
 * ACTIVE → Vehicle is currently parked
 * PAID → Fee has been calculated and paid, vehicle is exiting
 */
public enum TicketStatus {
    ACTIVE,
    PAID
}
