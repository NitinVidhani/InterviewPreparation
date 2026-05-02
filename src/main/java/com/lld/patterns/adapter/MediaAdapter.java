package com.lld.patterns.adapter;

/**
 * Adapter — MediaAdapter.
 *
 * This is the KEY class: it implements the Target interface (MediaPlayer) and
 * internally delegates to the Adaptee (AdvancedMediaPlayer).
 *
 * The adapter translates calls from the target interface to the adaptee's
 * format. The client code only knows about MediaPlayer — the adapter hides
 * the complexity of dealing with AdvancedMediaPlayer.
 *
 * PATTERN IN ACTION:
 * Client → MediaPlayer.play("vlc", "song.vlc")
 * → MediaAdapter.play("vlc", "song.vlc")
 * → VlcPlayer.playVlc("song.vlc")
 */
public class MediaAdapter implements MediaPlayer {

    // Composition: The adapter wraps (HAS-A) an AdvancedMediaPlayer
    private final AdvancedMediaPlayer advancedPlayer;

    /**
     * The adapter decides which concrete adaptee to use based on the format.
     */
    public MediaAdapter(String audioType) {
        if (audioType.equalsIgnoreCase("vlc")) {
            advancedPlayer = new VlcPlayer();
        } else if (audioType.equalsIgnoreCase("mp4")) {
            advancedPlayer = new Mp4Player();
        } else {
            throw new IllegalArgumentException("Unsupported format: " + audioType);
        }
    }

    /**
     * Translates the target interface call → the adaptee's specific method.
     */
    @Override
    public void play(String audioType, String fileName) {
        if (audioType.equalsIgnoreCase("vlc")) {
            advancedPlayer.playVlc(fileName);
        } else if (audioType.equalsIgnoreCase("mp4")) {
            advancedPlayer.playMp4(fileName);
        }
    }
}
