package com.lld.hld.kafka.consumer;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ============================================================
 * OrderConsumer — Kafka Consumer Deep Dive
 * ============================================================
 *
 * CONCEPTS COVERED:
 * 1. Consumer group mechanics (group.id, partition assignment)
 * 2. Offset management: auto vs manual commit
 * 3. At-least-once vs at-most-once delivery
 * 4. Graceful shutdown pattern (WakeupException)
 * 5. Rebalance listeners (partition revoke/assign events)
 * 6. Consumer configurations explained
 * 7. Error handling and Dead Letter Queue (DLQ) pattern
 *
 * CONSUMER POLL LOOP FLOW:
 *
 * [Start Consumer]
 * ↓
 * consumer.subscribe(topics)
 * ↓
 * ┌─── poll(timeout) ──────────────────────────────────────┐
 * │ 1. Send heartbeat to Group Coordinator │
 * │ 2. Check for partition rebalance │
 * │ 3. Fetch records from broker (if available) │
 * │ 4. Return up to max.poll.records records │
 * └────────────────────────────────────────────────────────┘
 * ↓
 * Process records
 * ↓
 * commitSync / commitAsync (if manual commit)
 * ↓
 * Repeat
 */
public class OrderConsumer {

    private static final String TOPIC = "orders";
    private static final String DLQ_TOPIC = "orders.DLQ"; // Dead Letter Queue

    // Shutdown flag — set to true by signal handler / health check
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final KafkaConsumer<String, String> consumer;

    // -------------------------------------------------------
    // Configuration
    // -------------------------------------------------------

    /**
     * Builds production-grade consumer configuration.
     *
     * group.id = "order-processor-cg"
     * → All consumers with the same group.id share the work of consuming a topic.
     * → Each partition is assigned to EXACTLY ONE consumer within a group.
     * → Different groups consume the SAME messages independently (pub-sub style).
     *
     * auto.offset.reset = "earliest"
     * → If no committed offset exists for this group, start from beginning.
     * → "latest" = only new messages after consumer starts.
     * → "none" = throw exception if no committed offset (strict mode).
     *
     * enable.auto.commit = false
     * → Disable automatic offset commits.
     * → We commit AFTER successful processing to ensure at-least-once delivery.
     * → If we committed before processing (auto-commit could do this), and then
     * we crashed, the message would be skipped → at-most-once delivery.
     *
     * max.poll.records = 100
     * → Limits records returned per poll() invocation.
     * → Smaller = more frequent commits, less reprocessing on restart.
     * → Larger = higher throughput but longer reprocessing window on failure.
     *
     * max.poll.interval.ms = 300000 (5 min)
     * → If poll() is not called within this interval, consumer is considered dead
     * and the group rebalances. Increase if processing is slow.
     *
     * session.timeout.ms = 30000 (30 sec)
     * → If broker doesn't receive a heartbeat within this time, consumer is dead.
     * → Must be between group.min.session.timeout.ms and
     * group.max.session.timeout.ms.
     *
     * heartbeat.interval.ms = 3000 (3 sec)
     * → How often consumer sends heartbeat to broker.
     * → Should be ~1/3 of session.timeout.ms.
     */
    private static Properties buildConsumerConfig() {
        Properties props = new Properties();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // Consumer group — all instances of this service share the group
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processor-cg");

        // Offset reset — start from beginning if no committed offset
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Manual offset commit — for at-least-once semantics
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Batch size and timing
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);

