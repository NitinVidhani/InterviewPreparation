package com.lld.hld.kafka.transactions;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;

/**
 * ============================================================
 * KafkaTransactionExample — Exactly-Once Semantics (EOS)
 * ============================================================
 *
 * PROBLEM:
 * In a read-process-write pattern (consume → transform → produce),
 * crashes between "produce output" and "commit input offset" cause
 * the message to be reprocessed = DUPLICATE output.
 *
 * SOLUTION: Kafka Transactions wrap the output write AND offset commit
 * into a single atomic unit. Either BOTH succeed, or BOTH are rolled back.
 *
 * HOW IT WORKS:
 * ┌─────────────────────────────────────────────────────────┐
 * │ producer.beginTransaction() │
 * │ producer.send(output-topic, ...) ← writes buffered │
 * │ producer.sendOffsetsToTransaction() ← offset buffered │
 * │ producer.commitTransaction() ← ATOMIC commit │
 * └─────────────────────────────────────────────────────────┘
 *
 * The broker holds all transactional writes in a "pending" state
 * until commitTransaction() is called. Consumers with
 * isolation.level=read_committed will ONLY see committed records.
 *
 * REQUIREMENTS:
 * 1. Producer: enable.idempotence=true, transactional.id=<unique-id>
 * 2. Consumer: isolation.level=read_committed (don't read uncommitted txns)
 *
 * USE CASE:
 * "Read from topic: raw-payments → Transform → Write to topic:
 * processed-payments"
 * with exactly-once guarantee (no duplicates, no data loss).
 *
 * INTERVIEW TIP:
 * "For our payments processing pipeline, we use Kafka Transactions to
 * guarantee that the transformed record is written and the input offset
 * is committed atomically. If our service crashes mid-transaction, Kafka
 * rolls back both. consumers with read_committed isolation will not see
 * the partial writes."
 */
public class KafkaTransactionExample {

    private static final String INPUT_TOPIC = "raw-payments";
    private static final String OUTPUT_TOPIC = "processed-payments";
    private static final String CONSUMER_GROUP = "payment-processor-eos-cg";

    // -------------------------------------------------------
    // Transactional Producer Setup
    // -------------------------------------------------------

