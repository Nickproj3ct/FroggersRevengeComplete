import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/**
 * Simple background audio manager for Frogger's Revenge.
 * Uses Java's built-in Clip API (WAV/AIFF/AU only).
 */
public class AudioManager {

    private Clip currentClip;

    /**
     * Play a looping track from the given file path.
     * Stops any currently playing track first.
     *
     * @param filePath e.g. "audio/menuMusic.wav"
     */
    public void playLoop(String filePath) {
        stop(); // stop anything currently playing

        try {
            File f = new File(filePath);
            if (!f.exists()) {
                System.err.println("Audio file not found: " + f.getAbsolutePath());
                return;
            }

            AudioInputStream ais = AudioSystem.getAudioInputStream(f);
            currentClip = AudioSystem.getClip();
            currentClip.open(ais);
            currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            currentClip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Failed to play audio: " + filePath);
            e.printStackTrace();
        }
    }

    /**
     * Stop and release the current clip, if any.
     */
    public void stop() {
        if (currentClip != null) {
            try {
                currentClip.stop();
                currentClip.close();
            } catch (Throwable ignored) {
            }
            currentClip = null;
        }
    }
}
