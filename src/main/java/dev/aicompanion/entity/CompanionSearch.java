package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The companion's own route search: A* over the cells he can stand or swim in, the cost of a move in ticks (CompanionMoves),
 * the straight distance to the targets over his walking speed as the estimate (weighted a little: the first route that arrives is
 * a few percent from the quickest, and far fewer cells are expanded). Limits as the game's search had: a cell farther than
 * {@code range} from the start is not expanded, no route is longer than {@code range} (a goal beyond it is far: walked in legs),
 * and one search expands at most {@link #BUDGET} cells. The search is resumable: {@link #advance} expands a share of the cells and
 * keeps the open set, so it goes on over the ticks (CompanionNavigation gives each tick its share) and the best part found so far
 * can be walked meanwhile ({@link #partialFrom}). It owns its evaluator (and so its cells). It ends FOUND (the route, a game
 * {@link Path}) or EXHAUSTED (the way to the cell that came nearest, as the game's was).
 */
final class CompanionSearch {
    /** Cells one search may expand: the game's 2048 for a range of 128, three times over (CompanionEntity). */
    static final int BUDGET = 2048 * 3;
    /** The estimate is this much dearer than the straight way: a hilly route (climbs cost more than the estimate knows) is found in fewer cells. */
    static final float WEIGHT = 1.5F;
    /** A goal farther than the range is not seen by the search: it stops once a cell this share of the range nearer to it is found (the next leg searches on from there). */
    static final float FAR_LEG = .5F;
    /** This search's own cell budget: BUDGET, lowered by a GameTest only. */
    int budget = BUDGET;

    enum State { RUNNING, FOUND, EXHAUSTED }

    /** A path of this search, told from the game's own (CompanionNavigation leaves it to be walked as it is). */
    static final class Found extends Path {
        Found(List<Node> nodes, BlockPos target, boolean reached) { super(nodes, target, reached); }
    }

    private final CompanionNodeEvaluator eval;
    private final Set<BlockPos> targets;
    private final int accuracy;
    private final float range;
    private final int minX, maxX, minZ, maxZ;
    private final CompanionMoves moves;
    private final BinaryHeap open = new BinaryHeap();
    private final List<Node> closed = new ArrayList<>();
    private final Node start;
    private final CompanionMoves.Sink sink = this::offer;
    private Node current;
    // For a goal beyond the range: the estimate to stop at, and the cell nearest to it so far.
    private final float stopAt;
    private Node nearestNode;
    private float nearestEstimate = Float.MAX_VALUE;
    private long expanded;
    private State state = State.RUNNING;
    private Found result;
    private Found partial;
    private int partialOf = -1;
    /** Where it began and when (game ticks), and when it ended. */
    final Vec3 origin;
    final BlockPos originBlock;
    /** The whole reachable region was expanded, no target in it: the same start asked for other targets is answered from it ({@link #answer}). */
    private boolean complete;
    /** Some cell was left unexpanded for the range: a wider search may find more. */
    private boolean limited;
    final long born;
    long ended;

    private CompanionSearch(CompanionNodeEvaluator eval, Mob mob, Node start, Set<BlockPos> targets, int accuracy, float range, long now) {
        this.eval = eval; this.start = start; this.targets = targets; this.accuracy = accuracy; this.range = range; this.born = now;
        this.moves = new CompanionMoves(eval, mob);
        this.origin = mob.position(); this.originBlock = mob.blockPosition();
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos t : targets) { x0 = Math.min(x0, t.getX()); x1 = Math.max(x1, t.getX()); z0 = Math.min(z0, t.getZ()); z1 = Math.max(z1, t.getZ()); }
        minX = x0; maxX = x1; minZ = z0; maxZ = z1;
        start.f = estimate(start);
        stopAt = start.f > range * CompanionMoves.TICKS_PER_BLOCK ? start.f - FAR_LEG * range * CompanionMoves.TICKS_PER_BLOCK : -1;
        open.insert(start);
    }

    /** A finished search over the region {@code from} expanded, for these targets. */
    private CompanionSearch(CompanionSearch from, Set<BlockPos> targets, int accuracy, long now) {
        this.eval = from.eval; this.moves = from.moves; this.start = from.start; this.origin = from.origin; this.originBlock = from.originBlock;
        this.range = from.range; this.targets = targets; this.accuracy = accuracy; this.born = now; this.stopAt = -1; this.complete = true; this.limited = from.limited;
        closed.addAll(from.closed);
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos t : targets) { x0 = Math.min(x0, t.getX()); x1 = Math.max(x1, t.getX()); z0 = Math.min(z0, t.getZ()); z1 = Math.max(z1, t.getZ()); }
        minX = x0; maxX = x1; minZ = z0; maxZ = z1;
    }

    /**
     * The answer for these targets from the region this search expanded whole (it found none of its own): none of them is in it either,
     * so the way to the cell nearest to one is the answer, with no cell expanded; null if one is, or the region was not whole.
     */
    CompanionSearch answer(Set<BlockPos> targets, int accuracy, long now) {
        if (!complete) return null;
        CompanionSearch quick = new CompanionSearch(this, targets, accuracy, now);
        for (Node node : closed) if (quick.reached(node) != null) return null;
        quick.state = State.EXHAUSTED; quick.result = quick.nearest(); quick.ended = now;
        return quick;
    }

    boolean complete() { return complete; }
    boolean limited() { return limited; }

    /** A search from where the (prepared) evaluator has him, or null if he has no cell to stand in. */
    static CompanionSearch create(CompanionNodeEvaluator eval, Mob mob, Set<BlockPos> targets, int accuracy, float range, long now) {
        Node start = eval.getStart();
        return start == null ? null : new CompanionSearch(eval, mob, start, targets, accuracy, range, now);
    }

    /** Cells expanded by the search. */
    long expanded() { return expanded; }
    State state() { return state; }

    /** Expands up to {@code cells} more cells (and no more than the budget of the whole search). */
    State advance(int cells, long now) {
        if (state != State.RUNNING) return state;
        long limit = Math.min(budget, expanded + cells);
        while (!open.isEmpty() && expanded < limit) {
            Node node = open.pop();
            if (node.closed) continue;
            node.closed = true; expanded++;
            closed.add(node);
            BlockPos hit = reached(node);
            if (hit != null) return finish(State.FOUND, build(node, hit, true), now);
            if (stopAt >= 0) {
                float est = estimate(node);
                if (est < nearestEstimate) { nearestEstimate = est; nearestNode = node; }
                if (est <= stopAt) return finish(State.EXHAUSTED, build(node, targets.iterator().next(), false), now);
            }
            if (node.distanceTo(start) < range) { current = node; moves.expand(node, sink); } else limited = true;
        }
        if (open.isEmpty() || expanded >= budget) { complete = open.isEmpty() && stopAt < 0; finish(State.EXHAUSTED, nearest(), now); }
        return state;
    }

    private State finish(State end, Found found, long now) { state = end; result = found; ended = now; eval.done(); open.clear(); return end; }

    private void offer(Node to, float ticks) {
        Node node = current;
        float g = node.g + ticks, walked = node.walkedDistance + node.distanceTo(to);
        if (walked >= range) limited = true;
        if (to.closed || walked >= range || (to.f > 0 && g >= to.g)) return;
        to.cameFrom = node; to.g = g; to.walkedDistance = walked;
        float f = g + WEIGHT * estimate(to);
        if (to.inOpenSet()) open.changeCost(to, f); else { to.f = f; open.insert(to); }
    }

    /** None arrives: the way to the cell nearest to a target, as the game gave it. */
    private Found nearest() {
        if (nearestNode != null) return build(nearestNode, targets.iterator().next(), false);
        Node best = start; BlockPos aim = targets.iterator().next(); double least = Double.MAX_VALUE;
        for (Node node : closed) for (BlockPos t : targets) {
            double distance = node.distanceToSqr(t);
            if (distance < least) { least = distance; best = node; aim = t; }
        }
        return build(best, aim, false);
    }

    /** The finished search's path as it leads from {@code at}: the whole route or the way to the nearest cell, from the cell of it he is at. */
    Path from(Vec3 at) { return result == null ? null : trimmed(result, at, result.canReach()); }

    /** The way to the cell nearest to a target among those expanded so far, from where he is; null if he is not on it. Walkable while the search goes on. */
    Path partialFrom(Vec3 at) {
        if (state != State.RUNNING) return from(at);
        if (partialOf != closed.size()) { partialOf = closed.size(); partial = nearest(); }
        return trimmed(partial, at, false);
    }

    /** The path from the cell of it nearest to {@code at} (within three blocks); the whole path if there is none and {@code keep}, else null. */
    private static Path trimmed(Path path, Vec3 at, boolean keep) {
        int best = -1; double least = 9;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            double dx = node.x + .5 - at.x, dy = node.y - at.y, dz = node.z + .5 - at.z, d = dx * dx + dy * dy + dz * dz;
            if (d < least) { least = d; best = i; }
        }
        if (best <= 0) return best == 0 || keep ? path : null;
        List<Node> nodes = new ArrayList<>(path.getNodeCount() - best);
        for (int i = best; i < path.getNodeCount(); i++) nodes.add(path.getNode(i));
        return new Found(nodes, path.getTarget(), path.canReach());
    }

    /** The target this cell is at (within the accuracy, as the game counted it: blocks along the axes), or null. */
    private BlockPos reached(Node node) {
        if (accuracy == 0) { BlockPos at = node.asBlockPos(); return targets.contains(at) ? at : null; }
        if (node.x < minX - accuracy || node.x > maxX + accuracy || node.z < minZ - accuracy || node.z > maxZ + accuracy) return null;
        BlockPos hit = null; float least = Float.MAX_VALUE;
        for (BlockPos t : targets) {
            float distance = node.distanceManhattan(t);
            if (distance <= accuracy && distance < least) { least = distance; hit = t; }
        }
        return hit;
    }

    /** Ticks to walk the straight way along the ground to the targets' bounding box, the accuracy deducted. */
    private float estimate(Node node) {
        double dx = Math.max(0, Math.max(minX - node.x, node.x - maxX)), dz = Math.max(0, Math.max(minZ - node.z, node.z - maxZ));
        return (float)Math.max(0, Math.sqrt(dx * dx + dz * dz) - accuracy) * CompanionMoves.TICKS_PER_BLOCK;
    }

    private static Found build(Node end, BlockPos target, boolean reached) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = end; node != null; node = node.cameFrom) nodes.add(0, node);
        return new Found(nodes, target, reached);
    }
}
