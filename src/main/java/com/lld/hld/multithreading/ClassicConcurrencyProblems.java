package com.lld.hld.multithreading;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * ============================================================
 * ClassicConcurrencyProblems — Interview-Ready Implementations
 * ============================================================
 *
 * PROBLEMS COVERED:
 * 1. Dining Philosophers — deadlock avoidance (resource ordering)
 * 2. Readers-Writers Problem — ReadWriteLock
 * 3. Bounded Blocking Stack — with ReentrantLock + Conditions
 * 4. Print FooBar Alternately (LeetCode 1115) — Semaphores
 * 5. Print in Order (LeetCode 1114) — CountDownLatch / Semaphore
 * 6. Thread-Safe Lazy Singleton — double-checked locking + holder
 * 7. Rate Limiter using Semaphore — token bucket style
 */
public class ClassicConcurrencyProblems {

    // =======================================================
    // 1. DINING PHILOSOPHERS — Deadlock Avoidance
    // =======================================================

    /**
     * Problem: 5 philosophers, 5 forks. Each needs 2 adjacent forks.
     * Naive approach (each grabs left then right) → circular wait → deadlock.
     *
     * FIX — Resource Ordering:
     * Always acquire the LOWER-NUMBERED fork first.
     * This breaks the circular dependency (one philosopher picks up
     * high-numbered fork first → can't form a cycle with others).
     */
    static class DiningPhilosophers {

