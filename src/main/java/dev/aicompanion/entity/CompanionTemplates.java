package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads a building from the game's structure templates (no file of ours) as the cells to build, without air, voids, jigsaws and entities. */
final class CompanionTemplates {
    /** A block to place, relative to the building's corner; {@code stage} orders the work from the floor up. */
    record Cell(BlockPos rel, BlockState state, int stage) { }
    record Loaded(int sizeX, int sizeY, int sizeZ, List<Cell> cells) { }

    /** Doors and beds come last, like a player's: a door closes the way in, a bed stands on a floor he still has to walk. */
    private static final int BEDS = 1000, DOORS = 1001, LATE = 500;

    private CompanionTemplates() { }

    /** The template of the building, or null when the game has none by that id. */
    static Loaded load(ServerLevel level, CompanionVillageBuildings.Building building) {
        var template = level.getServer().getStructureManager().get(building.template()).orElse(null);
        if (template == null) return null;
        // The template's saved form is the public view of its palette and blocks. Block entities (the «nbt» of a block) are never read: they stand empty.
        CompoundTag tag = template.save(new CompoundTag());
        ListTag size = tag.getList("size", Tag.TAG_INT);
        List<BlockState> palette = new ArrayList<>();
        for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), (CompoundTag) entry));
        Map<BlockPos, BlockState> solid = new HashMap<>();
        for (Tag entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            BlockState state = palette.get(block.getInt("state"));
            ListTag at = block.getList("pos", Tag.TAG_INT);
            if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID) && !state.is(Blocks.JIGSAW)) solid.put(new BlockPos(at.getInt(0), at.getInt(1), at.getInt(2)), state);
        }
        // What overhangs (eaves), and what stands on it, comes after the layers beside it: put up first it hides their faces from the ground
        // (the 5×5 house's roof is ordered the same way by hand).
        Set<BlockPos> late = new HashSet<>();
        List<BlockPos> bottomUp = solid.keySet().stream().sorted(Comparator.comparingInt(BlockPos::getY)).toList();
        for (BlockPos rel : bottomUp) if (rel.getY() > 0 && (!solid.containsKey(rel.below()) || late.contains(rel.below()))) late.add(rel);
        List<Cell> cells = new ArrayList<>();
        for (BlockPos rel : bottomUp) if (!secondHalf(solid.get(rel))) cells.add(new Cell(rel, solid.get(rel), stage(solid.get(rel), rel, late.contains(rel))));
        return new Loaded(size.getInt(0), size.getInt(1), size.getInt(2), List.copyOf(cells));
    }

    /** The upper half of a door and the head of a bed are put down with the lower half and the foot. */
    private static boolean secondHalf(BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                || state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    /** A layer at a time; in a layer what hangs on a wall (a torch, a ladder) after the wall, so it finds its wall. */
    private static int stage(BlockState state, BlockPos rel, boolean late) {
        if (state.getBlock() instanceof DoorBlock) return DOORS;
        if (state.getBlock() instanceof BedBlock) return BEDS;
        boolean hangs = state.getBlock() instanceof WallTorchBlock || state.getBlock() instanceof LadderBlock || state.getBlock() instanceof WallSignBlock;
        return (late ? LATE : 0) + rel.getY() * 2 + (hangs ? 1 : 0);
    }
}
