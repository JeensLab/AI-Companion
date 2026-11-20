package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.ToDoubleFunction;

/**
 * The way out of lava, ahead of every other goal: nothing else takes him out of it. The float lifts him but does not walk him, a mine step
 * stops caring for the ground once it has ended in a rest, and a fight holds the turn; in a minute of that he is dead. This goal runs at
 * priority -1 with the move flag only (the float goes on lifting him), walks him to the nearest cell he can stand on and then a few ticks
 * on, so that he is not stopped at the edge with the lava at his heel.
 */
final class CompanionLavaExit extends Goal {
    private static final int STEP_DOWN = 2, REPLAN_EVERY = 5, OUT_TICKS = 30, EDGE_PENALTY = 9;
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
                BlockPos found = nearestOut();
                if (found != null) target = found;
            }
        } else outTicks++;
        if (target == null) return;
        c.getMoveControl().setWantedPosition(target.getX() + .5, target.getY(), target.getZ() + .5, SPEED);
        jumpToBank(c, target, JUMP_EVERY);
    }

    private boolean arrived() {
        return Math.hypot(c.getX() - (target.getX() + .5), c.getZ() - (target.getZ() + .5)) < REACHED && Math.abs(c.getY() - target.getY()) < 1;
    }

    /** The nearest cell with a floor and no fluid that a straight line reaches; one beside lava counts as farther than one clear of it. */
    private BlockPos nearestOut() {
        ServerLevel level = (ServerLevel) c.level();
        return nearest(c, STEP_DOWN, pos -> beside(level, pos) ? EDGE_PENALTY : 0);
    }

    /** The nearest standable cell in a straight line from him, by squared distance plus the penalty of the cell. */
    static BlockPos nearest(CompanionEntity c, int stepDown, ToDoubleFunction<BlockPos> penalty) {
        BlockPos from = c.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) for (int dy = -stepDown; dy <= STEP_UP; dy++) {
            BlockPos pos = from.offset(dx, dy, dz);
            double score = pos.distSqr(from);
            if (score >= bestScore || !CompanionMiningNavigation.standable(c, pos)) continue;
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

    /** No block between his middle and the cell's: a wade is a straight line, not a route. */
    static boolean open(CompanionEntity c, BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from), end = Vec3.atCenterOf(to);
        return c.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c)).getType() == HitResult.Type.MISS;
    }
}
