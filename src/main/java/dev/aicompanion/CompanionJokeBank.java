package dev.aicompanion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/** Editable UTF-8 bank; file I/O runs outside the client tick and never fetches network content. */
public final class CompanionJokeBank {
    public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_JOKES = 10_000;

    private CompanionJokeBank() { }

    public static List<String> defaults() {
        try (var input = CompanionJokeBank.class.getResourceAsStream("/assets/aicompanion/jokes.txt")) {
            if (input == null) throw new IOException("Missing bundled joke bank");
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load bundled joke bank", error);
        }
    }

    public record Bank(List<String> lines, boolean customFile) { }

    public static Bank load(Path file, boolean russian) {
        var base = russian ? CompanionJokes.ALL : List.<String>of();
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("Joke bank exceeds two MiB");
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            var combined = new LinkedHashSet<>(base);
            combined.addAll(parse(text));
            var bank = List.copyOf(combined);
            AICompanionMod.LOGGER.info("Loaded {} local jokes", bank.size());
            return new Bank(bank, true);
        } catch (NoSuchFileException missing) {
            return new Bank(base, false);
        } catch (IOException | SecurityException error) {
            AICompanionMod.LOGGER.warn("Cannot read custom joke bank; keeping language-eligible bundled jokes", error);
            return new Bank(base, true);
        }
    }

    public static boolean hasRussian(String text) {
        // A local script hint, not a language-model request; shared Cyrillic words can be ambiguous.
        return text != null && text.codePoints().anyMatch(c -> c >= 'а' && c <= 'я' || c >= 'А' && c <= 'Я' || c == 'ё' || c == 'Ё');
    }

    public static boolean allowBundled(String gameLanguage, String systemLanguage, boolean russianPlayer) {
        return russianPlayer || russianLocale(gameLanguage) || russianLocale(systemLanguage);
    }

    private static boolean russianLocale(String language) {
        String code = language.toLowerCase(java.util.Locale.ROOT);
        return code.equals("ru") || code.startsWith("ru_") || code.startsWith("ru-");
    }

    public static List<String> parse(String text) {
        var lines = new LinkedHashSet<String>();
        // A BOM is common in text files exported by editors on Windows.
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (!line.startsWith("#") && CompanionConversation.fitsMurmur(line)
                    && line.length() <= 500 && line.codePoints().noneMatch(Character::isISOControl)) lines.add(line);
            if (lines.size() == MAX_JOKES) break;
        }
        return List.copyOf(lines);
    }
}
