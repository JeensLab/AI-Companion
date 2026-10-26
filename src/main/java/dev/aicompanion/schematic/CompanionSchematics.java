package dev.aicompanion.schematic;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
    /** Built into the mod, not a file: what is built when the folder has no schematic at all. */
    public static final String DEFAULT_HOUSE = "стандартный дом 5×5";
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
        return "Схем в папке " + FOLDER + " нет — построю стандартный дом 5×5. Свои схемы (.litematic, .schem, .schematic, .nbt) клади в эту папку.";
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
        if (DEFAULT_HOUSE.equals(file)) return DefaultHouse.HOUSE;
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

    /**
     * The house of the mod's first versions: a plank floor 5×5, walls three high with a door gap and three windows, a slab roof
     * with a one-block overhang, a door and a step on the north side. Oak; other wood is used when there is no oak around.
     */
    private static final class DefaultHouse {
        static final CompanionSchematic HOUSE = build();
        private static CompanionSchematic build() {
            List<BlockState> palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(),
                    Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH),
                    Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH),
                    Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            int size = 7, height = 5;
            int[] cells = new int[size * height * size];
            java.util.Arrays.fill(cells, -1);
            for (int y = 0; y < height; y++) for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                // The house proper is x and z 1..5; row 0 and 6 hold only the overhang of the roof and the step.
                boolean inside = x >= 1 && x <= 5 && z >= 1 && z <= 5;
                boolean wall = inside && (x == 1 || x == 5 || z == 1 || z == 5);
                int cell = -1;
                if (y == 0) cell = inside ? 1 : x == 3 && z == 0 ? 3 : -1;
                else if (y == 4) cell = 2;
                else if (inside) {
                    if (!wall) cell = 0;
                    else if (x == 3 && z == 1 && y < 3) cell = y == 1 ? 4 : 5;
                    else if (y == 2 && (z == 5 && x == 3 || z == 3 && (x == 1 || x == 5))) cell = 0;
                    else cell = 1;
                }
                cells[(y * size + z) * size + x] = cell;
            }
            return new CompanionSchematic(DEFAULT_HOUSE, size, height, size, palette, cells, 0);
        }
    }
}
