package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.ToDoubleFunction;

/**
 * The way out of lava, ahead of every other goal: nothing else takes him out of it. The float lifts him but does not walk him, a work step
 * may stop caring for the ground once it has ended in a rest, and a fight holds the turn; in a minute of that he is dead. This goal runs at
 * priority -1 with the move flag only (the float goes on lifting him), walks him to the nearest cell he can stand on and then a few ticks
 * on, so that he is not stopped at the edge with the lava at his heel.
 */
final class CompanionLavaExit extends Goal {
    private static final int STEP_DOWN = 2, REPLAN_EVERY = 5, OUT_TICKS = 30, EDGE_PENALTY = 9;
    /** What a cell of lava on the straight way to a bank costs, against the squared distance: half a second in it is a burn and more fire. */
    private static final int CROSS_PENALTY = 25;
    private static final double CROSS_STEP = .25;
    private static final double[] BODY = {.1, .9, 1.7};
    static final int RADIUS = 6, STEP_UP = 1, JUMP_EVERY = 10;
    private static final double SPEED = 1.3, REACHED = .36;

    private final CompanionEntity c;
    private BlockPos target;
    private long nextPlan;
    private int outTicks;

    CompanionLavaExit(CompanionEntity c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Lava in the cube of a cell and the cells around it: a cell he must not end a walk in, since a step to the side is a step into it. */
    static boolean beside(ServerLevel level, BlockPos cell) {
        for (BlockPos near : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
            if (level.hasChunkAt(near) && level.getFluidState(near).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    @Override public boolean canUse() { return c.isAlive() && c.isInLava() && (target = nearestOut()) != null; }

    @Override public boolean canContinueToUse() { return c.isAlive() && target != null && (c.isInLava() || outTicks < OUT_TICKS && !arrived()); }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        nextPlan = c.level().getGameTime() + REPLAN_EVERY;
        outTicks = 0;
        c.getNavigation().stop();
    }

    @Override public void stop() { target = null; }

    @Override public void tick() {
        if (c.isInLava()) {
            outTicks = 0;
            if (c.level().getGameTime() >= nextPlan) {
                nextPlan = c.level().getGameTime() + REPLAN_EVERY;
                // No bank any more (it turned to lava): drop the old target rather than wade to it.
                target = nearestOut();
            }
        } else outTicks++;
        if (target == null) return;
        // The code outside the goals (the climb out of water, the death trip) steers him with the navigation: not during the exit.
        c.getNavigation().stop();
        c.getMoveControl().setWantedPosition(target.getX() + .5, target.getY(), target.getZ() + .5, SPEED);
        jumpToBank(c, target, JUMP_EVERY);
    }

    private boolean arrived() {
        return Math.hypot(c.getX() - (target.getX() + .5), c.getZ() - (target.getZ() + .5)) < REACHED && Math.abs(c.getY() - target.getY()) < 1;
    }

    /**
     * The nearest cell with a floor and no fluid, or of water with no lava in it or over it, that a straight line reaches; one beside lava counts as
     * farther than one clear of it, and one past more lava farther still (run s7-d1/retreat: the nearest bank lay across a spreading sheet, and he
     * burned to death on the way). Out of lava into water is out: runs void and restart of seed 7 (series h, 21.11), afloat in a lake under a lava
     * sheet, the only floor in reach lay across the sheet, the bank he came from two blocks up, and he waded into the lava to it.
     */
    BlockPos nearestOut() {
        ServerLevel level = (ServerLevel) c.level();
        BlockPos from = c.blockPosition();
        return nearest(c, STEP_DOWN, true, pos -> (beside(level, pos) ? EDGE_PENALTY : 0) + CROSS_PENALTY * crossed(level, from, pos));
    }

    /** The cells of lava a straight wade from {@code from} to {@code to} goes through at his feet, his own cell apart. */
    private static int crossed(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atBottomCenterOf(from), end = Vec3.atBottomCenterOf(to);
        int steps = (int) Math.ceil(start.distanceTo(end) / CROSS_STEP), count = 0;
        BlockPos last = from;
        for (int i = 1; i <= steps; i++) {
            BlockPos cell = BlockPos.containing(start.lerp(end, (double) i / steps).add(0, .1, 0));
            if (cell.equals(last)) continue;
            last = cell;
            if (level.getFluidState(cell).is(FluidTags.LAVA)) count++;
        }
        return count;
    }

    /** The nearest standable cell in a straight line from him, by squared distance plus the penalty of the cell; {@code swim}: a swimmer's cell of water ({@link CompanionMiningNavigation#pickupStand}) is one too. */
    static BlockPos nearest(CompanionEntity c, int stepDown, boolean swim, ToDoubleFunction<BlockPos> penalty) {
        BlockPos from = c.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) for (int dy = -stepDown; dy <= STEP_UP; dy++) {
            BlockPos pos = from.offset(dx, dy, dz);
            double score = pos.distSqr(from);
            if (score >= bestScore || !(swim ? CompanionMiningNavigation.pickupStand(c, pos) : CompanionMiningNavigation.standable(c, pos))) continue;
            score += penalty.applyAsDouble(pos);
            if (score >= bestScore || !open(c, from, pos)) continue;
            best = pos;
            bestScore = score;
        }
        return best;
    }

    /** A bank a block over the fluid: the float's lift stops short of it, so the last push is a jump, as a player's. */
    static void jumpToBank(CompanionEntity c, BlockPos target, int every) {
        if (target.getY() > c.getY() && c.horizontalCollision && c.getDeltaMovement().y < .2 && c.tickCount >= c.nextWaterJump) {
            c.nextWaterJump = c.tickCount + every;
            c.setDeltaMovement(c.getDeltaMovement().x, c.jumpPower(), c.getDeltaMovement().z);
        }
    }

    /** No block between his middle and the cell's, and the line is one he can walk or wade: a wade is a straight line, not a route. */
    static boolean open(CompanionEntity c, BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from), end = Vec3.atCenterOf(to);
        return c.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c)).getType() == HitResult.Type.MISS
                && walkable(c.level(), from, to);
    }

    /**
     * Along the line, a floor or a fluid under his feet (a drop of one block is a step, not more), and room for his body, 0.6 wide and 1.8
     * tall, with nothing at the head: a hole or a lip across the way makes the bank farther than it looks. The last block and a half is at
     * the height of the bank, as the jump at the edge is.
     */
    private static boolean walkable(Level level, BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atBottomCenterOf(from), end = Vec3.atBottomCenterOf(to);
        double flat = Math.hypot(end.x - start.x, end.z - start.z);
        if (flat < 1e-6) return true;
        double sideX = (start.z - end.z) / flat * .3, sideZ = (end.x - start.x) / flat * .3;
        int steps = (int) Math.ceil(flat / CROSS_STEP);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps, x = start.x + (end.x - start.x) * t, z = start.z + (end.z - start.z) * t;
            double y = flat * (1 - t) < 1.5 ? end.y : start.y;
            for (int side = -1; side <= 1; side++) for (double up : BODY) {
                BlockPos cell = BlockPos.containing(x + sideX * side, y + up, z + sideZ * side);
                if (level.hasChunkAt(cell) && !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) return false;
            }
            BlockPos feet = BlockPos.containing(x, y + .1, z);
            if (!supports(level, feet.below()) && !supports(level, feet.below(2))) return false;
        }
        return true;
    }

    /** Something under the feet at this cell: a block with a collision shape, or water or lava he wades. */
    private static boolean supports(Level level, BlockPos cell) {
        return !level.getFluidState(cell).isEmpty() || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
    }
}
