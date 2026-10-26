package dev.aicompanion.schematic;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The file formats people already have buildings in: Litematica (.litematic), WorldEdit/Sponge (.schem, v1–v3), the old
 * MCEdit/Schematica (.schematic, numeric ids) and vanilla structure blocks (.nbt). Older game versions are brought up to date
 * with the game's own data fixers, the way a world from an old version is.
 */
final class CompanionSchematicFormats {
    /** About a 100-block cube: a house, a tower, a small castle. Larger boxes are refused before memory is spent on them. */
    static final int MAX_VOLUME = 1_000_000;
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    /** Sponge v1 files carry no data version: they were written by 1.13.2. */
    private static final int SPONGE_V1_VERSION = 1631;
    /** The numeric ids of the old format were turned into names by 1.13. */
    private static final int FLATTENED_VERSION = 1519;

    private CompanionSchematicFormats() { }

    static CompanionSchematic read(Path path) throws IOException {
        String file = path.getFileName().toString();
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("файл больше 64 МБ");
        CompoundTag root = readNbt(Files.readAllBytes(path));
        String lower = file.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".litematic")) return litematic(file, root);
        if (lower.endsWith(".nbt")) return structure(file, root);
        CompoundTag sponge = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        if (sponge.contains("Palette", Tag.TAG_COMPOUND) || sponge.contains("Blocks", Tag.TAG_COMPOUND)) return sponge(file, sponge);
        if (root.contains("Blocks", Tag.TAG_BYTE_ARRAY)) return legacy(file, root);
        if (root.contains("Regions", Tag.TAG_COMPOUND)) return litematic(file, root);
        if (root.contains("palette", Tag.TAG_LIST) || root.contains("palettes", Tag.TAG_LIST)) return structure(file, root);
        throw new IOException("незнакомый формат схемы");
    }

    private static CompoundTag readNbt(byte[] bytes) throws IOException {
        NbtAccounter quota = NbtAccounter.create(256L * 1024 * 1024);
        boolean gzip = bytes.length > 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
        if (gzip) return NbtIo.readCompressed(new ByteArrayInputStream(bytes), quota);
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), quota);
    }

    // ---- Litematica ----

    private static CompanionSchematic litematic(String file, CompoundTag root) throws IOException {
        int dataVersion = root.contains("MinecraftDataVersion") ? root.getInt("MinecraftDataVersion") : SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        CompoundTag regions = root.getCompound("Regions");
        if (regions.isEmpty()) throw new IOException("в схеме нет ни одной области");
        record Region(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, CompoundTag tag) { }
        List<Region> parts = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : regions.getAllKeys()) {
            CompoundTag region = regions.getCompound(key);
            CompoundTag position = region.getCompound("Position"), size = region.getCompound("Size");
            int[] start = {position.getInt("x"), position.getInt("y"), position.getInt("z")};
            int[] extent = {size.getInt("x"), size.getInt("y"), size.getInt("z")};
            int[] low = new int[3];
            for (int axis = 0; axis < 3; axis++) {
                if (extent[axis] == 0) throw new IOException("область «" + key + "» пустая");
                // A negative size grows from the position towards smaller coordinates (Litematica's own rule).
                low[axis] = extent[axis] < 0 ? start[axis] + extent[axis] + 1 : start[axis];
                extent[axis] = Math.abs(extent[axis]);
            }
            Region part = new Region(low[0], low[1], low[2], extent[0], extent[1], extent[2], region);
            parts.add(part);
            minX = Math.min(minX, part.minX); minY = Math.min(minY, part.minY); minZ = Math.min(minZ, part.minZ);
            maxX = Math.max(maxX, part.minX + part.sizeX - 1); maxY = Math.max(maxY, part.minY + part.sizeY - 1); maxZ = Math.max(maxZ, part.minZ + part.sizeZ - 1);
        }
        Box box = Box.of(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        Palette palette = new Palette(dataVersion);
        for (Region part : parts) {
            ListTag states = part.tag.getList("BlockStatePalette", Tag.TAG_COMPOUND);
            int[] local = new int[states.size()];
            for (int index = 0; index < states.size(); index++) local[index] = palette.add(palette.fromTag(states.getCompound(index)));
            long[] packed = part.tag.getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.size() - 1)));
            long mask = (1L << bits) - 1L;
            long volume = (long) part.sizeX * part.sizeY * part.sizeZ;
            if (packed.length * 64L < volume * bits) throw new IOException("область повреждена: данных меньше, чем блоков");
            for (long index = 0; index < volume; index++) {
                // Entries may span two longs (LitematicaBitArray), unlike the chunk storage of the game.
                long start = index * bits;
                int first = (int) (start >> 6), last = (int) ((start + bits - 1) >> 6), offset = (int) (start & 63);
                long value = first == last ? packed[first] >>> offset & mask : (packed[first] >>> offset | packed[last] << (64 - offset)) & mask;
                if (value >= local.length) continue;
                int x = (int) (index % part.sizeX), z = (int) (index / part.sizeX % part.sizeZ), y = (int) (index / ((long) part.sizeX * part.sizeZ));
                box.set(part.minX - minX + x, part.minY - minY + y, part.minZ - minZ + z, local[(int) value]);
            }
        }
        return palette.build(file, box);
    }

    // ---- WorldEdit / Sponge ----

    private static CompanionSchematic sponge(String file, CompoundTag tag) throws IOException {
        int version = tag.getInt("Version");
        int dataVersion = tag.contains("DataVersion") ? tag.getInt("DataVersion") : SPONGE_V1_VERSION;
        Box box = Box.of(tag.getShort("Width") & 0xFFFF, tag.getShort("Height") & 0xFFFF, tag.getShort("Length") & 0xFFFF);
        CompoundTag blocks = version >= 3 || tag.contains("Blocks", Tag.TAG_COMPOUND) ? tag.getCompound("Blocks") : tag;
        CompoundTag paletteTag = blocks.getCompound("Palette");
        byte[] data = blocks.contains("Data", Tag.TAG_BYTE_ARRAY) ? blocks.getByteArray("Data") : blocks.getByteArray("BlockData");
        Palette palette = new Palette(dataVersion);
        Map<Integer, Integer> byId = new HashMap<>();
        for (String key : paletteTag.getAllKeys()) byId.put(paletteTag.getInt(key), palette.add(palette.fromString(key)));
        int index = 0, cursor = 0;
        int volume = box.volume();
        while (cursor < data.length && index < volume) {
            int value = 0, shift = 0;
            byte next;
            do {
                if (cursor >= data.length) throw new IOException("данные блоков обрываются");
                next = data[cursor++];
                value |= (next & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("данные блоков повреждены");
            } while ((next & 0x80) != 0);
            // Sponge order is x, then z, then y: the same as the box.
            Integer cell = byId.get(value);
            box.cells[index++] = cell == null ? -1 : cell;
        }
        return palette.build(file, box);
    }

    // ---- MCEdit / Schematica ----

    private static CompanionSchematic legacy(String file, CompoundTag tag) throws IOException {
        if (tag.contains("Materials") && !"Alpha".equals(tag.getString("Materials"))) throw new IOException("схема из Pocket Edition не поддерживается");
        Box box = Box.of(tag.getShort("Width") & 0xFFFF, tag.getShort("Height") & 0xFFFF, tag.getShort("Length") & 0xFFFF);
        byte[] ids = tag.getByteArray("Blocks"), meta = tag.getByteArray("Data"), add = tag.getByteArray("AddBlocks");
        if (ids.length < box.volume()) throw new IOException("данные блоков обрываются");
        Palette palette = new Palette(FLATTENED_VERSION);
        Map<Integer, Integer> byLegacy = new HashMap<>();
        for (int index = 0; index < box.volume(); index++) {
            int id = ids[index] & 0xFF;
            // The extra high bits of ids above 255, packed two to a byte (WorldEdit's reading of the format).
            if ((index >> 1) < add.length) id |= ((index & 1) == 0 ? (add[index >> 1] & 0x0F) << 8 : (add[index >> 1] & 0xF0) << 4);
            int data = index < meta.length ? meta[index] & 0x0F : 0;
            int legacy = id << 4 | data;
            Integer cell = byLegacy.get(legacy);
            if (cell == null) {
                Dynamic<?> found = BlockStateData.getTag(legacy);
                // An id the old game did not have (a mod's block) comes back as air: that cell is unknown, not empty.
                boolean unknown = id != 0 && "minecraft:air".equals(found.get("Name").asString(""));
                cell = palette.add(!unknown && found.getValue() instanceof CompoundTag compound ? palette.fromTag(compound) : null);
                byLegacy.put(legacy, cell);
            }
            box.cells[index] = cell;
        }
        return palette.build(file, box);
    }

    // ---- Vanilla structure block ----

    private static CompanionSchematic structure(String file, CompoundTag tag) throws IOException {
        int dataVersion = NbtUtils.getDataVersion(tag, 500);
        tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag, dataVersion);
        ListTag size = tag.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IOException("в схеме нет размера");
        Box box = Box.of(size.getInt(0), size.getInt(1), size.getInt(2));
        ListTag states = tag.contains("palettes", Tag.TAG_LIST) ? tag.getList("palettes", Tag.TAG_LIST).getList(0) : tag.getList("palette", Tag.TAG_COMPOUND);
        // Already fixed to the current version together with the whole structure.
        Palette palette = new Palette(SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        int[] local = new int[states.size()];
        for (int index = 0; index < states.size(); index++) local[index] = palette.add(palette.fromTag(states.getCompound(index)));
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int state = block.getInt("state");
            if (pos.size() != 3 || state < 0 || state >= local.length) continue;
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            if (x < 0 || y < 0 || z < 0 || x >= box.sizeX || y >= box.sizeY || z >= box.sizeZ) continue;
            box.set(x, y, z, local[state]);
        }
        return palette.build(file, box);
    }

    // ---- Shared ----

    private static final class Box {
        final int sizeX, sizeY, sizeZ;
        final int[] cells;
        private Box(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
            cells = new int[sizeX * sizeY * sizeZ];
            Arrays.fill(cells, -1);
        }
        static Box of(int sizeX, int sizeY, int sizeZ) throws IOException {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) throw new IOException("у схемы нулевой размер");
            if ((long) sizeX * sizeY * sizeZ > MAX_VOLUME) throw new IOException("схема слишком большая: " + sizeX + "×" + sizeY + "×" + sizeZ
                    + " (можно до " + MAX_VOLUME + " клеток, это примерно куб 100×100×100)");
            return new Box(sizeX, sizeY, sizeZ);
        }
        int volume() { return cells.length; }
        void set(int x, int y, int z, int cell) { cells[(y * sizeZ + z) * sizeX + x] = cell; }
    }

    /** States of the file turned into states of this game; null (unknown block, structure void) means «leave the cell as it is». */
    private static final class Palette {
        private final int dataVersion;
        private final int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        private final List<BlockState> states = new ArrayList<>();
        private final Map<BlockState, Integer> index = new HashMap<>();
        private int unknown;
        Palette(int dataVersion) { this.dataVersion = dataVersion; }

        /** -1 for a state that is not part of the building. */
        int add(BlockState state) {
            if (state == null) { unknown++; return -1; }
            if (state.is(Blocks.STRUCTURE_VOID)) return -1;
            return index.computeIfAbsent(state, added -> { states.add(added); return states.size() - 1; });
        }

        BlockState fromTag(CompoundTag tag) {
            if (tag == null) return null;
            CompoundTag fixed = tag;
            if (dataVersion < current) {
                try { fixed = (CompoundTag) DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, (Tag) tag.copy()), dataVersion, current).getValue(); }
                catch (RuntimeException failed) { fixed = tag; }
            }
            ResourceLocation id = ResourceLocation.tryParse(fixed.getString("Name"));
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), fixed);
        }

        BlockState fromString(String flat) {
            String fixed = flat;
            if (dataVersion < current) {
                try { fixed = DataFixers.getDataFixer().update(References.FLAT_BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, (Tag) StringTag.valueOf(flat)), dataVersion, current).getValue().getAsString(); }
                catch (RuntimeException failed) { fixed = flat; }
            }
            // Block entity data written after the state («chest[facing=north]{Items:[]}») is not part of the state.
            int nbt = fixed.indexOf('{');
            if (nbt >= 0) fixed = fixed.substring(0, nbt);
            try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), fixed, false).blockState(); }
            catch (CommandSyntaxException unknownBlock) { return null; }
        }

        CompanionSchematic build(String file, Box box) throws IOException {
            boolean any = false;
            for (int cell : box.cells) if (cell >= 0 && !states.get(cell).isAir()) { any = true; break; }
            if (!any) throw new IOException("в схеме нет ни одного блока" + (unknown > 0 ? " этой версии игры" : ""));
            return new CompanionSchematic(file, box.sizeX, box.sizeY, box.sizeZ, states, box.cells, unknown);
        }
    }
}
