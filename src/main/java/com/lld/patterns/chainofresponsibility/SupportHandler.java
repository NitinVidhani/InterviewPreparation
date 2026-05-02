package com.lld.patterns.chainofresponsibility;

/**
 * Abstract Handler — SupportHandler.
 *
 * Defines the contract for all handlers in the chain:
 * 1. setNext() — links this handler to the next one in the chain.
 * 2. handle() — processes the request or forwards it.
 *
 * The chaining mechanism is implemented here so concrete handlers
 * only need to focus on their processing logic.
 *
 * DESIGN NOTE: setNext() returns the next handler to allow fluent chaining:
 * handler1.setNext(handler2).setNext(handler3);
 */
public abstract class SupportHandler {

    private SupportHandler nextHandler;

    /**
     * Links this handler to the next handler in the chain.
     * Returns the NEXT handler to support fluent chaining.
     */
    public SupportHandler setNext(SupportHandler next) {
        this.nextHandler = next;
        return next; // enables: h1.setNext(h2).setNext(h3)
    }

    /**
     * Attempt to handle the request. If this handler can't handle it,
     * forward it to the next handler in the chain.
     *
     * Concrete handlers override this to add their own logic.
     */
    public void handle(SupportRequest request) {
        if (nextHandler != null) {
            nextHandler.handle(request);
        } else {
            System.out.println("  [End of Chain] No handler could process: "
                    + request.getDescription());
        }
    }
}
