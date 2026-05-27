package com.lld.hld.spotify.service;

import com.lld.hld.spotify.model.User;

/**
 * Strategy: High Quality — always selects the highest available bitrate.
 *
 * Premium users → 320 kbps (OGG Vorbis, near-CD quality)
 * Free users → 160 kbps (cap enforced by Spotify's licensing)
 */
public class HighQualityStrategy implements AudioQualityStrategy {

    @Override
    public int selectBitrate(User user, int networkBandwidth) {
        return user.isPremium() ? 320 : 160;
    }

    @Override
    public String getName() {
        return "High Quality";
    }
}
