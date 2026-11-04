package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The way out of a column of falling water for an order that waits on it. In still water the order works on (the float holds no
 * flag, CompanionSwimming.WadingFloatGoal): a flooded tunnel is his way and his work. A fall carries him down and nothing else
 * takes him sideways: at a waterfall with air one step away he bobbed, with no route, until he drowned. A route through the water
 * is left to the work. This goal moves him to the nearest dry
 * cell, with the move flag only: the float goes on lifting him, and the guard (priority 0) keeps the turn if it has it.
 */
final class CompanionWaterExit extends Goal {
    private static final int STEP_DOWN = 3, REPLAN_EVERY = 10, TREADING_TICKS = 60;
    private static final double DRIFT = 2;

    private final CompanionEntity c;
    private BlockPos target;
    private long nextPlan, treadingSince;
    private Vec3 anchor;

    CompanionWaterExit(CompanionEntity c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() {
        return c.inventoryViewers == 0 && c.work.isActive() && !c.gatheringLogs && !c.gatheringDirt && c.canWorkNow()
                && c.getNavigation().isDone() && treadingWater() && (target = nearestDry()) != null;
    }

    /** A swim that gets somewhere is left alone: crossing a river to the player is not a reason to turn back to the nearest bank. */
    private boolean treadingWater() {
        if (!deepInWater()) { anchor = null; return false; }
        long now = c.level().getGameTime();
        double dx = anchor == null ? 0 : c.getX() - anchor.x, dz = anchor == null ? 0 : c.getZ() - anchor.z;
        if (anchor == null || dx * dx + dz * dz > DRIFT * DRIFT) { anchor = c.position(); treadingSince = now; return false; }
        return now - treadingSince >= TREADING_TICKS;
    }

    @Override public boolean canContinueToUse() { return c.inventoryViewers == 0 && c.isAlive() && c.isInWater(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { nextPlan = c.level().getGameTime() + REPLAN_EVERY; c.getNavigation().stop(); }
    @Override public void stop() { target = null; anchor = null; }

    @Override public void tick() {
        if (target == null || c.level().getGameTime() >= nextPlan) {
            nextPlan = c.level().getGameTime() + REPLAN_EVERY;
            BlockPos found = nearestDry();
            if (found != null) target = found;
        }
        if (target == null) return;
        c.getMoveControl().setWantedPosition(target.getX() + .5, target.getY(), target.getZ() + .5, 1.0);
        CompanionLavaExit.jumpToBank(c, target, CompanionLavaExit.JUMP_EVERY);
    }

    /** Deep enough to float, in water that falls. */
    private boolean deepInWater() {
        if (!c.isInWater() || c.isInLava() || c.getFluidHeight(FluidTags.WATER) <= c.getFluidJumpThreshold()) return false;
        var fluid = c.level().getFluidState(c.blockPosition());
        return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
    }

    private BlockPos nearestDry() { return CompanionLavaExit.nearest(c, STEP_DOWN, pos -> 0); }
}
