package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.User;

/**
 * Strategy: Adaptive — dynamically selects quality based on network bandwidth.
 *
 * This is the DEFAULT strategy used by Spotify clients in production.
 * The client measures bandwidth every 10 seconds and adjusts the stream
 * quality.
 *
 * Algorithm:
 * - bandwidth < 150 kbps → 96 kbps (prevent buffering)
 * - bandwidth 150-300 kbps → 160 kbps (normal quality)
 * - bandwidth > 300 kbps → 320 kbps (high quality, if premium)
 *
 * The tier cap is always enforced:
 * - Free users are capped at 160 kbps regardless of bandwidth
 */
public class AdaptiveQualityStrategy implements AudioQualityStrategy {

    @Override
    public int selectBitrate(User user, int networkBandwidth) {
        int maxForTier = user.isPremium() ? 320 : 160;

        int selected;
        if (networkBandwidth <= 0) {
            // Unknown bandwidth → default to normal
            selected = 160;
        } else if (networkBandwidth < 150) {
            selected = 96;
        } else if (networkBandwidth < 300) {
            selected = 160;
        } else {
            selected = 320;
        }

        // Enforce tier cap: free users can never exceed 160
        return Math.min(selected, maxForTier);
    }

    @Override
    public String getName() {
        return "Adaptive";
    }
}
