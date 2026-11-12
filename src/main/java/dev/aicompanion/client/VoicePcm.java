package dev.aicompanion.client;

import javax.sound.sampled.*;
import java.io.*;

/** Pure decoding, also testable on the server without opening an audio device or OpenAL. */
public final class VoicePcm {
    private VoicePcm() { }
    public record Pcm(AudioFormat format, byte[] bytes) { }
    /** A real two-second silence, independent of the voice engine's punctuation handling. */
    public static byte[] joinWithPause(byte[] firstWav, byte[] secondWav) throws Exception {
        Pcm first = decode(firstWav), second = decode(secondWav);
        AudioFormat format = first.format();
        byte[] story;
        try (var input = new AudioInputStream(new ByteArrayInputStream(second.bytes()), second.format(),
                second.bytes().length / second.format().getFrameSize());
             var converted = AudioSystem.getAudioInputStream(format, input)) {
            story = converted.readAllBytes();
        }
        var joined = new ByteArrayOutputStream();
        joined.write(first.bytes());
        joined.write(new byte[Math.round(format.getFrameRate() * 2) * format.getFrameSize()]);
        joined.write(story);
        byte[] pcm = joined.toByteArray();
        try (var input = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / format.getFrameSize());
             var output = new ByteArrayOutputStream()) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        }
    }
    public static Pcm decode(byte[] wav) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat mono = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getFormat().getSampleRate(), 16, 1, 2, source.getFormat().getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(mono, source)) {
                byte[] bytes = converted.readAllBytes();
                if (bytes.length == 0) throw new IOException("The voice returned empty audio");
                return new Pcm(mono, bytes);
            }
        }
    }
}
