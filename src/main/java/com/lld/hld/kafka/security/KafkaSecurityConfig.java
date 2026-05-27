package com.lld.hld.kafka.security;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * ============================================================
 * KafkaSecurityConfig — SSL + SASL Production Security Setup
 * ============================================================
 *
 * WHY SECURITY MATTERS IN KAFKA:
 * Without security, any client can:
 * - Produce to ANY topic (even __consumer_offsets)
 * - Consume ANY topic (read sensitive data)
 * - Delete topics, reset offsets
 * - Sniff messages on the network (plaintext by default)
 *
 * THREE SECURITY LAYERS:
 *
 * 1. ENCRYPTION (SSL/TLS)
 * → Encrypts data in transit between client and broker.
 * → Prevents eavesdropping / man-in-the-middle attacks.
 * → Protocol: SSL (wraps plaintext Kafka protocol).
 *
 * 2. AUTHENTICATION (SASL)
 * → "Who are you?" — verifies client identity.
 * → SASL/PLAIN: username+password (simple, not recommended for prod)
 * → SASL/SCRAM-SHA-256: password hashed+salted (stored in ZooKeeper/KRaft)
 * → SASL/GSSAPI (Kerberos): enterprise SSO, complex setup
 * → SASL/OAUTHBEARER: OAuth2 tokens (modern, cloud-native)
 *
 * 3. AUTHORIZATION (ACLs)
 * → "What can you do?" — controls which authenticated user/principal
 * can read/write/create/delete which topics.
 * → Configured via kafka-acls.sh tool.
 *
 * INTERVIEW TIP:
 * "In our Kafka cluster we use SSL for encryption and SASL/SCRAM-SHA-256
 * for authentication. Each microservice has its own service account in
 * Kafka. We grant it READ on its subscribed topics and WRITE on its
 * output topics only. No service can access another service's topics
 * without an explicit ACL grant."
 *
 * SECURITY PROTOCOL COMBINATIONS:
 * ┌──────────────────────────┬──────────────┬────────────────┐
 * │ security.protocol │ Encryption │ Authentication │
 * ├──────────────────────────┼──────────────┼────────────────┤
 * │ PLAINTEXT │ None │ None │
 * │ SSL │ TLS │ mTLS (certs) │
 * │ SASL_PLAINTEXT │ None │ SASL only │
 * │ SASL_SSL │ TLS │ SASL + TLS │ ← Production
 * └──────────────────────────┴──────────────┴────────────────┘
 */
public class KafkaSecurityConfig {

    // -------------------------------------------------------
    // 1. SSL-Only (Encryption + mTLS Client Authentication)
    // -------------------------------------------------------

    /**
     * SSL with mutual TLS: both broker and client present certificates.
     * Broker verifies client cert → authentication via PKI.
     *
     * SETUP:
     * Broker keystore: contains broker's cert + private key
     * Broker truststore: contains CA cert that signed client certs
     * Client keystore: contains client's cert + private key
     * Client truststore: contains CA cert that signed broker cert
     *
     * GENERATE KEYSTORES (for dev):
     * # Create CA
     * openssl req -new -x509 -keyout ca-key -out ca-cert -days 365
     *
     * # Create broker keystore
     * keytool -genkey -keyalg RSA -keystore server.keystore.jks -validity 365
     * keytool -certreq -keystore server.keystore.jks | \
     * openssl x509 -req -signkey ca-key -CA ca-cert -out server-signed.crt
     * keytool -importcert -file server-signed.crt -keystore server.keystore.jks
     */
    public static Properties sslOnlyProducerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka.prod.example.com:9093");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // ── SSL Encryption ───────────────────────────────────
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");

