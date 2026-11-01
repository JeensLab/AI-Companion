package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The block of his own that shuts his way up: a walk over the cells he can stand in, taking his plugs (blocks he laid to close a cave) as
 * open, to a cell of the way up; the first plug on that walk is the one to take out.
 */
final class CompanionExpeditionPlugs {
    /** A plug to take out, and the cell he stands in to do it. */
    record Plug(BlockPos stand, BlockPos cell) { }

    private static final int BUDGET = 40000;

    private CompanionExpeditionPlugs() { }

    /** The first plug on the shortest walk from {@code from} to within a block of {@code goal}, or null: no walk, or none shut by a plug of his. */
    static Plug first(ServerLevel level, CompanionMineSite mine, BlockPos from, BlockPos goal) {
        Map<BlockPos, BlockPos> before = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = from.immutable();
        before.put(start, start);
        queue.add(start);
        BlockPos end = null;
        while (!queue.isEmpty() && before.size() < BUDGET) {
            BlockPos at = queue.poll();
            if (at.distManhattan(goal) <= 1) { end = at; break; }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int rise = 1; rise >= -1; rise--) {
                    BlockPos next = at.relative(side).above(rise);
                    if (before.containsKey(next) || !standable(level, mine, next, rise > 0 ? at.above(2) : null)) continue;
                    before.put(next, at);
                    queue.add(next);
                    break;
                }
            }
        }
        if (end == null) return null;
        List<BlockPos> walk = new ArrayList<>();
        for (BlockPos at = end; !at.equals(start); at = before.get(at)) walk.add(at);
        walk.add(start);
        Collections.reverse(walk);
        BlockPos stand = start;
        for (BlockPos at : walk) {
            for (BlockPos cell : new BlockPos[]{at, at.above()}) {
                if (mine.seals().contains(cell) && !CompanionShaftPlan.open(level, cell)) return new Plug(stand, cell);
            }
            stand = at;
        }
        return null;
    }

    /** Room for his body over a firm floor, plugs counting as room; {@code headroom}: the cell over the one he steps up from. */
    private static boolean standable(ServerLevel level, CompanionMineSite mine, BlockPos at, BlockPos headroom) {
        if (!level.hasChunkAt(at) || !level.hasChunkAt(at.below())) return false;
        if (!roomIn(level, mine, at) || !roomIn(level, mine, at.above()) || headroom != null && !roomIn(level, mine, headroom)) return false;
        BlockPos floor = at.below();
        BlockState state = level.getBlockState(floor);
        return state.isFaceSturdy(level, floor, Direction.UP);
    }

    private static boolean roomIn(ServerLevel level, CompanionMineSite mine, BlockPos cell) {
        return CompanionShaftPlan.open(level, cell) || mine.seals().contains(cell);
    }
}
