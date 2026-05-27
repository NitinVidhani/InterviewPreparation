package com.lld.hld.kafka.patterns;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ============================================================
 * OutboxPattern — Reliable Kafka Publishing Without Losing Events
 * ============================================================
 *
 * PROBLEM: Dual-write inconsistency
 * ─────────────────────────────────
 * The naive approach to publishing to Kafka after a DB write:
 *
 * BEGIN TRANSACTION
 * INSERT INTO orders (id, status) VALUES (123, 'PLACED');
 * COMMIT;
 * producer.send("orders", "order-123-placed"); ← CRASH HERE?
 *
 * If the app crashes AFTER DB commit but BEFORE Kafka send:
 * → DB has the order (status=PLACED)
 * → Kafka never got the event
 * → Downstream services (payment, inventory) never triggered
 * → SILENT INCONSISTENCY — no error, just a stuck order
 *
 * SOLUTION: The Outbox Pattern
 * ─────────────────────────────────────────────────────────────
 * Write the event to an outbox TABLE inside the SAME DB transaction
 * as the business data. A separate "relay" process reads the
 * outbox and publishes to Kafka, then marks records as published.
 *
 * Step 1: Business logic writes to DB + outbox in same transaction
 * ┌────────────────────────────────────────────────────────────┐
 * │ BEGIN TRANSACTION │
 * │ INSERT INTO orders (id, status) VALUES (123, 'PLACED') │
 * │ INSERT INTO outbox (event_type, payload, published) │
 * │ VALUES ('OrderPlaced', '{"orderId":123}', false) │
 * │ COMMIT │
 * └────────────────────────────────────────────────────────────┘
 * → Either BOTH succeed OR BOTH are rolled back. No dual-write risk.
 *
 * Step 2: OutboxRelay polls the outbox table and publishes to Kafka
 * ┌────────────────────────────────────────────────────────────┐
 * │ SELECT * FROM outbox WHERE published = false LIMIT 100 │
 * │ For each record: │
 * │ producer.send(topic, record.payload) │
 * │ UPDATE outbox SET published=true WHERE id=record.id │
 * └────────────────────────────────────────────────────────────┘
 * → If Kafka publish fails → record stays in outbox → retry next poll
 * → If app crashes after Kafka publish but before UPDATE → duplicate!
 * (Handled by idempotent producer + idempotent consumers)
 *
 * ARCHITECTURE DIAGRAM:
 *
 * API Request
 * ↓
 * ┌───────────────────────────────────────┐
 * │ OrderService.placeOrder() │
 * │ ┌─── DB Transaction ──────────────┐ │
 * │ │ INSERT orders (...) │ │
 * │ │ INSERT outbox (event, payload) │ │
 * │ └────────────────────────────────┘ │
 * └───────────────────────────────────────┘
 * ↓ (async, separate process)
 * ┌──────────────────────────────────────┐
 * │ OutboxRelay (background thread) │
 * │ poll outbox → send to Kafka │
 * │ mark published │
 * └──────────────────────────────────────┘
 * ↓
 * [Kafka Topic: orders]
 * ↓ ↓ ↓
 * [Payment] [Inventory] [Notification]
 *
 * INTERVIEW TIP:
 * "The Outbox Pattern solves the dual-write problem. By writing the event
 * to the outbox table in the same transaction as the business entity,
 * we eliminate the window where the DB has the data but Kafka doesn't.
 * The relay may produce duplicates on retry, so consumers must be
 * idempotent — they check if the event was already processed."
 */
public class OutboxPattern {

    // -------------------------------------------------------
    // Outbox Table DDL (reference)
    // -------------------------------------------------------

    /**
     * CREATE TABLE outbox (
     * id BIGSERIAL PRIMARY KEY,
     * aggregate_id VARCHAR(255) NOT NULL, -- e.g., "order-123"
     * event_type VARCHAR(100) NOT NULL, -- e.g., "OrderPlaced"
     * topic VARCHAR(255) NOT NULL, -- which Kafka topic to publish to
     * payload JSONB NOT NULL, -- event body
     * published BOOLEAN NOT NULL DEFAULT FALSE,
     * created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
     * published_at TIMESTAMPTZ
     * );
     *
     * -- Index for polling unpublished events
     * CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at)
     * WHERE published = FALSE;
     */

