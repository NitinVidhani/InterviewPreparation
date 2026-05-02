package com.lld.patterns.chainofresponsibility;

/**
 * Request object — represents an incoming support request.
 *
 * This is the "message" that flows through the chain. Each handler
 * inspects it and decides whether to handle or pass along.
 */
public class SupportRequest {

    public enum Priority {
        BASIC, // Level 1 can handle
        INTERMEDIATE, // Level 2 can handle
        CRITICAL // Only Level 3 / Manager can handle
    }

    private final String description;
    private final Priority priority;

    public SupportRequest(String description, Priority priority) {
        this.description = description;
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }
}
