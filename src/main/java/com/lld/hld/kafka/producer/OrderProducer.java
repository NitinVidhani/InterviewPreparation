package com.lld.hld.kafka.producer;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * ============================================================
 * OrderProducer — Kafka Producer Deep Dive
 * ============================================================
 *
 * CONCEPTS COVERED:
 * 1. Producer configuration (acks, retries, batching, compression)
 * 2. Idempotent producer (exactly-once on producer side)
 * 3. Synchronous vs Asynchronous sends
 * 4. Partition assignment via keys
 * 5. Callback-based error handling
 * 6. Proper producer lifecycle (close on shutdown)
 *
 * ARCHITECTURE:
 * App code
 * → Serializer (key + value)
 * → Partitioner (hash(key) % numPartitions)
 * → RecordAccumulator (in-memory buffer per partition)
 * → Sender thread (batches flushed when batch.size OR linger.ms triggers)
 * → Kafka Broker (Leader for that partition)
 * → Broker acks back (based on acks config)
 * → Callback invoked on success/failure
 */
public class OrderProducer {

    // -------------------------------------------------------
    // TOPIC constant
    // -------------------------------------------------------
    private static final String TOPIC = "orders";

    // -------------------------------------------------------
    // Configuration
    // -------------------------------------------------------

    /**
     * Builds a production-grade Kafka Producer configuration.
     *
     * Key settings explained:
     *
     * acks=all
     * → Leader waits for ALL in-sync replicas to acknowledge.
     * → Safest setting — zero data loss if min.insync.replicas is set correctly.
     * → Use acks=1 only for non-critical data (metrics, logs).
     *
     * retries=MAX_INT
     * → Kafka will retry forever on transient failures (network blips, leader
     * elections).
     * → Paired with idempotence, retries don't cause duplicates.
     *
     * enable.idempotence=true
     * → Kafka assigns each producer a unique Producer ID (PID).
     * → Each record gets a sequence number. Broker deduplicates retries.
     * → Automatically sets: acks=all, retries=MAX_INT,
     * max.in.flight.requests.per.connection=5
     *
     * max.in.flight.requests.per.connection=5
     * → Up to 5 unacknowledged batches in-flight per broker connection.
     * → With idempotence, safe up to 5 (ordering guaranteed even with retries).
     * → Without idempotence, set to 1 to prevent out-of-order on retry.
     *
     * batch.size=32768 (32KB)
     * → Max bytes in a single batch per partition.
     * → LARGER batches = better throughput (fewer network round trips).
     * → But adds latency (wait to fill the batch).
     *
     * linger.ms=20
     * → Producer waits up to 20ms before sending, even if batch is not full.
     * → Tradeoff: 20ms extra latency for much better batching.
     * → For low-latency use cases, set linger.ms=0.
     *
     * compression.type=snappy
     * → Compresses batches before sending. Reduces network bandwidth and broker
     * storage.
     * → snappy: great balance of speed and compression ratio.
     * → lz4: faster compression (good for high-throughput).
     * → gzip: best compression but slower.
     * → zstd: best compression+speed (Kafka 2.1+).
     *
     * buffer.memory=33554432 (32MB)
     * → Total memory for all batches waiting to be sent.
     * → If buffer fills up, send() blocks for max.block.ms then throws
     * BufferExhaustedException.
     *
     * delivery.timeout.ms=120000 (2 min)
     * → Total time for a record to be acknowledged (including retries).
     * → Must be >= linger.ms + request.timeout.ms.
     */
    private static Properties buildProducerConfig() {
        Properties props = new Properties();

        // Connection
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Durability — highest safety
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotence — exactly-once producer semantics (deduplicates retries)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Retry configuration
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);