        static void solve() throws InterruptedException {
            int n = 5;
            ReentrantLock[] forks = new ReentrantLock[n];
            for (int i = 0; i < n; i++)
                forks[i] = new ReentrantLock();

            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int id = i;
                threads.add(new Thread(() -> {
                    // CRITICAL: always acquire lower index first
                    int left = id;
                    int right = (id + 1) % n;
                    ReentrantLock first = (left < right) ? forks[left] : forks[right];
                    ReentrantLock second = (left < right) ? forks[right] : forks[left];

                    for (int meal = 0; meal < 3; meal++) {
                        // Think
                        System.out.printf("  Philosopher %d is thinking%n", id);
                        sleep((long) (Math.random() * 100));

                        // Eat (with try-lock + timeout to avoid starvation)
                        try {
                            if (first.tryLock(500, TimeUnit.MILLISECONDS)) {
                                try {
                                    if (second.tryLock(500, TimeUnit.MILLISECONDS)) {
                                        try {
                                            System.out.printf("  Philosopher %d is EATING (meal %d)%n", id, meal + 1);
                                            sleep(100);
                                        } finally {
                                            second.unlock();
                                        }
                                    }
                                } finally {
                                    first.unlock();
                                }
                            } else {
                                System.out.printf("  Philosopher %d couldn't get forks, backing off%n", id);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }, "Philosopher-" + id));
            }

            threads.forEach(Thread::start);
            for (Thread t : threads)
                t.join();
            System.out.println("All philosophers done — no deadlock!");
        }
    }

    // =======================================================
    // 2. BOUNDED BLOCKING STACK — ReentrantLock + 2 Conditions
    // =======================================================

    /**
     * LIFO stack with blocking push/pop.
     * Uses TWO conditions (notFull, notEmpty) for precise signaling.
     */
    static class BoundedBlockingStack<T> {
        private final Deque<T> stack = new ArrayDeque<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedBlockingStack(int capacity) {
            this.capacity = capacity;
        }

        public void push(T item) throws InterruptedException {
            lock.lock();
            try {
                while (stack.size() == capacity)
                    notFull.await();
                stack.push(item);
                notEmpty.signal(); // one consumer can now pop
            } finally {
                lock.unlock();
            }
        }

        public T pop() throws InterruptedException {
            lock.lock();
            try {
                while (stack.isEmpty())
                    notEmpty.await();
                T item = stack.pop();
                notFull.signal(); // one producer can now push
                return item;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try {
                return stack.size();
            } finally {
                lock.unlock();
            }
        }

        static void demo() throws InterruptedException {
            System.out.println("\n=== Bounded Blocking Stack ===");
            BoundedBlockingStack<Integer> s = new BoundedBlockingStack<>(3);

            Thread producer = new Thread(() -> {
                for (int i = 1; i <= 6; i++) {
                    try {
                        s.push(i);
                        System.out.printf("  Pushed %d, size=%d%n", i, s.size());
                        sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            Thread consumer = new Thread(() -> {
                for (int i = 0; i < 6; i++) {
                    try {
                        sleep(150);
                        int v = s.pop();
                        System.out.printf("  Popped %d, size=%d%n", v, s.size());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
        }
    }

    // =======================================================
    // 3. PRINT FOO BAR ALTERNATELY (LeetCode 1115) — Semaphores
    // =======================================================

    /**
     * Two threads: one prints "foo", other prints "bar".
     * Output must be: foobarfoobarfoobar (alternating, n times).
     *
     * SOLUTION: Two semaphores.
     * - fooSem starts with 1 (foo thread can go first)
     * - barSem starts with 0 (bar thread must wait)
     */
    static class FooBar {
        private final int n;
        private final Semaphore fooSem = new Semaphore(1); // foo goes first
        private final Semaphore barSem = new Semaphore(0); // bar waits

        FooBar(int n) {
            this.n = n;
        }

        // Called by Thread 1
        public void foo() throws InterruptedException {
            for (int i = 0; i < n; i++) {
                fooSem.acquire(); // wait for permission to print foo
                System.out.print("foo");
                barSem.release(); // signal bar thread
            }
        }

        // Called by Thread 2
        public void bar() throws InterruptedException {
            for (int i = 0; i < n; i++) {
                barSem.acquire(); // wait for permission to print bar
                System.out.print("bar");
                fooSem.release(); // signal foo thread
            }
        }

        static void demo() throws InterruptedException {
            System.out.println("\n\n=== FooBar Alternating (n=4) ===");
            FooBar fb = new FooBar(4);
            Thread t1 = new Thread(() -> {
                try {
                    fb.foo();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    fb.bar();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            System.out.println(); // newline
        }
    }

    // =======================================================
    // 4. PRINT IN ORDER (LeetCode 1114) — Semaphore
    // =======================================================

    /**
     * 3 threads call first(), second(), third() in any order.
     * Guarantee output order: first → second → third.
     *
     * SOLUTION: Two semaphores as gates.
     */
    static class PrintInOrder {
        private final Semaphore secondGate = new Semaphore(0); // second waits
        private final Semaphore thirdGate = new Semaphore(0); // third waits

        public void first() throws InterruptedException {
            System.out.print("first");
            secondGate.release(); // allow second to proceed
        }

        public void second() throws InterruptedException {
            secondGate.acquire(); // wait for first
            System.out.print(" second");
            thirdGate.release(); // allow third to proceed
        }

        public void third() throws InterruptedException {
            thirdGate.acquire(); // wait for second
            System.out.print(" third");
        }

        static void demo() throws InterruptedException {
            System.out.println("\n=== Print In Order ===");
            PrintInOrder pio = new PrintInOrder();

            // Threads start in wrong order (second, third, first)
            Thread t2 = new Thread(() -> {
                try {
                    pio.second();
                } catch (InterruptedException e) {
                }
            });
            Thread t3 = new Thread(() -> {
                try {
                    pio.third();
                } catch (InterruptedException e) {
                }
            });
            Thread t1 = new Thread(() -> {
                try {
                    pio.first();
                } catch (InterruptedException e) {
                }
            });

            t2.start();
            t3.start();
            t1.start();
            t1.join();
            t2.join();
            t3.join();
            System.out.println(); // newline
        }
    }

    // =======================================================
    // 5. READER-WRITER PROBLEM — StampedLock
    // =======================================================

    /**
     * Multiple readers can read simultaneously.
     * Writers need exclusive access (block all readers + other writers).
     *
     * StampedLock adds optimistic reads for max performance.
     */
    static class ReadWriteRegistry {
        private final Map<String, String> data = new HashMap<>();
        private final StampedLock lock = new StampedLock();

        // Optimistic read — no lock if data not changing
        public String get(String key) {
            long stamp = lock.tryOptimisticRead();
            String value = data.get(key);
            if (!lock.validate(stamp)) { // writer changed data during read
                stamp = lock.readLock(); // fall back to read lock
                try {
                    value = data.get(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            return value;
        }

        // Write lock — exclusive
        public void put(String key, String value) {
            long stamp = lock.writeLock();
            try {
                data.put(key, value);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        static void demo() throws InterruptedException {
            System.out.println("\n=== Reader-Writer (StampedLock) ===");
            ReadWriteRegistry reg = new ReadWriteRegistry();
            reg.put("config", "value-1");

            List<Thread> readers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final int id = i;
                readers.add(new Thread(() -> {
                    for (int j = 0; j < 3; j++) {
                        System.out.printf("  Reader-%d: config=%s%n", id, reg.get("config"));
                        sleep(50);
                    }
                }));
            }

            Thread writer = new Thread(() -> {
                for (int v = 2; v <= 4; v++) {
                    sleep(100);
                    String newVal = "value-" + v;
                    reg.put("config", newVal);
                    System.out.println("  Writer: updated config to " + newVal);
                }
            });

            readers.forEach(Thread::start);
            writer.start();
            readers.forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            });
            writer.join();
        }
    }

    // =======================================================
    // 6. THREAD-SAFE LAZY SINGLETON
    // =======================================================

    /**
     * Three correct patterns shown side by side.
     */
    // Pattern 1: Eager initialization
    static class EagerSingleton {
        private static final EagerSingleton INSTANCE = new EagerSingleton();

        private EagerSingleton() {
        }

        public static EagerSingleton getInstance() {
            return INSTANCE;
        }
    }

    // Pattern 2: Initialization-on-demand holder (BEST for lazy init)
    static class HolderSingleton {
        private HolderSingleton() {
        }

        private static class Holder {
            // Loaded lazily when Holder class is first accessed
            // Class loading is thread-safe by JVM spec
            static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        public static HolderSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    // Pattern 3: Double-checked locking with volatile (Java 5+)
    static class DoubleCheckedSingleton {
        private static volatile DoubleCheckedSingleton instance; // volatile!

        private DoubleCheckedSingleton() {
        }

        public static DoubleCheckedSingleton getInstance() {
            if (instance == null) { // first check (no lock)
                synchronized (DoubleCheckedSingleton.class) {
                    if (instance == null) { // second check (under lock)
                        instance = new DoubleCheckedSingleton();
                        // volatile prevents: assign-before-init reordering
                    }
                }
            }
            return instance;
        }
    }

    // Pattern 4: Enum singleton (prevents reflection/serialization attacks)
    enum EnumSingleton {
        INSTANCE;

        public void doSomething() {
            System.out.println("EnumSingleton.doSomething()");
        }
    }

    // =======================================================
    // 7. RATE LIMITER — Semaphore-based
    // =======================================================

    /**
     * Allow at most N operations per second.
     * Background refiller thread adds permits at fixed rate.
     */
    static class RateLimiter {
        private final Semaphore permits;
        private final int permitsPerSecond;
        private final ScheduledExecutorService refiller;

        RateLimiter(int permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
            this.permits = new Semaphore(permitsPerSecond);
            this.refiller = Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "rate-limiter-refiller");
                        t.setDaemon(true);
                        return t;
                    });

            // Refill permits every second
            refiller.scheduleAtFixedRate(() -> {
                int current = permits.availablePermits();
                int toAdd = permitsPerSecond - current;
                if (toAdd > 0)
                    permits.release(toAdd);
            }, 1, 1, TimeUnit.SECONDS);
        }

        // Try to acquire a permit (non-blocking)
        public boolean tryAcquire() {
            return permits.tryAcquire();
        }

        // Acquire with block (waits if no permits)
        public void acquire() throws InterruptedException {
            permits.acquire();
        }

        public void stop() {
            refiller.shutdown();
        }

        static void demo() throws InterruptedException {
            System.out.println("\n=== Rate Limiter (5 req/sec) ===");
            RateLimiter limiter = new RateLimiter(5);

            for (int i = 0; i < 10; i++) {
                if (limiter.tryAcquire()) {
                    System.out.printf("  Request %d: ALLOWED%n", i + 1);
                } else {
                    System.out.printf("  Request %d: RATE LIMITED%n", i + 1);
                }
                sleep(80); // 80ms apart → ~12 req/sec attempted, 5 allowed
            }
            limiter.stop();
        }
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
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Classic Concurrency Problems ===\n");

        System.out.println("1. Dining Philosophers:");
        DiningPhilosophers.solve();

        BoundedBlockingStack.demo();
        FooBar.demo();
        PrintInOrder.demo();
        ReadWriteRegistry.demo();
        RateLimiter.demo();

        System.out.println("\n--- Singleton Patterns ---");
        System.out.println("HolderSingleton: " + HolderSingleton.getInstance());
        System.out.println("DoubleChecked:   " + DoubleCheckedSingleton.getInstance());
        EnumSingleton.INSTANCE.doSomething();
    }
}
