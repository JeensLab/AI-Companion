package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Edits of the mine record the expedition makes when a move fails: what to refuse, where the tunnels go on from, where the mine starts again. */
final class CompanionExpeditionRecord {
    /** A tunnel at least this long is worth moving the base to; the last cells stay beyond the new foot, so its first advances are through open cells. */
    private static final int LONG_TUNNEL = 8, KEPT_AHEAD = 2, MAX_RESTARTS = 3, MAX_DROP = 3;
    /** The shore search: how many strokes and steps from where he stands it looks, and how many cells it visits at most. */
    private static final int SHORE_RADIUS = 24, SHORE_BUDGET = 6000;

    /** Where the tunnels go on from: the way the new base is walked to, and the stairs' direction there. */
    record Base(Direction direction, List<BlockPos> path) { }

    /** Light a block takes off entirely: rock and soil; the leaves and the glass of a roof take less. */
    private static final int LIGHT_BLOCKED = 15;

    private CompanionExpeditionRecord() { }

    /** Open sky over the cell, the leaves of trees not counted: a jungle's canopy (leaves and branches) shuts the sky light off the whole surface, and the surface under it is not a pit of the mine. */
    static boolean underSky(LevelReader level, BlockPos pos) {
        if (level.canSeeSky(pos)) return true;
        if (!level.hasChunkAt(pos)) return false;
        BlockPos.MutableBlockPos above = pos.mutable();
        for (int y = pos.getY(); y < level.getMaxBuildHeight(); y++) {
            BlockState state = level.getBlockState(above.setY(y));
            boolean tree = state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
            if (!tree && (state.getLightBlock(level, above) >= LIGHT_BLOCKED || !state.getFluidState().isEmpty())) return false;
        }
        return true;
    }

    /** The move under way is refused so that the next order takes another one instead of walking into the same trouble. */
    static void rememberMove(CompanionMineSite mine, Direction tunnel, CompanionExpeditionStep plan, BlockPos stand, Direction dir) {
        if (tunnel != null) mine.refuse(mine.foot(), tunnel);
        else if (plan != null) mine.refuse(plan.from, plan.turn);
        else if (stand != null && dir != null) mine.refuse(stand, dir);
    }

    /**
     * Every tunnel off the foot is done: the base moves along the longest one, up to two cells short of its end, and the tunnels
     * are forgotten. {@code atFoot}: he stands at the foot, not at the end of the last tunnel. Null when no tunnel is long
     * enough, or the new base would be in a cave he fled from or past the record's cap.
     */
    static Base moveBase(CompanionMineSite mine, boolean atFoot) {
        List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
        BlockPos foot = mine.foot();
        int best = -1;
        for (int i = 0; i < tunnels.size(); i++) {
            CompanionMineSite.Tunnel t = tunnels.get(i);
            boolean usable = t.length() >= LONG_TUNNEL && !mine.shunned(foot.relative(t.direction(), t.length() - KEPT_AHEAD));
            if (usable && (best < 0 || t.length() > tunnels.get(best).length())) best = i;
        }
        if (best < 0) return null;
        CompanionMineSite.Tunnel longest = tunnels.get(best);
        int last = tunnels.size() - 1, k = longest.length() - KEPT_AHEAD;
        Direction d = longest.direction();
        List<BlockPos> path = new ArrayList<>();
        if (!atFoot) {
            CompanionMineSite.Tunnel end = tunnels.get(last);
            for (int i = end.length(); i >= (best == last ? k : 0); i--) path.add(foot.relative(end.direction(), i));
        }
        if (atFoot || best != last) for (int i = 1; i <= k; i++) path.add(foot.relative(d, i));
        return mine.rebase(k, d) ? new Base(d, path) : null;
    }

    /** A dry cell on the shore of the water he is in, and the side the mine starts again to from it. */
    record Shore(BlockPos cell, Direction side) { }

    /** Water in the cell he stands in: no stairs start from such a cell, since its feet are not open. */
    static boolean inWater(ServerLevel level, BlockPos here) {
        return level.getFluidState(here).is(FluidTags.WATER);
    }

    /**
     * The nearest cell, over the water and the air around him (lava and rock shut), where the mine can start again, or null: he is not in water,
     * the restarts are used up, or no shore is in reach.
     */
    static Shore shore(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        if (!inWater(level, here) || !mine.exists() || mine.restarts() >= MAX_RESTARTS) return null;
        Map<BlockPos, Integer> strokes = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        strokes.put(here.immutable(), 0);
        queue.add(here.immutable());
        while (!queue.isEmpty() && strokes.size() < SHORE_BUDGET) {
            BlockPos at = queue.poll();
            Direction side = restartSideOf(level, mine, at);
            if (side != null) return new Shore(at, side);
            int next = strokes.get(at) + 1;
            if (next > SHORE_RADIUS) continue;
            for (Direction step : Direction.values()) {
                BlockPos to = at.relative(step);
                if (strokes.containsKey(to) || !level.hasChunkAt(to) || !swimmable(level, to)) continue;
                strokes.put(to.immutable(), next);
                queue.add(to.immutable());
            }
        }
        return null;
    }

    /** A cell his body passes through: air or water, no lava. */
    private static boolean swimmable(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.getCollisionShape(level, cell).isEmpty() && (state.getFluidState().isEmpty() || state.getFluidState().is(FluidTags.WATER));
    }

