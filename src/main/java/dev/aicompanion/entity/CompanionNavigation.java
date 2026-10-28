package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

import java.util.Set;

/**
 * The game's ground navigation with the companion's walking search, and a meter on it: cells the searches expanded and the time
 * they took (the polygon of GameTests and the route-outcome event of the journal read it). Nothing about the routes changes.
 */
public final class CompanionNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
    private CompanionNodeEvaluator evaluator;
    private long nanos, tickNanos, tickOf = -1, maxTickNanos;
    // The cell budget of one search (the game's, times its multiplier) and what the searches took: the last one, and this tick's.
    private int maxVisited;
    private float multiplier = 1F;
    private long lastCells, tickCells;
    private int tickWides, maxWides;

    CompanionNavigation(Mob mob, Level level) { super(mob, level); }

    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.evaluator = new CompanionNodeEvaluator();
        this.nodeEvaluator = evaluator;
        this.nodeEvaluator.setCanPassDoors(true);
        this.maxVisited = maxVisitedNodes;
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /** A smaller cell budget for every search from now on (GameTests that need a search to run out of it). */
    public void limitSearch(int cells) { multiplier = (float)cells / maxVisited; super.setMaxVisitedNodesMultiplier(multiplier); }

    /** Every createPath variant ends here. */
    @Override protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        long start = System.nanoTime(), cellsBefore = evaluator.expanded();
        try {
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        } finally {
            long spent = System.nanoTime() - start, now = level.getGameTime();
            if (now != tickOf) { tickOf = now; tickNanos = 0; tickCells = 0; }
            lastCells = evaluator.expanded() - cellsBefore; tickCells += lastCells;
            tickNanos += spent; maxTickNanos = Math.max(maxTickNanos, tickNanos); nanos += spent;
        }
    }

    /**
     * The same search with a much longer range: a goal beyond one search is far, not lost, when this one finds the way. Same cell
     * budget, so no dearer than one search that fails. The game's search counts the distance walked so far, and re-counts it every
     * time a cell is reached from another side, so along a wall the figure grows well beyond the way itself (a 190-block way round
     * a wall ten blocks wide was cut off at three times the range of 80): six times keeps such ways in.
     */
    public Path wide(Set<BlockPos> targets, int accuracy) {
        long now = level.getGameTime();
        if (now != wideCountOf) { wideCountOf = now; tickWides = 0; }
        maxWides = Math.max(maxWides, ++tickWides);
        return createPath(targets, 8, false, accuracy, (float)(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) * 6));
    }

    private long wideTick = -1, wideCountOf = -1;
    /** One wide search per companion per game tick: the first takes it, the rest are told «not now» (the choice loops multiplied them). */
    boolean takeWide() { long now = level.getGameTime(); if (wideTick == now) return false; wideTick = now; return true; }
    boolean wideSpent() { return wideTick == level.getGameTime(); }
    /** The searches of this tick have expanded this many cells already: a choice that goes on searching waits for the next tick. */
    boolean overBudget() { return tickOf == level.getGameTime() && tickCells > CELLS_PER_TICK; }
    private static final int CELLS_PER_TICK = 3000;
    /** The last search stopped at its cell budget: it saw only part of what it could reach, so it proves nothing about the cells it did not get to. */
    boolean lastSearchExhausted() { return lastCells >= (int)(maxVisited * multiplier) - 1; }
    /** The most wide searches of one tick so far (at most one is allowed). */
    public int maxWideSearchesPerTick() { return maxWides; }

    /** Cells expanded and nanoseconds spent in searches so far; the difference of two readings is the cost of what lay between. */
    public record Meter(long nodes, long nanos, long maxTickNanos) {
        public double ms() { return nanos / 1e6; }
        public double maxTickMs() { return maxTickNanos / 1e6; }
        public Meter since(Meter before) { return new Meter(nodes - before.nodes, nanos - before.nanos, maxTickNanos); }
    }

    public Meter meter() { return new Meter(evaluator == null ? 0 : evaluator.expanded(), nanos, maxTickNanos); }
    public void resetMaxTick() { maxTickNanos = 0; maxWides = 0; }

    static Meter meterOf(CompanionEntity owner) { return owner.getNavigation() instanceof CompanionNavigation nav ? nav.meter() : new Meter(0, 0, 0); }

    /**
     * The route-outcome event of the journal: who led (the work), from where, to where, how it ended and why, ticks, straight distance in
     * blocks, cells and ms the searches took in between.
     */
    static void outcome(CompanionEntity owner, BlockPos from, BlockPos to, String outcome, String reason, long ticks, double length, Meter spent) {
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "route_outcome", "entity", owner.getUUID(),
                "activity", owner.work.job == null ? "none" : owner.work.job.name(), "from", from, "to", to, "outcome", outcome,
                "reason", reason, "ticks", ticks, "distance", Math.round(length), "nodes", spent.nodes(), "ms", Math.round(spent.ms() * 10) / 10.0);
    }
}
