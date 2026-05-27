package com.lld.hld.urlshortener.model;

import java.time.Instant;

/**
 * Represents a single click/redirect event for analytics.
 * These are published asynchronously to a Kafka topic (simulated here).
 */
public class ClickEvent {

    private String shortCode;
    private Instant clickedAt;
    private String ipAddress;
    private String country;
    private String deviceType; // "mobile" | "desktop" | "tablet" | "bot"
    private String referrer;
    private String userAgent;

    public ClickEvent(String shortCode, String ipAddress, String userAgent) {
        this.shortCode = shortCode;
        this.clickedAt = Instant.now();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceType = detectDevice(userAgent);
    }

    private String detectDevice(String ua) {
        if (ua == null)
            return "unknown";
        ua = ua.toLowerCase();
        if (ua.contains("mobile"))
            return "mobile";
        if (ua.contains("tablet"))
            return "tablet";
        if (ua.contains("bot"))
            return "bot";
        return "desktop";
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getShortCode() {
        return shortCode;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getCountry() {
        return country;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setReferrer(String ref) {
        this.referrer = ref;
    }

    @Override
    public String toString() {
        return "ClickEvent{shortCode='" + shortCode + "', at=" + clickedAt +
                ", device='" + deviceType + "', ip='" + ipAddress + "'}";
    }
}
