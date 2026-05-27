package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.User;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * STRATEGY PATTERN — Audio Quality Selection
 * ═══════════════════════════════════════════════════════════════════════
 *
 * WHY STRATEGY PATTERN?
 * The bitrate selection logic varies based on multiple factors:
 * - User tier (free = max 160kbps, premium = max 320kbps)
 * - Network bandwidth (adapt down if connection is slow)
 * - User preference (data saver mode)
 *
 * Instead of if-else chains, we encapsulate each algorithm
 * behind the AudioQualityStrategy interface. The StreamingService
 * delegates quality selection to the injected strategy.
 *
 * PATTERN STRUCTURE:
 * AudioQualityStrategy (interface)
 * ├── HighQualityStrategy → returns 320kbps for premium, 160 for free
 * ├── NormalQualityStrategy → returns 160kbps always
 * ├── DataSaverStrategy → returns 96kbps always
 * └── AdaptiveStrategy → picks based on bandwidth + tier
 *
 * IN PRODUCTION:
 * The adaptive strategy runs on the client side, measuring bandwidth
 * every 10 seconds and adjusting the bitrate. Here we simulate it
 * on the server side for simplicity.
 * ═══════════════════════════════════════════════════════════════════════
 */
public interface AudioQualityStrategy {

    /**
     * Select the appropriate bitrate (96, 160, or 320 kbps).
     *
     * @param user             the user making the request
     * @param networkBandwidth estimated bandwidth in kbps (0 = unknown)
     * @return selected bitrate: 96, 160, or 320
     */
    int selectBitrate(User user, int networkBandwidth);

    /**
     * @return human-readable name of this strategy
     */
    String getName();
}
