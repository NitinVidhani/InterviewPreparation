package com.lld.hld.multithreading;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * ============================================================
 * ThreadPoolDemo — Executors, Custom Pools, Shutdown Patterns
 * ============================================================
 *
 * CONCEPTS COVERED:
 * 1. Fixed, cached, single, scheduled thread pools
 * 2. Custom ThreadPoolExecutor with all parameters
 * 3. Graceful shutdown pattern
 * 4. Future, Callable — async task with result
 * 5. Rejection policies explained
 * 6. Thread naming (critical for debugging in production)
 *
 * INTERVIEW TIP:
 * "We never use raw Thread.new() in production. All async work
 * goes through named thread pools with explicit bounds, rejection
 * policies, and shutdown hooks. Thread names like 'order-worker-N'
 * make heap dumps and stack traces immediately intelligible."
 */
public class ThreadPoolDemo {

    // =======================================================
    // 1. Standard Factory Pools
    // =======================================================

    static void demonstrateStandardPools() {
        System.out.println("=== Standard Thread Pools ===\n");

        // ── Fixed Pool: N threads always alive
        System.out.println("--- Fixed Thread Pool (4 threads) ---");
        ExecutorService fixedPool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 8; i++) {
            final int taskId = i;
            fixedPool.submit(() -> {
                System.out.printf("[FixedPool] Task %d on %s%n",
                        taskId, Thread.currentThread().getName());
                sleep(200);
            });
        }
        // 8 tasks, 4 threads → tasks 5-8 wait in queue
        shutdown(fixedPool, "FixedPool");

