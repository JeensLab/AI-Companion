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
    private static boolean ioWarning, keepFile;

    private CompanionSoulFiles() { }

    public static Path folder() { return FMLPaths.GAMEDIR.get().resolve("mods").resolve("oleh_ai_soul"); }

    public static synchronized CompanionSoul soul() {
        if (soul == null) {
            soul = read(folder().resolve("soul.json"));
            // His memory is there but out of reach: this session he remembers in the game only and never writes over it.
            if (soul == null) { soul = new CompanionSoul(); keepFile = true; }
        }
        return soul;
    }

    /**
     * A file that is no memory (not a JSON object) is kept aside as soul.broken.json, and he starts anew. Null for a file that
     * cannot be read just now (held by an antivirus or a sync client) or moved aside: it must not be overwritten by an empty memory.
     */
    public static CompanionSoul read(Path file) {
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            return new CompanionSoul();
        } catch (Exception unreadable) {
            AICompanionMod.LOGGER.warn("Oleg's memory {} could not be read ({}); left as it is", file, unreadable.getClass().getSimpleName());
            return null;
        }
        try {
            return CompanionSoul.load(JsonParser.parseString(text).getAsJsonObject());
        } catch (RuntimeException broken) {
            AICompanionMod.LOGGER.warn("Oleg's memory {} is broken ({}); kept aside, starting anew", file, broken.getClass().getSimpleName());
            try { Files.move(file, file.resolveSibling("soul.broken.json"), StandardCopyOption.REPLACE_EXISTING); return new CompanionSoul(); }
            catch (Exception kept) { return null; }
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
        if (!current.changed() || keepFile) return;
        String json = PRETTY.toJson(current.save());
        WRITER.execute(() -> write(folder().resolve("soul.json"), json));
    }

    public static void write(Path file, String json) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            // getBytes, not writeString: a lone surrogate from a model's answer becomes «?» instead of failing every save.
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
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
            Files.write(log, (json + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
