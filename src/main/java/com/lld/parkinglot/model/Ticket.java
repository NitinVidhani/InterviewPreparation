package com.lld.parkinglot.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a parking ticket issued when a vehicle enters the lot.
 *
 * The ticket links a Vehicle to a ParkingSpot and tracks the entry/exit times.
 * It is the primary data carrier between the entry gate, the pricing engine,
 * and the exit gate.
 *
 * Lifecycle:
 * 1. Created at ENTRY → status = ACTIVE, entryTime = now
 * 2. Processed at EXIT → status = PAID, exitTime = now, fee calculated
 */
public class Ticket {

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;
    private double fee;

    public Ticket(Vehicle vehicle, ParkingSpot spot) {
        // Generate a readable ticket ID using a short UUID prefix
        this.ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = LocalDateTime.now();
        this.status = TicketStatus.ACTIVE;
        this.fee = 0.0;
    }

    /** Mark the ticket as paid and record the exit time. */
    public void markPaid(double fee) {
        this.exitTime = LocalDateTime.now();
        this.fee = fee;
        this.status = TicketStatus.PAID;
    }

    // --- Getters ---
    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public double getFee() {
        return fee;
    }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        return String.format("Ticket[%s | %s | Spot: %s | Entry: %s | Status: %s]",
                ticketId, vehicle, spot.getSpotId(), entryTime.format(fmt), status);
    }
}
