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
 * open, to a cell of the way up; the first plug on that walk is the one to take out, and the cells up to it are the way to it.
 */
final class CompanionExpeditionPlugs {
    /** The cells to walk to the stand of the first plug (all of the way when none shuts it: {@code cell} is then null), and the plug cell to take out from that stand. */
    record Way(List<BlockPos> walk, BlockPos cell) { }

    private static final int BUDGET = 40000, MAX_BRIDGE = 12;

    private CompanionExpeditionPlugs() { }

    /** The shortest walk from {@code from} to within a block of {@code goal} cut short at the first plug on it, or null when there is no walk. */
    static Way way(ServerLevel level, CompanionMineSite mine, BlockPos from, BlockPos goal) { return way(level, mine, from, goal, false); }

    /** {@link #way} over cells with no floor under them as well (a pit, a shaft of his own): the floor is laid as he goes. Null when more than a dozen floors would be laid. */
    static Way bridge(ServerLevel level, CompanionMineSite mine, BlockPos from, BlockPos goal) { return way(level, mine, from, goal, true); }

    private static Way way(ServerLevel level, CompanionMineSite mine, BlockPos from, BlockPos goal, boolean bridging) {
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
                    if (before.containsKey(next) || !standable(level, mine, next, rise > 0 ? at.above(2) : null, bridging)) continue;
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
        if (bridging && walk.stream().filter(at -> !firm(level, at.below())).count() > MAX_BRIDGE) return null;
        for (int i = 0; i < walk.size(); i++) {
            BlockPos at = walk.get(i);
            for (BlockPos cell : new BlockPos[]{at, at.above()}) {
                if (mine.seals().contains(cell) && !CompanionShaftPlan.open(level, cell)) {
                    List<BlockPos> toStand = List.copyOf(walk.subList(0, Math.max(1, i)));
                    return new Way(toStand, cell);
                }
            }
        }
        return new Way(List.copyOf(walk), null);
    }

    /** Room for his body over a firm floor, plugs counting as room; {@code headroom}: the cell over the one he steps up from. */
    private static boolean standable(ServerLevel level, CompanionMineSite mine, BlockPos at, BlockPos headroom, boolean bridging) {
        if (!level.hasChunkAt(at) || !level.hasChunkAt(at.below())) return false;
        if (!roomIn(level, mine, at) || !roomIn(level, mine, at.above()) || headroom != null && !roomIn(level, mine, headroom)) return false;
        if (firm(level, at.below())) return true;
        BlockState state = level.getBlockState(at.below());
        return bridging && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static boolean firm(ServerLevel level, BlockPos floor) {
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private static boolean roomIn(ServerLevel level, CompanionMineSite mine, BlockPos cell) {
        return CompanionShaftPlan.open(level, cell) || mine.seals().contains(cell);
    }
}