    /** The side the mine starts again to from where he stands, or null: restarts are used up, or he is not on firm ground with room to stand. */
    static Direction restartSide(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        Direction side = restartSideOf(level, mine, here);
        if (side == null && dev.aicompanion.CompanionDiagnostics.enabled()) {
            StringBuilder sides = new StringBuilder();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                sides.append(d).append('=').append(CompanionShaftPlan.assessStep(level, here, d, mine.seals(), mine.avoid())).append(mine.shunned(here.relative(d).below()) ? "(shunned)" : "").append(mine.refused(here, d) ? "(refused) " : " ");
            }
            dev.aicompanion.CompanionDiagnostics.event("mine", "restart_refused", "here", here.toShortString(), "restarts", mine.restarts(), "floor", level.getBlockState(here.below()), "feet", level.getBlockState(here), "head", level.getBlockState(here.above()), "sides", sides);
        }
        return side;
    }

    private static Direction restartSideOf(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        return sideOf(level, mine, here, MAX_RESTARTS);
    }

    /** The side of the last restart, one past the cap, where he stands or after he dug out of a plugged pocket: a fruitless chain stays bounded by it. */
    static Direction digOutSide(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        return sideOf(level, mine, here, MAX_RESTARTS + 1);
    }

    /** Whether the cap leaves room for the restart a dig out ends in. */
    static boolean mayDigOut(CompanionMineSite mine) { return mine.exists() && mine.restarts() <= MAX_RESTARTS; }

    private static Direction sideOf(ServerLevel level, CompanionMineSite mine, BlockPos here, int cap) {
        BlockPos floor = here.below();
        if (!mine.exists() || mine.restarts() >= cap || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return null;
        if (!CompanionShaftPlan.open(level, here) || !CompanionShaftPlan.open(level, here.above())) return null;
        // A side refused in an earlier try is no side to restart to: the next step would turn off it at once, and the restart would be spent.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!mine.refused(here, d) && CompanionShaftPlan.assessStep(level, here, d, mine.seals(), mine.avoid()).kind() != CompanionShaftPlan.Kind.BLOCKED) return d;
        }
        // The caves he fled from may surround him: the restart forgets the ones beside him, so they do not shut a side.
        CompanionShaftPlan.Avoid avoid = mine.avoid();
        CompanionShaftPlan.Avoid ignoringCaves = new CompanionShaftPlan.Avoid(avoid.forbidden(), avoid.refused(), cell -> false);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!mine.refused(here, d) && CompanionShaftPlan.assessStep(level, here, d, mine.seals(), ignoringCaves).kind() != CompanionShaftPlan.Kind.BLOCKED) return d;
        }
        return null;
    }

    /** Under the ground below the entrance on the surface, where the way up is his only one and a stop that comes again calls for a change of way. */
    static boolean deep(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        BlockPos origin = mine.origin();
        return mine.exists() && origin != null && here.getY() < origin.getY() - CompanionExpedition.UNDERGROUND_DEPTH && !underSky(level, here.above());
    }

    /** The side the stairs start anew to in a change of way, or null: as {@link #sideOf}, past no cap, and a cave he fled from shuts its side here. */
    static Direction cleanSide(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        BlockPos floor = here.below();
        if (!mine.exists() || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return null;
        if (!CompanionShaftPlan.open(level, here) || !CompanionShaftPlan.open(level, here.above())) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (CompanionShaftPlan.assessStep(level, here, d, mine.seals(), mine.avoid()).kind() != CompanionShaftPlan.Kind.BLOCKED) return d;
        }
        return null;
    }

    /**
     * The side of the entrance on the surface that stairs were not begun to yet, for a mine with no side left under the ground: the
     * mine begins again there, a side that is not refused and whose first step is open, one needing nothing laid first preferred.
     * Null when the restarts are used up, the entrance is unknown or unloaded, or every side was tried or is shut.
     */
    static Direction originSide(ServerLevel level, CompanionMineSite mine) {
        BlockPos origin = mine.origin();
        if (!mine.exists() || origin == null || mine.restarts() >= MAX_RESTARTS || !level.hasChunkAt(origin)) return null;
        Direction best = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (mine.originTried(d) || mine.refused(origin, d)) continue;
            CompanionShaftPlan.Kind kind = CompanionShaftPlan.assessStep(level, origin, d, mine.seals(), mine.avoid()).kind();
            if (kind == CompanionShaftPlan.Kind.OK) return d;
            if (kind != CompanionShaftPlan.Kind.BLOCKED && best == null) best = d;
        }
        return best;
    }

    /**
     * The cell he really stands in: his own, or the first one below it (at most {@code MAX_DROP}) with a firm floor, as mid-jump his feet are in the air above it.
     * A cell that is shut (a fence post of a shaft he broke into stands beside him in it) is no place to stand: the open cell beside it that he is nearest to.
     */
    static BlockPos standingCell(ServerLevel level, Entity who) {
        BlockPos here = beside(level, who);
        for (int drop = 0; drop <= MAX_DROP; drop++) {
            BlockPos cell = here.below(drop), floor = cell.below();
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return cell;
        }
        return here;
    }

    private static BlockPos beside(ServerLevel level, Entity who) {
        BlockPos here = who.blockPosition();
        if (CompanionShaftPlan.open(level, here)) return here;
        BlockPos best = here;
        double bestDistance = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos cell = here.relative(d), floor = cell.below();
            if (!CompanionShaftPlan.open(level, cell) || !CompanionShaftPlan.open(level, cell.above()) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) continue;
            double distance = Math.pow(cell.getX() + .5 - who.getX(), 2) + Math.pow(cell.getZ() + .5 - who.getZ(), 2);
            if (distance < bestDistance) { best = cell; bestDistance = distance; }
        }
        return best;
    }
}
