package com.lld.hld.multithreading;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * ============================================================
 * CompletableFutureDemo — Async Pipelines and Composition
 * ============================================================
 *
 * CompletableFuture solves the limitations of raw Future:
 * - Chain dependent async steps (thenCompose/thenApply)
 * - Combine independent async tasks (thenCombine, allOf, anyOf)
 * - Handle errors gracefully (exceptionally, handle, whenComplete)
 * - Avoid callback hell with readable pipelines
 *
 * REAL-WORLD SCENARIO:
 * E-commerce order placement:
 * Step 1. Validate user (DB lookup) [async]
 * Step 2. Check inventory (HTTP to warehouse) [async, depends on Step 1]
 * Step 3a. Reserve inventory [async]
 * Step 3b. Calculate shipping [async, parallel with 3a]
 * Step 4. Charge payment (both 3a + 3b needed) [async, waits for 3a and 3b]
 * Step 5. Send confirmation email [fire-and-forget]
 */
public class CompletableFutureDemo {

    private static final ExecutorService ioPool = Executors.newFixedThreadPool(8,
            r -> {
                Thread t = new Thread(r, "cf-io-worker");
                t.setDaemon(true);
                return t;
            });

    // =======================================================
    // Simulated Service Calls
    // =======================================================

    private static CompletableFuture<User> validateUser(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(100); // DB lookup
            if ("invalid".equals(userId))
                throw new RuntimeException("User not found: " + userId);
            return new User(userId, "Alice");
        }, ioPool);
    }

    private static CompletableFuture<Boolean> checkInventory(String productId, int qty) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(150); // HTTP call to warehouse service
            return qty <= 10; // simulate: up to 10 in stock
        }, ioPool);
    }

    private static CompletableFuture<String> reserveInventory(String productId, int qty) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return "RESERVE-" + UUID.randomUUID().toString().substring(0, 8);
        }, ioPool);
    }

    private static CompletableFuture<Double> calculateShipping(String userId, String productId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(180); // external shipping API
            return 5.99;
        }, ioPool);
    }

    private static CompletableFuture<String> chargePayment(String userId,
            String reservationId,
            double shipping) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(300); // payment gateway
            return "CHARGE-" + UUID.randomUUID().toString().substring(0, 8);
        }, ioPool);
    }

    // =======================================================
    // 1. Sequential Chain (thenCompose)
    // =======================================================

    /**
     * thenCompose: when each step DEPENDS on the previous step's result.
     * (Like flatMap in streams — flattens CompletableFuture<CompletableFuture<T>>)
     */
    static void demonstrateSequentialChain() throws Exception {
        System.out.println("=== Sequential Chain (thenCompose) ===");
        long start = System.currentTimeMillis();

        CompletableFuture<Boolean> pipeline = validateUser("user-123") // Step 1: DB lookup
                .thenCompose(user -> // Step 2: check inventory (needs user)
                checkInventory("product-456", 3));

        Boolean inStock = pipeline.get(5, TimeUnit.SECONDS);
        System.out.printf("In stock: %s | Time: %dms%n", inStock,
                System.currentTimeMillis() - start); // ~250ms (100 + 150)
    }

    // =======================================================
    // 2. Parallel Independent Tasks (thenCombine, allOf)
    // =======================================================

    /**
     * thenCombine: run two INDEPENDENT futures concurrently, combine results.
     * allOf: wait for ALL futures to complete.
     */
    static void demonstrateParallelTasks() throws Exception {
        System.out.println("\n=== Parallel Tasks (thenCombine + allOf) ===");
        long start = System.currentTimeMillis();

        // 3a and 3b run CONCURRENTLY (not waiting for each other)
        CompletableFuture<String> reservationFuture = reserveInventory("product-456", 3);
        CompletableFuture<Double> shippingFuture = calculateShipping("user-123", "product-456");

        // thenCombine: fires when BOTH are complete
        CompletableFuture<String> paymentFuture = reservationFuture
                .thenCombine(shippingFuture, (reservationId, shipping) -> {
                    System.out.printf("  Reservation: %s, Shipping: $%.2f%n", reservationId, shipping);
                    return chargePayment("user-123", reservationId, shipping).join();
                });

        String chargeId = paymentFuture.get(5, TimeUnit.SECONDS);
        System.out.printf("Charge ID: %s | Time: %dms (parallel: max(200,180)=%dms)%n",
                chargeId, System.currentTimeMillis() - start, Math.max(200, 180));
    }

    // =======================================================
    // 3. Full Order Pipeline
    // =======================================================

    /**
     * Full end-to-end order flow combining sequential and parallel steps.
     *
     * Timeline (parallel where possible):
     * 0ms: validateUser starts
     * 100ms: checkInventory starts
     * 250ms: [reserveInventory] AND [calculateShipping] start IN PARALLEL
     * 450ms: chargePayment starts (both 3a & 3b done)
     * 750ms: ORDER COMPLETE
     * (fire-and-forget email, doesn't block response)
     */
    static void demonstrateFullPipeline() throws Exception {
        System.out.println("\n=== Full Order Pipeline ===");
        long start = System.currentTimeMillis();

        String result = validateUser("user-123")
                // Sequential: need user to check inventory
                .thenCompose(user -> checkInventory("product-456", 3)
                        .thenCompose(inStock -> {
                            if (!inStock)
                                throw new RuntimeException("Out of stock!");

                            // Parallel: reserve inventory AND calculate shipping simultaneously
                            CompletableFuture<String> reserveCF = reserveInventory("product-456", 3);
                            CompletableFuture<Double> shippingCF = calculateShipping(user.id(), "product-456");

                            // All three are running concurrently now!
                            return reserveCF.thenCombine(shippingCF,
                                    (reservationId, shipping) -> Map.entry(reservationId, shipping))
                                    // Sequential: charge payment (needs reservation + shipping)
                                    .thenCompose(entry -> chargePayment(
                                            user.id(), entry.getKey(), entry.getValue()));
                        }))
                // Async side-effect: send email — don't wait for it
                .whenCompleteAsync((chargeId, ex) -> {
                    if (ex == null) {
                        System.out.printf("  [Email] Sending confirmation for charge %s...%n", chargeId);
                        sleep(500); // email send (fire-and-forget)
                    }
                }, ioPool)
                .exceptionally(ex -> "ORDER FAILED: " + ex.getMessage())
                .get(5, TimeUnit.SECONDS);

        System.out.printf("Result: %s | Total pipeline time: %dms%n", result,
                System.currentTimeMillis() - start);
    }

    // =======================================================
    // 4. Error Handling
    // =======================================================

    static void demonstrateErrorHandling() throws Exception {
        System.out.println("\n=== Error Handling ===");

        // exceptionally: fallback value on error
        String result1 = validateUser("invalid") // throws RuntimeException
                .thenApply(User::name)
                .exceptionally(ex -> "GUEST") // fallback
                .get();
        System.out.println("exceptionally result: " + result1); // "GUEST"

        // handle: always runs (success OR failure)
        String result2 = validateUser("invalid")
                .thenApply(User::name)
                .handle((name, ex) -> {
                    if (ex != null)
                        return "Error handled: " + ex.getMessage();
                    return name;
                })
                .get();
        System.out.println("handle result: " + result2);

        // whenComplete: side effect, doesn't change result
        validateUser("user-123")
                .whenComplete((user, ex) -> {
                    if (ex != null)
                        System.err.println("Failed: " + ex.getMessage());
                    else
                        System.out.println("whenComplete: user=" + user.name());
                })
                .get();
    }

    // =======================================================
    // 5. anyOf — Return First Completed (Hedged Requests)
    // =======================================================

    /**
     * anyOf: returns as soon as ANY future completes.
     * Used for: hedged requests (send to multiple replicas, use first response)
     */
    static void demonstrateAnyOf() throws Exception {
        System.out.println("\n=== anyOf — Hedged Requests ===");

        // Query 3 cache replicas, use first response
        CompletableFuture<Object> winner = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return "Replica-1 (slow)";
                }, ioPool),
                CompletableFuture.supplyAsync(() -> {
                    sleep(100);
                    return "Replica-2 (fast)";
                }, ioPool),
                CompletableFuture.supplyAsync(() -> {
                    sleep(200);
                    return "Replica-3 (medium)";
                }, ioPool));
        System.out.println("Winner: " + winner.get()); // "Replica-2 (fast)"
    }

    // =======================================================
    // 6. allOf — Fan-out, then Collect Results
    // =======================================================

    static void demonstrateAllOf() throws Exception {
        System.out.println("\n=== allOf — Fan-out Collect ===");

        List<String> userIds = List.of("u1", "u2", "u3", "u4", "u5");

        // Start all fetches concurrently
        List<CompletableFuture<User>> futures = userIds.stream()
                .map(CompletableFutureDemo::validateUser)
                .toList();

        // Wait for ALL to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(f -> f.join()) // safe: allOf guarantees all are done
                        .toList())
                .thenAccept(users -> {
                    System.out.printf("Loaded %d users concurrently:%n", users.size());
                    users.forEach(u -> System.out.println("  - " + u));
                })
                .get(5, TimeUnit.SECONDS);
    }

    // =======================================================
    // Data classes
    // =======================================================
    record User(String id, String name) {
    }

    // =======================================================
    // Helper
    // =======================================================
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =======================================================
    // MAIN
    // =======================================================
    public static void main(String[] args) throws Exception {
        demonstrateSequentialChain();
        demonstrateParallelTasks();
        demonstrateFullPipeline();
        demonstrateErrorHandling();
        demonstrateAnyOf();
        demonstrateAllOf();

        ioPool.shutdown();
    }
}
