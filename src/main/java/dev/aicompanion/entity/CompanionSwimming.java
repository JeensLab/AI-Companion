package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.pathfinder.Path;

final class CompanionSwimming {
    final CompanionEntity c;

    CompanionSwimming(CompanionEntity c) { this.c = c; }

    /** Nodes of deep water at his level he must still have ahead for the fast stroke; the last of the way is an ordinary one. */
    private static final int FAST_STROKE_AHEAD = 3;
    /** The fast stroke is held a few ticks more, as a player keeps Ctrl held: see stroke(). */
    private int fastStrokeUntil;

    /**
     * A long way through deep water like a player holding Ctrl: sprinting, lying flat. The water then holds him back less (0.9
     * instead of 0.8 a tick) and he swims twice as fast; with the ordinary stroke the player sprint-swimming left him behind
     * (seen in the owner's game). Near the bank, the end of the way, in shallow water and out of it — the ordinary stroke, upright.
     */
    void stroke() {
        var path = c.getNavigation().getPath();
        boolean ahead = c.isInWater() && swimAhead(path);
        if (ahead) fastStrokeUntil = c.tickCount + 5;
        // Between two routes (one ends, the next comes a tick later), or bobbed out of the water by the float for a tick: still the
        // same swim, not a stop (28.10: else he stood up and lay down again there). A route taken away («стой», the player
        // reached) is a stop: upright at once.
        boolean held = c.isSprinting() && c.tickCount < fastStrokeUntil && (path != null && path.isDone() || !c.isInWater());
        boolean fast = (ahead || held) && !c.onGround() && !c.isPassenger() && c.isAlive();
        // The same flag runs on land (a long way, a sprinting player): set here, or this stroke would switch it off every tick.
        // Not wading: water at his feet, even before the game has noticed it, is walked through upright (knee-deep tests).
        boolean sprint = fast || !c.isInWater() && c.level().getFluidState(c.blockPosition()).isEmpty() && !c.isPassenger() && c.isAlive() && c.travel().running();
        if (c.isSprinting() != sprint) c.setSprinting(sprint);
        if (c.getPose() == (fast ? Pose.STANDING : Pose.SWIMMING)) c.setPose(fast ? Pose.SWIMMING : Pose.STANDING);
        // At a sprint the game sinks nothing in water (Entity.getFluidFallingAdjustedMovement): the float lifted him till he lay on
        // the water and now and then out of it. The ordinary sinking keeps him in it at the usual depth. The game takes it off after
        // the water's drag (0.8), here it comes before: a fifth more, or the float still lifted him out now and then (2 of 100 swims).
        if (fast && c.isInWater()) c.setDeltaMovement(c.getDeltaMovement().add(0, -c.getGravity() / 16 / .8, 0));
    }

    private boolean swimAhead(Path path) {
        if (path == null || path.isDone()) return false;
        int ahead = Math.min(path.getNodeCount(), path.getNextNodeIndex() + FAST_STROKE_AHEAD);
        // A route the search cut short goes on from its end (water costs the search more, and its steps ran out after fourteen
        // blocks of the pool in the tests): only the real end of the way slows him down.
        if (ahead - path.getNextNodeIndex() < FAST_STROKE_AHEAD && path.canReach()) return false;
        for (int index = path.getNextNodeIndex(); index < ahead; index++) {
            BlockPos node = path.getNodePos(index);
            // Two blocks deep at least: in water to the knees a player stands, and his eyes over it never start the swim.
            if (node.getY() > c.getY() + .5 || !c.level().getFluidState(node).is(FluidTags.WATER) || !c.level().getFluidState(node.below()).is(FluidTags.WATER)) return false;
        }
        return true;
    }

    /**
     * At a bank a block over the water, like a player: hold the jump while pressing against it, until out (marathon 23.10,
     * «берег»: the game's float stops pushing once the head is out, the lift of the water reached half a block short of the bank,
     * and he bobbed at the island's edge for minutes). Only while the route really goes up onto that bank.
     */
    void climbOutOfWater() {
        if (c.isInWater()) c.lastInWaterTick = c.tickCount;
        // In the water, or just lifted out of it at the bank by the water's own push (a few ticks ago).
        if (c.tickCount - c.lastInWaterTick > 3) return;
        var path = c.getNavigation().getPath();
        boolean routed = path != null && !path.isDone();
        // With a route he must be pressing on the bank; without one (see below) he is turned to press on it.
        if (routed && !c.horizontalCollision) return;
        // The bank a node or two ahead counts too: along an island's edge the route often keeps one more cell of water first
        // (marathon 23.10: he bobbed along the shore of the island and never got out).
        boolean bank = false;
        // Lifted out by the water, his feet are already less than half a block under the bank, and it is still the bank (28.10:
        // with the half block asked there too, the push off came only on the fourth lift — three seconds of bobbing at the edge).
        double above = c.isInWater() ? .5 : 0;
        if (routed)
            for (int index = path.getNextNodeIndex(); index < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3) && !bank; index++) {
                BlockPos node = path.getNodePos(index);
                bank = node.getY() > c.getY() + above && Math.abs(node.getX() + .5 - c.getX()) < 1.6 && Math.abs(node.getZ() + .5 - c.getZ()) < 1.6;
            }
        // Bobbing at the bank, the game's navigation gives the route up as «stuck»: then the bank in front of him, if where he is
        // going (the player, the work) lies higher than the water.
        if (!bank && !routed) {
            BlockPos destination = c.recoveryDestination();
            if (destination == null || destination.getY() <= c.getY() + above) return;
            BlockPos front = c.blockPosition().relative(c.getDirection());
            for (int up = 1; up <= 2 && !bank; up++) bank = CompanionMiningNavigation.standable(c, front.above(up));
            // Pressing on towards it, as the route would have.
            if (bank) c.getMoveControl().setWantedPosition(front.getX() + .5, front.getY() + 1, front.getZ() + .5, 1.0);
        }
        if (!bank) return;
        if (c.isInWater()) { c.getJumpControl().jump(); return; }
        if (!c.horizontalCollision) return;
        // Lifted out at the edge, the push off is a player's ordinary jump: the water's push alone stops some way short of a block.
        if (c.getDeltaMovement().y < .2 && c.tickCount >= c.nextWaterJump) {
            c.nextWaterJump = c.tickCount + 10;
            c.setDeltaMovement(c.getDeltaMovement().x, c.jumpPower(), c.getDeltaMovement().z);
        }
    }

    /**
     * The game's movement control «jumps» at a point higher than a step and then holds the stroke forward, deaf to the route,
     * until the feet touch ground — in deep water never (marathon 23.10, «берег»: at the island's bank he swam straight on past
     * it, through «стой», forty blocks into the corner). In the water the jump is a stroke up and over at once: the next tick he
     * steers by his route again, or stops if there is none.
     */
    static final class SwimmingMoveControl extends net.minecraft.world.entity.ai.control.MoveControl {
        final CompanionEntity c;
        SwimmingMoveControl(CompanionEntity c) { super(c); this.c = c; }
        @Override public void tick() {
            if (operation == Operation.JUMPING && (c.isInWater() || c.isInLava()) && !c.onGround()) operation = c.getNavigation().isDone() ? Operation.WAIT : Operation.MOVE_TO;
            super.tick();
        }
    }
}
