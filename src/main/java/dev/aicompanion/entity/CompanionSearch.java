package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The companion's own route search: A* over the cells he can stand or swim in, the cost of a move in ticks (CompanionMoves),
 * the straight distance to the targets over his walking speed as the estimate (it never over-estimates, so the first route
 * that arrives is the quickest). Limits as the game's search had: a cell farther than {@code range} from the start is not
 * expanded, no route is longer than {@code range} (a goal beyond it is far: walked in legs), and one search expands at most
 * {@link #BUDGET} cells. Gives the game's {@link Path}, so the game walks it; one that does not arrive is the way to the cell
 * that came nearest, as the game's was.
 */
final class CompanionSearch {
    /** Cells one search may expand: the game's 2048 for a range of 128, three times over (CompanionEntity). */
    static final int BUDGET = 2048 * 3;

    /** A path of this search, told from the game's own (CompanionNavigation leaves it to be walked as it is). */
    static final class Found extends Path {
        Found(List<Node> nodes, BlockPos target, boolean reached) { super(nodes, target, reached); }
    }

    private record Entry(Node node, float f) { }

    private final CompanionNodeEvaluator eval;
    private final Set<BlockPos> targets;
    private final int accuracy;
    private final float range;
    private final int minX, maxX, minZ, maxZ;
    private long expanded;

    CompanionSearch(CompanionNodeEvaluator eval, Set<BlockPos> targets, int accuracy, float range) {
        this.eval = eval; this.targets = targets; this.accuracy = accuracy; this.range = range;
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos t : targets) { x0 = Math.min(x0, t.getX()); x1 = Math.max(x1, t.getX()); z0 = Math.min(z0, t.getZ()); z1 = Math.max(z1, t.getZ()); }
        minX = x0; maxX = x1; minZ = z0; maxZ = z1;
    }

    /** Cells expanded by the search. */
    long expanded() { return expanded; }

    /** The path from where the evaluator was prepared at, or null if he has no cell to stand in. */
    Path run(Mob mob) {
        Node start = eval.getStart();
        if (start == null) return null;
        CompanionMoves moves = new CompanionMoves(eval, mob);
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        List<Node> closed = new ArrayList<>();
        start.f = estimate(start);
        open.add(new Entry(start, start.f));
        while (!open.isEmpty() && expanded < BUDGET) {
            Node node = open.poll().node;
            if (node.closed) continue;
            node.closed = true; expanded++;
            closed.add(node);
            BlockPos hit = reached(node);
            if (hit != null) return build(node, hit, true);
            if (node.distanceTo(start) < range) moves.expand(node, (to, ticks) -> {
                float g = node.g + ticks, walked = node.walkedDistance + node.distanceTo(to);
                if (to.closed || walked >= range || (to.f > 0 && g >= to.g)) return;
                to.cameFrom = node; to.g = g; to.walkedDistance = walked; to.f = g + estimate(to);
                open.add(new Entry(to, to.f));
            });
        }
        // None arrives: the way to the cell nearest to a target, as the game gave it.
        Node best = start; BlockPos aim = targets.iterator().next(); double least = Double.MAX_VALUE;
        for (Node node : closed) for (BlockPos t : targets) {
            double distance = node.distanceToSqr(t);
            if (distance < least) { least = distance; best = node; aim = t; }
        }
        return build(best, aim, false);
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
