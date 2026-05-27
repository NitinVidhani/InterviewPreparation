package com.lld.hld.kafka.consumer;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ============================================================
 * MultiThreadedConsumer — High-Throughput Consumer Pattern
 * ============================================================
 *
 * PROBLEM WITH SINGLE-THREADED CONSUMER:
 * The KafkaConsumer is NOT thread-safe. You cannot call poll() from
 * multiple threads. So how do you process records concurrently?
 *
 * SOLUTION — Two patterns:
 *
 * Pattern A — Multiple Consumer Instances (EASIEST, RECOMMENDED):
 * Run Kafka consumer in separate threads. Each thread = one consumer.
 * All consumers share the same group.id → Kafka balances partitions.
 *
 * ┌── Thread 1: Consumer ──────────────────────────────────┐
 * │ poll() → process → commit (owns Partition 0, 1) │
 * └────────────────────────────────────────────────────────┘
 * ┌── Thread 2: Consumer ──────────────────────────────────┐
 * │ poll() → process → commit (owns Partition 2, 3) │
 * └────────────────────────────────────────────────────────┘
 *
 * Pattern B — Consumer + Worker Thread Pool (THIS FILE):
 * ONE consumer thread polls Kafka. Records handed off to a
 * thread pool for PARALLEL processing. Consumer commits only
 * after workers confirm completion.
 *
 * ┌── Poll Thread ──────────────────────────────────────────┐
 * │ poll() → hand off to WorkerPool → wait → commit │
 * └────────────────────────────────────────────────────────┐
 * ↓ ↓ ↓
 * ┌── Worker 1 ─┐ ┌── Worker 2 ─┐ ┌── Worker 3 ─┐
 * │ process rec │ │ process rec │ │ process rec │
 * └─────────────┘ └─────────────┘ └─────────────┘
 *
 * WHEN TO USE PATTERN B:
 * - Processing is CPU-intensive (ML inference, image/video processing).
 * - You want parallelism WITHIN a partition (NOT within Kafka's model).
 * - You have fewer partitions than desired processing threads.
 *
 * WARNING:
 * Pattern B is COMPLEX. Ordering within a partition is broken if workers
 * complete out-of-order. Offset commits must be carefully managed.
 *
 * INTERVIEW TIP:
 * "For I/O-bound workloads (DB calls), I'd use multiple consumer instances
 * (Pattern A). For CPU-bound workloads, I'd use Pattern B but acknowledge
 * that it breaks within-partition ordering and requires careful offset
 * tracking."
 */
public class MultiThreadedConsumer {

    private static final String TOPIC = "orders";
    private static final String GROUP_ID = "order-processor-mt-cg";
    private static final int WORKER_THREADS = 8;

    // -------------------------------------------------------
    // Pattern A: Multiple Consumer Instances
    // (PREFERRED for most use cases)
    // -------------------------------------------------------

    /**
     * PATTERN A: Spawn N independent consumer threads.
     *
     * Each ConsumerWorker is a complete, self-contained consumer loop:
     * - Has its own KafkaConsumer instance (thread-safe per instance).
     * - All share the same group.id → Kafka balances partitions.
     * - No coordination needed between threads.
     *
     * SCALING: Add more ConsumerWorker threads up to = num partitions.
     * Beyond that, extra threads are idle (no partition to assign).
     */
    public static List<Thread> startMultipleConsumers(int numConsumers) {
        List<Thread> threads = new ArrayList<>();
        List<ConsumerWorkerA> workers = new ArrayList<>();

        for (int i = 0; i < numConsumers; i++) {
            ConsumerWorkerA worker = new ConsumerWorkerA("consumer-" + i);
            workers.add(worker);

            Thread thread = new Thread(worker, "kafka-consumer-" + i);
            thread.setDaemon(false); // Non-daemon: JVM won't exit while these are running
            threads.add(thread);
            thread.start();

            System.out.printf("[Multi-Consumer] Started consumer thread: kafka-consumer-%d%n", i);
        }

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Multi-Consumer] Shutdown initiated...");
            workers.forEach(ConsumerWorkerA::shutdown);
            threads.forEach(t -> {
                try {
                    t.join(10_000);
                } catch (InterruptedException ignored) {
                }
            });
            System.out.println("[Multi-Consumer] All consumers stopped.");
        }));

        return threads;
    }

    // -------------------------------------------------------
    // Pattern A: ConsumerWorker — self-contained consumer loop
    // -------------------------------------------------------
    static class ConsumerWorkerA implements Runnable {

        private final String consumerId;
        private final KafkaConsumer<String, String> consumer;
        private final AtomicBoolean running = new AtomicBoolean(true);

        ConsumerWorkerA(String consumerId) {
            this.consumerId = consumerId;
            this.consumer = new KafkaConsumer<>(buildConsumerProps());
        }

        @Override
        public void run() {
            try {
                consumer.subscribe(List.of(TOPIC));

                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                    for (ConsumerRecord<String, String> record : records) {
                        processRecord(consumerId, record);
                    }

                    if (!records.isEmpty()) {
                        consumer.commitAsync(); // best-effort async commit
                    }
                }
            } catch (WakeupException e) {
                // Expected on shutdown
            } finally {
                try {
                    consumer.commitSync(); // Final sync commit before exit
                } finally {
                    consumer.close();
                    System.out.printf("[%s] Closed cleanly.%n", consumerId);
                }
            }
        }

        void shutdown() {
            running.set(false);
            consumer.wakeup();
        }
    }

    // -------------------------------------------------------
    // Pattern B: Single consumer + ThreadPool for processing
    // -------------------------------------------------------

    /**
     * PATTERN B: One consumer polls. Workers process in parallel.
     *
     * OFFSET TRACKING CHALLENGE:
     * Workers may complete out of order:
     * Record offset 100 → Worker 1 finishes LAST
     * Record offset 101 → Worker 2 finishes FIRST
     * Record offset 102 → Worker 3 finishes SECOND
     *
     * If we commit 102 after Worker 2 finishes, and crash before 100 finishes,
     * offset 100 is PERMANENTLY SKIPPED.
     *
     * SOLUTION: Track completion per offset. Only advance commit pointer
     * when all earlier offsets are also complete (contiguous commit tracking).
     *
     * In this implementation we use a simpler approach:
     * Wait for ALL futures in a batch to complete before committing any offset.
     * This adds latency but is safe. Production systems use per-partition
     * concurrent watermark tracking.
     */
    static class ThreadPoolConsumer {

        private final KafkaConsumer<String, String> consumer;
        private final ExecutorService workerPool;
        private final AtomicBoolean running = new AtomicBoolean(true);

        ThreadPoolConsumer() {
            this.consumer = new KafkaConsumer<>(buildConsumerProps());
            this.workerPool = new ThreadPoolExecutor(
                    WORKER_THREADS, // core threads
                    WORKER_THREADS, // max threads
                    60L, TimeUnit.SECONDS, // idle thread keepalive
                    new ArrayBlockingQueue<>(500), // bounded queue — applies back-pressure
                    new ThreadPoolExecutor.CallerRunsPolicy() // if queue full: poll thread processes
            );
        }

        public void start() {
            consumer.subscribe(List.of(TOPIC));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                consumer.wakeup();
            }));

            try {
                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                    if (records.isEmpty())
                        continue;

                    // ── Submit all records to worker pool ────────────────
                    List<Future<?>> futures = new ArrayList<>();

                    for (ConsumerRecord<String, String> record : records) {
                        Future<?> future = workerPool.submit(() -> {
                            processRecord("worker-pool", record);
                        });
                        futures.add(future);
                    }

                    // ── Wait for ALL futures before committing ───────────
                    // This ensures no message is skipped on restart.
                    // Tradeoff: adds latency (bounded by slowest worker in batch).
                    for (Future<?> future : futures) {
                        try {
                            future.get(30, TimeUnit.SECONDS); // fail-fast timeout
                        } catch (TimeoutException e) {
                            System.err.println("[ThreadPoolConsumer] Worker timed out! Aborting batch.");
                            return; // Don't commit — let Kafka reprocess this batch
                        } catch (ExecutionException e) {
                            System.err.println("[ThreadPoolConsumer] Worker exception: " + e.getCause());
                            // In production: send to DLQ, then commit to avoid poison pill
                        }
                    }

                    // ── Commit only after ALL workers are done ───────────
                    // Build explicit offset map from the last record per partition
                    Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                    for (ConsumerRecord<String, String> r : records) {
                        offsets.merge(
                                new TopicPartition(r.topic(), r.partition()),
                                new OffsetAndMetadata(r.offset() + 1),
                                (existing, next) -> next.offset() > existing.offset() ? next : existing);
                    }
                    consumer.commitSync(offsets);
                    System.out.printf("[ThreadPoolConsumer] Committed %d records.%n", records.count());
                }
            } catch (WakeupException e) {
                System.out.println("[ThreadPoolConsumer] Wakeup received.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                workerPool.shutdown();
                try {
                    workerPool.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                consumer.close();
                System.out.println("[ThreadPoolConsumer] Shut down cleanly.");
            }
        }
    }

    // -------------------------------------------------------
    // Shared: record processing logic (simulated)
    // -------------------------------------------------------
    private static void processRecord(String workerId, ConsumerRecord<String, String> record) {
        System.out.printf("[%s] Processing: topic=%s, partition=%d, offset=%d, key=%s%n",
                workerId, record.topic(), record.partition(), record.offset(), record.key());

        // Simulate variable processing time (e.g. DB write, external API call)
        try {
            Thread.sleep((long) (Math.random() * 50));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------
    // Shared: consumer config
    // -------------------------------------------------------
    private static Properties buildConsumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30_000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3_000);
        return props;
    }

    // -------------------------------------------------------
    // Main
    // -------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Multi-Threaded Kafka Consumer Demo ===\n");
        System.out.println("Pattern A: Multiple independent consumers (4 threads)");

        // Pattern A — 4 consumer threads sharing 1 group
        // Each handles a subset of the topic's partitions
        List<Thread> threads = startMultipleConsumers(4);

        System.out.println("\nPattern B: Single consumer + worker thread pool");
        // Un-comment to run Pattern B:
        // new ThreadPoolConsumer().start();

        // Wait for all consumer threads
        for (Thread t : threads) {
            t.join();
        }
    }
}
