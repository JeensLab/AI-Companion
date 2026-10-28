package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single route: the one way the activities lead the companion to a goal. A walk to one of the cells from which a block is
 * worked, the nearest of many blocks on foot, a far goal in legs, and an outcome that tells «far, keep walking» from «no way».
 * The search behind it is the companion's own (CompanionSearch); every walk it leads ends in one {@code route_outcome} event of the journal.
 */
final class CompanionRoute {
    /** What is wanted of the cells a block is worked from (see CompanionMiningNavigation.standCells). */
    record Need(double reach, boolean clearLeaves, boolean avoidStandingOnTarget, boolean forTheDrop) { }

    /** WALK: a whole route by one search. FAR: none within one search, but one a wider search finds (walked on, searched again). NONE: no way. */
    enum Kind { WALK, FAR, NONE }

    /** A block chosen by the walk to a cell it can be worked from, that walk and its length in blocks; NONE says why. */
    record Reach(Kind kind, BlockPos block, Path path, double length, String reason) {
        boolean found() { return kind != Kind.NONE; }
        /** No way was looked for (the tick's cells were spent): not a failure, ask again next tick. */
        boolean busy() { return BUSY.equals(reason); }
    }

    private static final int PATIENCE = 160;
    static final String BUSY = "busy";
    /** Not even the short search ran (he is on a ladder or in a jump): neither a far goal nor no way is known. */
    static final String NOT_SEARCHED = "not searched";
    /** How far a far walk counts walking as progress: twice the time its length takes at his pace, and ten seconds more. */
    static long farLimit(double length) { return (long)(2 * length / .19) + 200; }

    private final CompanionWork w;
    // The walk as the journal tells it: to what, from where, when, what the searches had cost by then; null once told.
    private BlockPos goal, from;
    private long began;
    private boolean far;
    private long farUntil;
    // The paths chosen as far ones (by identity): the kind goes with the path, whichever search it came from.
    private final Set<Path> farPaths = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private long failedAt = Long.MIN_VALUE / 2;
    private int failures;
    private CompanionNavigation.Meter failedMeter;
    private CompanionNavigation.Meter meter;
    /** A wide search of these cells that failed: when, and whether its failure holds for these cells alone (it ran out of budget) or for any subset. */
    private record Wide(long at, boolean exact) { }
    private final Map<Set<BlockPos>, Wide> wideFailed = new HashMap<>();

    CompanionRoute(CompanionWork w) { this.w = w; }

    /**
     * The block a player would go to first: one search to the cells of all of them at once, so the nearest on foot wins, not the
     * nearest by the straight line. Beyond one search the same is asked of a wider one: that is a far goal, not a lost one.
     */
    Reach nearest(Collection<BlockPos> blocks, Need need) {
        CompanionEntity owner = w.owner;
        Map<BlockPos, BlockPos> blockAt = new HashMap<>();
        for (BlockPos block : blocks) for (BlockPos stand : CompanionMiningNavigation.standCells(owner, block, need.reach(), need.clearLeaves(), need.avoidStandingOnTarget())) {
            if (need.forTheDrop() && !CompanionMiningNavigation.dropCollectable(owner.level(), block, stand)) continue;
            BlockPos other = blockAt.get(stand);
            if (other == null || stand.distSqr(block) < stand.distSqr(other)) blockAt.put(stand, block.immutable());
        }
        if (blockAt.isEmpty()) return failed(blocks, "no stand cell");
        Reach found = find(blockAt.keySet(), 0, blockAt::get);
        return found.found() || found.busy() ? found : failed(blocks, found.reason());
    }

    /**
     * The way to one of these cells (a drop's pickup spots, the place he came from): one search, else the wide one. The way itself
     * if it comes all the way ({@code accuracy}: how close counts), else none.
     */
    Reach cells(Set<BlockPos> cells, int accuracy) { return cells(cells, accuracy, false); }

    /** {@code whole}: the answer now, not by shares of this tick's cells (the diagnosis of a failure). */
    private Reach cells(Set<BlockPos> cells, int accuracy, boolean whole) {
        Reach found = find(cells, accuracy, cell -> null, whole);
        return found.found() || found.busy() || cells.isEmpty() ? found : failed(cells, found.reason());
    }

    private Reach find(Set<BlockPos> cells, int accuracy, java.util.function.Function<BlockPos, BlockPos> blockOf) { return find(cells, accuracy, blockOf, false); }