    /**
     * TRANSACTIONAL PRODUCER configuration.
     *
     * transactional.id:
     * - UNIQUE per producer instance (but CONSISTENT across restarts).
     * - Kafka uses this to "fence" zombie producers.
     * - If an old producer instance comes back after a restart and tries
     * to produce, the broker rejects it because the new instance has
     * a higher "epoch" for the same transactional.id.
     * - This prevents the "zombie producer problem" (split-brain scenario).
     *
     * enable.idempotence (auto-enabled with transactional.id):
     * - Assigns Producer ID (PID) + sequence numbers.
     * - Deduplicates retries on the broker side.
     *
     * transaction.timeout.ms = 60000:
     * - If a transaction is not committed/aborted within this time,
     * the broker automatically aborts it.
     * - Prevents long-running transactions from blocking consumers.
     */
    private static KafkaProducer<String, String> createTransactionalProducer(String instanceId) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // REQUIRED for transactions
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "payment-processor-" + instanceId);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Transaction timeout
        props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 60_000);

        return new KafkaProducer<>(props);
    }

    // -------------------------------------------------------
    // Transactional Consumer Setup
    // -------------------------------------------------------

    /**
     * TRANSACTIONAL CONSUMER — MUST use isolation.level=read_committed.
     *
     * isolation.level = read_committed:
     * - Consumer ONLY reads records from COMMITTED transactions.
     * - Records from ABORTED or IN-PROGRESS transactions are skipped.
     * - This is what prevents a consumer from reading "phantom" records
     * that were part of an aborted transaction.
     *
     * isolation.level = read_uncommitted (default):
     * - Consumer reads ALL records, even from aborted transactions.
     * - Do NOT use this with transactional producers if you want EOS.
     *
     * IMPORTANT: Disable auto-commit.
     * - Offsets MUST be committed via sendOffsetsToTransaction()
     * not via normal consumer.commitSync/Async().
     */
    private static KafkaConsumer<String, String> createTransactionalConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);

        // CRITICAL: Read only committed records
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // CRITICAL: Never auto-commit — offset committed transactionally
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        return new KafkaConsumer<>(props);
    }

    // -------------------------------------------------------
    // Exactly-Once Read-Process-Write Loop
    // -------------------------------------------------------

    /**
     * THE EXACTLY-ONCE PATTERN:
     *
     * For each batch of input records:
     * 1. Begin transaction on the producer.
     * 2. Process each input record → create output record.
     * 3. Send output records to the output topic (within the transaction).
     * 4. Commit input offsets via sendOffsetsToTransaction()
     * (this atomically links offset commit to the transaction).
     * 5. Commit the transaction (makes output + offset commit visible atomically).
     *
     * If crash at step 2 or 3 → transaction is aborted → input offset is NOT
     * committed
     * → consumer reprocesses from same offset → exactly-once (no data loss, no
     * duplicates).
     */
    public static void runExactlyOnceProcessor() {
        KafkaProducer<String, String> producer = createTransactionalProducer("instance-1");
        KafkaConsumer<String, String> consumer = createTransactionalConsumer();

        // STEP 0: Initialize transactions — MUST be called once before any transaction.
        // Kafka registers this producer with the transactional.id and bumps the epoch.
        // Any old producer instance with the same transactional.id is "fenced"
        // (rejected).
        producer.initTransactions();

        consumer.subscribe(List.of(INPUT_TOPIC));

        System.out.println("[EOS] Exactly-once processor started.");

        int batchesProcessed = 0;

        try {
            while (batchesProcessed < 5) { // Demo: process 5 batches then stop
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                if (records.isEmpty()) {
                    System.out.println("[EOS] No records in this poll. Waiting...");
                    batchesProcessed++;
                    continue;
                }

                System.out.printf("[EOS] Processing batch of %d records%n", records.count());

                try {
                    // ── BEGIN TRANSACTION ──────────────────────────────────
                    producer.beginTransaction();

                    // Process each record and produce output
                    for (ConsumerRecord<String, String> record : records) {
                        String processedValue = transform(record.value());

                        // Send to output topic — buffered within the transaction
                        producer.send(new ProducerRecord<>(
                                OUTPUT_TOPIC,
                                record.key(),
                                processedValue), (metadata, e) -> {
                                    if (e != null) {
                                        System.err.println("[EOS] Send error: " + e.getMessage());
                                    }
                                });

                        System.out.printf("[EOS] Transformed: %s → %s%n",
                                record.value(), processedValue);
                    }

                    // Commit input offsets WITHIN the transaction
                    // This is the KEY operation that links input consumption with output production
                    Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = getCurrentOffsets(records);
                    producer.sendOffsetsToTransaction(
                            offsetsToCommit,
                            consumer.groupMetadata() // includes generation ID for fencing
                    );

                    // ── COMMIT TRANSACTION ──────────────────────────────────
                    // This atomically:
                    // 1. Makes output records visible to read_committed consumers
                    // 2. Commits the input offsets
                    producer.commitTransaction();

                    System.out.printf("[EOS] Transaction committed. Offsets: %s%n", offsetsToCommit);

                } catch (Exception e) {
                    System.err.println("[EOS] Transaction failed, aborting: " + e.getMessage());

                    // ── ABORT TRANSACTION ──────────────────────────────────
                    // All output records and offset commits are rolled back.
                    // Consumer will reprocess from the same offsets.
                    producer.abortTransaction();
                }

                batchesProcessed++;
            }

        } finally {
            consumer.close();
            producer.close();
            System.out.println("[EOS] Processor shut down.");
        }
    }

    // -------------------------------------------------------
    // Helper: Build offset map for a batch of records
    // -------------------------------------------------------

    /**
     * Builds a map of {TopicPartition → OffsetAndMetadata(offset + 1)}
     * from a batch of consumed records.
     *
     * WHY offset + 1?
     * Kafka's offset commit means "I have processed up to this offset,
     * next time start from offset+1". So we always commit the NEXT offset to read.
     */
    private static Map<TopicPartition, OffsetAndMetadata> getCurrentOffsets(
            ConsumerRecords<String, String> records) {

        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();

        for (ConsumerRecord<String, String> record : records) {
            TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            // We want to commit the offset AFTER the last processed record
            OffsetAndMetadata current = offsets.get(tp);
            if (current == null || record.offset() + 1 > current.offset()) {
                offsets.put(tp, new OffsetAndMetadata(record.offset() + 1));
            }
        }

        return offsets;
    }

    // -------------------------------------------------------
    // Business logic — transformation
    // -------------------------------------------------------

    /**
     * Simulates payment transformation:
     * raw payment → enriched/validated payment
     */
    private static String transform(String rawPayment) {
        // In real code: parse JSON, validate, enrich, etc.
        return rawPayment.replace("raw", "processed").toUpperCase() + "_VALIDATED";
    }

    // -------------------------------------------------------
    // Main
    // -------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=== Kafka Exactly-Once Demo (Transactions) ===\n");
        System.out.println("Pattern: read from raw-payments → transform → write to processed-payments");
        System.out.println("Guarantee: output write AND offset commit are ATOMIC\n");

        /*
         * In production, you would NOT use this pattern directly.
         * Use Kafka Streams with:
         * props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG,
         * StreamsConfig.EXACTLY_ONCE_V2);
         *
         * Kafka Streams handles all the transaction boilerplate for you.
         */
        runExactlyOnceProcessor();
    }
}
