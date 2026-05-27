package com.lld.hld.kafka.admin;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.TopicConfig;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * ============================================================
 * KafkaAdminClientDemo — Topic Management & Inspection
 * ============================================================
 *
 * USE CASE:
 * Automate Kafka topic provisioning in your service startup or
 * deployment pipeline. This is commonly used in:
 * - Spring Boot @ApplicationStartup beans
 * - CI/CD pipeline setup scripts
 * - Infrastructure-as-code (when you don't use Terraform/Pulumi for Kafka)
 *
 * CONCEPTS COVERED:
 * 1. Programmatic topic creation with fine-grained configs
 * 2. Topic description (partition leaders, ISR status)
 * 3. Dynamic config updates (change retention without downtime)
 * 4. Consumer group inspection (lag monitoring)
 *
 * INTERVIEW TIP:
 * "We use AdminClient to auto-create topics at service startup
 * to ensure all required topics exist with the right configs.
 * For production, we also automate lag monitoring by calling
 * listConsumerGroupOffsets() and comparing with latest offsets."
 */
public class KafkaAdminClientDemo {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    // -------------------------------------------------------
    // AdminClient Configuration
    // -------------------------------------------------------
    private static AdminClient createAdminClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 60_000);
        return AdminClient.create(props);
    }

    // -------------------------------------------------------
    // 1. Create Topics
    // -------------------------------------------------------

    /**
     * Creates a production-grade topic with custom configurations.
     *
     * TOPIC CONFIGS EXPLAINED:
     *
     * retention.ms = 604800000 (7 days):
     * → Delete log segments older than 7 days.
     * → Set to -1 for unlimited retention (for event sourcing).
     *
     * retention.bytes = -1:
     * → No size-based retention limit (time-based only).
     * → Set to 10GB if you want size-based eviction.
     *
     * min.insync.replicas = 2:
     * → Producer with acks=all MUST get acks from at least 2 replicas.
     * → With RF=3, tolerates 1 broker failure while maintaining write availability.
     * → If set to 3 (== RF), ANY broker failure stops writes.
     *
     * cleanup.policy = delete:
     * → Delete old segments (time/size based). Use for event logs.
     * → Use "compact" for CDC/changelog topics (keep latest per key).
     * → Use "delete,compact" for both behaviors.
     *
     * max.message.bytes = 1048576 (1MB):
     * → Max size of a single message.
     * → Must also set max.request.size on producer and fetch.max.bytes on consumer.
     *
     * compression.type = producer:
     * → Use whatever compression the producer sends (don't re-compress on broker).
     * → "snappy", "lz4", "gzip" re-compresses on broker (use if producers have
     * different codecs).
     */
    public static void createTopics(AdminClient adminClient) throws ExecutionException, InterruptedException {
        List<NewTopic> topics = new ArrayList<>();

        // --- Orders Topic ---
        Map<String, String> ordersConfig = new HashMap<>();
        ordersConfig.put(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 60 * 60 * 1000L)); // 7 days
        ordersConfig.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2");
        ordersConfig.put(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
        ordersConfig.put(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, String.valueOf(1024 * 1024)); // 1MB
        ordersConfig.put(TopicConfig.COMPRESSION_TYPE_CONFIG, "producer"); // Preserve producer compression

        topics.add(new NewTopic("orders", 6, (short) 3).configs(ordersConfig));

        // --- Dead Letter Queue Topic ---
        Map<String, String> dlqConfig = new HashMap<>();
        dlqConfig.put(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(30L * 24 * 60 * 60 * 1000L)); // 30 days
        dlqConfig.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2");

        topics.add(new NewTopic("orders.DLQ", 3, (short) 3).configs(dlqConfig));

        // --- User Events (Compacted) Topic ---
        Map<String, String> compactedConfig = new HashMap<>();
        compactedConfig.put(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
        compactedConfig.put(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, "60000"); // 1 min before compaction eligible
        compactedConfig.put(TopicConfig.DELETE_RETENTION_MS_CONFIG, "86400000"); // 1 day tombstone retention
        compactedConfig.put(TopicConfig.SEGMENT_BYTES_CONFIG, String.valueOf(1024 * 1024 * 100)); // 100MB segments

        topics.add(new NewTopic("user-profiles", 6, (short) 3).configs(compactedConfig));

        System.out.println("Creating topics: " + topics.stream()
                .map(NewTopic::name).toList());

        CreateTopicsResult result = adminClient.createTopics(topics);

        // Wait for all topics to be created
        result.all().get();
        System.out.println("✅ All topics created successfully.\n");
    }

    // -------------------------------------------------------
    // 2. Describe Topics (ISR, Leaders, Partition Info)
    // -------------------------------------------------------

    /**
     * Inspects topic metadata — crucial for debugging replication issues.
     *
     * Output shows:
     * - Which broker is the leader for each partition.
     * - Which brokers are in the ISR (in-sync replicas).
     * - If ISR < replication factor → ALERT! A replica is falling behind.
     */
    public static void describeTopics(AdminClient adminClient, String... topicNames)
            throws ExecutionException, InterruptedException {

        DescribeTopicsResult result = adminClient.describeTopics(Arrays.asList(topicNames));
        Map<String, TopicDescription> descriptions = result.allTopicNames().get();

        descriptions.forEach((topicName, description) -> {
            System.out.println("Topic: " + topicName);
            System.out.println("  Partitions: " + description.partitions().size());

            description.partitions().forEach(partition -> {
                boolean underReplicated = partition.isr().size() < partition.replicas().size();
                System.out.printf("  Partition %d: leader=Broker%d, replicas=%s, ISR=%s %s%n",
                        partition.partition(),
                        partition.leader().id(),
                        partition.replicas().stream().map(n -> "Broker" + n.id()).toList(),
                        partition.isr().stream().map(n -> "Broker" + n.id()).toList(),
                        underReplicated ? "⚠️ UNDER-REPLICATED!" : "✅");
            });
            System.out.println();
        });
    }

    // -------------------------------------------------------
    // 3. Update Topic Configuration (Online — No Downtime)
    // -------------------------------------------------------

    /**
     * Change topic configuration WITHOUT deleting and recreating the topic.
     *
     * COMMON LIVE UPDATES:
     * - Extend retention when we realize we need more history.
     * - Change min.insync.replicas to improve write availability during
     * maintenance.
     * - Enable/disable compression.
     */
    public static void updateTopicRetention(AdminClient adminClient, String topicName, long retentionMs)
            throws ExecutionException, InterruptedException {

        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

        Map<ConfigResource, Collection<AlterConfigOp>> updates = Map.of(
                resource, List.of(
                        new AlterConfigOp(
                                new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retentionMs)),
                                AlterConfigOp.OpType.SET)));

        adminClient.incrementalAlterConfigs(updates).all().get();
        System.out.printf("✅ Updated '%s' retention to %d ms (%d days)%n",
                topicName, retentionMs, retentionMs / 86_400_000);
    }

    // -------------------------------------------------------
    // 4. Monitor Consumer Group Lag
    // -------------------------------------------------------

    /**
     * CONSUMER LAG MONITORING — critical for production health.
     *
     * Lag = (latest offset in partition) - (committed offset for consumer group)
     *
     * High lag means:
     * - Consumer is processing too slowly.
     * - Need more consumers (and/or more partitions).
     *
     * In production, this data feeds into:
     * - Prometheus/Grafana dashboards.
     * - Kubernetes HPA (auto-scale consumers based on lag).
     * - PagerDuty alerts when lag exceeds threshold.
     */
    public static void monitorConsumerGroupLag(AdminClient adminClient, String groupId)
            throws ExecutionException, InterruptedException {

        System.out.println("=== Consumer Group Lag: " + groupId + " ===");

        // Step 1: Get committed offsets for the group
        Map<TopicPartition, OffsetAndMetadata> committedOffsets = adminClient.listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get();

        if (committedOffsets.isEmpty()) {
            System.out.println("No committed offsets found for group: " + groupId);
            return;
        }

        // Step 2: Get latest (end) offsets for each partition
        ListOffsetsResult latestOffsetsResult = adminClient.listOffsets(
                committedOffsets.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> OffsetSpec.latest())));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = latestOffsetsResult.all().get();

        // Step 3: Calculate and display lag
        long totalLag = 0;
        System.out.printf("%-40s %-15s %-15s %-10s%n",
                "Partition", "Committed", "Latest", "Lag");
        System.out.println("-".repeat(80));

        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committedOffsets.entrySet()) {
            TopicPartition tp = entry.getKey();
            long committed = entry.getValue().offset();
            long latest = latestOffsets.get(tp).offset();
            long lag = latest - committed;
            totalLag += lag;

            System.out.printf("%-40s %-15d %-15d %-10d %s%n",
                    tp.topic() + "-" + tp.partition(),
                    committed, latest, lag,
                    lag > 1000 ? "⚠️ HIGH LAG" : "✅");
        }
        System.out.println("-".repeat(80));
        System.out.printf("TOTAL LAG: %d%n%n", totalLag);
    }

    // -------------------------------------------------------
    // Main
    // -------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=== Kafka AdminClient Demo ===\n");

        try (AdminClient adminClient = createAdminClient()) {

            // 1. Create topics
            System.out.println("--- 1. Creating Topics ---");
            createTopics(adminClient);

            // 2. Describe topics
            System.out.println("--- 2. Describing Topics ---");
            describeTopics(adminClient, "orders", "orders.DLQ");

            // 3. Update configuration
            System.out.println("--- 3. Updating Retention ---");
            updateTopicRetention(adminClient, "orders", 14L * 24 * 60 * 60 * 1000); // 14 days

            // 4. Monitor lag (assumes consumers are running)
            System.out.println("--- 4. Consumer Group Lag ---");
            monitorConsumerGroupLag(adminClient, "order-processor-cg");

        } catch (ExecutionException | InterruptedException e) {
            System.err.println("Admin operation failed: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
