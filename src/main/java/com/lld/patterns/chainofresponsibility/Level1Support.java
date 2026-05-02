package com.lld.patterns.chainofresponsibility;

/**
 * Concrete Handler — Level1Support.
 *
 * Handles BASIC priority requests (password resets, FAQ questions).
 * If the request priority is higher, it passes the request to the next handler.
 */
public class Level1Support extends SupportHandler {

    @Override
    public void handle(SupportRequest request) {
        if (request.getPriority() == SupportRequest.Priority.BASIC) {
            System.out.println("  [Level 1 Support] Handled: " + request.getDescription());
        } else {
            System.out.println("  [Level 1 Support] Cannot handle, escalating...");
            super.handle(request); // pass to next handler
        }
    }
}
