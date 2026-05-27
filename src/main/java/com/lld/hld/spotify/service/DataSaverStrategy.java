package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.User;

/**
 * Strategy: Data Saver — always selects lowest quality (96 kbps).
 *
 * Used when user enables "Data Saver" mode on mobile.
 * Reduces bandwidth consumption by ~70% compared to high quality.
 */
public class DataSaverStrategy implements AudioQualityStrategy {

    @Override
    public int selectBitrate(User user, int networkBandwidth) {
        return 96; // Always lowest
    }

    @Override
    public String getName() {
        return "Data Saver";
    }
}