    private Reach find(Set<BlockPos> cells, int accuracy, java.util.function.Function<BlockPos, BlockPos> blockOf, boolean whole) {
        CompanionEntity owner = w.owner;
        Kind kind = Kind.WALK;
        Path path = nav().search(cells, accuracy, nav().followRange(1), whole);
        // The search goes on at the next tick (it keeps what it has found): not a failure.
        if (path == CompanionNavigation.BUSY) return new Reach(Kind.NONE, null, null, 0, BUSY);
        if (!reaches(path, cells, accuracy)) {
            // No path at all: nothing was searched (he is in the air); that is no evidence of a far goal or of none.
            if (path == null) return new Reach(Kind.NONE, null, null, 0, NOT_SEARCHED);
            // A goal that no wide search reaches is not searched wide again at once (a failed one costs a whole search): five seconds.
            long now = owner.level().getGameTime();
            // Failed wide for these cells; for a set that holds them all too, if that search ran out of range (a group that failed has
            // failed for each of its faces), not if it ran out of budget (it may have spent it on a near cell that cannot be reached).
            for (var entry : wideFailed.entrySet())
                if (now - entry.getValue().at() < 100 && (entry.getValue().exact() ? entry.getKey().equals(cells) : entry.getKey().containsAll(cells)))
                    return new Reach(Kind.NONE, null, null, 0, "no route");
            if (!(owner.getNavigation() instanceof CompanionNavigation nav)) return new Reach(Kind.NONE, null, null, 0, "no route");
            path = nav.wide(cells, accuracy, whole);
            // The wide search goes on at the next tick (this tick's cells are spent): not a failure.
            if (path == CompanionNavigation.BUSY) return new Reach(Kind.NONE, null, null, 0, BUSY);
            if (!reaches(path, cells, accuracy)) {
                if (wideFailed.size() > 64) wideFailed.values().removeIf(old -> now - old.at() >= 100);
                if (wideFailed.size() > 256) wideFailed.clear();
                wideFailed.put(Set.copyOf(cells), new Wide(now, nav.lastSearchExhausted()));
                return new Reach(Kind.NONE, null, null, 0, "no route");
            }
            kind = Kind.FAR;
            if (farPaths.size() > 32) farPaths.clear();
            farPaths.add(path);
        }
        return new Reach(kind, blockOf.apply(path.getTarget()), path, CompanionMiningNavigation.length(owner, path), kind == Kind.FAR ? "far" : "near");
    }

    /** This tick's cells are spent: a choice that goes on searching waits for the next tick. */
    boolean busy() { return w.owner.getNavigation() instanceof CompanionNavigation nav && nav.overBudget(); }

    Reach to(BlockPos block, Need need) { return nearest(List.of(block), need); }

