package dev.aicompanion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Local observable events only: never record model reasoning, HTTP bodies or credentials. */
public final class CompanionDiagnostics {
    private static final Gson JSON = new Gson();
    private static final String SESSION = UUID.randomUUID().toString();
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong EVENTS = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final Set<String> SECRETS = new CopyOnWriteArraySet<>();
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static volatile boolean enabled = true;
    private static boolean ioWarning;
    private static final java.util.concurrent.atomic.AtomicBoolean ANNOUNCED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2048), runnable -> {
                Thread thread = new Thread(runnable, "AI Companion diagnostics");
                thread.setDaemon(true);
                return thread;
            }, (task, executor) -> DROPPED.incrementAndGet());

    private CompanionDiagnostics() { }

    public static void configure(boolean active, String... secrets) {
        enabled = active;
        for (String secret : secrets) if (secret != null && !secret.isBlank()) {
            SECRETS.add(secret.trim());
            // Also hide the credential part if a user pasted an Authorization prefix.
            if (secret.trim().contains(" ")) SECRETS.add(secret.trim().substring(secret.trim().lastIndexOf(' ') + 1));
        }
        if (active && ANNOUNCED.compareAndSet(false, true)) event("", "session_started", "schema", 1,
                "scope", "observable_dialogue_actions_and_game_state", "maxFileBytes", MAX_BYTES, "retainedFiles", 4);
    }

    public static String newTrace() { return SESSION.substring(0, 8) + "-" + IDS.incrementAndGet(); }
    public static boolean enabled() { return enabled; }

    public static void event(String trace, String event, Object... fields) {
        if (!enabled) return;
        try {
            JsonObject row = new JsonObject();
            row.addProperty("time", Instant.now().toString());
            row.addProperty("session", SESSION);
            row.addProperty("sequence", EVENTS.incrementAndGet());
            row.addProperty("trace", trace == null ? "" : trace);
            row.addProperty("event", event);
            row.addProperty("thread", Thread.currentThread().getName());
            for (int i = 0; i + 1 < fields.length; i += 2) {
                Object value = fields[i + 1];
                String key = String.valueOf(fields[i]);
                if (value instanceof Boolean flag) row.addProperty(key, flag);
                else if (value instanceof Number number) row.addProperty(key, number);
                else row.addProperty(key, sanitize(String.valueOf(value)));
            }
            // The entire row is masked again in the writer, including secrets registered after enqueue.
            String line = JSON.toJson(row);
            WRITER.execute(() -> write(line));
        } catch (RuntimeException ignored) {
            // Diagnostics must never fail a conversation or change game behavior.
        }
    }

    public static void failure(String trace, String stage, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        event(trace, "error", "stage", stage, "type", cause.getClass().getSimpleName(), "message", cause.getMessage(),
                "frames", java.util.Arrays.stream(cause.getStackTrace()).filter(frame -> frame.getClassName().startsWith("dev.aicompanion"))
                        .limit(8).toList());
    }

    public static String sanitize(String text) {
        for (String secret : SECRETS) if (!secret.isEmpty()) text = text.replace(secret, "[REDACTED]");
        text = text.replaceAll("(?i)sk-or-v1-[a-z0-9_-]+", "[REDACTED]")
                .replaceAll("(?i)\\b(Bearer|Basic)\\s+[a-z0-9+/=_:.-]+", "$1 [REDACTED]");
        return text.length() <= 24000 ? text : text.substring(0, 24000) + " [truncated]";
    }

    private static void write(String line) {
        try {
            Path directory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
            Files.createDirectories(directory);
            Path latest = directory.resolve("companion-latest.jsonl");
            if (Files.exists(latest) && Files.size(latest) >= MAX_BYTES) {
                for (int i = 3; i >= 1; i--) {
                    Path source = i == 1 ? latest : directory.resolve("companion-" + (i - 1) + ".jsonl");
                    if (Files.exists(source)) Files.move(source, directory.resolve("companion-" + i + ".jsonl"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            long skipped = DROPPED.getAndSet(0);
            if (skipped > 0) Files.writeString(latest, "{\"event\":\"diagnostic_overflow\",\"dropped\":" + skipped + "}\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // Mask values before serialization above; only exact secret replacement here keeps valid JSON escaping.
            for (String secret : SECRETS) line = line.replace(JSON.toJson(secret).substring(1, JSON.toJson(secret).length() - 1), "[REDACTED]");
            Files.writeString(latest, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            ioWarning = false;
        } catch (Exception error) {
            if (!ioWarning) AICompanionMod.LOGGER.warn("AI Companion diagnostic file could not be written ({})", error.getClass().getSimpleName());
            ioWarning = true;
        }
    }

    /** Bounded drain when a server stops; keep the writer reusable for the next world. */
    public static void flush() {
        try { WRITER.submit(() -> { }).get(2, TimeUnit.SECONDS); }
        catch (Exception ignored) { }
    }
}
