package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;

final class CompanionSwimming {
    final CompanionEntity c;

    CompanionSwimming(CompanionEntity c) { this.c = c; }

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
        if (routed)
            for (int index = path.getNextNodeIndex(); index < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3) && !bank; index++) {
                BlockPos node = path.getNodePos(index);
                bank = node.getY() > c.getY() + .5 && Math.abs(node.getX() + .5 - c.getX()) < 1.6 && Math.abs(node.getZ() + .5 - c.getZ()) < 1.6;
            }
        // Bobbing at the bank, the game's navigation gives the route up as «stuck»: then the bank in front of him, if where he is
        // going (the player, the work) lies higher than the water.
        if (!bank && !routed) {
            BlockPos destination = c.recoveryDestination();
            if (destination == null || destination.getY() <= c.getY() + .5) return;
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
            c.setDeltaMovement(c.getDeltaMovement().x, c.getJumpPower(), c.getDeltaMovement().z);
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
