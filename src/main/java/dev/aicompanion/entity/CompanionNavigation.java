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

    CompanionNavigation(Mob mob, Level level) { super(mob, level); }

    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.evaluator = new CompanionNodeEvaluator();
        this.nodeEvaluator = evaluator;
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /** Every createPath variant ends here. */
    @Override protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        long start = System.nanoTime();
        try {
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        } finally {
            long spent = System.nanoTime() - start, now = level.getGameTime();
            if (now != tickOf) { tickOf = now; tickNanos = 0; }
            tickNanos += spent; maxTickNanos = Math.max(maxTickNanos, tickNanos); nanos += spent;
        }
    }

    /** Cells expanded and nanoseconds spent in searches so far; the difference of two readings is the cost of what lay between. */
    public record Meter(long nodes, long nanos, long maxTickNanos) {
        public double ms() { return nanos / 1e6; }
        public double maxTickMs() { return maxTickNanos / 1e6; }
        public Meter since(Meter before) { return new Meter(nodes - before.nodes, nanos - before.nanos, maxTickNanos); }
    }

    public Meter meter() { return new Meter(evaluator == null ? 0 : evaluator.expanded(), nanos, maxTickNanos); }
    public void resetMaxTick() { maxTickNanos = 0; }

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
