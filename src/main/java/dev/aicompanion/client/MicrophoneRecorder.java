package dev.aicompanion.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

/** Captures mono 16 kHz LINEAR16 microphone audio while the push-to-talk key is held. */
public final class MicrophoneRecorder {
    public static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private static volatile TargetDataLine activeLine;
    private static volatile boolean stopRequested;
    private static CompletableFuture<byte[]> activeRecording;

    private MicrophoneRecorder() {
    }

    public static synchronized CompletableFuture<byte[]> start() {
        if (activeRecording != null && !activeRecording.isDone()) return activeRecording;
        stopRequested = false;
        activeRecording = new CompletableFuture<>();
        CompletableFuture<byte[]> result = activeRecording;
        Thread captureThread = new Thread(() -> capture(result), "aicompanion-microphone");
        captureThread.setDaemon(true);
        captureThread.start();
        return result;
    }

    public static synchronized CompletableFuture<byte[]> stop() {
        stopRequested = true;
        TargetDataLine line = activeLine;
        if (line != null) line.close();
        return activeRecording == null ? CompletableFuture.completedFuture(new byte[0]) : activeRecording;
    }

    private static void capture(CompletableFuture<byte[]> result) {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        TargetDataLine line = null;
        try {
            line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
            line.open(FORMAT);
            activeLine = line;
            if (stopRequested) {
                result.complete(new byte[0]);
                return;
            }
            line.start();
            byte[] buffer = new byte[2048];
            while (!stopRequested) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) audio.write(buffer, 0, count);
            }
            result.complete(audio.toByteArray());
        } catch (Exception exception) {
            if (stopRequested) result.complete(audio.toByteArray());
            else result.completeExceptionally(new IllegalStateException("Не удалось открыть микрофон: " + exception.getMessage(), exception));
        } finally {
            activeLine = null;
            if (line != null && line.isOpen()) line.close();
        }
    }
}
