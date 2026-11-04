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
    /** How many drops in a row are added up (a chain longer than that is far over what he may fall anyway). */
    private static final int CHAIN_LINKS = 8;
    /** A block in a cell no higher than this (a bottom slab, a carpet, a few layers of snow) is stepped onto; a higher one fills the cell. */
    private static final double STEPPED_OVER = .5;

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
            if (to != null && fallsFar(from, to)) to = null;
            sides[d.get2DDataValue()] = to;
            if (to != null && (to.costMalus >= 0 || from.costMalus < 0)) sink.offer(to, cost(from, to));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction e = d.getClockWise();
            if (!diagonalSides(from, sides[d.get2DDataValue()], sides[e.get2DDataValue()])) continue;
            Node to = accept(x + d.getStepX() + e.getStepX(), y, z + d.getStepZ() + e.getStepZ(), up, floor, d, here, floats);
            if (to != null && to.type != PathType.WALKABLE_DOOR && to.costMalus >= 0 && (to.y <= y || roomOverDiagonal(from, d, e, to)) && !fallsFar(from, to)) sink.offer(to, cost(from, to));
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
     * that hurts, and head room over it, the edge cell and the landing for the whole arc.
     */
    private void jump(Node from, Direction d, double floor, Sink sink) {
        // He takes off from the edge cell: his head reaches 2.2 above its floor as he rises.
        if (eval.typeAt(from.x, from.y + 2, from.z) != PathType.OPEN) return;
        for (int gap = 1; gap <= JUMP_WIDTH; gap++) {
            int x = from.x + d.getStepX() * gap, z = from.z + d.getStepZ() * gap;
            if (!safePit(x, from.y, z)) return;
            int lx = x + d.getStepX(), lz = z + d.getStepZ();
            if (eval.typeAt(lx, from.y, lz) != PathType.WALKABLE || eval.floorAt(lx, from.y, lz) != floor) continue;
            // He comes down on the landing cell with his head still high in the arc: a block there cuts the jump short, into the pit.
            if (eval.typeAt(lx, from.y + 2, lz) != PathType.OPEN || eval.typeAt(lx, from.y + 3, lz) != PathType.OPEN) continue;
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
        float malus = eval.malusAt(type, x, y, z);
        // The game calls a cell with a block of partial shape open (an azalea's leaves are a plate over a stem); a cell his body does not fit in is a wall.
        boolean filled = type == PathType.WALKABLE && filled(x, y, z);
        Node node = malus >= 0 && !filled ? eval.mark(x, y, z, type, malus) : null;
        if (type == PathType.WALKABLE && !filled) return node;
        if (node == null) {
            boolean wall = type != PathType.FENCE && type != PathType.UNPASSABLE_RAIL && type != PathType.TRAPDOOR && type != PathType.POWDER_SNOW;
            return up > 0 && wall ? climb(x, y, z, up, floor, d) : null;
        }
        // The companion floats: water is swum in at the level it is met; air is fallen through.
        return type == PathType.OPEN ? drop(x, y, z) : node;
    }

    /**
     * Whether a block in the cell stops his body standing on its floor: something in the cell or the one over it that reaches above a
     * step and is neither stairs nor a ladder, which he walks up. The way on is then over it (a step up), if it is no higher than that.
     */
    private boolean filled(int x, int y, int z) {
        var level = mob.level();
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        if (eval.climbableAt(x, y, z) || level.getBlockState(pos).is(net.minecraft.tags.BlockTags.STAIRS)) return false;
        double half = mob.getBbWidth() / 2, floor = eval.floorAt(x, y, z);
        return eval.collides(new AABB(x + .5 - half, floor + STEPPED_OVER, z + .5 - half, x + .5 + half, floor + mob.getBbHeight() - .002, z + .5 + half));
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

    /**
     * A diagonal jump onto a step passes over three columns, not one: the head must clear them all (forest endurance run: past a niche with a
     * low ceiling the jump ended in the niche, his head under the ceiling, again and again until the watchdog).
     */
    private boolean roomOverDiagonal(Node from, Direction d, Direction e, Node to) {
        if (mob.getBbWidth() >= 1) return true;
        double half = mob.getBbWidth() / 2, top = mob.getBbHeight() + eval.floorAt(to.x, to.y, to.z) - .002;
        for (Direction side : new Direction[]{null, d, e}) {
            int x = from.x + (side == null ? 0 : side.getStepX()), z = from.z + (side == null ? 0 : side.getStepZ());
            double fx = x + .5, fz = z + .5;
            if (eval.collides(new AABB(fx - half, eval.floorAt(new BlockPos(x, to.y, z)) + .001, fz - half, fx + half, top, fz + half))) return false;
        }
        return true;
    }

    /**
     * A drop straight after a drop is no new fall: he is still in the air over the first landing when he turns to the second (a drop of
     * three takes nine ticks, the next column four and a half), so the falls add up (marathon m15: down a slope of ledges three blocks
     * each, 6 and 26 blocks in one fall). The chain may not come to more than he may fall. A drop of one block lands within the time he
     * takes for a column (4 ticks in the air against 4.6), so a stair is walked step by step and breaks the chain, before and after it.
     * Into water and onto a ladder or vine nothing is added.
     */
    private static boolean fallsFar(Node from, Node to) {
        int fall = from.y - to.y;
        if (fall <= 1 || to.type == PathType.WATER || to.type == PathType.OPEN) return false;
        Node at = from;
        for (int link = 0; link < CHAIN_LINKS && at.cameFrom != null && at.type != PathType.WATER; link++) {
            Node before = at.cameFrom;
            int drop = before.y - at.y;
            if (drop <= 1 || before.x == at.x && before.z == at.z) break;
            fall += drop;
            at = before;
        }
        return fall > LAND_DROP;
    }

    /** Down through air to the first cell with a floor: no deeper than he may fall, unless it is water. */
    private Node drop(int x, int y, int z) {
        if (eval.climbableAt(x, y, z)) return eval.mark(x, y, z, PathType.OPEN, 0);
        for (int i = y - 1; i >= mob.level().getMinBuildHeight() && y - i <= WATER_DROP; i--) {
            PathType type = eval.typeAt(x, i, z);
            // A fall onto a ladder or vine is held there: he takes hold, and a climber's fall distance is reset.
            if (type == PathType.OPEN && eval.climbableAt(x, i, z) && y - i <= LAND_DROP) return eval.mark(x, i, z, PathType.OPEN, 0);
            if (type == PathType.OPEN) continue;
            float malus = eval.malusAt(type, x, i, z);
            return malus < 0 || (y - i > LAND_DROP && type != PathType.WATER) || type == PathType.WALKABLE && filled(x, i, z) ? null : eval.mark(x, i, z, type, malus);
        }
        return null;
    }
}
