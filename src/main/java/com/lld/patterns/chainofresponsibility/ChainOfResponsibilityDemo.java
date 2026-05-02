package com.lld.patterns.chainofresponsibility;

/**
 * Driver class that demonstrates the Chain of Responsibility pattern.
 *
 * We build a chain: Level1 → Level2 → Level3
 * Each request is sent to the FIRST handler. The chain automatically
 * routes it to the appropriate level.
 *
 * KEY INSIGHT: The client only knows about the first handler.
 * It doesn't care about the rest of the chain — the handlers
 * figure out who should process the request.
 */
public class ChainOfResponsibilityDemo {

    public static void main(String[] args) {
        System.out.println("=== Chain of Responsibility Pattern Demo ===\n");

        // 1. Create handlers
        SupportHandler level1 = new Level1Support();
        SupportHandler level2 = new Level2Support();
        SupportHandler level3 = new Level3Support();

        // 2. Build the chain: Level1 → Level2 → Level3
        // Fluent API: setNext() returns the next handler
        level1.setNext(level2).setNext(level3);

        // 3. Send requests — all go to level1 (the chain entry point)
        System.out.println("Request 1: Password Reset (BASIC)");
        level1.handle(new SupportRequest("Password Reset", SupportRequest.Priority.BASIC));

        System.out.println("\nRequest 2: Billing Dispute (INTERMEDIATE)");
        level1.handle(new SupportRequest("Billing Dispute", SupportRequest.Priority.INTERMEDIATE));

        System.out.println("\nRequest 3: Data Breach (CRITICAL)");
        level1.handle(new SupportRequest("Data Breach Detected", SupportRequest.Priority.CRITICAL));

        // 4. Demonstrate dynamic chain reconfiguration
        System.out.println("\n--- Reconfigured Chain (skip Level 1) ---");
        // New chain: Level2 → Level3 only
        level2 = new Level2Support();
        level3 = new Level3Support();
        level2.setNext(level3);

        System.out.println("Request 4: Basic question sent to Level 2 first");
        level2.handle(new SupportRequest("How do I reset password?", SupportRequest.Priority.BASIC));
        // Neither Level2 nor Level3 handles BASIC → falls through
    }
}
