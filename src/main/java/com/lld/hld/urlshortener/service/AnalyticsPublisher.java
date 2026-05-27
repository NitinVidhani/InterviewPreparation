package com.lld.hld.urlshortener.service;

import com.lld.hld.urlshortener.model.ClickEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Analytics Publisher — simulates publishing click events to Kafka
 * asynchronously.
 *
 * In production:
 * - KafkaProducer publishes to topic "url-click-events"
 * - Kafka consumers (Flink/Spark) process the stream
 * - Enriches with geo-IP, device-type detection
 * - Aggregates into ClickHouse analytics DB
 *
 * WHY ASYNC?
 * The redirect response (302) must be returned to the user immediately.
 * Analytics tracking must NEVER block or slow down that redirect.
 * Fire-and-forget via a background thread pool.
 */
public class AnalyticsPublisher {

    // Simulates a Kafka producer thread pool
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * Publish a click event asynchronously.
     * Returns immediately — does NOT block the redirect path.
     */
    public void publishAsync(ClickEvent event) {
        executor.submit(() -> {
            try {
                // Simulate network I/O latency of publishing to Kafka (~5ms)
                Thread.sleep(5);
                System.out.printf("[Analytics] EVENT published → %s%n", event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
