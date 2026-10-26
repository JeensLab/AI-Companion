package dev.aicompanion.schematic;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** The folder where the player drops buildings for Oleg: mods/oleh_ai_schemas, read on demand, never written. */
public final class CompanionSchematics {
    public static final String FOLDER = "mods/oleh_ai_schemas";
    public static final List<String> EXTENSIONS = List.of(".litematic", ".schem", ".schematic", ".nbt");
    private static final int CACHED = 8;
    private record Key(String file, long modified, long size) { }
    private static final Map<Key, CompanionSchematic> cache = new LinkedHashMap<>(16, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, CompanionSchematic> eldest) { return size() > CACHED; }
    };

    private CompanionSchematics() { }

    public static Path folder() { return FMLPaths.GAMEDIR.get().resolve("mods").resolve("oleh_ai_schemas"); }

    /** Schematic files in the folder, by name. The folder is created on the first look, so the player sees where to put them. */
    public static List<String> list() {
        Path folder = folder();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> files = Files.list(folder)) {
                return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).filter(CompanionSchematics::supported)
                        .sorted(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))).toList();
            }
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    public static boolean supported(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static String noneFound() {
        return "Схем в папке " + FOLDER + " не найдено. Положи туда файл .litematic, .schem, .schematic или .nbt и попроси ещё раз.";
    }

    /** The file the player named («построй замок» → zamok.litematic, Castle.schem), or null if no name fits. */
    public static String match(List<String> files, String hint) {
        if (hint == null || hint.isBlank()) return null;
        String wanted = normalize(CompanionSchematic.title(hint.trim()));
        for (String file : files) if (normalize(CompanionSchematic.title(file)).equals(wanted)) return file;
        for (String file : files) if (normalize(file).contains(wanted) || wanted.contains(normalize(CompanionSchematic.title(file)))) return file;
        return null;
    }
    private static String normalize(String name) { return name.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[\\s_\\-.]+", ""); }

    /** The building in that file, parsed once per version of the file. Names with paths are refused: only this folder is read. */
    public static CompanionSchematic load(String file) throws IOException {
        if (file == null || file.isBlank() || file.contains("/") || file.contains("\\") || file.contains("..") || !supported(file))
            throw new IOException("«" + file + "» — не файл схемы из папки " + FOLDER);
        Path path = folder().resolve(file);
        if (!Files.isRegularFile(path)) throw new IOException("файла «" + file + "» больше нет в папке " + FOLDER);
        Key key = new Key(file, Files.getLastModifiedTime(path).toMillis(), Files.size(path));
        synchronized (cache) {
            CompanionSchematic known = cache.get(key);
            if (known != null) return known;
        }
        CompanionSchematic loaded;
        try { loaded = CompanionSchematicFormats.read(path); }
        catch (IOException | RuntimeException broken) {
            throw new IOException("не смог прочитать «" + file + "»: " + (broken.getMessage() == null ? broken.getClass().getSimpleName() : broken.getMessage()), broken);
        }
        synchronized (cache) { cache.put(key, loaded); }
        return loaded;
    }
}
