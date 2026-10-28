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
 * depth into water), swimming. What a cell is (walkable, a door, a fence, danger, water) is the game's own judgement of it,
 * the evaluator's; which neighbour a step may come to follows the game's walking search, so the same routes are found.
 * A new move (dig, place) is one more method called from {@link #expand} that offers its cells with their cost.
 */
final class CompanionMoves {
    /** A cell offered by a move and the ticks it takes to get there. */
    interface Sink { void offer(Node to, float ticks); }

    /** A block on foot takes this long (0.19 blocks a tick, measured on the polygon's flat arena). */
    static final float TICKS_PER_BLOCK = 1 / .19F;
    /** Extra for a jump up one block and for each block of a drop. */
    private static final float CLIMB = 6, DROP = 2;
    /** The deepest drop into water that is taken (a fall into water does no harm). */
    private static final int WATER_DROP = 20;
    /** The deepest drop onto land, chasing or not (the game's own allows a mob with a target up to 12 on Normal, 16 on Hard). */
    private static final int LAND_DROP = 3;

    private final CompanionNodeEvaluator eval;
    private final Mob mob;
    private final Node[] sides = new Node[4];

    CompanionMoves(CompanionNodeEvaluator eval, Mob mob) { this.eval = eval; this.mob = mob; }

    void expand(Node from, Sink sink) {
        int x = from.x, y = from.y, z = from.z;
        PathType here = eval.typeAt(x, y, z);
        int up = mob.getPathfindingMalus(eval.typeAt(x, y + 1, z)) >= 0 && here != PathType.STICKY_HONEY ? Mth.floor(Math.max(1F, mob.maxUpStep())) : 0;
        double floor = eval.floorAt(x, y, z);
        boolean floats = eval.typeAt(x, y - 1, z) == PathType.WATER;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Node to = accept(x + d.getStepX(), y, z + d.getStepZ(), up, floor, d, here, floats);
            sides[d.get2DDataValue()] = to;
            if (to != null && (to.costMalus >= 0 || from.costMalus < 0)) sink.offer(to, cost(from, to));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction e = d.getClockWise();
            if (!diagonalSides(from, sides[d.get2DDataValue()], sides[e.get2DDataValue()])) continue;
            Node to = accept(x + d.getStepX() + e.getStepX(), y, z + d.getStepZ() + e.getStepZ(), up, floor, d, here, floats);
            if (to != null && to.type != PathType.WALKABLE_DOOR && to.costMalus >= 0) sink.offer(to, cost(from, to));
        }
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
        for (int i = y - 1; i >= mob.level().getMinBuildHeight() && y - i <= WATER_DROP; i--) {
            PathType type = eval.typeAt(x, i, z);
            if (type == PathType.OPEN) continue;
            float malus = mob.getPathfindingMalus(type);
            return malus < 0 || (y - i > LAND_DROP && type != PathType.WATER) ? null : eval.mark(x, i, z, type, malus);
        }
        return null;
    }
}
