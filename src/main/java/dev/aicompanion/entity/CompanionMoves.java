package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

/**
 * The moves of the companion's own search and what each costs in ticks: a step to the four sides, a diagonal when both cells
 * beside it can be walked, a climb of one block (two out of the water onto a bank), a drop of as far as he may fall (any
 * depth into water; onto a ladder or vine too: he takes hold), swimming, a jump over a pit of one or two blocks, a climb up and down a ladder or vine. What a cell is (walkable, a door, a fence, danger, water) is the game's own judgement of it,
 * the evaluator's; which neighbour a step may come to follows the game's walking search, so the same routes are found.
 * A new move (dig, place) is one more method called from {@link #expand} that offers its cells with their cost.
 */
final class CompanionMoves {
    /** A cell offered by a move and the ticks it takes to get there. */
    interface Sink { void offer(Node to, float ticks); }

    /** A block on foot takes this long (a player's walk, 0.2158 blocks a tick; a long way is run, faster). */
    static final float TICKS_PER_BLOCK = 1 / .2158F;
    /** Extra for a jump up one block and for each block of a drop. */
    private static final float CLIMB = 6, DROP = 2;
    /** The deepest drop into water that is taken (a fall into water does no harm). */
    private static final int WATER_DROP = 20;
    /** The deepest drop onto land, chasing or not (the game's own allows a mob with a target up to 12 on Normal, 16 on Hard). */
    private static final int LAND_DROP = 3;

    /** The widest pit he jumps (a player's sprinting jump clears far more; two blocks leave room for a miss of a few tenths). */
    private static final int JUMP_WIDTH = 2;
    /** Extra ticks of a jump: the run-up, the take-off and the landing. */
    private static final float JUMP = 8;
    /** Ticks a block of ladder takes: up by the game's climb, 0.2 a tick; down at the 0.15 a tick a climber's fall is held to. */
    private static final float LADDER_UP = 5, LADDER_DOWN = 7;

    private final CompanionNodeEvaluator eval;
    private final Mob mob;
    private final Node[] sides = new Node[4];

    CompanionMoves(CompanionNodeEvaluator eval, Mob mob) { this.eval = eval; this.mob = mob; }

