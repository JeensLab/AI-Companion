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
 * The search behind it is still the game's; every walk it leads ends in one {@code route_outcome} event of the journal.
 */
final class CompanionRoute {
    /** What is wanted of the cells a block is worked from (see CompanionMiningNavigation.standCells). */
    record Need(double reach, boolean clearLeaves, boolean avoidStandingOnTarget, boolean forTheDrop) { }

    /** WALK: a whole route by one search. FAR: none within one search, but one a wider search finds (walked on, searched again). NONE: no way. */
    enum Kind { WALK, FAR, NONE }

    /** A block chosen by the walk to a cell it can be worked from, that walk and its length in blocks; NONE says why. */
    record Reach(Kind kind, BlockPos block, Path path, double length, String reason) {
        boolean found() { return kind != Kind.NONE; }
    }

    private static final int PATIENCE = 160;

    private final CompanionWork w;
    // The walk as the journal tells it: to what, from where, when, what the searches had cost by then; null once told.
    private BlockPos goal, from;
    private long began;
    private boolean far;
    private Path farPath;
    private String failedReason = "";
    private long failedAt;
    private int failures;
    private CompanionNavigation.Meter meter;

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
        Kind kind = Kind.WALK;
        Path path = owner.getNavigation().createPath(blockAt.keySet(), 0);
        if (!reaches(path, blockAt)) {
            kind = Kind.FAR;
            path = owner.getNavigation() instanceof CompanionNavigation nav ? nav.wide(blockAt.keySet(), 0) : null;
            if (!reaches(path, blockAt)) return failed(blocks, "no route");
            farPath = path;
        }
        return new Reach(kind, blockAt.get(path.getTarget()), path, CompanionMiningNavigation.length(owner, path), kind == Kind.FAR ? "far" : "near");
    }

    Reach to(BlockPos block, Need need) { return nearest(List.of(block), need); }

    /** One search to any of these cells: the way if it comes all the way, else null. For the short last steps, not for far goals. */
    Path toCells(Set<BlockPos> cells) {
        Path path = w.owner.getNavigation().createPath(cells, 0);
        return path != null && path.canReach() ? path : null;
    }

    void walk(Path path) { CompanionMiningNavigation.walk(w.owner, path); }

    /** The leg being walked is over (or he is stuck in it): time for the next search. */
    boolean legOver() { return w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck(); }

    void stop() { w.owner.getNavigation().stop(); }

    /** A far goal in legs (CompanionTravel); NEAR once within {@code near} blocks of it. */
    CompanionTravel.Step legs(BlockPos target, double near) { return w.owner.travel().toward(target, near); }

    /** The legs are over, the goal reached or not: the walk is told to the journal if it was not yet. */
    void legsEnd(boolean arrived) { if (arrived) w.owner.travel().arrived(); else w.owner.travel().reset(); }

    /**
     * One tick of the approach to a block he works from a standing place (ore, a bench to take): 1 in reach and sight (and, for the
     * drop, where it can be picked up), 0 walking, -1 no way or no progress. A walk that needed a wider search counts walking as
     * progress; the others, coming closer to the block.
     */
    int approach(ServerLevel level, BlockPos block, boolean forTheDrop) {
        if (!block.equals(goal)) begin(block);
        if (w.path != null && w.path == farPath) far = true;
        double distance = w.owner.distanceToSqr(block.getCenter());
        if (distance <= 3.8D * 3.8D && CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), block)
                && (!forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, w.owner.blockPosition()))) { w.owner.getNavigation().stop(); told("ARRIVED", far ? "far" : "near"); return 1; }
        w.breaking.reset();
        if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = level.getGameTime(); }
        // Along a route longer than one search the straight distance first grows: walking is progress then.
        if (far && (w.approachMoved == null || w.owner.position().distanceToSqr(w.approachMoved) > .7 * .7)) { w.approachMoved = w.owner.position(); w.lastProgressTick = level.getGameTime(); }
        if (level.getGameTime() - w.lastProgressTick > PATIENCE) { told("FAILED", "no progress"); return -1; }
        if (w.path == null || legOver()) {
            Reach reach = to(block, new Need(3.5, false, true, forTheDrop));
            w.path = reach.path();
            if (reach.kind() == Kind.FAR) far = true;
            // A failed search was told by nearest() already.
            if (!reach.found()) { meter = null; return -1; }
        }
        if (w.path == null || !w.path.canReach()) { told("FAILED", "no route"); return -1; }
        walk(w.path);
        return 0;
    }

    /** The walk to this goal is over by other means (the goal was dropped, the work ended). */
    void abandon() { told("ABANDONED", "goal dropped"); }

    private void begin(BlockPos block) {
        told("ABANDONED", "replaced");
        goal = block.immutable(); farPath = null; from = w.owner.blockPosition(); began = w.owner.level().getGameTime(); far = false;
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
        // One event per kind of failure per ten seconds, however often the choice is searched again.
        long now = w.owner.level().getGameTime();
        failures++;
        if (!blocks.isEmpty() && (!reason.equals(failedReason) || now - failedAt >= 200)) {
            BlockPos block = blocks.iterator().next();
            CompanionNavigation.outcome(w.owner, w.owner.blockPosition(), block, "FAILED", reason + " (" + failures + " searches)", 0,
                    w.owner.position().distanceTo(block.getCenter()), new CompanionNavigation.Meter(0, 0, 0));
            failedReason = reason; failedAt = now; failures = 0;
        }
        return new Reach(Kind.NONE, null, null, 0, reason);
    }

    private static boolean reaches(Path path, Map<BlockPos, BlockPos> blockAt) {
        return path != null && path.canReach() && blockAt.containsKey(path.getTarget());
    }
}
