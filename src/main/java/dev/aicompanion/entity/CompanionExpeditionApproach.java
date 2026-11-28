package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The way to a cell he cannot reach by a straight push (the two cells beside it are solid, it lies in a pit): a breadth-first walk over
 * standing cells, orthogonal moves only, so that every move is one the move control makes. The path search of the travel is no use here:
 * it counts a goal within two blocks as reached and returns the cell he already stands in.
 */
final class CompanionExpeditionApproach {
    private static final int RADIUS = 6, RISE = 2, MAX_CELLS = 400;

    private CompanionExpeditionApproach() { }

    /** The cells from the one after {@code start} to {@code goal}, or null when no way is found within a few blocks. */
    static List<BlockPos> path(ServerLevel level, BlockPos start, BlockPos goal) {
        Map<BlockPos, BlockPos> from = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        from.put(start, start);
        queue.add(start);
        while (!queue.isEmpty() && from.size() < MAX_CELLS) {
            BlockPos at = queue.poll();
            if (at.equals(goal)) return unwind(from, start, goal);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = at.relative(side).above(dy);
                    if (from.containsKey(next) || Math.abs(next.getX() - goal.getX()) > RADIUS || Math.abs(next.getZ() - goal.getZ()) > RADIUS
                            || Math.abs(next.getY() - goal.getY()) > RISE || !moves(level, at, next, goal)) continue;
                    from.put(next, at);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static List<BlockPos> unwind(Map<BlockPos, BlockPos> from, BlockPos start, BlockPos goal) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos at = goal; !at.equals(start); at = from.get(at)) cells.add(at);
        Collections.reverse(cells);
        return cells;
    }

    private static boolean moves(ServerLevel level, BlockPos at, BlockPos next, BlockPos goal) {
        if (!level.hasChunkAt(next) || !CompanionShaftPlan.open(level, next) || !CompanionShaftPlan.open(level, next.above())) return false;
        if (!next.equals(goal) && !level.getBlockState(next.below()).isFaceSturdy(level, next.below(), Direction.UP)) return false;
        int rise = next.getY() - at.getY();
        return rise == 0 || CompanionShaftPlan.open(level, (rise > 0 ? at : next).above(2));
    }
}