        // ── Cached Pool: grows/shrinks on demand
        System.out.println("\n--- Cached Thread Pool ---");
        ExecutorService cachedPool = Executors.newCachedThreadPool();
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            cachedPool.submit(() -> {
                System.out.printf("[CachedPool] Task %d on %s%n",
                        taskId, Thread.currentThread().getName());
                sleep(100);
            });
        }
        // Each task may get its own thread (all run concurrently)
        shutdown(cachedPool, "CachedPool");

        // ── Single Thread: sequential execution guaranteed
        System.out.println("\n--- Single Thread Executor ---");
        ExecutorService singleThread = Executors.newSingleThreadExecutor();
        for (int i = 0; i < 4; i++) {
            final int taskId = i;
            singleThread.submit(() -> {
                System.out.printf("[SingleThread] Task %d on %s%n",
                        taskId, Thread.currentThread().getName());
                sleep(100);
            });
        }
        // All 4 tasks run SEQUENTIALLY on single thread
        shutdown(singleThread, "SingleThread");
    }

    // =======================================================
    // 2. Custom ThreadPoolExecutor
    // =======================================================

    /**
     * Production-grade thread pool with:
     * - Named threads for debugging
     * - Bounded queue (back-pressure)
     * - Caller-runs rejection policy (natural throttling)
     * - JMX-friendly monitoring
     */
    static ThreadPoolExecutor buildProductionPool(String serviceName) {
        int cores = Runtime.getRuntime().availableProcessors();

        return new ThreadPoolExecutor(
                cores, // corePoolSize: always-alive workers
                cores * 2, // maximumPoolSize: burst capacity
                60L, TimeUnit.SECONDS, // idle thread timeout
                new ArrayBlockingQueue<>(200), // BOUNDED queue — prevents OOM
                new NamedThreadFactory(serviceName), // meaningful thread names
                new ThreadPoolExecutor.CallerRunsPolicy() // back-pressure: caller runs task
        // Other options:
        // AbortPolicy → throw RejectedExecutionException
        // DiscardPolicy → silently drop task
        // DiscardOldestPolicy → drop oldest queued task
        );
    }

    /** Custom ThreadFactory that names threads meaningfully. */
    static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger count = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-worker-" + count.getAndIncrement());
            t.setDaemon(false); // don't exit JVM while these are alive
            t.setPriority(Thread.NORM_PRIORITY);
            // In production: add UncaughtExceptionHandler
            t.setUncaughtExceptionHandler(
                    (thread, ex) -> System.err.printf("[%s] Uncaught exception: %s%n", thread.getName(), ex));
            return t;
        }
    }

    // =======================================================
    // 3. Future / Callable — Async Task with Return Value
    // =======================================================

    static void demonstrateFuture() throws InterruptedException {
        System.out.println("\n=== Future / Callable Demo ===");

        ExecutorService exec = buildProductionPool("order");

        // Submit 3 tasks that return results
        Future<String> f1 = exec.submit(() -> {
            sleep(300);
            return "Result from DB query";
        });

        Future<String> f2 = exec.submit(() -> {
            sleep(500);
            return "Result from API call";
        });

        Future<String> f3 = exec.submit(() -> {
            sleep(100);
            if (Math.random() > 0.5)
                throw new RuntimeException("Simulated failure!");
            return "Result from cache";
        });

        // Collect results safely
        List<Future<String>> futures = List.of(f1, f2, f3);
        for (int i = 0; i < futures.size(); i++) {
            try {
                String result = futures.get(i).get(2, TimeUnit.SECONDS); // timeout
                System.out.printf("Task %d result: %s%n", i + 1, result);
            } catch (TimeoutException e) {
                System.err.printf("Task %d timed out!%n", i + 1);
                futures.get(i).cancel(true);
            } catch (ExecutionException e) {
                System.err.printf("Task %d failed: %s%n", i + 1, e.getCause().getMessage());
            }
        }

        shutdown(exec, "order-pool");
    }

    // =======================================================
    // 4. Scheduled Tasks
    // =======================================================

    static void demonstrateScheduledPool() throws InterruptedException {
        System.out.println("\n=== Scheduled Thread Pool ===");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                2, new NamedThreadFactory("scheduler"));

        AtomicInteger count = new AtomicInteger(0);

        // ── Fixed-rate: fires every 1 second regardless of task duration
        ScheduledFuture<?> fixedRate = scheduler.scheduleAtFixedRate(() -> {
            System.out.printf("[FixedRate] Tick #%d at %s%n",
                    count.incrementAndGet(), new java.util.Date());
        }, 0, 1, TimeUnit.SECONDS);

        // ── Fixed-delay: waits 2 seconds AFTER each completion
        ScheduledFuture<?> fixedDelay = scheduler.scheduleWithFixedDelay(() -> {
            System.out.println("[FixedDelay] Processing... (simulating 500ms work)");
            sleep(500); // 500ms work + 2000ms delay = next run after 2500ms total
        }, 0, 2, TimeUnit.SECONDS);

        // ── One-shot delayed task
        scheduler.schedule(() -> {
            System.out.println("[OneShot] Cleaning up old data...");
        }, 3, TimeUnit.SECONDS);

        sleep(5000); // run for 5 seconds

        fixedRate.cancel(false); // allow current run to complete, then cancel
        fixedDelay.cancel(false);
        scheduler.shutdown();
        System.out.println("Scheduler stopped.");
    }

    // =======================================================
    // 5. Graceful Shutdown Pattern
    // =======================================================

    /**
     * PRODUCTION SHUTDOWN PATTERN:
     * 1. shutdown() — stop accepting new tasks; finish current + queued work
     * 2. awaitTermination()— wait for all tasks to complete
     * 3. If timeout: shutdownNow() — interrupt running tasks, return queued tasks
     * 4. Log unprocessed tasks
     */
    static void shutdown(ExecutorService exec, String name) {
        exec.shutdown(); // graceful — no new tasks, finish existing
        try {
            if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.printf("[%s] Timeout waiting for shutdown, forcing...%n", name);
                List<Runnable> unfinished = exec.shutdownNow(); // interrupt running tasks
                System.err.printf("[%s] %d tasks were unfinished%n", name, unfinished.size());
            } else {
                System.out.printf("[%s] Shut down cleanly.%n", name);
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =======================================================
    // 6. Monitoring Thread Pool Health
    // =======================================================

    static void monitorPool(ThreadPoolExecutor executor, String name) {
        System.out.printf("%n[MONITOR %s]%n", name);
        System.out.printf("  Active threads:    %d%n", executor.getActiveCount());
        System.out.printf("  Pool size:         %d (core=%d, max=%d)%n",
                executor.getPoolSize(), executor.getCorePoolSize(), executor.getMaximumPoolSize());
        System.out.printf("  Queue size:        %d%n", executor.getQueue().size());
        System.out.printf("  Completed tasks:   %d%n", executor.getCompletedTaskCount());
        System.out.printf("  Total tasks:       %d%n", executor.getTaskCount());
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
        demonstrateStandardPools();
        demonstrateFuture();
        demonstrateScheduledPool();

        System.out.println("\n=== Custom Production Pool ===");
        ThreadPoolExecutor productionPool = buildProductionPool("payment");
        for (int i = 0; i < 10; i++) {
            final int id = i;
            productionPool.submit(() -> {
                sleep(100);
                System.out.printf("[payment-worker-%d] Processed payment #%d%n",
                        id, id);
            });
        }
        monitorPool(productionPool, "payment-pool");
        shutdown(productionPool, "payment-pool");
    }
}
