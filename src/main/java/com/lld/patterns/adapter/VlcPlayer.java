package com.lld.patterns.adapter;

/** Concrete Adaptee — plays VLC files. */
public class VlcPlayer implements AdvancedMediaPlayer {

    @Override
    public void playVlc(String fileName) {
        System.out.println("[VLC Player] Playing vlc file: " + fileName);
    }

    @Override
    public void playMp4(String fileName) {
        // Do nothing — this player doesn't handle mp4
    }
}
