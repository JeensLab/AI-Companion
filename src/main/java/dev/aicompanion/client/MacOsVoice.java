package dev.aicompanion.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Offline macOS speech, rendered to WAV for the existing positional voice player. */
public final class MacOsVoice {
    public static final class Unavailable extends RuntimeException {
        public Unavailable(String reason) { super(reason, null, false, false); }
    }
    private static final java.util.concurrent.ExecutorService THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "AI Companion macOS voice"); thread.setDaemon(true); return thread;
    });
    private static final Pattern RUSSIAN = Pattern.compile("(?m)^(.+?)\\s+ru[_-]RU\\s+#.*$", Pattern.UNICODE_CHARACTER_CLASS);
    private MacOsVoice() {}

    public static boolean macos() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"); }

    /** Prefer Yuri if installed, otherwise use any installed Russian voice, never an English substitute. */
    public static Optional<String> chooseVoice(String listing) {
        var matcher = RUSSIAN.matcher(listing);
        String first = null;
        while (matcher.find()) {
            String name = matcher.group(1).strip();
            if (name.equals("Yuri")) return Optional.of(name);
            if (first == null) first = name;
        }
        return Optional.ofNullable(first);
    }

    public static CompletableFuture<byte[]> synthesize(String text) {
        if (!macos()) return CompletableFuture.failedFuture(new Unavailable("not_macos"));
        return CompletableFuture.supplyAsync(() -> render(text), THREAD);
    }

    public static String unavailableReason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof Unavailable ? cause.getMessage() : null;
    }

    private static byte[] render(String text) {
        Path listing = null;
        Path audio = null;
        try {
            listing = Files.createTempFile("aicompanion-macos-voices-", ".txt");
            Process voices = new ProcessBuilder("/usr/bin/say", "-v", "?")
                    .redirectOutput(listing.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            waitFor(voices, 5);
            String voice = chooseVoice(Files.readString(listing, StandardCharsets.UTF_8))
                    .orElseThrow(() -> new Unavailable("no_russian_voice"));
            audio = Files.createTempFile("aicompanion-macos-voice-", ".wav");
            Process speech = new ProcessBuilder("/usr/bin/say", "-v", voice, "-o", audio.toString(),
                    "--file-format=WAVE", "--data-format=LEI16@22050", "--channels=1", "-f", "-")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                // Text is stdin, never shell syntax or say options; neutralize Apple's embedded speech commands as well.
                String line = (text.length() > 1800 ? text.substring(0, 1800) : text).replace("[[", " ").replace("]]", " ");
                try (var input = speech.getOutputStream()) { input.write(line.getBytes(StandardCharsets.UTF_8)); }
                waitFor(speech, 40);
                byte[] wav = Files.readAllBytes(audio);
                if (wav.length <= 44) throw new Unavailable("empty_audio");
                return wav;
            } finally {
                if (speech.isAlive()) speech.destroyForcibly();
            }
        } catch (IOException error) {
            throw new Unavailable("macos_voice_failed");
        } finally {
            delete(listing); delete(audio);
        }
    }

    private static void waitFor(Process process, int seconds) {
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw new Unavailable("macos_voice_timeout");
            if (process.exitValue() != 0) throw new Unavailable("macos_voice_failed");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new Unavailable("macos_voice_interrupted");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void delete(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); }
        catch (IOException ignored) { /* The OS may retain a locked temporary file. */ }
    }
}