        // ── Client Truststore (verifies broker's certificate) ─
        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "/etc/kafka/ssl/client.truststore.jks");
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "changeit");

        // ── Client Keystore (client presents this cert to broker) ─
        // Required for mTLS; omit these 3 if not using client-auth
        props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, "/etc/kafka/ssl/client.keystore.jks");
        props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "changeit");
        props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, "changeit");

        // ── TLS version (enforce TLS 1.2+ minimum) ───────────
        props.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLSv1.3");
        props.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.3,TLSv1.2");

        // ── Cipher suites (optional: restrict to strong ciphers) ─
        // props.put(SslConfigs.SSL_CIPHER_SUITES_CONFIG,
        // "TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256");

        // Production settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return props;
    }

    // -------------------------------------------------------
    // 2. SASL/SCRAM-SHA-256 + SSL (Production Recommended)
    // -------------------------------------------------------

    /**
     * SASL/SCRAM-SHA-256 with SSL transport encryption.
     *
     * SCRAM = Salted Challenge Response Authentication Mechanism
     * - Passwords are stored as hashed+salted values in ZooKeeper/KRaft
     * - Client never sends plaintext passwords — uses challenge/response
     * - More secure than SASL/PLAIN (which sends base64-encoded credentials)
     *
     * SETUP USERS ON BROKER:
     * # Create user "order-service" with password
     * kafka-configs.sh --zookeeper localhost:2181 \
     * --alter --add-config \
     * 'SCRAM-SHA-256=[iterations=8192,password=secret123]' \
     * --entity-type users --entity-name order-service
     *
     * SETUP ACLs:
     * # Grant "order-service" WRITE on "orders" topic
     * kafka-acls.sh --bootstrap-server localhost:9092 \
     * --add --allow-principal User:order-service \
     * --operation Write --topic orders
     *
     * # Grant "billing-service" READ on "orders" topic
     * kafka-acls.sh --bootstrap-server localhost:9092 \
     * --add --allow-principal User:billing-service \
     * --operation Read --topic orders \
     * --group billing-service-cg
     */
    public static Properties saslScramProducerConfig(String username, String password) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka.prod.example.com:9094");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // ── AUTH: SASL over SSL ───────────────────────────────
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");

        // ── JAAS config (inline — avoid .jaas files in production) ─
        String jaasConfig = String.format(
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                        "username=\"%s\" password=\"%s\";",
                username, password);
        props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);

        // ── SSL Truststore (still need to trust the broker cert) ─
        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "/etc/kafka/ssl/client.truststore.jks");
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "changeit");

        // Production settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return props;
    }

    /**
     * SASL/SCRAM Consumer config (SASL_SSL).
     */
    public static Properties saslScramConsumerConfig(String username, String password,
            String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka.prod.example.com:9094");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // ── Auth ───────────────────────────────────────────────
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");
        props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                        "username=\"%s\" password=\"%s\";",
                username, password));
        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "/etc/kafka/ssl/client.truststore.jks");
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "changeit");

        // ── Consumer settings ──────────────────────────────────
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"); // ignore aborted txns

        return props;
    }

    // -------------------------------------------------------
    // 3. SASL/OAUTHBEARER (OAuth2 / Cloud-native)
    // -------------------------------------------------------

    /**
     * OAuth2 Bearer Token Authentication.
     * Used with cloud-managed Kafka (Confluent Cloud, Amazon MSK with IAM).
     *
     * HOW IT WORKS:
     * 1. Client requests an access token from an OAuth2 IDP (Okta, Keycloak, AWS
     * Cognito).
     * 2. Client sends token to Kafka broker.
     * 3. Broker validates token with IDP (via JWKS endpoint or introspection).
     * 4. Token expires → client refreshes automatically.
     *
     * ADVANTAGES over SCRAM:
     * - Integrates with existing company SSO
     * - Short-lived tokens (no long-lived passwords)
     * - Broker doesn't need to know user passwords at all
     */
    public static Properties oauthBearerProducerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka.confluent.cloud:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");

        // For Confluent Cloud: use their custom token provider
        // In JAAS config, reference your OAuth2 client credentials
        props.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                        "oauth.token.endpoint.uri=\"https://your-idp.example.com/oauth/token\" " +
                        "oauth.client.id=\"kafka-client-id\" " +
                        "oauth.client.secret=\"client-secret\" " +
                        "oauth.scope=\"kafka-access\";");

        // Custom login callback for token refresh (Confluent provides this)
        props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler");

        return props;
    }

    // -------------------------------------------------------
    // 4. ACL Patterns Reference
    // -------------------------------------------------------

    /**
     * ACL REFERENCE — Common patterns for production Kafka clusters.
     *
     * PRINCIPLE OF LEAST PRIVILEGE: Each service gets ONLY what it needs.
     *
     * ─────────────────────────────────────────────────────────────────────
     * SERVICE NEEDS ACL GRANT
     * ─────────────────────────────────────────────────────────────────────
     * order-service Write orders ALLOW Write on Topic: orders
     * billing-service Read orders ALLOW Read on Topic: orders
     * ALLOW Describe on Group: billing-cg
     * admin-service Manage topics ALLOW Create,Delete,Alter on Topic:*
     * ─────────────────────────────────────────────────────────────────────
     *
     * CLI EXAMPLES:
     *
     * # Grant order-service: write to "orders" topic
     * kafka-acls.sh --bootstrap-server localhost:9092 \
     * --add \
     * --allow-principal User:order-service \
     * --operation Write \
     * --topic orders
     *
     * # Grant billing-service: read from "orders" topic + use consumer group
     * kafka-acls.sh --bootstrap-server localhost:9092 \
     * --add \
     * --allow-principal User:billing-service \
     * --operation Read \
     * --topic orders \
     * --group billing-service-cg
     *
     * # Grant DLQ writer (only the consumer-error handler can write to DLQ)
     * kafka-acls.sh --bootstrap-server localhost:9092 \
     * --add \
     * --allow-principal User:order-service \
     * --operation Write \
     * --topic orders.DLQ
     *
     * # Deny ALL access to internal topics (prevent accidental reads)
     * kafka-acls.sh --bootstrap-server localhost:9092 \
     * --add \
     * --deny-principal User:* \
     * --operation All \
     * --topic __consumer_offsets
     *
     * # List all ACLs
     * kafka-acls.sh --bootstrap-server localhost:9092 --list
     *
     * # List ACLs for specific topic
     * kafka-acls.sh --bootstrap-server localhost:9092 --list --topic orders
     */

    // -------------------------------------------------------
    // 5. Spring Boot Integration (reference only)
    // -------------------------------------------------------

    /**
     * In Spring Boot with spring-kafka:
     *
     * application.yml:
     * ─────────────────────────────────────────
     * spring:
     * kafka:
     * bootstrap-servers: kafka.prod.example.com:9094
     * security:
     * protocol: SASL_SSL
     * properties:
     * sasl.mechanism: SCRAM-SHA-256
     * sasl.jaas.config: >
     * org.apache.kafka.common.security.scram.ScramLoginModule required
     * username="${KAFKA_USERNAME}"
     * password="${KAFKA_PASSWORD}";
     * ssl.truststore.location: /etc/kafka/ssl/client.truststore.jks
     * ssl.truststore.password: ${KAFKA_TRUSTSTORE_PASSWORD}
     * producer:
     * acks: all
     * key-serializer: org.apache.kafka.common.serialization.StringSerializer
     * value-serializer: org.apache.kafka.common.serialization.StringSerializer
     * consumer:
     * group-id: order-service-cg
     * enable-auto-commit: false
     * auto-offset-reset: earliest
     * key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
     * value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
     *
     * IMPORTANT: Store KAFKA_USERNAME / KAFKA_PASSWORD in Kubernetes Secrets
     * or AWS Secrets Manager — NEVER hardcode in source code.
     */

    // -------------------------------------------------------
    // Main: print configs (for inspection)
    // -------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=== Kafka Security Configuration Reference ===\n");

        System.out.println("--- 1. SSL-Only (mTLS) Producer Config ---");
        sslOnlyProducerConfig().forEach((k, v) -> System.out.printf("  %-45s = %s%n", k, v));

        System.out.println("\n--- 2. SASL/SCRAM-SHA-256 + SSL Producer Config ---");
        saslScramProducerConfig("order-service", "s3cr3t!").forEach(
                (k, v) -> System.out.printf("  %-45s = %s%n", k, v));

        System.out.println("\n--- 3. SASL/SCRAM-SHA-256 + SSL Consumer Config ---");
        saslScramConsumerConfig("billing-service", "p4ssw0rd!", "billing-service-cg").forEach(
                (k, v) -> System.out.printf("  %-45s = %s%n", k, v));

        System.out.println("\n[See source comments for ACL CLI commands and OAuth2 setup]");
    }
}
