package com.lld.hld.kafka.producer;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;

import java.util.List;
import java.util.Map;

/**
 * ============================================================
 * GeoPriorityPartitioner — Custom Kafka Partitioner
 * ============================================================
 *
 * PROBLEM:
 * Default hash-based partitioning distributes evenly but ignores
 * business priorities. For a ride-hailing app like Uber:
 * - "premium" rides should go to dedicated partitions (fast lane)
 * - "standard" rides share remaining partitions
 * - Same city's rides should be co-located for stream joins
 *
 * CUSTOM PARTITIONER STRATEGY:
 * Topic: trips (9 partitions)
 *
 * Partitions 0,1,2 → PREMIUM tier (dedicated workers, low latency)
 * Partitions 3,4,5 → STANDARD tier
 * Partitions 6,7,8 → BUDGET tier
 *
 * Within each tier band → hash(city_id) for locality
 *
 * INTERFACE CONTRACT:
 * Kafka calls partition() for EVERY ProducerRecord before batching.
 * Must return a partition index in [0, numPartitions).
 *
 * USAGE IN PRODUCER CONFIG:
 * props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
 * GeoPriorityPartitioner.class.getName());
 *
 * INTERVIEW TIP:
 * "We use a custom partitioner so that premium rides always go to
 * partitions consumed by a dedicated consumer pool with higher
 * compute. This ensures SLA differentiation without separate topics,
 * which keeps our Kafka cluster topology simple."
 */
public class GeoPriorityPartitioner implements Partitioner {

    // -------------------------------------------------------
    // Tier boundaries (partition index ranges)
    // -------------------------------------------------------
    private static final int PREMIUM_PARTITIONS = 3; // 0,1,2
    private static final int STANDARD_PARTITIONS = 3; // 3,4,5
    // budget gets whatever is left: partitions 6,7,8,...

    // -------------------------------------------------------
    // configure() — called once per producer lifecycle
    // -------------------------------------------------------

    /**
     * Called by Kafka with the producer's config when the partitioner
     * is instantiated. Use this to read custom config values.
     *
     * Example: you could add custom properties to the producer config:
     * props.put("premium.partitions.count", "3");
     *
     * Then read them here from `configs`.
     */
    @Override
    public void configure(Map<String, ?> configs) {
        // In production: read configs like "premium.partition.count" here
        // For now we use the constants above
        System.out.println("[GeoPriorityPartitioner] Configured with: " + configs.keySet());
    }

    // -------------------------------------------------------
    // partition() — THE CORE METHOD — called for every record
    // -------------------------------------------------------

    /**
     * Determines which partition a record goes to.
     *
     * @param topic      The topic name
     * @param key        The message key (Object, not yet serialized)
     * @param keyBytes   The serialized key bytes (null if key is null)
     * @param value      The message value (Object, not yet serialized)
     * @param valueBytes The serialized value bytes
     * @param cluster    Current cluster metadata (partition count, leaders, etc.)
     * @return Partition index [0, numPartitions)
     *
     *         IMPORTANT: This method is called on the PRODUCER'S MAIN THREAD.
     *         It must be FAST (no I/O, no blocking). Any sleeps or network calls
     *         here will directly hurt producer throughput.
     */
    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
            Object value, byte[] valueBytes, Cluster cluster) {

        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();

        /*
         * Message key format (set by producer): "TIER:CITY_ID"
         * Examples: "premium:bangalore", "standard:mumbai", "budget:pune"
         *
         * If key is null or malformed, fall back to round-robin.
         */
        if (keyBytes == null || key == null) {
            // No key — distribute evenly (same as default)
            return defaultPartition(numPartitions);
        }

        String keyStr = key.toString();
        String[] parts = keyStr.split(":", 2);

        if (parts.length < 2) {
            return defaultPartition(numPartitions);
        }

        String tier = parts[0].toLowerCase();
        String cityId = parts[1];

        int tierOffset = getTierOffset(tier, numPartitions);
        int tierSize = getTierSize(tier, numPartitions);

        // Within this tier's partition band, use hash of cityId for locality
        // Same city → same partition → good for stream joins on city-level
        int cityHash = Math.abs(cityId.hashCode()) % tierSize;

        int targetPartition = tierOffset + cityHash;

        System.out.printf("[PARTITIONER] key=%s → tier=%s, city=%s, partition=%d%n",
                keyStr, tier, cityId, targetPartition);

        return targetPartition;
    }

    // -------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------

    /**
     * Returns the starting partition index for a given tier.
     *
     * premium → 0
     * standard → PREMIUM_PARTITIONS (3)
     * budget → PREMIUM_PARTITIONS + STANDARD_PARTITIONS (6)
     * unknown → PREMIUM_PARTITIONS + STANDARD_PARTITIONS (budget lane)
     */
    private int getTierOffset(String tier, int numPartitions) {
        return switch (tier) {
            case "premium" -> 0;
            case "standard" -> PREMIUM_PARTITIONS;
            default -> PREMIUM_PARTITIONS + STANDARD_PARTITIONS; // budget or unknown
        };
    }

    /**
     * Returns how many partitions are allocated to a tier.
     * Budget tier gets all remaining partitions.
     */
    private int getTierSize(String tier, int numPartitions) {
        return switch (tier) {
            case "premium" -> PREMIUM_PARTITIONS;
            case "standard" -> STANDARD_PARTITIONS;
            default -> numPartitions - PREMIUM_PARTITIONS - STANDARD_PARTITIONS;
        };
    }

    /** Simple round-robin fallback using a static counter. */
    private static final java.util.concurrent.atomic.AtomicInteger rrCounter = new java.util.concurrent.atomic.AtomicInteger(
            0);

    private int defaultPartition(int numPartitions) {
        return Math.abs(rrCounter.getAndIncrement()) % numPartitions;
    }

    // -------------------------------------------------------
    // close() — cleanup (called when producer is closed)
    // -------------------------------------------------------

    /**
     * Called when the producer is closed.
     * Release any resources allocated in configure().
     */
    @Override
    public void close() {
        System.out.println("[GeoPriorityPartitioner] Closed.");
    }

    // -------------------------------------------------------
    // Usage example
    // -------------------------------------------------------

    /**
     * How to use this custom partitioner:
     *
     * Properties props = new Properties();
     * props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
     * props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
     * StringSerializer.class.getName());
     * props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
     * StringSerializer.class.getName());
     *
     * // Register custom partitioner
     * props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
     * GeoPriorityPartitioner.class.getName());
     *
     * KafkaProducer<String, String> producer = new KafkaProducer<>(props);
     *
     * // key format: "tier:cityId"
     * producer.send(new ProducerRecord<>("trips", "premium:bangalore", rideJson));
     * producer.send(new ProducerRecord<>("trips", "standard:mumbai", rideJson));
     * producer.send(new ProducerRecord<>("trips", "budget:pune", rideJson));
     *
     * CONSUMER SIDE:
     * // For premium-only consumer: assign specific partitions (0,1,2)
     * consumer.assign(List.of(
     * new TopicPartition("trips", 0),
     * new TopicPartition("trips", 1),
     * new TopicPartition("trips", 2)
     * ));
     */
}
