package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What lava does, as a player sees it: a lake, a hardened flow or a still fall stands (it has no fluid tick scheduled) and is
 * walked along like a wall; lava that is running now, fire and magma are danger. One judgement for walking, for his things after a
 * death and, later, for mining. No state: it asks the world.
 */
final class CompanionLava {
    /** The game's malus of a cell by fire or lava (8) makes a step 5 times as dear; by lava that stands, a step costs 1.5 times. */
    static final float SETTLED_MALUS = 1;

    private CompanionLava() {}

    /** Lava in this cell that is changing now: a fluid tick is scheduled for it (a fresh pour, a flow that goes on, lava let out of a wall). */
    static boolean spreading(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.LAVA) && level.getFluidTicks().hasScheduledTick(pos, fluid.getType());
    }

    static boolean settled(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA) && !spreading(level, pos);
    }

    /** The lava cells that run into this cell when it is opened: the one above and the four beside it, source or flow. */
    static List<BlockPos> inflow(Level level, BlockPos opened) {
        List<BlockPos> cells = new ArrayList<>(5);
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            BlockPos next = opened.relative(d);
            if (level.isLoaded(next) && level.getFluidState(next).is(FluidTags.LAVA)) cells.add(next);
        }
        return cells;
    }

    /**
     * True if the game's «fire near» of this cell (its 3x3x3 neighbourhood) comes from lava that stands and nothing else.
     * {@code typeOf} is the game's own judgement of a block (the evaluator's, which alone may call it).
     */
    static boolean onlySettledLavaNear(Level level, int x, int y, int z, Function<BlockPos, PathType> typeOf) {
        boolean lava = false;
        for (BlockPos pos : BlockPos.betweenClosed(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)) {
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
