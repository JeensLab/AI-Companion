package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The way out of a lit fuse, over the floor as it is: a breadth-first walk over the cells he can stand on (four neighbours, a rise of one
 * block, a drop of up to three, which hurts nothing) to a dozen steps, that never goes towards a creeper. The cell it ends at is the one the
 * fuse cannot see (a fuse goes out without a line of sight, and a blast does not reach round a corner), then the farthest from the nearest
 * creeper up to the gap beyond which the fuse goes out anyway, then the nearest to walk to.
 */
final class CompanionFuseEscape {
    private static final int DEPTH = 12, REPLAN_TICKS = 5, MAX_DROP = 3;
    private static final long NEVER = Long.MIN_VALUE / 2;
    /** How much nearer to a creeper than he is now a cell may be (a corner is rounded on a cell not much farther), and how near to one never. */
    private static final double TOLERANCE = 1, MIN_CLEAR = 1, REACHED = .6, DERAILED = 2.5;
    private static final Direction[] SIDES = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
    private static final int[] LEVELS = {0, 1, -1, -2, -MAX_DROP};
    /** How far inside the edge of the floor his footprint is counted, and how far above the floor a point still stands on it. */
    private static final double EDGE = .02, SETTLED = .001;

    private final CompanionEntity owner;
    private List<BlockPos> path = List.of();
    private int index;
    private long builtAt = NEVER;
    private boolean holding;

    CompanionFuseEscape(CompanionEntity owner) { this.owner = owner; }

    /** Forgets the way, so the next step builds it afresh. */
    void reset() {
        path = List.of();
        index = 0;
        builtAt = NEVER;
        holding = false;
    }

    BlockPos goal() { return path.isEmpty() ? null : path.get(path.size() - 1); }

    boolean holding() { return holding; }

    int length() { return path.size() - index; }

    /** Builds the way now; its length, or -1 where he has none. */
    int plan(ServerLevel level, List<Creeper> threats) {
        build(level, threats);
        builtAt = level.getGameTime();
        return holding ? 0 : path.isEmpty() ? -1 : path.size();
    }

    /**
     * The point to push him to now: the next cell of the way, built again every few ticks and where he has left it; where he stands when he is
     * out of the fuse's sight already; null when there is no way.
     */
    Vec3 step(ServerLevel level, List<Creeper> threats) {
        long now = level.getGameTime();
        if (now - builtAt >= REPLAN_TICKS || derailed() || arrived()) { build(level, threats); builtAt = now; }
        if (holding) return owner.position();
        if (path.isEmpty()) return null;
        while (index < path.size() - 1 && reached(path.get(index))) index++;
        return Vec3.atBottomCenterOf(path.get(index));
    }

    private boolean reached(BlockPos cell) {
        Vec3 centre = Vec3.atBottomCenterOf(cell);
        return Math.hypot(owner.getX() - centre.x, owner.getZ() - centre.z) < REACHED && Math.abs(owner.getY() - centre.y) < 1.1;
    }

    private boolean derailed() {
        return !path.isEmpty() && owner.position().distanceTo(Vec3.atBottomCenterOf(path.get(index))) > DERAILED;
    }

    private boolean arrived() { return !path.isEmpty() && index == path.size() - 1 && reached(path.get(index)); }

    private record Score(boolean exposed, double gap, int steps) {
        boolean beats(Score other) {
            if (exposed != other.exposed) return !exposed;
            if (gap != other.gap) return gap > other.gap;
            return steps < other.steps;
        }
    }

