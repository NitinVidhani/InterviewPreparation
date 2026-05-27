package com.lld.hld.kafka.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Properties;

/**
 * ============================================================
 * FraudDetectionStream — Kafka Streams Deep Dive
 * ============================================================
 *
 * SYSTEM DESIGN:
 * Topic: transactions (raw)
 * → Filter: amount > 0 (sanity check)
 * → Branch: amount >= 10000 → high-risk-transactions
 * → Windowed Aggregation: count per user per minute
 * → Filter: count > 10 in 1 min → velocity-alerts
 * → Join with KTable: known_bad_users
 * → fraud-alerts
 *
 * KAFKA STREAMS CONCEPTS:
 *
 * KStream:
 * Represents an UNBOUNDED stream of records.
 * Each record is an independent event.
 * Like a stream of transaction events.
 *
 * KTable:
 * Represents the LATEST STATE for each key.
 * Acts like a continuously updated database table.
 * Like the current account balance per user.
 *
 * GlobalKTable:
 * Like KTable but REPLICATED to ALL Kafka Streams instances.
 * Used for reference data lookups (e.g., user blacklist).
 * No partition co-partitioning required for joins.
 *
 * Windowing:
 * Tumbling: fixed-size, non-overlapping windows (0-60s, 60-120s)
 * Hopping: fixed-size, overlapping windows (0-60s, 30-90s, 60-120s)
 * Session: activity-based, close after inactivity gap
 *
 * State Stores (RocksDB):
 * Kafka Streams uses LOCAL RocksDB to store aggregation state.
 * State is backed up to Kafka changelog topics for fault tolerance.
 * On restart, state is restored from the changelog.
 *
 * INTERVIEW TIP:
 * "We use Kafka Streams because it's a lightweight library — no separate
 * cluster to manage. State is stored locally in RocksDB, backed by Kafka
 * changelog topics. For exactly-once, we set
 * processing.guarantee=exactly_once_v2."
 */
public class FraudDetectionStream {

    // -------------------------------------------------------
    // Topics
    // -------------------------------------------------------
    static final String INPUT_TOPIC = "transactions";
    static final String HIGH_RISK_TOPIC = "high-risk-transactions";
    static final String VELOCITY_TOPIC = "velocity-alerts";
    static final String FRAUD_ALERT_TOPIC = "fraud-alerts";
    static final String BAD_USERS_TABLE = "blacklisted-users"; // KTable source

    // -------------------------------------------------------
    // Configuration
    // -------------------------------------------------------

