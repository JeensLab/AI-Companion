package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Set;

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
    // The leg is only the best part found so far by a search that is still going (the whole route replaces it when found); the search running.
    private boolean legPartial;
    private CompanionSearch waiting;
    // The last step was only waiting for a search (no leg is being walked): the walk makes no progress meanwhile.
    private boolean searchOnly;
    /** Nothing yet to walk: a search is under way (nextLeg's answer). */
    private static final Path WAIT = CompanionNavigation.BUSY;
    private double best;
    private long lastProgress;
    // Walking counts as progress for three times the time the straight way takes (a way round is longer, not endless).
    private long walkUntil;
    private int turn;
    private boolean sideStep;
    // How fast the legs are walked: a walking player is kept up with at 1.15, other walks go at 1.
    private double speed = 1D;
    private net.minecraft.world.phys.Vec3 legMoved;
    // A wide search that found no whole way to this goal is not repeated at every leg: when it was, in game ticks.
    private long wideFailedAt = Long.MIN_VALUE / 2;
    // The walk as the journal tells it: where and when it began, what the searches had cost by then; null once told.
    private BlockPos from;
    private long began;
    private CompanionNavigation.Meter meter;
    // The caller restarts a failed walk on the next tick: at most one FAILED event per this many ticks.
    private static final int FAILURE_EVENT_GAP = 200;
    private long lastFailureEvent = Long.MIN_VALUE;
    private static final int NEAR_EVENT_GAP = 1200;
    private BlockPos lastNear;
    private long lastNearAt;

    CompanionTravel(CompanionEntity owner) { this.owner = owner; }

    private void drop() { if (waiting != null && owner.getNavigation() instanceof CompanionNavigation nav) nav.forget(waiting); waiting = null; }

    BlockPos goal() { return goal; }
    /** The last step was only waiting for a search: he stands and the order's stall time goes on. */
    boolean searchOnly() { return searchOnly; }
    /** The walk ended where its caller needed it (the goal's level, not its distance): told as arrived, then over. */
    void arrived() { told("NEAR", "arrived"); reset(); }
    /** The leg being walked comes all the way to the goal: a route to it exists. */
    boolean onRoute() { return leg != null && leg.canReach(); }
    /** Also the destination for a self-rescue: a goal given up must not look like a trap afterwards. */
    void reset() { meter = null; if (goal != null) owner.movementIntent(null); drop(); goal = null; leg = null; legPartial = false; turn = 0; sideStep = false; legMoved = null; wideFailedAt = Long.MIN_VALUE / 2; }

    /**
     * Towards a goal that moves on (the player walking): the walk keeps its legs and its measure of progress while the goal
     * shifts by less than sixteen blocks (so does its time bound: a player on the move does not extend it); a larger jump starts a new walk.
     */
    Step follow(BlockPos target, double near, double speed) {
        this.speed = speed;
        if (goal != null && !target.equals(goal) && goal.distSqr(target) <= 16 * 16) { goal = target.immutable(); best = Math.min(best, distance()); }
        return step(target, near);
    }

    /** One tick of walking towards the goal; NEAR once within {@code near} blocks, the caller then works it as usual. */
    Step toward(BlockPos target, double near) {
        speed = 1D;
        return step(target, near);
    }

    private Step step(BlockPos target, double near) {
        if (!(owner.level() instanceof ServerLevel level)) return Step.FAILED;
        long now = level.getGameTime();
        if (!target.equals(goal)) { reset(); goal = target.immutable(); best = distance(); lastProgress = now; walkUntil = now + (long)(3 * distance() / .19) + 200; from = owner.blockPosition(); began = now; meter = CompanionNavigation.meterOf(owner); }
        owner.movementIntent(goal);
        double distance = distance();
        if (distance <= near) { owner.getNavigation().stop(); told("NEAR", "arrived"); return Step.NEAR; }
        if (distance + PROGRESS <= best) { best = distance; lastProgress = now; turn = 0; }
        // Along a whole route that winds away from the goal (a mine, a tunnel) the straight distance first grows: walking counts.
        // Only on a leg that arrives (its end within the search's accuracy of the goal), not on side steps and parts that only head that way.
        if (leg != null && leg.canReach() && endDistance(leg) <= near + 2 && now < walkUntil && (legMoved == null || owner.position().distanceToSqr(legMoved) > .7 * .7)) { legMoved = owner.position(); lastProgress = now; }
        if (now - lastProgress > PATIENCE) { owner.getNavigation().stop(); told("FAILED", "no progress for half a minute"); return Step.FAILED; }
        if (!owner.onGround() && !owner.isInWater()) return Step.WALKING;
        boolean over = leg == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck();
        searchOnly = false;
        if (!over && !legPartial) return Step.WALKING;
        // A leg is over (or there was none): the next one. A search still going on is given its share every tick, and while it goes
        // on the best part of the way found so far is walked; the whole route takes over when it is found.
        Path next = nextLeg(level, distance, !over);
        if (next == WAIT) {
            searchOnly = over;
            if (over) {
                Path part = waiting == null ? null : waiting.partialFrom(owner.position());
                if (part != null && part.getNodeCount() > 1 && endDistance(part) + 3 <= distance) { leg = part; legPartial = true; searchOnly = false; CompanionMiningNavigation.walk(owner, leg, speed); }
            }
            return Step.WALKING;
        }
        if (!over) {
            if (next != null && next.canReach()) { leg = next; legPartial = false; walkUntil = Math.max(walkUntil, bound(now, near)); CompanionMiningNavigation.walk(owner, leg, speed); }
            return Step.WALKING;
        }
        leg = next; legPartial = false;
        walkUntil = Math.max(walkUntil, bound(now, near));
        if (leg == null) { owner.getNavigation().stop(); told("FAILED", "no leg found"); return Step.FAILED; }
        CompanionMiningNavigation.walk(owner, leg, speed);
        return Step.WALKING;
    }

    /** A whole way to the goal that is longer than the straight line promised: walking it counts as progress for as long as it takes (else the walk bound cuts it off). */
    private long bound(long now, double near) {
        return leg != null && leg.canReach() && endDistance(leg) <= near + 2 ? now + CompanionRoute.farLimit(CompanionMiningNavigation.length(owner, leg)) : 0;
    }

    /**
     * The next leg, or WAIT while the search for it is going on. {@code wholeOnly}: a leg is being walked (the best part so far),
     * and only the whole route is wanted, no other kind of leg.
     */
    private Path nextLeg(ServerLevel level, double distance, boolean wholeOnly) {
        // The whole way, or the part of it that ends nearer the goal: on rough ground the search runs out of steps before it arrives.
        if (!sideStep) {
            Set<BlockPos> cells = nav().cellsOf(goal);
            CompanionSearch main = cells == null ? null : nav().advance(cells, 2, nav().followRange(1));
            if (main != null && main.state() == CompanionSearch.State.RUNNING) { waiting = main; return WAIT; }
            Path route = main == null ? null : main.from(owner.position());
            if (route != null && route.getNodeCount() > 1 && route.canReach()) return route;
            // The way round may be longer than one search walks (a mine winding up, a cave, a cliff trail): a wider one still finds it
            // whole. For a goal within its reach it is asked before a part of the short one that merely ends nearer, which can lead into
            // a dead end; a goal beyond it is walked to by the parts nearer, as before. Reach: half as far again as one search sees: farther
            // out the wide search fails at its cell budget (3500 cells, 14 ms in one tick on the polygon's far hills) and finds nothing.
            boolean within = distance <= owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) * 1.5;
            Path round = within || wholeOnly ? wideRound(level) : null;
            if (round != null) return round;
            if (wholeOnly) return null;
            if (route != null && route.getNodeCount() > 1 && endDistance(route) + 3 <= distance) return route;
            round = within ? null : wideRound(level);
            if (round != null) return round;
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
                    Path ahead = nav().search(point, 2);
                    if (ahead == WAIT) { waiting = null; return WAIT; }
                    if (ahead != null && ahead.getNodeCount() > 1 && endDistance(ahead) + 3 <= distance) return ahead;
                }
            }
        }
        // Into a wall or a cliff: round it, like a player trying left and right. Each side step is walked to its end. Not for a goal
        // this near: the player on a roof or a pillar is not walked round for a minute (the self-rescue takes him there).
        if (Math.hypot(goal.getX() + .5 - owner.getX(), goal.getZ() + .5 - owner.getZ()) <= STRAIGHT_LEG) return null;
        while (turn < TURNS.length) {
            double angle = Math.atan2(goal.getZ() - owner.getZ(), goal.getX() - owner.getX()) + Math.toRadians(TURNS[turn++]);
            int x = (int) Math.floor(owner.getX() + Math.cos(angle) * SIDE_STEP), z = (int) Math.floor(owner.getZ() + Math.sin(angle) * SIDE_STEP);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos point = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            Path aside = nav().search(point, 2);
            if (aside == WAIT) { turn--; waiting = null; return WAIT; }
            if (aside != null && aside.getNodeCount() > 3 && (aside.canReach() || endDistance(aside, point) + 6 <= SIDE_STEP)) {
                sideStep = false;
                return aside;
            }
        }
        return null;
    }

    /** The whole way by the wide search, or null; a search that found none is not repeated for five seconds. */
    private Path wideRound(ServerLevel level) {
        long now = level.getGameTime();
        if (now - wideFailedAt < 100) return null;
        CompanionSearch wide = nav().advance(Set.of(goal), 2, nav().followRange(3));
        if (wide != null && wide.state() == CompanionSearch.State.RUNNING) { waiting = wide; return WAIT; }
        Path round = wide == null ? null : wide.from(owner.position());
        if (round != null && round.getNodeCount() > 1 && round.canReach()) return round;
        wideFailedAt = now;
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
        // Standing near a player he cannot see, the caller restarts the walk every tick: one NEAR per goal for a while.
        if (outcome.equals("NEAR")) {
            if (lastNear != null && lastNear.distSqr(goal) <= 4 && now - lastNearAt < NEAR_EVENT_GAP) { meter = null; return; }
            lastNear = goal; lastNearAt = now;
        }
        CompanionNavigation.outcome(owner, from, goal, outcome, reason, now - began, Math.sqrt(from.distSqr(goal)),
                CompanionNavigation.meterOf(owner).since(meter));
        meter = null;
    }

    private CompanionNavigation nav() { return (CompanionNavigation)owner.getNavigation(); }
    private double distance() { return Math.sqrt(owner.distanceToSqr(goal.getCenter())); }
    private double endDistance(Path path) { return endDistance(path, goal); }
    private static double endDistance(Path path, BlockPos to) {
        var end = path.getEndNode();
        return end == null ? Double.MAX_VALUE : Math.sqrt(end.asBlockPos().distSqr(to));
    }
}
