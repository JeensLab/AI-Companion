package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * What lava does, as a player sees it: a lake, a hardened flow or a still fall stands (it has no fluid tick scheduled) and is
 * walked along like a wall; lava that is running now, fire and magma are danger. One judgement for walking, for his things after a
 * death and for mining, where lava that would run into an opened cell is plugged with a block from his bag. No state: it asks the world.
 */
final class CompanionLava {
    /** The game's malus of a cell by fire or lava (8) makes a step 5 times as dear; by lava that stands, a step costs 1.5 times. */
    static final float SETTLED_MALUS = 1;

    /** What he plugs lava with, cheapest first. Never sand or gravel (they fall through lava), never tools or valuables. */
    private static final List<Item> PLUGS = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.NETHERRACK,
            Items.STONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF);

    /** Why an ore may not be opened now: its drop would fall into lava, lava would run in and there is nothing to plug it with, or a lava fall stands beside it. */
    enum Opening { SAFE, OVER_LAVA, NO_PLUG, FALL }

    private CompanionLava() {}

    /** Lava in this cell that is changing now: a fluid tick is scheduled for it (a fresh pour, a flow that goes on, lava let out of a wall). */
    static boolean spreading(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.LAVA) && level.getFluidTicks().hasScheduledTick(pos, fluid.getType());
    }

    /** Lava (source or flow) in this cell: the one liquid he keeps away from at work; water he swims in. */
    static boolean at(Level level, BlockPos pos) { return level.getFluidState(pos).is(FluidTags.LAVA); }

    static boolean settled(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA) && !spreading(level, pos);
    }

    /**
     * The lava cells that run into this cell when it is opened: the one above, and of the four beside it those that spread sideways.
     * A flow with lava or nothing under it falls and does not spread (the game's FlowingFluid.spread, isWaterHole): a fall past the
     * cell is no inflow, and a plug in it would give the fall above a floor to spill from.
     */
    static List<BlockPos> inflow(Level level, BlockPos opened) { return inflow(level, opened, FluidTags.LAVA); }

    /**
     * The water cells that run into this cell when it is opened, by the same rule as lava, without the cells of his own way (water that
     * lies in his tunnel or where he stands is open for him to walk, not an inflow to plug).
     */
    static List<BlockPos> waterInflow(Level level, BlockPos opened, Collection<BlockPos> own) {
        List<BlockPos> cells = inflow(level, opened, FluidTags.WATER);
        cells.removeAll(own);
        return cells;
    }

    private static List<BlockPos> inflow(Level level, BlockPos opened, TagKey<Fluid> liquid) {
        List<BlockPos> cells = new ArrayList<>(5);
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            BlockPos next = opened.relative(d);
            if (level.isLoaded(next) && level.getFluidState(next).is(liquid) && (d == Direction.UP || spreadsSideways(level, next, liquid))) cells.add(next);
        }
        return cells;
    }

    /** A source, or a flow standing on something solid. */
    private static boolean spreadsSideways(Level level, BlockPos fluid, TagKey<Fluid> liquid) {
        if (level.getFluidState(fluid).isSource()) return true;
        BlockPos under = fluid.below();
        return level.isLoaded(under) && !level.getFluidState(under).is(liquid) && !level.getBlockState(under).canBeReplaced();
    }

    /**
     * A lava fall stands at the foot of this cell: lava that runs in is flowing lava with flowing lava over it. A plug there only
     * lifts the spill by a block (the fall above gets a floor and spreads into the opened cell), so such a cell is not opened at
     * all. A source over it (a lake two layers deep) is not a fall: its plug holds.
     */
    static boolean besideFall(Level level, Collection<BlockPos> opened) {
        for (BlockPos cell : opened)
            for (BlockPos lava : inflow(level, cell)) {
                BlockPos above = lava.above();
                if (!level.getFluidState(lava).isSource() && level.isLoaded(above) && at(level, above) && !level.getFluidState(above).isSource()) return true;
            }
        return false;
    }

    /** The lava cells that run in when all these cells are opened (a cell next to two of them is counted once). */
    static Set<BlockPos> inflow(Level level, Collection<BlockPos> opened) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (BlockPos cell : opened) cells.addAll(inflow(level, cell));
        return cells;
    }

    /** Water in a cell touching this one: an ore that lies in a flooded vein. */
    static boolean wetBeside(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) if (level.isLoaded(pos.relative(d)) && level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) return true;
        return false;
    }

    /** Plug blocks in his bag. */
    static int plugsCarried(CompanionEntity owner) {
        int count = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (plugRank(owner, slot) >= 0) count += owner.getItem(slot).getCount();
        return count;
    }

    /** Whether he may break this ore: not on lava, and with enough plugs for the lava that runs in once it is gone. */
    static Opening opening(CompanionEntity owner, Level level, BlockPos ore) {
        if (at(level, ore.below())) return Opening.OVER_LAVA;
        if (besideFall(level, List.of(ore))) return Opening.FALL;
        return inflow(level, ore).size() <= plugsCarried(owner) ? Opening.SAFE : Opening.NO_PLUG;
    }

    /** The rank of the plug in this slot (0 is the cheapest), or -1 if what lies there is no plug or is carried for the player. */
    private static int plugRank(CompanionEntity owner, int slot) {
        ItemStack stack = owner.getItem(slot);
        return owner.carriedForPlayer(stack) ? -1 : PLUGS.indexOf(stack.getItem());
    }

    /**
     * Plugs each of these lava or water cells with a block from his bag, the cheapest first, like a player who puts a cobblestone into the
     * flow: a real item used up, the swing, the sound and the game's placement event. Called in the tick the cell is broken, before
     * lava moves (30 ticks later in the Overworld, 10 in the Nether).
     */
    static void plug(CompanionEntity owner, ServerLevel level, Collection<BlockPos> lava) {
        for (BlockPos cell : lava) {
            int best = -1;
            for (int slot = 0; slot < owner.getContainerSize(); slot++)
                if (plugRank(owner, slot) >= 0 && (best < 0 || plugRank(owner, slot) < plugRank(owner, best))) best = slot;
            if (best < 0 || level.getFluidState(cell).isEmpty()) continue;
            BlockState state = ((BlockItem) owner.getItem(best).getItem()).getBlock().defaultBlockState();
            if (!level.setBlockAndUpdate(cell, state)) continue;
            ItemStack used = owner.removeItem(best, 1);
            owner.animateAction(1, cell, used);
            var sound = state.getSoundType(level, cell, owner);
            level.playSound(null, cell, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) * 0.5F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, cell, GameEvent.Context.of(owner, state));
        }
    }

    /**
     * True if the game's «fire near» of this cell (its 3x3x3 neighbourhood) comes from lava that stands and nothing else.
     * {@code typeOf} is the game's own judgement of a block (the evaluator's, which alone may call it).
     */
    static boolean onlySettledLavaNear(Level level, int x, int y, int z, Function<BlockPos, PathType> typeOf) {
        boolean lava = false;
        for (BlockPos pos : BlockPos.betweenClosed(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)) {
            if (!level.isLoaded(pos)) continue; // never loads a chunk at the edge of the loaded world
            if (settled(level, pos)) { lava = true; continue; }
            PathType type = typeOf.apply(pos);
            if (type == PathType.DAMAGE_FIRE || type == PathType.LAVA) return false;
        }
        return lava;
    }

    /** Where his things lie they are safe to fetch: not in lava, and no lava running within two blocks of them. */
    static boolean safeToFetch(Level level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.LAVA)) return false;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2)))
            if (level.isLoaded(near) && spreading(level, near)) return false;
        return true;
    }
}
