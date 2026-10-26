package dev.aicompanion.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;
import java.util.Locale;

/**
 * A building read from a file in mods/oleh_ai_schemas: a box of block states. A cell without a state (structure void, a block
 * of a mod that is not installed) keeps whatever the world has there; explicit air is a cell that has to be cleared.
 */
public final class CompanionSchematic {
    private final String file;
    private final int sizeX, sizeY, sizeZ;
    private final List<BlockState> palette;
    /** Index into the palette for every cell, -1 for «not part of the building». */
    private final int[] cells;
    private final int blocks;
    private final int unknown;

    CompanionSchematic(String file, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] cells, int unknown) {
        this.file = file; this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette); this.cells = cells; this.unknown = unknown;
        int solid = 0;
        for (int cell : cells) if (cell >= 0 && !this.palette.get(cell).isAir()) solid++;
        blocks = solid;
    }

    public String file() { return file; }
    /** The file name without its extension, as the player sees it. */
    public String title() { return title(file); }
    public static String title(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    /** Blocks other than air: what has to be built. */
    public int blocks() { return blocks; }
    /** States of blocks this game does not know (mods that are not installed); those cells are left as they are. */
    public int unknown() { return unknown; }

    /** The state at a cell of the file, or null when the building says nothing about it. */
    public BlockState get(int x, int y, int z) {
        int cell = cells[(y * sizeZ + z) * sizeX + x];
        return cell < 0 ? null : palette.get(cell);
    }

    public interface CellVisitor { void visit(int x, int y, int z, BlockState state); }
    /** Every cell with a state, bottom layer first. */
    public void forEach(CellVisitor visitor) {
        int index = 0;
        for (int y = 0; y < sizeY; y++) for (int z = 0; z < sizeZ; z++) for (int x = 0; x < sizeX; x++, index++) {
            int cell = cells[index];
            if (cell >= 0) visitor.visit(x, y, z, palette.get(cell));
        }
    }

    public String describe() {
        return title() + " (" + sizeX + "×" + sizeY + "×" + sizeZ + ", блоков: " + blocks + ")";
    }

    /** Where and how the building stands in the world: the file cell 0,0,0 goes to origin, then mirror and rotation around it. */
    public record Placement(String file, BlockPos origin, Rotation rotation, Mirror mirror) {
        public Placement { origin = origin.immutable(); }
        public BlockPos world(int x, int y, int z) {
            return origin.offset(StructureTemplate.transform(new BlockPos(x, y, z), mirror, rotation, BlockPos.ZERO));
        }
        public BlockState state(BlockState state) { return state.mirror(mirror).rotate(rotation); }
        public BoundingBox box(CompanionSchematic schematic) {
            BlockPos a = world(0, 0, 0), b = world(schematic.sizeX() - 1, schematic.sizeY() - 1, schematic.sizeZ() - 1);
            return BoundingBox.fromCorners(a, b);
        }
        public Placement moved(BlockPos to) { return new Placement(file, to, rotation, mirror); }
        public String rotationName() {
            return switch (rotation) { case NONE -> "0°"; case CLOCKWISE_90 -> "90°"; case CLOCKWISE_180 -> "180°"; case COUNTERCLOCKWISE_90 -> "270°"; };
        }
        public String mirrorName() {
            return switch (mirror) { case NONE -> "без отражения"; case LEFT_RIGHT -> "отражено слева направо"; case FRONT_BACK -> "отражено спереди назад"; };
        }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("File", file);
            tag.putLong("Origin", origin.asLong());
            tag.putString("Rotation", rotation.name());
            tag.putString("Mirror", mirror.name());
            return tag;
        }
        public static Placement load(CompoundTag tag) {
            if (!tag.contains("File") || !tag.contains("Origin")) return null;
            try {
                return new Placement(tag.getString("File"), BlockPos.of(tag.getLong("Origin")),
                        Rotation.valueOf(tag.getString("Rotation").toUpperCase(Locale.ROOT)), Mirror.valueOf(tag.getString("Mirror").toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) { return null; }
        }
    }
}