    void expand(Node from, Sink sink) {
        int x = from.x, y = from.y, z = from.z;
        PathType here = eval.typeAt(x, y, z);
        int up = mob.getPathfindingMalus(eval.typeAt(x, y + 1, z)) >= 0 && here != PathType.STICKY_HONEY ? Mth.floor(Math.max(1F, mob.maxUpStep())) : 0;
        boolean ladder = eval.climbableAt(x, y, z);
        // Holding a vine (nothing under his feet in the game's eyes) he is at the level of its cell, as on a ladder.
        double floor = ladder ? Math.max(eval.floorAt(x, y, z), y) : eval.floorAt(x, y, z);
        boolean floats = eval.typeAt(x, y - 1, z) == PathType.WATER;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Node to = accept(x + d.getStepX(), y, z + d.getStepZ(), up, floor, d, here, floats);
            // Not off a ladder into a fall: his feet are anywhere in its cell, so the drop is more than the node's, and damage.
            if (ladder && to != null && to.y < y) to = null;
            sides[d.get2DDataValue()] = to;
            if (to != null && (to.costMalus >= 0 || from.costMalus < 0)) sink.offer(to, cost(from, to));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction e = d.getClockWise();
            if (!diagonalSides(from, sides[d.get2DDataValue()], sides[e.get2DDataValue()])) continue;
            Node to = accept(x + d.getStepX() + e.getStepX(), y, z + d.getStepZ() + e.getStepZ(), up, floor, d, here, floats);
            if (to != null && to.type != PathType.WALKABLE_DOOR && to.costMalus >= 0) sink.offer(to, cost(from, to));
        }
        if (ladder) {
            ladder(from, y + 1, LADDER_UP, sink);
            ladder(from, y - 1, LADDER_DOWN, sink);
        }
        if (here == PathType.WALKABLE && from.costMalus >= 0) for (Direction d : Direction.Plane.HORIZONTAL) jump(from, d, floor, sink);
    }

    /** Along the climbable column to the cell at {@code y} if it is climbable too (a vine or ladder needs no wall by it), with room for his head going up. */
    private void ladder(Node from, int y, float ticks, Sink sink) {
        if (!eval.climbableAt(from.x, y, from.z) || (y > from.y && eval.typeAt(from.x, y + 1, from.z) == PathType.BLOCKED)) return;
        PathType type = eval.typeAt(from.x, y, from.z);
        float malus = mob.getPathfindingMalus(type);
        if (malus >= 0) sink.offer(eval.mark(from.x, y, from.z, type, malus), ticks);
    }

    /**
     * Over a pit of one or two blocks to ground at the same level on its far side (CompanionJumps walks it: the route's two nodes
     * more than a block apart are a jump). Only where a miss is safe: the pit's floor within the fall he may take and nothing in it
     * that hurts, and head room over it for the whole arc.
     */
    private void jump(Node from, Direction d, double floor, Sink sink) {
        for (int gap = 1; gap <= JUMP_WIDTH; gap++) {
            int x = from.x + d.getStepX() * gap, z = from.z + d.getStepZ() * gap;
            if (!safePit(x, from.y, z)) return;
            int lx = x + d.getStepX(), lz = z + d.getStepZ();
            if (eval.typeAt(lx, from.y, lz) != PathType.WALKABLE || eval.floorAt(lx, from.y, lz) != floor) continue;
            Node to = cell(lx, from.y, lz, 0, floor, d);
            if (to != null && to.type == PathType.WALKABLE && to.costMalus >= 0) sink.offer(to, (gap + 1) * TICKS_PER_BLOCK + JUMP);
        }
    }

    /** Air over a floor no deeper than he may fall onto (land that does no harm, or water), with head room above for a jump. */
    private boolean safePit(int x, int y, int z) {
        // At the top of the jump his head is over three blocks up: a ceiling lower than four cuts the jump short over the pit.
        for (int up = 0; up <= 3; up++) if (eval.typeAt(x, y + up, z) != PathType.OPEN) return false;
        for (int i = y - 1; y - i <= LAND_DROP; i--) {
            PathType type = eval.typeAt(x, i, z);
            if (type == PathType.OPEN) continue;
            return type == PathType.WALKABLE || type == PathType.WATER;
        }
        return false;
    }

    /** No corner cut through a wall: both cells beside the diagonal are walkable (or lower), and none is a door. */
    private static boolean diagonalSides(Node from, Node a, Node b) {
        if (a == null || b == null || a.y > from.y || b.y > from.y) return false;
        if (a.type == PathType.WALKABLE_DOOR || b.type == PathType.WALKABLE_DOOR) return false;
        return (a.y < from.y || a.costMalus >= 0) && (b.y < from.y || b.costMalus >= 0);
    }

    private static float cost(Node from, Node to) {
        float dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        // The malus of a cell (water: 2) makes walking in it dearer: twice as slow in water.
        float ticks = (float)Math.sqrt(dx * dx + dz * dz) * TICKS_PER_BLOCK * (1 + to.costMalus / 2);
        return ticks + (dy > 0 ? CLIMB * dy : -DROP * dy);
    }

    /**
     * The cell a step to (x, y, z) leads to, or null: that cell if it can be stood or swum in, the cell above if it is a wall
     * he can climb ({@code up} blocks), the first floor below if it is air. From the surface of the water a jump reaches half
     * a block higher and a bank a block over the water is climbed (marathon 23.10, «берег»): only by one who floats, not by one
     * wading along the bottom.
     */
    private Node accept(int x, int y, int z, int up, double floor, Direction d, PathType from, boolean floats) {
        Node node = cell(x, y, z, up, floor, d);
        if (node != null || from != PathType.WATER || !floats || up < 1) return node;
        Node out = cell(x, y, z, up + 1, floor + .5, d);
        return out != null && out.y > y ? out : null;
    }

    private Node cell(int x, int y, int z, int up, double floor, Direction d) {
        if (eval.floorAt(x, y, z) - floor > Math.max(1.125, mob.maxUpStep())) return null;
        PathType type = eval.typeAt(x, y, z);
        float malus = mob.getPathfindingMalus(type);
        Node node = malus >= 0 ? eval.mark(x, y, z, type, malus) : null;
        if (type == PathType.WALKABLE) return node;
        if (node == null) {
            boolean wall = type != PathType.FENCE && type != PathType.UNPASSABLE_RAIL && type != PathType.TRAPDOOR && type != PathType.POWDER_SNOW;
            return up > 0 && wall ? climb(x, y, z, up, floor, d) : null;
        }
        // The companion floats: water is swum in at the level it is met; air is fallen through.
        return type == PathType.OPEN ? drop(x, y, z) : node;
    }

    /** Onto the cell above a wall; the cell he jumps from needs head room for it. */
    private Node climb(int x, int y, int z, int up, double floor, Direction d) {
        Node node = cell(x, y + 1, z, up - 1, floor, d);
        if (node == null) return null;
        if (mob.getBbWidth() >= 1 || (node.type != PathType.OPEN && node.type != PathType.WALKABLE)) return node;
        double fx = x - d.getStepX() + .5, fz = z - d.getStepZ() + .5, half = mob.getBbWidth() / 2;
        AABB box = new AABB(fx - half, eval.floorAt(BlockPos.containing(fx, y + 1, fz)) + .001, fz - half, fx + half,
                mob.getBbHeight() + eval.floorAt(node.x, node.y, node.z) - .002, fz + half);
        return eval.collides(box) ? null : node;
    }

    /** Down through air to the first cell with a floor: no deeper than he may fall, unless it is water. */
    private Node drop(int x, int y, int z) {
        if (eval.climbableAt(x, y, z)) return eval.mark(x, y, z, PathType.OPEN, 0);
        for (int i = y - 1; i >= mob.level().getMinBuildHeight() && y - i <= WATER_DROP; i--) {
            PathType type = eval.typeAt(x, i, z);
            // A fall onto a ladder or vine is held there: he takes hold, and a climber's fall distance is reset.
            if (type == PathType.OPEN && eval.climbableAt(x, i, z) && y - i <= LAND_DROP) return eval.mark(x, i, z, PathType.OPEN, 0);
            if (type == PathType.OPEN) continue;
            float malus = mob.getPathfindingMalus(type);
            return malus < 0 || (y - i > LAND_DROP && type != PathType.WATER) ? null : eval.mark(x, i, z, type, malus);
        }
        return null;
    }
}
