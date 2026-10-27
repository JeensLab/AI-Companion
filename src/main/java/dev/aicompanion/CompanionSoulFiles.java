package dev.aicompanion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * mods/oleh_ai_soul on the player's computer: soul.json is Oleg's memory (CompanionSoul), dialogues.jsonl every line said
 * between them. Local only, never published; written on a thread of its own, never on the game tick. Secrets are masked first.
 */
public final class CompanionSoulFiles {
    public static final String FOLDER = "mods/oleh_ai_soul";
    private static final long MAX_LOG = 4L * 1024 * 1024;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson LINE = new GsonBuilder().disableHtmlEscaping().create();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AI Companion soul");
        thread.setDaemon(true);
        return thread;
    });
    private static CompanionSoul soul;
    private static boolean ioWarning;

    private CompanionSoulFiles() { }

    public static Path folder() { return FMLPaths.GAMEDIR.get().resolve("mods").resolve("oleh_ai_soul"); }

    public static synchronized CompanionSoul soul() {
        if (soul == null) soul = read(folder().resolve("soul.json"));
        return soul;
    }

    /** A file that cannot be read is kept aside as soul.broken.json instead of being overwritten by an empty memory. */
    public static CompanionSoul read(Path file) {
        try {
            return CompanionSoul.load(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (NoSuchFileException absent) {
            return new CompanionSoul();
        } catch (Exception unreadable) {
            AICompanionMod.LOGGER.warn("Oleg's memory {} could not be read ({}); kept aside, starting anew", file, unreadable.getClass().getSimpleName());
            try { Files.move(file, file.resolveSibling("soul.broken.json"), StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception ignored) { }
            return new CompanionSoul();
        }
    }

    /** The player's or his line: into his memory of the current talk and into the dialogue log. */
    public static void heard(String text) { line("player", text, true); }
    public static void said(String text) { line("oleg", text, false); }
    private static void line(String who, String text, boolean player) {
        if (text == null || text.isBlank()) return;
        String clean = CompanionDiagnostics.sanitize(text.strip());
        long now = System.currentTimeMillis();
        if (player) soul().heard(clean, now); else soul().said(clean, now);
        JsonObject row = new JsonObject();
        row.addProperty("time", Instant.ofEpochMilli(now).toString()); row.addProperty("who", who); row.addProperty("text", clean);
        String json = LINE.toJson(row);
        WRITER.execute(() -> append(json));
    }

    /** Writes soul.json if anything changed since the last call. */
    public static void saveIfChanged() {
        CompanionSoul current = soul();
        if (!current.changed()) return;
        String json = PRETTY.toJson(current.save());
        WRITER.execute(() -> write(folder().resolve("soul.json"), json));
    }

    public static void write(Path file, String json) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            ioWarning = false;
        } catch (Exception error) {
            warn(error);
        }
    }

    private static void append(String json) {
        try {
            Path folder = folder();
            Files.createDirectories(folder);
            Path log = folder.resolve("dialogues.jsonl");
            if (Files.exists(log) && Files.size(log) >= MAX_LOG) Files.move(log, folder.resolve("dialogues-1.jsonl"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(log, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            ioWarning = false;
        } catch (Exception error) {
            warn(error);
        }
    }

    private static void warn(Exception error) {
        if (!ioWarning) AICompanionMod.LOGGER.warn("Oleg's memory in {} could not be written ({})", FOLDER, error.getClass().getSimpleName());
        ioWarning = true;
    }
}
