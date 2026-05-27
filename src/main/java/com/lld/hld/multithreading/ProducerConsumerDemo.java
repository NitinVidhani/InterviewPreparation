package com.lld.hld.multithreading;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * ============================================================
 * ProducerConsumerDemo — Classic Concurrency Pattern
 * ============================================================
 *
 * Three implementations, each with different tradeoffs:
 *
 * 1. BLOCKING QUEUE (modern, recommended)
 * 2. WAIT/NOTIFY (intrinsic lock, foundational)
 * 3. REENTRANT LOCK + CONDITIONS (explicit, flexible)
 *
 * The Producer-Consumer pattern:
 * - Producer generates items and places them in a shared buffer.
 * - Consumer takes items from the buffer and processes them.
 * - Buffer is BOUNDED — prevents producers from overwhelming consumers.
 * - When buffer FULL → producer blocks (backpressure)
 * - When buffer EMPTY → consumer blocks (no busy-waiting)
 *
 * INTERVIEW TIP:
 * "The BlockingQueue version is what I'd use in production — it's
 * battle-tested, handles spurious wakeups, and separates concerns.
 * But I understand the wait/notify mechanism underneath because it
 * helps debug deadlocks and understand lock semantics."
 */
public class ProducerConsumerDemo {

    // =======================================================
    // 1. BLOCKING QUEUE — Production Standard (simplest, best)
    // =======================================================

    /**
     * BlockingQueue encapsulates all the wait/notify complexity.
     * put() and take() handle blocking and wakeup automatically.
     */
    static class BlockingQueueDemo {
        private final BlockingQueue<String> queue;
        private final AtomicBoolean running = new AtomicBoolean(true);

        BlockingQueueDemo(int capacity) {
            this.queue = new LinkedBlockingQueue<>(capacity);
        }

        class Producer implements Runnable {
            private final String name;
            private int count = 0;

            Producer(String name) {
                this.name = name;
            }

            @Override
            public void run() {
                while (running.get()) {
                    try {
                        String item = name + "-item-" + count++;
                        queue.put(item); // blocks if queue is full
                        System.out.printf("[PRODUCER %s] Produced: %s | Queue size: %d%n",
                                name, item, queue.size());
                        Thread.sleep(100); // simulate production time
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        class Consumer implements Runnable {
            private final String name;

            Consumer(String name) {
                this.name = name;
            }

            @Override
            public void run() {
                while (running.get() || !queue.isEmpty()) {
                    try {
                        String item = queue.poll(500, TimeUnit.MILLISECONDS); // blocks if empty
                        if (item != null) {
                            process(item);
                            System.out.printf("[CONSUMER %s] Consumed: %s | Queue size: %d%n",
                                    name, item, queue.size());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            private void process(String item) {
                try {
                    Thread.sleep(200);
                } // simulate processing time
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        void run() throws InterruptedException {
            ExecutorService exec = Executors.newFixedThreadPool(5);

            // 2 producers, 3 consumers
            exec.submit(new Producer("P1"));
            exec.submit(new Producer("P2"));
            exec.submit(new Consumer("C1"));
            exec.submit(new Consumer("C2"));
            exec.submit(new Consumer("C3"));

            Thread.sleep(3000); // run for 3 seconds

            running.set(false);
            exec.shutdown();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // =======================================================
    // 2. WAIT/NOTIFY — Foundational (understand this deeply)
    // =======================================================

    /**
     * BoundedBuffer using wait/notify with intrinsic locks (synchronized).
     *
     * CRITICAL: Always use 'while' not 'if' for wait() condition check.
     * Reason: spurious wakeups (OS/JVM may wake thread without notify()).
     * Also: notifyAll() wakes all threads; only ONE gets the lock;
     * the others re-check the condition and wait again.
     */
    static class WaitNotifyBuffer<T> {
        private final Queue<T> buffer = new LinkedList<>();
        private final int capacity;

        WaitNotifyBuffer(int capacity) {
            this.capacity = capacity;
        }

        /**
         * PRODUCER calls put(): blocks when buffer is full.
         *
         * Pattern:
         * synchronized → check condition (while) → wait if not met → do work → notify
         */
        public synchronized void put(T item) throws InterruptedException {
            // WHILE (not if) — re-check after wakeup (spurious or after another consumer)
            while (buffer.size() == capacity) {
                System.out.println(Thread.currentThread().getName() + " waiting to put (buffer full)");
                wait(); // releases the lock and waits
                // After wakeup: re-acquires lock, re-evaluates condition
            }
            buffer.offer(item);
            System.out.printf("[PUT] %s added '%s' | size=%d%n",
                    Thread.currentThread().getName(), item, buffer.size());
            notifyAll(); // wake all waiting consumers AND producers
        }

        /**
         * CONSUMER calls take(): blocks when buffer is empty.
         */
        public synchronized T take() throws InterruptedException {
            while (buffer.isEmpty()) {
                System.out.println(Thread.currentThread().getName() + " waiting to take (buffer empty)");
                wait();
            }
            T item = buffer.poll();
            System.out.printf("[TAKE] %s removed '%s' | size=%d%n",
                    Thread.currentThread().getName(), item, buffer.size());
            notifyAll(); // wake waiting producers
            return item;
        }

        // Peek without blocking (for monitoring)
        public synchronized int size() {
            return buffer.size();
        }
    }

    // =======================================================
    // 3. REENTRANT LOCK + CONDITIONS — Two separate conditions
    // =======================================================

    /**
     * Using explicit Lock and Condition objects.
     *
     * ADVANTAGE over wait/notify:
     * - Two SEPARATE conditions: notFull (for producers) and notEmpty (for
     * consumers)
     * - When buffer has space: signal ONLY producers (not all threads)
     * - When buffer has items: signal ONLY consumers (not all threads)
     * - Reduces unnecessary wakeups vs notifyAll()
     *
     * This is exactly how java.util.concurrent.LinkedBlockingQueue works
     * internally!
     */
    static class ReentrantLockBuffer<T> {
        private final Queue<T> buffer = new LinkedList<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock(true); // fair=true
        private final Condition notFull = lock.newCondition(); // producers wait here
        private final Condition notEmpty = lock.newCondition(); // consumers wait here

        ReentrantLockBuffer(int capacity) {
            this.capacity = capacity;
        }

        public void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (buffer.size() == capacity) {
                    notFull.await(); // producer waits on notFull condition
                }
                buffer.offer(item);
                notEmpty.signal(); // signal ONE consumer (buffer now has item)
            } finally {
                lock.unlock(); // ALWAYS in finally
            }
        }

        public T take() throws InterruptedException {
            lock.lock();
            try {
                while (buffer.isEmpty()) {
                    notEmpty.await(); // consumer waits on notEmpty condition
                }
                T item = buffer.poll();
                notFull.signal(); // signal ONE producer (buffer now has space)
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    // =======================================================
    // MAIN — Run all demos
    // =======================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Producer-Consumer Demo ===\n");

        // Demo 1: BlockingQueue (most common in practice)
        System.out.println("--- BlockingQueue Implementation ---");
        new BlockingQueueDemo(5).run();

        System.out.println("\n--- Wait/Notify Implementation ---");
        WaitNotifyBuffer<Integer> buf = new WaitNotifyBuffer<>(3);

        Thread producer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    buf.put(i);
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Producer");

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    buf.take();
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("Done!\n");
    }
}