    // -------------------------------------------------------
    // Service Layer — writes order + outbox in one transaction
    // -------------------------------------------------------

    /**
     * STEP 1: Business service that writes atomically to DB + outbox.
     *
     * The key invariant: if the transaction commits, BOTH the order
     * and the outbox entry exist. If it rolls back, NEITHER exists.
     * Kafka is never involved at this stage.
     */
    static class OrderService {

        private final Connection dbConn;

        OrderService(Connection dbConn) {
            this.dbConn = dbConn;
        }

        /**
         * Places an order and writes an outbox event in one atomic transaction.
         *
         * @param orderId Unique order ID (idempotency key)
         * @param userId  The ordering user
         * @param amount  Order amount in cents
         */
        public void placeOrder(String orderId, String userId, long amount) throws SQLException {
            dbConn.setAutoCommit(false); // BEGIN TRANSACTION

            try {
                // ── Business write: Insert the order ─────────────────
                String insertOrder = "INSERT INTO orders (id, user_id, amount, status) " +
                        "VALUES (?, ?, ?, 'PLACED') " +
                        "ON CONFLICT (id) DO NOTHING"; // idempotent
                try (PreparedStatement ps = dbConn.prepareCall(insertOrder)) {
                    ps.setString(1, orderId);
                    ps.setString(2, userId);
                    ps.setLong(3, amount);
                    ps.executeUpdate();
                }

                // ── Outbox write: Insert the event ───────────────────
                // SAME TRANSACTION — either both succeed or both rollback
                String eventPayload = String.format(
                        "{\"orderId\":\"%s\",\"userId\":\"%s\",\"amount\":%d,\"status\":\"PLACED\"}",
                        orderId, userId, amount);

                String insertOutbox = "INSERT INTO outbox " +
                        "(aggregate_id, event_type, topic, payload, published) " +
                        "VALUES (?, ?, ?, ?::JSONB, FALSE) " +
                        "ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = dbConn.prepareStatement(insertOutbox)) {
                    ps.setString(1, orderId);
                    ps.setString(2, "OrderPlaced");
                    ps.setString(3, "orders");
                    ps.setString(4, eventPayload);
                    ps.executeUpdate();
                }

                dbConn.commit(); // COMMIT TRANSACTION — both writes persisted atomically
                System.out.printf("[OrderService] Order %s placed and outbox event written.%n", orderId);

            } catch (SQLException e) {
                dbConn.rollback(); // ROLLBACK — nothing in outbox or orders table
                System.err.printf("[OrderService] Error placing order %s: %s%n", orderId, e.getMessage());
                throw e;
            } finally {
                dbConn.setAutoCommit(true);
            }
        }
    }

    // -------------------------------------------------------
    // Outbox Relay — polls outbox and publishes to Kafka
    // -------------------------------------------------------

    /**
     * STEP 2: OutboxRelay runs as a separate background process/thread.
     * It continuously polls the outbox table for unpublished events
     * and publishes them to Kafka.
     *
     * IMPORTANT DESIGN DECISIONS:
     *
     * 1. Polling vs CDC (Debezium):
     * - Polling (this class): Simple. Works with any DB. Adds latency (poll
     * interval).
     * - CDC (Debezium): Reads WAL directly → near-zero latency. More complex to
     * operate.
     * - For sub-second latency, prefer CDC.
     *
     * 2. Order of processing:
     * - We ORDER BY created_at, id to process in insertion order.
     * - This preserves event ordering per aggregate.
     *
     * 3. At-least-once delivery:
     * - We mark published=true AFTER kafka send.
     * - If app crashes after Kafka send but before DB update → duplicate.
     * - Consumers MUST be idempotent.
     *
     * 4. Batch size:
     * - Fetch LIMIT 100 per poll cycle → reduces DB queries.
     * - Publish in a Kafka producer batch for efficiency.
     *
     * 5. Cleanup:
     * - Old published records can be deleted by a scheduled job:
     * DELETE FROM outbox WHERE published = TRUE AND published_at < NOW() - INTERVAL
     * '7 days';
     */
    static class OutboxRelay {

        private final Connection dbConn;
        private final MockKafkaProducer kafkaProducer; // In real code: KafkaProducer<String, String>
        private final AtomicBoolean running = new AtomicBoolean(true);
        private static final int BATCH_SIZE = 100;
        private static final int POLL_INTERVAL_MS = 100; // check every 100ms

        OutboxRelay(Connection dbConn, MockKafkaProducer kafkaProducer) {
            this.dbConn = dbConn;
            this.kafkaProducer = kafkaProducer;
        }

        /** Start the relay loop in the current thread. */
        public void start() {
            System.out.println("[OutboxRelay] Starting...");

            while (running.get()) {
                try {
                    int published = processOutboxBatch();

                    if (published == 0) {
                        // Nothing to process — sleep before next poll
                        Thread.sleep(POLL_INTERVAL_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[OutboxRelay] Error: " + e.getMessage());
                    // Brief backoff before retry on DB error
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            System.out.println("[OutboxRelay] Stopped.");
        }

        /** Fetches one batch of unprocessed outbox events and publishes them. */
        private int processOutboxBatch() throws SQLException {
            // ── Fetch unprocessed events ─────────────────────────────
            String selectSql = "SELECT id, aggregate_id, event_type, topic, payload " +
                    "FROM outbox WHERE published = FALSE " +
                    "ORDER BY created_at, id " +
                    "LIMIT " + BATCH_SIZE;

            List<OutboxRecord> records = new ArrayList<>();

            try (Statement stmt = dbConn.createStatement();
                    ResultSet rs = stmt.executeQuery(selectSql)) {

                while (rs.next()) {
                    records.add(new OutboxRecord(
                            rs.getLong("id"),
                            rs.getString("aggregate_id"),
                            rs.getString("event_type"),
                            rs.getString("topic"),
                            rs.getString("payload")));
                }
            }

            if (records.isEmpty())
                return 0;

            System.out.printf("[OutboxRelay] Publishing batch of %d events...%n", records.size());

            // ── Publish each event to Kafka ──────────────────────────
            for (OutboxRecord record : records) {
                try {
                    // Key = aggregate_id (orderId) → ensures per-order ordering in Kafka
                    kafkaProducer.send(record.topic, record.aggregateId, record.payload);

                    // ── Mark as published ───────────────────────────
                    // IMPORTANT: Do this AFTER successful Kafka send.
                    // If Kafka fails → outbox entry stays unpublished → retry on next poll.
                    // If app crashes here → duplicate delivery → consumer must be idempotent.
                    markPublished(record.id());

                    System.out.printf("[OutboxRelay] Published event: type=%s, aggregateId=%s%n",
                            record.eventType, record.aggregateId);

                } catch (Exception e) {
                    System.err.printf("[OutboxRelay] Failed to publish event %d: %s%n",
                            record.id(), e.getMessage());
                    // Don't mark as published — will retry next batch
                    // In production: add retry count column, move to DLQ after N failures
                }
            }

            return records.size();
        }

        private void markPublished(long outboxId) throws SQLException {
            String updateSql = "UPDATE outbox SET published = TRUE, published_at = NOW() WHERE id = ?";
            try (PreparedStatement ps = dbConn.prepareStatement(updateSql)) {
                ps.setLong(1, outboxId);
                ps.executeUpdate();
            }
        }

        public void stop() {
            running.set(false);
        }

        // ── Outbox record POJO ───────────────────────────────────
        record OutboxRecord(long id, String aggregateId, String eventType, String topic, String payload) {
        }
    }

    // -------------------------------------------------------
    // Idempotent Consumer (receives events from Kafka)
    // -------------------------------------------------------

    /**
     * IMPORTANT: Consumer MUST be idempotent because the relay may
     * send the same event more than once (at-least-once delivery).
     *
     * Implementation using a "processed_events" dedup table:
     *
     * CREATE TABLE processed_events (
     * event_id VARCHAR(255) PRIMARY KEY, -- e.g., "OrderPlaced-order-123"
     * processed_at TIMESTAMPTZ DEFAULT NOW()
     * );
     *
     * On receiving an event:
     * 1. Try INSERT INTO processed_events (event_id) VALUES
     * ('OrderPlaced-order-123')
     * 2. If INSERT succeeds → process the event (first time)
     * 3. If INSERT fails (unique constraint) → SKIP (already processed)
     *
     * This INSERT must be in the SAME transaction as the business logic.
     */
    static class IdempotentPaymentConsumer {

        public boolean processOrderPlaced(String orderId, String orderPayload, Connection dbConn)
                throws SQLException {

            String eventId = "OrderPlaced-" + orderId;
            String insertDedup = "INSERT INTO processed_events (event_id) VALUES (?) " +
                    "ON CONFLICT (event_id) DO NOTHING";

            dbConn.setAutoCommit(false);
            try {
                // ── Dedup check (atomic with business logic) ─────────
                try (PreparedStatement ps = dbConn.prepareStatement(insertDedup)) {
                    ps.setString(1, eventId);
                    int rows = ps.executeUpdate();

                    if (rows == 0) {
                        // Already processed — skip
                        System.out.printf("[Consumer] DUPLICATE skipped: %s%n", eventId);
                        dbConn.rollback();
                        return false;
                    }
                }

                // ── Business logic (same transaction) ────────────────
                // e.g., create payment record, call payment gateway, etc.
                System.out.printf("[Consumer] Processing payment for order: %s%n", orderId);
                // ... do actual work ...

                dbConn.commit();
                System.out.printf("[Consumer] Payment processed for order: %s%n", orderId);
                return true;

            } catch (Exception e) {
                dbConn.rollback();
                throw e;
            } finally {
                dbConn.setAutoCommit(true);
            }
        }
    }

    // -------------------------------------------------------
    // Mock Kafka Producer (for demo without real Kafka)
    // -------------------------------------------------------
    static class MockKafkaProducer {
        public void send(String topic, String key, String value) {
            System.out.printf("[MockKafka] → topic=%s, key=%s, value=%s%n", topic, key, value);
        }
    }

    // -------------------------------------------------------
    // Main — Demonstration flow
    // -------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=== Outbox Pattern Demo ===\n");
        System.out.println("Pattern: DB transaction (orders + outbox) → Relay → Kafka\n");
        System.out.println("Guarantees:");
        System.out.println("  1. Event is NEVER lost (outbox persisted before Kafka publish)");
        System.out.println("  2. At-least-once delivery (relay retries on Kafka failure)");
        System.out.println("  3. Consumer idempotence handles duplicates\n");

        /*
         * In a real application:
         * 1. OrderService.placeOrder() is called from REST API handler
         * 2. OutboxRelay runs as a separate Spring @Scheduled bean or a
         * Debezium CDC connector reading the PostgreSQL WAL
         * 3. IdempotentConsumer runs in a Kafka consumer loop
         *
         * Alternatives to polling:
         * - Debezium PostgreSQL connector: reads pg_wal → publishes to Kafka
         * → zero-latency, no polling overhead, self-healing
         * - Redis-based outbox: write to Redis list atomically with Lua,
         * relay reads from Redis, publishes to Kafka
         */
        System.out.println("REAL PRODUCTION SETUP:");
        System.out.println("  OrderService → DB (orders + outbox tables)");
        System.out.println("  Debezium CDC → reads PostgreSQL WAL → publishes to Kafka");
        System.out.println("  Zero polling latency + battle-tested reliability");
    }
}
