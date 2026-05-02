package com.lld.patterns.chainofresponsibility;

/**
 * Concrete Handler — Level3Support (Manager).
 *
 * Handles CRITICAL priority requests (data breaches, system outages).
 * This is typically the last handler in the chain.
 */
public class Level3Support extends SupportHandler {

    @Override
    public void handle(SupportRequest request) {
        if (request.getPriority() == SupportRequest.Priority.CRITICAL) {
            System.out.println("  [Level 3 / Manager] Handled: " + request.getDescription());
        } else {
            // Even the manager can't handle? Pass along (or log as unhandled)
            super.handle(request);
        }
    }
}
