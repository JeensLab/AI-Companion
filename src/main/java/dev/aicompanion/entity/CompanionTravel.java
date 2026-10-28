package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

/**
 * A long walk like a player's (game log 23.10, session aa07e605: the trees left were 70–90 blocks off over hills; the game's
 * pathfinder builds no route that long in one search, every tree was declared unreachable in 15 seconds without a single step).
 * The way is walked in legs: the whole route if it is found; otherwise the part of it that brings him closer; and where that runs
 * into a wall, a step to the side (left, right, half-left…) to get round it. Given up only after half a minute without coming closer.
 */
final class CompanionTravel {
    enum Step { WALKING, NEAR, FAILED }

    /** Without coming at least this much closer for this long, the walk is given up. */
    private static final int PATIENCE = 20 * 30;
    private static final double PROGRESS = 2;
    /** A side step: this far, at these turns from the direction of the goal. */
    private static final int SIDE_STEP = 20;
    /** A leg straight on towards the goal: this far. */
    private static final int STRAIGHT_LEG = 16;
    private static final int[] TURNS = {90, -90, 45, -45, 135, -135};

    private final CompanionEntity owner;
    private BlockPos goal;
    private Path leg;
    private double best;
    private long lastProgress;
    private int turn;
    private boolean sideStep;
    // The walk as the journal tells it: where and when it began, what the searches had cost by then; null once told.
    private BlockPos from;
    private long began;
    private CompanionNavigation.Meter meter;
    // The caller restarts a failed walk on the next tick: at most one FAILED event per this many ticks.
    private static final int FAILURE_EVENT_GAP = 200;
    private long lastFailureEvent = Long.MIN_VALUE;

    CompanionTravel(CompanionEntity owner) { this.owner = owner; }

    BlockPos goal() { return goal; }
    /** The walk ended where its caller needed it (the goal's level, not its distance): told as arrived, then over. */
    void arrived() { told("NEAR", "arrived"); reset(); }
    /** Also the destination for a self-rescue: a goal given up must not look like a trap afterwards. */
    void reset() { meter = null; if (goal != null) owner.movementIntent(null); goal = null; leg = null; turn = 0; sideStep = false; }

    /**
     * Towards a goal that moves on (the player walking): the walk keeps its legs and its measure of progress while the goal
     * shifts by less than sixteen blocks; a larger jump starts a new walk.
     */
    Step follow(BlockPos target, double near) {
        if (goal != null && !target.equals(goal) && goal.distSqr(target) <= 16 * 16) { goal = target.immutable(); best = Math.min(best, distance()); }
        return toward(target, near);
    }

    /** One tick of walking towards the goal; NEAR once within {@code near} blocks, the caller then works it as usual. */
    Step toward(BlockPos target, double near) {
        if (!(owner.level() instanceof ServerLevel level)) return Step.FAILED;
        long now = level.getGameTime();
        if (!target.equals(goal)) { reset(); goal = target.immutable(); best = distance(); lastProgress = now; from = owner.blockPosition(); began = now; meter = CompanionNavigation.meterOf(owner); }
        owner.movementIntent(goal);
        double distance = distance();
        if (distance <= near) { owner.getNavigation().stop(); told("NEAR", "arrived"); return Step.NEAR; }
        if (distance + PROGRESS <= best) { best = distance; lastProgress = now; turn = 0; }
        if (now - lastProgress > PATIENCE) { owner.getNavigation().stop(); told("FAILED", "no progress for half a minute"); return Step.FAILED; }
        if (!owner.onGround() && !owner.isInWater()) return Step.WALKING;
        if (leg != null && !owner.getNavigation().isDone() && !owner.getNavigation().isStuck()) return Step.WALKING;
        // A leg is over (or there was none): the next one.
        leg = nextLeg(level, distance);
        if (leg == null) { owner.getNavigation().stop(); told("FAILED", "no leg found"); return Step.FAILED; }
        CompanionMiningNavigation.walk(owner, leg);
        return Step.WALKING;
    }

    private Path nextLeg(ServerLevel level, double distance) {
        // The whole way, or the part of it that ends nearer the goal: on rough ground the search runs out of steps before it arrives.
        if (!sideStep) {
            Path route = owner.getNavigation().createPath(goal, 2);
            if (route != null && route.getNodeCount() > 1 && (route.canReach() || endDistance(route) + 3 <= distance)) return route;
            // The way round may be longer than one search walks (a mine winding up, a cave): a wider one still finds it whole.
            if (owner.getNavigation() instanceof CompanionNavigation nav) {
                Path round = nav.wide(java.util.Set.of(goal), 2);
                if (round != null && round.getNodeCount() > 1 && round.canReach()) return round;
            }
        }
        // Straight on towards it, a short leg at a time, as a player heads for what he sees (marathon 23.10, «берег»: back from the
        // island the long search through the water found no part that came nearer, and the side steps led him off across the sea).
        if (!sideStep) {
            double dx = goal.getX() + .5 - owner.getX(), dz = goal.getZ() + .5 - owner.getZ(), length = Math.hypot(dx, dz);
            if (length > 8) {
                double step = Math.min(STRAIGHT_LEG, length);
                BlockPos column = BlockPos.containing(owner.getX() + dx / length * step, 0, owner.getZ() + dz / length * step);
                if (level.hasChunkAt(column)) {
                    BlockPos point = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                    Path ahead = owner.getNavigation().createPath(point, 2);
                    if (ahead != null && ahead.getNodeCount() > 1 && endDistance(ahead) + 3 <= distance) return ahead;
                }
            }
        }
        // Into a wall or a cliff: round it, like a player trying left and right. Each side step is walked to its end.
        while (turn < TURNS.length) {
            double angle = Math.atan2(goal.getZ() - owner.getZ(), goal.getX() - owner.getX()) + Math.toRadians(TURNS[turn++]);
            int x = (int) Math.floor(owner.getX() + Math.cos(angle) * SIDE_STEP), z = (int) Math.floor(owner.getZ() + Math.sin(angle) * SIDE_STEP);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos point = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            Path aside = owner.getNavigation().createPath(point, 2);
            if (aside != null && aside.getNodeCount() > 3 && (aside.canReach() || endDistance(aside, point) + 6 <= SIDE_STEP)) {
                sideStep = false;
                return aside;
            }
        }
        return null;
    }

    /** The route-outcome event of the journal, once per walk: arrived or failed; a walk dropped by reset() is not told. */
    private void told(String outcome, String reason) {
        if (meter == null || goal == null) return;
        long now = owner.level().getGameTime();
        if (outcome.equals("FAILED")) {
            if (lastFailureEvent != Long.MIN_VALUE && now - lastFailureEvent < FAILURE_EVENT_GAP) { meter = null; return; }
            lastFailureEvent = now;
        }
        CompanionNavigation.outcome(owner, from, goal, outcome, reason, now - began, Math.sqrt(from.distSqr(goal)),
                CompanionNavigation.meterOf(owner).since(meter));
        meter = null;
    }

    private double distance() { return Math.sqrt(owner.distanceToSqr(goal.getCenter())); }
    private double endDistance(Path path) { return endDistance(path, goal); }
    private static double endDistance(Path path, BlockPos to) {
        var end = path.getEndNode();
        return end == null ? Double.MAX_VALUE : Math.sqrt(end.asBlockPos().distSqr(to));
    }
}
