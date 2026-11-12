package dev.aicompanion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Local bounded UTF-8 input. Call file operations off the client thread. */
public final class CompanionJokeBank {
    public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_JOKES = 10_000, MAX_WORDS = 150, MAX_CHARACTERS = 1500;
    public enum Theme { FOREST, MINE, VILLAGE, NETHER, END, TRAVEL, CREEPER, SURVIVAL }
    public record Joke(String text, Set<Theme> themes) {
        public Joke { themes = Set.copyOf(themes); }
    }
    public record Parsed(List<Joke> jokes, int invalid, int duplicates, int overflow) {
        public Parsed { jokes = List.copyOf(jokes); }
        public int skipped() { return invalid + duplicates + overflow; }
    }
    public record Bank(List<Joke> custom, Parsed parsed) { }
    private CompanionJokeBank() { }

    public static String normalize(String text) { return text.replaceAll("(?U)\\s+", " ").strip(); }
    public static boolean valid(String text) {
        return !text.isBlank() && text.length() <= MAX_CHARACTERS
                && text.split("(?U)\\s+").length <= MAX_WORDS
                && text.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == 0x00a7);
    }
    public static List<Joke> defaults() {
        try (var input = CompanionJokeBank.class.getResourceAsStream("/assets/aicompanion/jokes.txt")) {
            if (input == null) throw new IOException("Missing bundled joke bank");
            var jokes = new ArrayList<Joke>();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\\t", 2);
                String text = normalize(parts[1]);
                if (!valid(text)) throw new IOException("Invalid bundled joke");
                jokes.add(new Joke(text, Set.of(Theme.valueOf(parts[0]))));
            }
            return List.copyOf(jokes);
        } catch (IOException | RuntimeException error) { throw new IllegalStateException("Cannot load bundled joke bank", error); }
    }
    public static Bank load(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        catch (NoSuchFileException missing) { var empty = parse(""); return new Bank(empty.jokes(), empty); }
        if (bytes.length > MAX_BYTES) throw new IOException("Файл больше 2 МиБ");
        var parsed = parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        return new Bank(parsed.jokes(), parsed);
    }
    public static Parsed parse(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\\R", -1);
        boolean blocks = java.util.Arrays.stream(lines).anyMatch(line -> line.strip().equals("# format: blocks"));
        var candidates = new ArrayList<String>();
        var block = new StringBuilder();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("#")) continue;
            if (!blocks) { if (!line.isEmpty()) candidates.add(raw); }
            else if (line.equals("---")) { candidates.add(block.toString()); block.setLength(0); }
            else block.append(raw).append('\n');
        }
        if (blocks && !block.toString().isBlank()) candidates.add(block.toString());
        var unique = new LinkedHashMap<String, Joke>();
        int invalid = 0, duplicates = 0, overflow = 0;
        for (String raw : candidates) {
            if (raw.isBlank()) continue;
            // Newlines/tabs are layout; other controls must not disappear through normalization.
            if (raw.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) { invalid++; continue; }
            String normalized = normalize(raw);
            if (!valid(normalized)) invalid++;
            else if (unique.containsKey(normalized)) duplicates++;
            else if (unique.size() >= MAX_JOKES) overflow++;
            else unique.put(normalized, new Joke(normalized, Set.of()));
        }
        return new Parsed(List.copyOf(unique.values()), invalid, duplicates, overflow);
    }
    public static List<Joke> combine(List<Joke> bundled, List<Joke> custom) {
        var unique = new LinkedHashMap<String, Joke>();
        for (Joke joke : bundled) unique.put(normalize(joke.text()), joke);
        for (Joke joke : custom) unique.putIfAbsent(normalize(joke.text()), joke);
        return List.copyOf(unique.values());
    }
    public static boolean hasRussian(String text) {
        return text != null && text.codePoints().anyMatch(c -> c >= 'а' && c <= 'я' || c >= 'А' && c <= 'Я' || c == 'ё' || c == 'Ё');
    }
    public static boolean allowBundled(String game, String system, boolean russianPlayer) {
        return russianPlayer || russianLocale(game) || russianLocale(system);
    }
    private static boolean russianLocale(String language) {
        String code = language.toLowerCase(java.util.Locale.ROOT);
        return code.equals("ru") || code.startsWith("ru_") || code.startsWith("ru-");
    }
    public static void createTemplate(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) return;
        try {
            Files.writeString(file, """
                    # format: blocks
                    # Свои анекдоты: UTF-8, до 150 слов и 1500 знаков на историю.
                    # Между историями ставьте отдельную строку ---.
                    # После сохранения нажмите «Перезагрузить» в настройках анекдотов.
                    # Пример (уберите # перед текстом):
                    # Стал шахтёр считать алмазы. До утра досчитал до трёх.
                    # Остальное время выяснял, почему кирка считает иначе.
                    # ---
                    # Для старого формата «одна строка — один анекдот» удалите первую строку.
                    """, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException raced) { /* Never overwrite the owner's edits. */ }
    }
}
