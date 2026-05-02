package com.lld.parkinglot.service;

import com.lld.parkinglot.model.Ticket;
import com.lld.parkinglot.model.Vehicle;

/**
 * Represents an entry or exit gate in the parking lot.
 *
 * Each gate acts as a client of the ParkingLot Singleton.
 *
 * ──────────────────────────────────────────────────────────────────────
 * SINGLETON PATTERN in action:
 * Multiple Gate instances all call ParkingLot.getInstance() and get
 * the SAME parking lot object. This is critical because:
 * - All gates must see the same spot availability.
 * - A spot assigned at Gate 1 must not be assigned again at Gate 2.
 * - The Singleton guarantees shared state across all gates.
 * ──────────────────────────────────────────────────────────────────────
 *
 * INTERVIEW TIP: "How do you handle multiple gates?"
 * Answer: All gates reference the same ParkingLot Singleton. Thread safety
 * is handled at the ParkingSpot level (synchronized park/unpark) and at
 * the ParkingFloor level (synchronized findAvailableSpot).
 */
public class Gate {

    public enum GateType {
        ENTRY, EXIT
    }

    private final String gateId;
    private final GateType gateType;

    public Gate(String gateId, GateType gateType) {
        this.gateId = gateId;
        this.gateType = gateType;
    }

    /**
     * Issue a ticket at an ENTRY gate.
     * Delegates to the ParkingLot Singleton.
     */
    public Ticket vehicleEntry(Vehicle vehicle) {
        if (gateType != GateType.ENTRY) {
            System.out.println("[Gate " + gateId + "] This is an EXIT gate!");
            return null;
        }

        System.out.printf("[Gate %s] Vehicle arriving: %s%n", gateId, vehicle);
        // All gates use the SAME ParkingLot instance (Singleton)
        return ParkingLot.getInstance().issueTicket(vehicle);
    }

    /**
     * Process exit at an EXIT gate.
     * Calculates fee and processes payment via the ParkingLot Singleton.
     */
    public double vehicleExit(Ticket ticket) {
        if (gateType != GateType.EXIT) {
            System.out.println("[Gate " + gateId + "] This is an ENTRY gate!");
            return -1;
        }

        System.out.printf("[Gate %s] Vehicle departing: %s%n", gateId, ticket.getVehicle());
        return ParkingLot.getInstance().processExit(ticket);
    }

    public String getGateId() {
        return gateId;
    }

    public GateType getGateType() {
        return gateType;
    }
}