    /**
     * «No way» is told only where the wide search can see: with the target within one and a half follow ranges in a straight line
     * and no whole route to it. Farther off it sees nothing at its cell budget, and a stuck walk is told as stuck, not as no way.
     */
    boolean noWayTo(BlockPos target) {
        double range = w.owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) * 1.5;
        if (w.owner.distanceToSqr(target.getCenter()) > range * range) return false;
        Reach reach = cells(Set.of(target), 1, true);
        return !reach.found() && !reach.busy() && !NOT_SEARCHED.equals(reach.reason());
    }

    /** One search to any of these cells: the way if it comes all the way, else null. For the short last steps, not for far goals. */
    Path toCells(Set<BlockPos> cells) { return toCells(cells, 0); }

    /** The same where coming within {@code accuracy} blocks of a cell counts. */
    Path toCells(Set<BlockPos> cells, int accuracy) {
        Path path = nav().search(cells, accuracy);
        return path != null && path.canReach() ? path : null;
    }

    private CompanionNavigation nav() { return (CompanionNavigation)w.owner.getNavigation(); }

    void walk(Path path) { CompanionMiningNavigation.walk(w.owner, path); }
    void walk(Path path, double speed) { CompanionMiningNavigation.walk(w.owner, path, speed); }

    /** The leg being walked is over (or he is stuck in it): time for the next search. */
    boolean legOver() { return done() || stuck(); }
    /** The leg is walked to its end / he cannot get on along it. */
    boolean done() { return w.owner.getNavigation().isDone(); }
    boolean stuck() { return w.owner.getNavigation().isStuck(); }

    void stop() { w.owner.getNavigation().stop(); }

    /** A far goal in legs (CompanionTravel); NEAR once within {@code near} blocks of it. */
    CompanionTravel.Step legs(BlockPos target, double near) { return w.owner.travel().toward(target, near); }

    /** The same towards a goal that moves (the player): the legs and their progress are kept while it shifts by less than sixteen blocks. */
    CompanionTravel.Step follow(BlockPos target, double near) { return w.owner.travel().follow(target, near, 1D); }

    /** The same at this speed (the following keeps up with a walking player). */
    CompanionTravel.Step follow(BlockPos target, double near, double speed) { return w.owner.travel().follow(target, near, speed); }

    /** The legs are over, the goal reached or not: the walk is told to the journal if it was not yet. */
    void legsEnd(boolean arrived) { if (arrived) w.owner.travel().arrived(); else w.owner.travel().reset(); }

    /**
     * One tick of the approach to a block he works from a standing place (ore, a bench to take): 1 in reach and sight (and, for the
     * drop, where it can be picked up), 0 walking, -1 no way or no progress. A walk that needed a wider search counts walking as
     * progress; the others, coming closer to the block.
     */
    int approach(ServerLevel level, BlockPos block, boolean forTheDrop) { return approach(level, block, new Need(3.5, false, true, forTheDrop)); }

    /** The same for a block worked from other cells than ore is (the stairs down: standing on the dug cell is allowed). */
    int approach(ServerLevel level, BlockPos block, Need need) {
        boolean forTheDrop = need.forTheDrop();
        if (!block.equals(goal)) begin(block);
        if (w.path != null && farPaths.contains(w.path)) setFar(w.path);
        double distance = w.owner.distanceToSqr(block.getCenter());
        if (distance <= 3.8D * 3.8D && CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), block)
                && (!forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, w.owner.blockPosition()))) { w.owner.getNavigation().stop(); told("ARRIVED", far ? "far" : "near"); return 1; }
        w.breaking.reset();
        if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = level.getGameTime(); }
        // Along a route longer than one search the straight distance first grows: walking is progress then.
        if (far && level.getGameTime() < farUntil && (w.approachMoved == null || w.owner.position().distanceToSqr(w.approachMoved) > .7 * .7)) { w.approachMoved = w.owner.position(); w.lastProgressTick = level.getGameTime(); }
        if (level.getGameTime() - w.lastProgressTick > PATIENCE) { told("FAILED", "no progress"); return -1; }
        if (w.path == null || legOver()) {
            Reach reach = to(block, need);
            // This tick's cells went to another choice: keep walking the leg he has, ask again next tick.
            if (reach.busy()) return 0;
            w.path = reach.path();
            if (reach.kind() == Kind.FAR) setFar(w.path);
            // A failed search was told by nearest() already.
            if (!reach.found()) { meter = null; return -1; }
        }
        if (w.path == null || !w.path.canReach()) { told("FAILED", "no route"); return -1; }
        walk(w.path);
        return 0;
    }

    private void setFar(Path path) {
        far = true;
        if (farUntil == 0 && path != null) farUntil = w.owner.level().getGameTime() + farLimit(CompanionMiningNavigation.length(w.owner, path));
    }

    /** The walk to this goal is over by other means (the goal was dropped, the work ended). */
    void abandon() { told("ABANDONED", "goal dropped"); }

    private void begin(BlockPos block) {
        told("ABANDONED", "replaced");
        goal = block.immutable(); farUntil = 0; from = w.owner.blockPosition(); began = w.owner.level().getGameTime(); far = false;
        meter = CompanionNavigation.meterOf(w.owner);
    }

    /** The route-outcome event of the journal, once per walk. */
    private void told(String outcome, String reason) {
        if (meter == null || goal == null) return;
        CompanionNavigation.outcome(w.owner, from, goal, outcome, reason, w.owner.level().getGameTime() - began, Math.sqrt(from.distSqr(goal)),
                CompanionNavigation.meterOf(w.owner).since(meter));
        meter = null;
    }

    private Reach failed(Collection<BlockPos> blocks, String reason) {
        // One event per ten seconds, whatever the reasons alternate; the failures in between are counted in it.
        long now = w.owner.level().getGameTime();
        failures++;
        if (!blocks.isEmpty() && now - failedAt >= 200) {
            BlockPos block = blocks.iterator().next();
            var metered = CompanionNavigation.meterOf(w.owner);
            CompanionNavigation.outcome(w.owner, w.owner.blockPosition(), block, "FAILED", reason + " (" + failures + " searches)", 0,
                    w.owner.position().distanceTo(block.getCenter()), failedMeter == null ? metered : metered.since(failedMeter));
            failedMeter = metered; failedAt = now; failures = 0;
        }
        return new Reach(Kind.NONE, null, null, 0, reason);
    }

    private static boolean reaches(Path path, Set<BlockPos> cells, int accuracy) {
        return path != null && path.canReach() && (accuracy > 0 || cells.contains(path.getTarget()));
    }
}