    private void build(ServerLevel level, List<Creeper> threats) {
        BlockPos start = owner.blockPosition();
        double[] floors = new double[threats.size()];
        for (int i = 0; i < floors.length; i++) {
            floors[i] = Math.max(MIN_CLEAR, Math.min(owner.position().distanceTo(threats.get(i).position()), CompanionCreeperGuard.SAFE_GAP) - TOLERANCE);
        }
        Map<BlockPos, BlockPos> from = new HashMap<>();
        Map<BlockPos, Integer> steps = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        from.put(start, start);
        steps.put(start, 0);
        queue.add(start);
        BlockPos best = start;
        Score bestScore = score(level, start, 0, threats);
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            int taken = steps.get(cell);
            if (taken == DEPTH) continue;
            for (Direction side : SIDES) {
                BlockPos next = move(level, cell, side);
                if (next == null || steps.containsKey(next) || !keepsOff(Vec3.atBottomCenterOf(next), threats, floors)) continue;
                from.put(next, cell);
                steps.put(next, taken + 1);
                queue.add(next);
                Score score = score(level, next, taken + 1, threats);
                if (score.beats(bestScore)) { best = next; bestScore = score; }
            }
        }
        List<BlockPos> way = new ArrayList<>();
        for (BlockPos cell = best; !cell.equals(start); cell = from.get(cell)) way.add(cell);
        Collections.reverse(way);
        path = List.copyOf(way);
        index = 0;
        holding = way.isEmpty() && !bestScore.exposed();
    }

    /**
     * The cell one step to the side that he can stand on: level, a block up, or a drop of up to three with room for the fall. Not one with lava
     * round it: the way ends at a rush, and a rush that stops at a lava edge or slips on it is a death (a mine death, a way through a big cave).
     */
    private BlockPos move(ServerLevel level, BlockPos cell, Direction side) {
        for (int rise : LEVELS) {
            BlockPos to = cell.relative(side).above(rise);
            if (!stands(level, to.getX() + .5, to.getY(), to.getZ() + .5) || lavaBeside(level, to)) continue;
            if (rise > 0 && !open(level, cell.above(2))) continue;
            boolean room = true;
            for (int y = to.getY() + 2; y <= cell.getY() + 1 && room; y++) room = open(level, new BlockPos(to.getX(), y, to.getZ()));
            if (room) return to;
        }
        return null;
    }

    /** He can stand with his feet at this point: his body fits there, no fluid is in it and some part of his footprint rests on a solid floor. */
    private boolean stands(ServerLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + SETTLED, z);
        if (!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        if (level.getBlockCollisions(owner, owner.getBoundingBox().move(x - owner.getX(), y - owner.getY(), z - owner.getZ()).deflate(SETTLED)).iterator().hasNext()) return false;
        return footing(level, x, z, owner.getBbWidth() / 2 - EDGE, feet.getY() - 1);
    }

    private static boolean footing(ServerLevel level, double x, double z, double half, int floor) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos pos = new BlockPos(Mth.floor(x + sx * half), floor, Mth.floor(z + sz * half));
                if (!level.hasChunkAt(pos) || level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) return true;
            }
        }
        return false;
    }

    /** Lava in the cube of a cell and the cells around it: a cell he must not end a walk in, since a step to the side is a step into it. */
    private static boolean lavaBeside(ServerLevel level, BlockPos cell) {
        for (BlockPos near : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
            if (level.hasChunkAt(near) && level.getFluidState(near).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** Nothing to break and nobody to burn: an open cell has no collision shape and no fluid. */
    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean keepsOff(Vec3 point, List<Creeper> threats, double[] floors) {
        for (int i = 0; i < floors.length; i++) if (point.distanceTo(threats.get(i).position()) < floors[i]) return false;
        return true;
    }

    private Score score(ServerLevel level, BlockPos cell, int steps, List<Creeper> threats) {
        Vec3 foot = Vec3.atBottomCenterOf(cell), eye = foot.add(0, owner.getEyeHeight(), 0);
        double gap = CompanionCreeperGuard.SAFE_GAP;
        boolean exposed = false;
        for (Creeper creeper : threats) {
            double distance = foot.distanceTo(creeper.position());
            gap = Math.min(gap, distance);
            if (distance < CompanionCreeperGuard.SAFE_GAP && !exposed) {
                exposed = level.clip(new ClipContext(creeper.getEyePosition(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
            }
        }
        return new Score(exposed, gap, steps);
    }
}
