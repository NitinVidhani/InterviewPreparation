package com.lld.patterns.adapter;

/**
 * Concrete Client — AudioPlayer.
 *
 * This player natively supports MP3. For VLC and MP4, it uses the
 * MediaAdapter to delegate to AdvancedMediaPlayer implementations.
 *
 * Notice the client code doesn't directly interact with VlcPlayer or
 * Mp4Player — the adapter handles the translation transparently.
 */
public class AudioPlayer implements MediaPlayer {

    @Override
    public void play(String audioType, String fileName) {
        // Native support for mp3
        if (audioType.equalsIgnoreCase("mp3")) {
            System.out.println("[Audio Player] Playing mp3 file: " + fileName);
        }
        // For vlc and mp4, use the adapter
        else if (audioType.equalsIgnoreCase("vlc") || audioType.equalsIgnoreCase("mp4")) {
            MediaAdapter adapter = new MediaAdapter(audioType);
            adapter.play(audioType, fileName);
        } else {
            System.out.println("[Audio Player] Invalid media format: " + audioType);
        }
    }
}
