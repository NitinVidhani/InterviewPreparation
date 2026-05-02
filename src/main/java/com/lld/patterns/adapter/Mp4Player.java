package com.lld.patterns.adapter;

/** Concrete Adaptee — plays MP4 files. */
public class Mp4Player implements AdvancedMediaPlayer {

    @Override
    public void playVlc(String fileName) {
        // Do nothing — this player doesn't handle vlc
    }

    @Override
    public void playMp4(String fileName) {
        System.out.println("[MP4 Player] Playing mp4 file: " + fileName);
    }
}
