package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

import java.util.Set;

/**
 * The game's ground navigation with the companion's own route search ({@link #search}: the single route uses it) beside the game's
 * walking search (the game's goals still use that one), and a meter on both: cells the searches expanded and the time they took
 * (the polygon of GameTests and the route-outcome event of the journal read it).
 */
public final class CompanionNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
    private CompanionNodeEvaluator evaluator;
    private long nanos, tickNanos, tickCells, tickOf = -1, maxTickNanos, searched;

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
            spent(System.nanoTime() - start, 0);
        }
    }

    /**
     * The companion's own search (CompanionSearch) to one of these cells, within {@code accuracy} blocks of it, as far as
     * {@code range} from here: what the game's createPath was, with the same limits. A path he is walking to one of them is kept.
     */
    Path search(Set<BlockPos> targets, int accuracy, float range) {
        if (targets.isEmpty() || mob.getY() < level.getMinBuildHeight() || !canUpdatePath()) return null;
        if (path instanceof CompanionSearch.Found && !path.isDone() && targets.contains(path.getTarget())) return path;
        long start = System.nanoTime();
        CompanionSearch search = null;
        Path found;
        try {
            int i = (int)(range + 8);
            BlockPos at = mob.blockPosition();
            evaluator.prepare(new net.minecraft.world.level.PathNavigationRegion(level, at.offset(-i, -i, -i), at.offset(i, i, i)), mob);
            try {
                search = new CompanionSearch(evaluator, targets, accuracy, range);
                found = search.run(mob);
            } finally {
                evaluator.done();
            }
            return found;
        } finally {
            if (search != null) searched += search.expanded();
            spent(System.nanoTime() - start, search == null ? 0 : search.expanded());
        }
    }

    public Path search(Set<BlockPos> targets, int accuracy) { return search(targets, accuracy, followRange(1)); }

    /** The same to one cell; a goal in the air is aimed at the ground under it, one in a wall at the air over it, as the game did. */
    Path search(BlockPos pos, int accuracy) {
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX()), net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) return null;
        if (chunk.getBlockState(pos).isAir()) {
            BlockPos below = pos.below();
            while (below.getY() > level.getMinBuildHeight() && chunk.getBlockState(below).isAir()) below = below.below();
            if (below.getY() > level.getMinBuildHeight()) return search(Set.of(below.above()), accuracy);
            while (below.getY() < level.getMaxBuildHeight() && chunk.getBlockState(below).isAir()) below = below.above();
            pos = below;
        }
        if (!chunk.getBlockState(pos).isSolid()) return search(Set.of(pos), accuracy);
        BlockPos above = pos.above();
        while (above.getY() < level.getMaxBuildHeight() && chunk.getBlockState(above).isSolid()) above = above.above();
        return search(Set.of(above), accuracy);
    }

    /**
     * The same search with three times the range: a goal beyond one search is far, not lost, when this one finds the way. Same cell
     * budget, so no dearer than one search that fails.
     */
    Path wide(Set<BlockPos> targets, int accuracy) { return search(targets, accuracy, followRange(3)); }

    private float followRange(int times) { return (float)(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) * times); }

    /** The time of a search, as the meter has it. */
    private void spent(long spent, long cells) {
        long now = level.getGameTime();
        if (now != tickOf) { tickOf = now; tickNanos = tickCells = 0; }
        tickCells += cells; tickNanos += spent; maxTickNanos = Math.max(maxTickNanos, tickNanos); nanos += spent;
    }

    private long wideTick = -1;
    /** One wide search per companion per game tick: the first takes it, the rest are told «not now» (the choice loops multiplied them). */
    boolean takeWide() { long now = level.getGameTime(); if (wideTick == now) return false; wideTick = now; return true; }
    boolean wideSpent() { return wideTick == level.getGameTime(); }
    /** Cells one tick's searches may expand together: counted in cells, not ms, so the answer does not depend on the machine's speed. */
    static final int TICK_CELLS = 3000;
    /** The searches of this tick have expanded that many cells already: a choice that goes on searching waits for the next one. */
    boolean overBudget() { return tickOf == level.getGameTime() && tickCells >= TICK_CELLS; }

    /** Cells expanded and nanoseconds spent in searches so far; the difference of two readings is the cost of what lay between. */
    public record Meter(long nodes, long nanos, long maxTickNanos) {
        public double ms() { return nanos / 1e6; }
        public double maxTickMs() { return maxTickNanos / 1e6; }
        public Meter since(Meter before) { return new Meter(nodes - before.nodes, nanos - before.nanos, maxTickNanos); }
    }

    /** The game recomputes a path to the goal of its own last search when a block near it changes; ours is not recomputed so. */
    @Override public void recomputePath() { if (!(path instanceof CompanionSearch.Found)) super.recomputePath(); }

    public Meter meter() { return new Meter((evaluator == null ? 0 : evaluator.expanded()) + searched, nanos, maxTickNanos); }
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
