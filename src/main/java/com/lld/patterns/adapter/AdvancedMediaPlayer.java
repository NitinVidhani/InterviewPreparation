package com.lld.patterns.adapter;

/**
 * Adaptee Interface — AdvancedMediaPlayer.
 *
 * This is a "third-party" or "legacy" interface that supports advanced formats
 * (VLC, MP4) but does NOT conform to MediaPlayer. We can't change this.
 */
public interface AdvancedMediaPlayer {

    void playVlc(String fileName);

    void playMp4(String fileName);
}