        // In-flight requests (5 is safe with idempotence)
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Batching — improves throughput
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024); // 32KB
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20); // wait 20ms to build batches

        // Compression — reduces network and disk usage
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Buffer
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 32 * 1024 * 1024); // 32MB

        return props;
    }

    // -------------------------------------------------------
    // Asynchronous Send with Callback
    // -------------------------------------------------------

    /**
     * ASYNC SEND — fire-and-forget with callback for error handling.
     *
     * This is the PREFERRED approach in production:
     * - Non-blocking — calling thread is never stalled.
     * - High throughput — sender thread batches and sends efficiently.
     * - Errors handled asynchronously via callback.
     *
     * PARTITIONING:
     * - If `key` is provided: partition = hash(key) % numPartitions
     * → Same key ALWAYS goes to same partition → ordering guaranteed per key.
     * - If key is null: records are spread across partitions (round-robin /
     * sticky).
     */
    public static void sendAsync(KafkaProducer<String, String> producer,
            String orderId, String orderJson) {

        // ProducerRecord: topic, key, value
        // Key = orderId → all events for this order go to the same partition
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, orderId, orderJson);

        /*
         * send() is NON-BLOCKING — it adds the record to the internal buffer
         * and returns immediately. The callback is invoked when:
         * - SUCCESS: record is acknowledged by broker(s)
         * - FAILURE: all retries exhausted or delivery.timeout.ms exceeded
         */
        producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                // SUCCESS PATH
                System.out.printf(
                        "[ASYNC SUCCESS] topic=%s, partition=%d, offset=%d, orderId=%s%n",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        orderId);
            } else {
                // FAILURE PATH — log and handle (alert, DLQ, etc.)
                System.err.printf(
                        "[ASYNC FAILURE] orderId=%s, error=%s%n",
                        orderId,
                        exception.getMessage());
                // In production: push to DLQ or alert on-call
            }
        });
    }

    // -------------------------------------------------------
    // Synchronous Send — use when ordering/acknowledgment is critical
    // -------------------------------------------------------

    /**
     * SYNC SEND — blocks until the broker acknowledges the record.
     *
     * Use when:
     * - You need to guarantee the record is persisted BEFORE returning to the
     * caller.
     * - You're building a request/response system.
     *
     * Downside:
     * - Low throughput (one round-trip per send, no batching benefit).
     * - Thread blocked during network call.
     *
     * INTERVIEW TIP: "For financial transactions where we need to guarantee
     * persistence before returning HTTP 200 to the user, we'd use sync send."
     */
    public static void sendSync(KafkaProducer<String, String> producer,
            String orderId, String orderJson) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, orderId, orderJson);

        try {
            // .get() blocks until ack or exception
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata metadata = future.get(); // BLOCKING

            System.out.printf(
                    "[SYNC SUCCESS] partition=%d, offset=%d, orderId=%s%n",
                    metadata.partition(),
                    metadata.offset(),
                    orderId);

        } catch (ExecutionException e) {
            // Non-retriable errors surface here: SerializationException, etc.
            System.err.println("[SYNC FAILURE] " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[SYNC INTERRUPTED]");
        }
    }

    // -------------------------------------------------------
    // Fire-and-Forget — for non-critical data (metrics, logs)
    // -------------------------------------------------------

    /**
     * FIRE-AND-FORGET — no callback, no blocking.
     *
     * Fastest approach. Records MAY be lost if broker is unavailable
     * and retries are exhausted with no callback to handle failure.
     *
     * Use only for: metrics, access logs, telemetry where loss is acceptable.
     *
     * DELIVERY GUARANTEE: At-most-once (can lose, never duplicate).
     */
    public static void sendFireAndForget(KafkaProducer<String, String> producer,
            String key, String value) {
        producer.send(new ProducerRecord<>(TOPIC, key, value));
        // No callback, no blocking. Record goes into buffer and we move on.
    }

    // -------------------------------------------------------
    // Main — Demo
    // -------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {

        Properties config = buildProducerConfig();

        /*
         * KafkaProducer is THREAD-SAFE — share ONE producer across all threads.
         * Creating a producer per request is expensive (TCP connections, etc.).
         * Use a singleton or dependency-injected producer.
         */
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {

            System.out.println("=== Kafka Producer Demo ===\n");

            // --- Async sends (most common in production) ---
            System.out.println("--- Async Sends ---");
            for (int i = 1; i <= 5; i++) {
                String orderId = "order-" + i;
                String orderJson = String.format(
                        "{\"orderId\":\"%s\",\"userId\":\"user-%d\",\"amount\":%d}",
                        orderId, i, i * 100);
                sendAsync(producer, orderId, orderJson);
            }

            // Give async callbacks time to complete before sync demo
            Thread.sleep(1000);

            // --- Sync send (use for critical writes) ---
            System.out.println("\n--- Sync Send ---");
            sendSync(producer, "order-critical-1",
                    "{\"orderId\":\"order-critical-1\",\"type\":\"payment\",\"amount\":5000}");

            // --- Fire and forget (metrics) ---
            System.out.println("\n--- Fire and Forget ---");
            sendFireAndForget(producer, "metric-1", "{\"cpu\":75,\"mem\":60}");

            // IMPORTANT: flush() ensures all buffered records are sent before close()
            // close() calls flush() internally, but explicit flush() is good practice
            producer.flush();

        } // producer.close() called here — sends remaining buffered records + releases
          // resources

        System.out.println("\nProducer shut down cleanly.");
    }
}