        // Heartbeat and session
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30_000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3_000);

        // Fetch tuning — wait for at least 1MB before returning (reduces requests)
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        return props;
    }

    // -------------------------------------------------------
    // Constructor
    // -------------------------------------------------------
    public OrderConsumer() {
        this.consumer = new KafkaConsumer<>(buildConsumerConfig());
    }

    // -------------------------------------------------------
    // Rebalance Listener
    // -------------------------------------------------------

    /**
     * REBALANCE LISTENER — hooks into partition assignment/revocation events.
     *
     * WHEN does rebalancing happen?
     * - A new consumer joins the group.
     * - An existing consumer crashes or leaves.
     * - New partitions are added to the topic.
     * - Static group membership rebalance (admin action).
     *
     * onPartitionsRevoked:
     * Called BEFORE partitions are taken away from this consumer.
     * CRITICAL: Commit all in-progress offsets here to avoid reprocessing!
     *
     * onPartitionsAssigned:
     * Called AFTER new partitions are assigned.
     * Good place to load partition-specific state (e.g., ML model per partition).
     */
    private class OrderRebalanceListener implements ConsumerRebalanceListener {

        private final Map<TopicPartition, OffsetAndMetadata> pendingOffsets;

        public OrderRebalanceListener(Map<TopicPartition, OffsetAndMetadata> pendingOffsets) {
            this.pendingOffsets = pendingOffsets;
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            System.out.println("[REBALANCE] Partitions REVOKED: " + partitions);

            // IMPORTANT: Commit any pending offsets before partitions are reassigned.
            // This prevents reprocessing by the new consumer that gets these partitions.
            if (!pendingOffsets.isEmpty()) {
                consumer.commitSync(pendingOffsets);
                System.out.println("[REBALANCE] Committed pending offsets during revoke: " + pendingOffsets);
                pendingOffsets.clear();
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            System.out.println("[REBALANCE] Partitions ASSIGNED: " + partitions);
            // Optional: seek to specific offsets, load state, warm up caches
        }
    }

    // -------------------------------------------------------
    // Main Poll Loop (at-least-once with manual commit)
    // -------------------------------------------------------

    /**
     * AT-LEAST-ONCE CONSUMER: Commit AFTER processing.
     *
     * Guarantee: No message is lost. A message may be processed MORE THAN ONCE
     * if the consumer crashes after processing but before committing.
     *
     * This is the MOST COMMON pattern for production systems.
     * Make your processing IDEMPOTENT to safely handle duplicates:
     * - Check if order is already in DB before inserting.
     * - Use unique constraints in the database.
     * - Use the record's offset as an idempotency key.
     *
     * GRACEFUL SHUTDOWN:
     * Java's consumer.wakeup() is thread-safe. Call it from a shutdown hook.
     * It causes poll() to throw WakeupException, letting us exit cleanly.
     */
    public void startAtLeastOnce() {
        // Track offsets to commit (per-partition)
        Map<TopicPartition, OffsetAndMetadata> pendingOffsets = new HashMap<>();

        // Add shutdown hook for graceful termination (SIGTERM from Kubernetes, etc.)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SHUTDOWN] Signal received. Initiating graceful shutdown...");
            shutdown.set(true);
            consumer.wakeup(); // Breaks out of poll() with WakeupException
        }));

        try {
            /*
             * subscribe() registers interest in a topic pattern.
             * Kafka handles partition assignment automatically.
             * The rebalance listener runs when assignments change.
             */
            consumer.subscribe(List.of(TOPIC), new OrderRebalanceListener(pendingOffsets));

            System.out.println("[CONSUMER] Started. Waiting for orders on topic: " + TOPIC);

            while (!shutdown.get()) {
                /*
                 * poll() does multiple things:
                 * 1. Sends heartbeat to Group Coordinator.
                 * 2. Checks for new partition assignments (rebalance).
                 * 3. Fetches records from leader brokers.
                 * 4. Returns up to max.poll.records records.
                 *
                 * Duration.ofMillis(1000) = wait up to 1 second if no records available.
                 * Short duration = faster rebalance detection but more CPU.
                 * Long duration = slower reaction but less CPU overhead.
                 */
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    boolean processed = processRecord(record);

                    if (processed) {
                        // Track offset to commit (current offset + 1 = NEXT offset to read)
                        pendingOffsets.put(
                                new TopicPartition(record.topic(), record.partition()),
                                new OffsetAndMetadata(record.offset() + 1, "processed"));
                    }
                    // If not processed, it goes to DLQ in processRecord()
                }

                // Commit ALL successfully processed offsets in ONE request
                if (!pendingOffsets.isEmpty()) {
                    /*
                     * commitAsync(): Non-blocking. Better throughput.
                     * - On failure, the next commitAsync (or commitSync on shutdown) will
                     * catch up. No duplicates because a later commit overrides earlier ones.
                     * - NEVER retry commitAsync on failure — it could commit an older offset
                     * AFTER a newer one has already been committed.
                     *
                     * commitSync(): Blocking. Use when absolute correctness is required
                     * (e.g., in onPartitionsRevoked before rebalance).
                     */
                    consumer.commitAsync(pendingOffsets, (offsets, exception) -> {
                        if (exception != null) {
                            System.err.println("[COMMIT FAILED] " + offsets + " → " + exception.getMessage());
                            // Not retrying — next poll cycle will commit again if processing succeeds
                        } else {
                            System.out.println("[COMMIT SUCCESS] " + offsets);
                            pendingOffsets.clear();
                        }
                    });
                }
            }

        } catch (WakeupException e) {
            // Expected on shutdown — wakeup() triggers this
            System.out.println("[CONSUMER] WakeupException received. Shutting down...");
        } finally {
            // Final commitSync before shutdown — ensures last batch is committed
            if (!pendingOffsets.isEmpty()) {
                consumer.commitSync(pendingOffsets);
                System.out.println("[CONSUMER] Final commit completed: " + pendingOffsets);
            }
            consumer.close(); // Releases resources, sends leave group request
            System.out.println("[CONSUMER] Closed cleanly.");
        }
    }

    // -------------------------------------------------------
    // Record Processing with DLQ
    // -------------------------------------------------------

    private static final int MAX_RETRIES = 3;

    /**
     * Processes a single Kafka record.
     *
     * PATTERNS USED:
     * 1. Idempotence check — don't process the same order twice.
     * 2. Retry on transient failure.
     * 3. Dead Letter Queue (DLQ) — poison pill messages go here.
     *
     * INTERVIEW TIP: "We make order processing idempotent by checking
     * if order_id already exists in our DB before inserting. This makes
     * reprocessing safe — we won't create duplicate orders even if we
     * process the same Kafka message twice."
     *
     * @return true if processing succeeded (offset should be committed), false
     *         otherwise.
     */
    private boolean processRecord(ConsumerRecord<String, String> record) {
        System.out.printf("[PROCESSING] order=%s, partition=%d, offset=%d%n",
                record.key(), record.partition(), record.offset());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // STEP 1: Idempotence check (simulate)
                if (isAlreadyProcessed(record.key())) {
                    System.out.println("[SKIP] Order already processed: " + record.key());
                    return true; // Still mark as processed (commit offset)
                }

                // STEP 2: Business logic (simulate order processing)
                processOrder(record.key(), record.value());

                // STEP 3: Mark as processed in our DB (for idempotence)
                markAsProcessed(record.key());

                System.out.printf("[SUCCESS] Order %s processed on attempt %d%n", record.key(), attempt);
                return true;

            } catch (TransientException e) {
                // Retry on transient failures (DB timeout, network blip)
                System.err.printf("[RETRY] attempt=%d, order=%s, error=%s%n",
                        attempt, record.key(), e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (PermanentException e) {
                // Non-retriable — send to DLQ immediately
                System.err.printf("[POISON PILL] Sending order %s to DLQ: %s%n", record.key(), e.getMessage());
                sendToDLQ(record);
                return true; // Commit offset so we don't replay this poison pill forever
            }
        }

        // All retries exhausted
        System.err.printf("[FAILED] All retries exhausted for order %s. Sending to DLQ.%n", record.key());
        sendToDLQ(record);
        return true; // Commit offset — stop blocking on this record
    }

    // -------------------------------------------------------
    // Helper methods (simulation)
    // -------------------------------------------------------

    private boolean isAlreadyProcessed(String orderId) {
        // In real code: check DB/Redis for orderId existence
        return false; // Simulate: not yet processed
    }

    private void processOrder(String orderId, String orderJson) {
        // In real code: parse JSON, validate, save to DB, call payment service, etc.
        System.out.printf("[BUSINESS LOGIC] Processing order %s: %s%n", orderId, orderJson);
    }

    private void markAsProcessed(String orderId) {
        // In real code: INSERT INTO processed_orders(order_id) ON CONFLICT DO NOTHING
        System.out.printf("[DB] Marked %s as processed%n", orderId);
    }

    private void sendToDLQ(ConsumerRecord<String, String> record) {
        // In real code: use a KafkaProducer to send to DLQ topic
        System.err.printf("[DLQ] Sent record [key=%s, offset=%d] to %s%n",
                record.key(), record.offset(), DLQ_TOPIC);
    }

    // Custom exceptions for demo
    static class TransientException extends RuntimeException {
        public TransientException(String msg) {
            super(msg);
        }
    }

    static class PermanentException extends RuntimeException {
        public PermanentException(String msg) {
            super(msg);
        }
    }

    // -------------------------------------------------------
    // Main
    // -------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=== Kafka Consumer Demo (At-Least-Once) ===\n");
        new OrderConsumer().startAtLeastOnce();
    }
}
