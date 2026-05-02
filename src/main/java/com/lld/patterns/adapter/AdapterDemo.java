package com.lld.patterns.adapter;

/**
 * Driver class that demonstrates the Adapter pattern.
 *
 * The AudioPlayer can play mp3 natively. For vlc and mp4, it transparently
 * delegates to AdvancedMediaPlayer via the MediaAdapter — the client code
 * never needs to know about VlcPlayer or Mp4Player.
 */
public class AdapterDemo {

    public static void main(String[] args) {
        System.out.println("=== Adapter Pattern Demo ===\n");

        AudioPlayer player = new AudioPlayer();

        player.play("mp3", "beyond_the_horizon.mp3");
        player.play("mp4", "alone.mp4");
        player.play("vlc", "far_far_away.vlc");
        player.play("avi", "mind_me.avi"); // unsupported
    }
}
