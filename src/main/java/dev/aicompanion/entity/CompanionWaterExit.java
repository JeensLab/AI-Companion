package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * A dry cell in sight that the push does not reach (under a lip lower than he floats: a pocket of an aquifer under a ceiling, he
 * floated there with the order shut out of its turn until it timed out) is given up after a while, and the turn goes back to the
 * order, which has its own way with the water; the push is tried again later.
 */
final class CompanionWaterExit extends Goal {
    private static final int STEP_DOWN = 3, REPLAN_EVERY = 10, TREADING_TICKS = 60, PATIENCE = 20 * 8, REST = 20 * 10, NO_BANK_REST = 20;
    private static final double DRIFT = 2, REACHED = .4, DIVE = .08;

    private final CompanionEntity c;
    private BlockPos target;
    private long nextPlan, treadingSince, giveUpAt, restUntil, begun;
    private Vec3 anchor;

    CompanionWaterExit(CompanionEntity c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() {
        boolean free = c.inventoryViewers == 0 && c.work.isActive() && !c.gatheringLogs && !c.gatheringDirt && c.canWorkNow()
                && c.level().getGameTime() >= restUntil && c.getNavigation().isDone();
        // The treading clock runs only while nothing else holds him: a path that was followed for a while, or a rest, is not treading.
        if (!free) { anchor = null; return false; }
        if (!treadingWater()) return false;
        if ((target = nearestDry()) != null) return true;
        restUntil = c.level().getGameTime() + NO_BANK_REST;
        return false;
    }

    /**
     * A swim that gets somewhere is left alone: crossing a river to the player is not a reason to turn back to the nearest bank. So is
     * a swing at a block from the fall: taken off ore at the foot of a low fall a few ticks before it broke, he lost the swing and then
     * the order, with no stand for that ore from the floor beside. Nor is a swim up the fall, a block or more of height, on purpose.
     */
    private boolean treadingWater() {
        if (!deepInWater() || c.work.swinging()) { anchor = null; return false; }
        long now = c.level().getGameTime();
        double dx = anchor == null ? 0 : c.getX() - anchor.x, dz = anchor == null ? 0 : c.getZ() - anchor.z;
        if (anchor == null || dx * dx + dz * dz > DRIFT * DRIFT || Math.abs(c.getY() - anchor.y) > 1) { anchor = c.position(); treadingSince = now; return false; }
        return now - treadingSince >= TREADING_TICKS;
    }

    /** The way out is the order's and ends with it («стой» on the way to the bank), and with his arrival: a cell of shallow water is a bank too. */
    @Override public boolean canContinueToUse() {
        return c.inventoryViewers == 0 && c.isAlive() && c.work.isActive() && c.isInWater() && c.level().getGameTime() < giveUpAt && !arrived();
    }

    private boolean arrived() {
        return target != null && c.onGround() && Math.hypot(c.getX() - (target.getX() + .5), c.getZ() - (target.getZ() + .5)) < REACHED
                && Math.abs(c.getY() - target.getY()) < 1;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        long now = c.level().getGameTime();
        nextPlan = now + REPLAN_EVERY;
        giveUpAt = now + PATIENCE;
        begun = now;
        c.getNavigation().stop();
    }
    @Override public void stop() {
        restUntil = c.level().getGameTime() + REST;
        // The turns he held the order off are not the order's: its deadline moves by them.
        c.work.credit(c.level().getGameTime() - begun);
        target = null;
        anchor = null;
    }

    @Override public void tick() {
        if (target == null || c.level().getGameTime() >= nextPlan) {
            nextPlan = c.level().getGameTime() + REPLAN_EVERY;
            BlockPos found = nearestDry();
            if (found != null) target = found;
        }
        if (target == null) return;
        c.getMoveControl().setWantedPosition(target.getX() + .5, target.getY(), target.getZ() + .5, 1.0);
        CompanionLavaExit.jumpToBank(c, target, CompanionLavaExit.JUMP_EVERY);
        // At the top of a fall the float holds him over the opening with his head against the lip of it: he sinks to it, as a player does
        // with Shift (LivingEntity.goDownInWater), while he is pushing against that lip.
        if (c.horizontalCollision && target.getY() < c.getY() - .5) c.setDeltaMovement(c.getDeltaMovement().add(0, -DIVE, 0));
    }

    /** Deep enough to float, in water that falls. */
    private boolean deepInWater() {
        if (!c.isInWater() || c.isInLava() || c.getFluidHeight(FluidTags.WATER) <= c.getFluidJumpThreshold()) return false;
        var fluid = c.level().getFluidState(c.blockPosition());
        return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
    }

    /** Not a cell beside lava, nor one past it, at his feet or under them: the line to the bank is a wade, and a cell of lava on it burns. */
    private BlockPos nearestDry() {
        ServerLevel level = (ServerLevel) c.level();
        BlockPos from = c.blockPosition();
        return CompanionLavaExit.nearest(c, STEP_DOWN, false, pos -> CompanionLavaExit.beside(level, pos)
                || CompanionLavaExit.crossed(level, from, pos) + CompanionLavaExit.crossed(level, from.below(), pos.below()) > 0 ? Double.POSITIVE_INFINITY : 0);
    }
}