    /**
     * Streams configuration explained:
     *
     * application.id:
     * UNIQUE identifier for this Streams application.
     * Used as the consumer group ID (for input topics) and as prefix
     * for internal Kafka topics (state store changelog, repartition topics).
     * CRITICAL: All instances of the SAME app MUST share the same application.id.
     *
     * processing.guarantee = exactly_once_v2 (EOS-V2):
     * Enables exactly-once semantics for the read-process-write pipeline.
     * Automatically wraps processing in Kafka transactions.
     * Requires RF >= 2 for internal topics.
     *
     * num.stream.threads:
     * Number of processing threads per instance.
     * Each thread processes a subset of assigned partitions.
     * Total parallelism = num.stream.threads × num instances.
     *
     * state.dir:
     * Local directory for RocksDB state stores.
     * Use a fast disk (SSD) and a path NOT shared between instances.
     *
     * commit.interval.ms:
     * How often to commit state store and offsets to Kafka.
     * Lower = faster recovery after failure but more overhead.
     */
    private static Properties buildStreamsConfig() {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "fraud-detection-v1");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // Default Serdes (serializer/deserializer) for key and value
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // Exactly-once processing (requires >= 2 replicas for internal topics)
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);

        // Processing parallelism
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 2);

        // State store directory (use fast disk in production)
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams/fraud-detection");

        // Commit interval (tradeoff: low latency vs overhead)
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);

        return props;
    }

    // -------------------------------------------------------
    // Topology Builder
    // -------------------------------------------------------

    /**
     * Builds the Kafka Streams processing topology.
     *
     * A Topology is a DAG (Directed Acyclic Graph) of:
     * Source Processors → Stream Processors → Sink Processors
     *
     * The DSL (KStream / KTable API) compiles to this topology internally.
     */
    public static Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        // ── SOURCE: Read raw transactions ──────────────────────────
        // Each record: key=userId, value=JSON transaction data
        KStream<String, String> transactions = builder.stream(INPUT_TOPIC);

        // ── FILTER: Remove invalid transactions (sanity check) ──────
        KStream<String, String> validTransactions = transactions
                .filter((userId, txnJson) -> {
                    double amount = extractAmount(txnJson);
                    return amount > 0 && amount < 1_000_000; // sanity bounds
                });

        // ── BRANCH: Route based on amount ───────────────────────────
        //
        // KStream.branch() splits one stream into N streams based on predicates.
        // Records matching the FIRST matching predicate go to that branch.
        //
        // Alternative (Kafka 2.8+): Use split() for named branches.
        @SuppressWarnings("unchecked")
        KStream<String, String>[] branches = validTransactions.branch(
                (userId, txnJson) -> extractAmount(txnJson) >= 10_000, // [0] High risk
                (userId, txnJson) -> true // [1] Normal
        );

        KStream<String, String> highRiskStream = branches[0];
        KStream<String, String> normalStream = branches[1];

        // ── SINK: High-risk transactions → separate topic ───────────
        highRiskStream
                .peek((userId, txnJson) -> System.out
                        .println("[HIGH-RISK] User: " + userId + " Amount: " + extractAmount(txnJson)))
                .to(HIGH_RISK_TOPIC);

        // ── WINDOWED AGGREGATION: Velocity check ────────────────────
        //
        // PROBLEM: Detect users making > 10 transactions in any 1-minute window.
        // This is a STATEFUL operation — we need to COUNT events per user per window.
        //
        // TUMBLING WINDOW (size=1min):
        // Window 1: 00:00 → 01:00 (all txns in this window go to same aggregate)
        // Window 2: 01:00 → 02:00
        // Window 3: 02:00 → 03:00
        //
        // State is stored in LOCAL RocksDB at state.dir.
        // Backed up to Kafka changelog topic for fault tolerance.
        KTable<Windowed<String>, Long> txnCountPerMinute = validTransactions
                // Group by userId (same key = same partition = same state store instance)
                .groupByKey()
                // Count within 1-minute tumbling windows
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .count(Materialized.as("txn-count-per-minute-store")); // named state store

        // ── FILTER: High-velocity users → velocity-alerts topic ─────
        txnCountPerMinute
                .toStream() // KTable → KStream for downstream processing
                // Windowed key format: userId@windowStart/windowEnd
                .filter((windowedUserId, count) -> count != null && count > 10)
                .peek((windowedUserId, count) -> System.out.printf(
                        "[VELOCITY ALERT] User: %s, Count: %d in window: %s%n",
                        windowedUserId.key(), count, windowedUserId.window()))
                // Re-key to just userId (drop window) for downstream joins
                .selectKey((windowedUserId, count) -> windowedUserId.key())
                .mapValues((userId, count) -> String
                        .format("{\"userId\":\"%s\",\"txnCount\":%d,\"alert\":\"VELOCITY\"}", userId, count))
                .to(VELOCITY_TOPIC);

        // ── KTABLE JOIN: Blacklisted users ──────────────────────────
        //
        // KTable representing users in our blacklist.
        // Key = userId, Value = reason for blacklisting
        //
        // IMPORTANT: For stream-table joins to work correctly,
        // the stream and table MUST be co-partitioned (same number of partitions,
        // same partitioning strategy). Use GlobalKTable to avoid this constraint.
        KTable<String, String> blacklistedUsers = builder.table(BAD_USERS_TABLE,
                Materialized.as("blacklisted-users-store"));

        // Inner join: only transactions where userId is in the blacklist
        normalStream
                .join(
                        blacklistedUsers,
                        (txnJson, blacklistReason) -> String.format(
                                "{\"txn\":%s,\"blacklistReason\":\"%s\",\"alert\":\"BLACKLISTED\"}",
                                txnJson, blacklistReason))
                .peek((userId, alertJson) -> System.out.println("[FRAUD ALERT - BLACKLISTED] " + alertJson))
                .to(FRAUD_ALERT_TOPIC);

        return builder.build();
    }

    // -------------------------------------------------------
    // JSON parsing helpers (simplified)
    // -------------------------------------------------------

    private static double extractAmount(String txnJson) {
        // In production: use Jackson ObjectMapper.readTree()
        // Simplified: extract amount from {"amount": 500, ...}
        try {
            int start = txnJson.indexOf("\"amount\":") + 9;
            if (start < 9)
                return 0;
            int end = txnJson.indexOf(",", start);
            if (end < 0)
                end = txnJson.indexOf("}", start);
            return Double.parseDouble(txnJson.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // -------------------------------------------------------
    // Main — start the Kafka Streams application
    // -------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Kafka Streams Fraud Detection Demo ===\n");

        Properties config = buildStreamsConfig();
        Topology topology = buildTopology();

        System.out.println("Topology description:");
        System.out.println(topology.describe()); // Print the DAG

        KafkaStreams streams = new KafkaStreams(topology, config);

        // ── State change listener ─────────────────────────────────
        // Monitor application lifecycle: CREATED → REBALANCING → RUNNING → ERROR
        streams.setStateListener((newState, oldState) -> {
            System.out.printf("[STATE] %s → %s%n", oldState, newState);
            if (newState == KafkaStreams.State.ERROR) {
                System.err.println("[ERROR] Streams app entered ERROR state! Check logs.");
                // In production: trigger alert, restart pod (Kubernetes liveness probe)
            }
        });

        // ── Uncaught exception handler ────────────────────────────
        streams.setUncaughtExceptionHandler((exception) -> {
            System.err.println("[EXCEPTION] " + exception.getMessage());
            // REPLACE_THREAD: restart only the failed thread (not the whole app)
            return StreamThreadExceptionResponse.REPLACE_THREAD;
        });

        // ── Graceful shutdown on SIGTERM ──────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SHUTDOWN] Closing Kafka Streams...");
            streams.close(Duration.ofSeconds(30));
            System.out.println("[SHUTDOWN] Kafka Streams closed.");
        }));

        System.out.println("Starting Kafka Streams...");
        streams.start();

        // Block until shutdown (in production, this is a long-running service)
        Thread.sleep(60_000);
        streams.close();
    }
}
