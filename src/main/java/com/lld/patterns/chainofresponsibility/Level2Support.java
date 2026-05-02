package com.lld.patterns.chainofresponsibility;

/**
 * Concrete Handler — Level2Support.
 *
 * Handles INTERMEDIATE priority requests (billing disputes, account issues).
 * Escalates CRITICAL requests to the next handler.
 */
public class Level2Support extends SupportHandler {

    @Override
    public void handle(SupportRequest request) {
        if (request.getPriority() == SupportRequest.Priority.INTERMEDIATE) {
            System.out.println("  [Level 2 Support] Handled: " + request.getDescription());
        } else {
            System.out.println("  [Level 2 Support] Cannot handle, escalating...");
            super.handle(request); // pass to next handler
        }
    }
}
