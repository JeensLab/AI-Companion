package dev.aicompanion.client;

import javax.sound.sampled.*;
import java.io.*;

/** Pure decoding, also testable on the server without opening an audio device or OpenAL. */
public final class VoicePcm {
    private VoicePcm() { }
    public record Pcm(AudioFormat format, byte[] bytes) { }
    public static Pcm decode(byte[] wav) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat mono = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getFormat().getSampleRate(), 16, 1, 2, source.getFormat().getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(mono, source)) {
                byte[] bytes = converted.readAllBytes();
                if (bytes.length == 0) throw new IOException("Inworld returned empty audio");
                return new Pcm(mono, bytes);
            }
        }
    }
}
