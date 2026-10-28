package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

import java.util.Set;

/**
 * The game's ground navigation (its path follower) with every path built by the companion's own route search ({@link #search}), and
 * a meter on it: cells the searches expanded and the time they took (the polygon of GameTests and the route-outcome event read it).
 */
public final class CompanionNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
    private CompanionNodeEvaluator evaluator;
    private long nanos, tickNanos, tickCells, tickOf = -1, maxTickNanos, maxTickCells, tickShared, searched;
    // The cell budget of one search (CompanionSearch.BUDGET unless a GameTest lowers it) and whether the last one ended at it.
    private int budget = CompanionSearch.BUDGET;
    private boolean ranOut;

    CompanionNavigation(Mob mob, Level level) { super(mob, level); }

    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.evaluator = new CompanionNodeEvaluator();
        this.nodeEvaluator = evaluator;
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /**
     * Every createPath variant ends here, so the game's goals (stroll, doors, the melee chase) walk our routes too; the game's own
     * search never runs. A search not done within this tick's share answers null: the goal asks again next tick.
     * Ticket 13: these callers (combat, stroll) must pass «no world changes» to the search once it has dig/place moves.
     */
    @Override protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        Path found = search(targets, accuracy, followRange);
        return found == BUSY ? null : found;
    }

    /** The answer to a search that has not finished within this tick's cells: ask again next tick (the search goes on where it was). */
    static final Path BUSY = new Path(java.util.List.of(), BlockPos.ZERO, false);
    /** The last search that finished found none and stopped at its cell budget: it saw only part of what it could reach, so it proves nothing about the cells it did not get to. */
    boolean lastSearchExhausted() { return ranOut; }
    /** A finished search that did not arrive is kept (and not repeated) this many ticks; one in progress is dropped after this many. */
    private static final int KEEP = 40, STALE = 80;
    private record Key(Set<BlockPos> targets, int accuracy, float range) { }
    private final java.util.Map<Key, CompanionSearch> searches = new java.util.LinkedHashMap<>();
    /** Per range: the last search that expanded the whole region it could reach and found none of its targets. */
    private final java.util.Map<Float, CompanionSearch> explored = new java.util.HashMap<>();

    /**
     * The companion's own search (CompanionSearch) to one of these cells, within {@code accuracy} blocks of it, as far as
     * {@code range} from here: what the game's createPath was, with the same limits. A path he is walking to one of them is kept.
     * A search that needs more cells than this tick has left answers {@link #BUSY} and goes on at the next ask.
     */
    Path search(Set<BlockPos> targets, int accuracy, float range) { return search(targets, accuracy, range, false); }

    /** {@code whole}: the answer is wanted now (the diagnosis of a failure, asked once in seconds): the search goes on to its end whatever this tick's share. */
    Path search(Set<BlockPos> targets, int accuracy, float range, boolean whole) {
        if (path instanceof CompanionSearch.Found && !path.isDone() && targets.contains(path.getTarget())) return path;
        CompanionSearch search = advance(targets, accuracy, range, whole, CHOICE_CELLS);
        if (search == null) return null;
        if (search.state() == CompanionSearch.State.RUNNING) return BUSY;
        ranOut = search.state() == CompanionSearch.State.EXHAUSTED && !search.complete();
        return search.from(mob.position());
    }

    /**
     * This tick's share of the search to these cells: the one begun before is gone on with, else a new one is begun (null if he
     * has no cell to stand in). A finished one that did not arrive is given again for a while, not searched from scratch.
     */
    CompanionSearch advance(Set<BlockPos> targets, int accuracy, float range) { return advance(targets, accuracy, range, false, TICK_CELLS); }

    private CompanionSearch advance(Set<BlockPos> targets, int accuracy, float range, boolean whole, int cap) {
        if (targets.isEmpty() || mob.getY() < level.getMinBuildHeight() || !canUpdatePath()) return null;
        long now = level.getGameTime();
        Key key = new Key(Set.copyOf(targets), accuracy, range);
        CompanionSearch search = searches.get(key);
        if (search != null && stale(search, now)) { searches.remove(key); search = null; }
        roll(now);
        long start = System.nanoTime();
        long cells = 0;
        try {
            if (search == null) {
                if (searches.size() >= 8) searches.values().removeIf(old -> stale(old, now) || old.state() == CompanionSearch.State.RUNNING && now - old.born > 20);
                search = known(key, now);
                if (search == null) search = begin(key, now);
                if (search == null) return null;
                searches.put(key, search);
            }
            int share = whole ? Integer.MAX_VALUE : (int)Math.max(0, cap - tickCells);
            long before = search.expanded();
            search.advance(share, now);
            cells = search.expanded() - before;
            searched += cells;
            if (search.state() == CompanionSearch.State.FOUND) searches.remove(key);
            else if (search.complete() && search.expanded() > 0) explored.put(key.range, search);
            return search;
        } finally {
            spent(System.nanoTime() - start, cells);
            if (!whole) { tickShared += cells; maxTickCells = Math.max(maxTickCells, tickShared); }
        }
    }

    /** Gone on with no longer: running too long, or finished and not arrived for good (kept a while; not if he has moved on from where it ended). */
    private boolean stale(CompanionSearch search, long now) {
        if (search.state() == CompanionSearch.State.RUNNING) return now - search.born > STALE;
        if (search.state() == CompanionSearch.State.FOUND) return true;
        if (now - search.ended > KEEP) return true;
        if (search.origin.distanceToSqr(mob.position()) <= 9) return false;
        Path way = search.from(mob.position());
        return way == null || way.getNodeCount() < 2;
    }

    /** The whole region reachable from here, expanded lately by a search that found none of its targets: the answer for others (none in it). */
    private CompanionSearch known(Key key, long now) {
        CompanionSearch whole = explored.get(key.range);
        if (whole == null) return null;
        if (now - whole.ended > KEEP || !whole.originBlock.equals(mob.blockPosition())) { explored.remove(key.range); return null; }
        return whole.answer(key.targets, key.accuracy, now);
    }

    private CompanionSearch begin(Key key, long now) {
        int i = (int)(key.range + 8);
        BlockPos at = mob.blockPosition();
        CompanionNodeEvaluator own = new CompanionNodeEvaluator();
        own.setCanPassDoors(evaluator.canPassDoors()); own.setCanOpenDoors(evaluator.canOpenDoors());
        own.setCanFloat(evaluator.canFloat()); own.setCanWalkOverFences(evaluator.canWalkOverFences());
        own.prepare(new net.minecraft.world.level.PathNavigationRegion(level, at.offset(-i, -i, -i), at.offset(i, i, i)), mob);
        CompanionSearch search = CompanionSearch.create(own, mob, key.targets, key.accuracy, key.range, now);
        if (search == null) own.done(); else search.budget = Math.min(search.budget, budget);
        return search;
    }

    /** The walk is over or its goal changed: the search to it is not gone on with. */
    void forget(CompanionSearch search) { searches.values().remove(search); }

    public Path search(Set<BlockPos> targets, int accuracy) { return search(targets, accuracy, followRange(1)); }

    /** The same to one cell; a goal in the air is aimed at the ground under it, one in a wall at the air over it, as the game did. */
    Path search(BlockPos pos, int accuracy) {
        Set<BlockPos> cells = cellsOf(pos);
        return cells == null ? null : search(cells, accuracy);
    }

    /** The cell to search to for this goal (see {@link #search(BlockPos, int)}), or null if its chunk is not loaded. */
    Set<BlockPos> cellsOf(BlockPos pos) {
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX()), net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) return null;
        if (chunk.getBlockState(pos).isAir()) {
            BlockPos below = pos.below();
            while (below.getY() > level.getMinBuildHeight() && chunk.getBlockState(below).isAir()) below = below.below();
            if (below.getY() > level.getMinBuildHeight()) return Set.of(below.above());
            while (below.getY() < level.getMaxBuildHeight() && chunk.getBlockState(below).isAir()) below = below.above();
            pos = below;
        }
        if (!chunk.getBlockState(pos).isSolid()) return Set.of(pos);
        BlockPos above = pos.above();
        while (above.getY() < level.getMaxBuildHeight() && chunk.getBlockState(above).isSolid()) above = above.above();
        return Set.of(above);
    }

    /**
     * The same search with three times the range: a goal beyond one search is far, not lost, when this one finds the way. Same cell
     * budget, so no dearer than one search that fails.
     */
    public Path wide(Set<BlockPos> targets, int accuracy) { return wide(targets, accuracy, false); }
    Path wide(Set<BlockPos> targets, int accuracy, boolean whole) { return search(targets, accuracy, followRange(3), whole); }
    float followRange(int times) { return (float)(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) * times); }


    /** The time of a search, as the meter has it. */
    private void roll(long now) { if (now != tickOf) { tickOf = now; tickNanos = tickCells = tickShared = 0; } }

    private void spent(long spent, long cells) {
        roll(level.getGameTime());
        tickCells += cells; tickNanos += spent; maxTickNanos = Math.max(maxTickNanos, tickNanos); nanos += spent;
    }

    /** Cells one tick's searches may expand together: counted in cells, not ms, so the answer does not depend on the machine's speed. */
    static final int TICK_CELLS = 600;
    /** Choices among many targets ask for the answer now and go through many searches; they get a larger share (a stone's faces: ten ticks to a search at 600 left him standing 10 s). */
    public static final int CHOICE_CELLS = 1500;
    /** The searches of this tick have expanded that many cells already: a choice that goes on searching waits for the next one. */
    boolean overBudget() { return tickOf == level.getGameTime() && tickCells >= CHOICE_CELLS; }

    /** Cells expanded and nanoseconds spent in searches so far; the difference of two readings is the cost of what lay between. */
    public record Meter(long nodes, long nanos, long maxTickNanos) {
        public double ms() { return nanos / 1e6; }
        public double maxTickMs() { return maxTickNanos / 1e6; }
        public Meter since(Meter before) { return new Meter(nodes - before.nodes, nanos - before.nanos, maxTickNanos); }
    }

    public Meter meter() { return new Meter(searched, nanos, maxTickNanos); }
    public void resetMaxTick() { maxTickNanos = 0; maxTickCells = 0; }
    /** The most cells the shared searches of one tick expanded so far (the diagnosis of a failure, asked for the answer now, is not counted). */
    public long maxTickCells() { return maxTickCells; }
    /** A smaller cell budget for every search from now on (GameTests that need a search to run out of it). */
    public void limitSearch(int cells) { budget = cells; }

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
