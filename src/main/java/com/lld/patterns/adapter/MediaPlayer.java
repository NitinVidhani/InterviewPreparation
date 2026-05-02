package com.lld.patterns.adapter;

/**
 * Target Interface — MediaPlayer.
 *
 * This is the interface that our client code (the application) understands.
 * It can play audio files natively.
 */
public interface MediaPlayer {

    /** Play a media file. */
    void play(String audioType, String fileName);
}
