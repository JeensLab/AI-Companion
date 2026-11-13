package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.pathfinder.Path;

final class CompanionSwimming {
    final CompanionEntity c;

    CompanionSwimming(CompanionEntity c) { this.c = c; }

    /**
     * The game's float lifts a mob that has more than half a block of water on him, so in water to the knee he bobbed, out of reach
     * and sight of the block he was mining (a stone or ore at the water took a thousand ticks of restarted cracks). Like a player he
     * wades one block of water standing on the floor, and floats from the second on. It holds no flag: with JUMP it took the goals
     * of the work from him in deep water, and an order there (a flooded tunnel, ore beyond it) stood and never ticked.
     */
    static final class WadingFloatGoal extends net.minecraft.world.entity.ai.goal.FloatGoal {
        private final CompanionEntity c;
        WadingFloatGoal(CompanionEntity c) { super(c); this.c = c; setFlags(java.util.EnumSet.noneOf(Flag.class)); }
        @Override public boolean canUse() { return c.isInLava() || c.isInWater() && c.getFluidHeight(FluidTags.WATER) > 1.0; }

        /**
         * The float lifted him to the surface whatever he fought, and a drowned or a guardian a few blocks under the player was out of
         * his reach (issue 59: «Олег не бьёт водных враждебных мобов»). Like a player holding Shift: down to a foe under water, while
         * more than half his breath is left and enough of it for the way up with three seconds to spare; then up for air, as the
         * float always took him (CompanionBreath, at a third, comes after). Never under a roof: the float lifts him only straight
         * up. Only with a blade in hand and more than half his health: in a test sea a drowned with a trident killed him four times
         * in a minute, and each time he came back empty-handed and dived again. With a bow and arrows he does not dive at all: he
         * shoots from above (the owner asked so, 03.11), see CompanionArchery.
         */
        @Override public void tick() {
            var target = c.getTarget();
            var hand = c.getMainHandItem();
            boolean dives = target != null && target.isAlive() && c.isInWater() && !c.isInLava() && target.isInWater()
                    && target.getY() < c.getY() - .5 && c.getHealth() > c.getMaxHealth() / 2
                    && (hand.is(net.minecraft.tags.ItemTags.SWORDS) || hand.is(net.minecraft.tags.ItemTags.AXES)) && !c.archery.armed()
                    && c.getAirSupply() > Math.max(c.getMaxAirSupply() / 2, waterOverhead(c) * TICKS_UP_A_BLOCK + 60);
            // «Подбери»: a drop that sank to the bottom of a pool is under him, and the float held him at the surface
            // over it, out of reach (game 08.11: the pork on the bottom of a four-deep pond was given up as out of reach).
            // Like the dive for a foe under water: down to it while the pickup is active and there is breath to come up.
            var chased = c.work.job == CompanionWork.Job.PICK_UP ? c.loot.chaseTarget() : null;
            boolean sinks = chased != null && chased.isAlive() && chased.isInWater() && c.isInWater() && !c.isInLava()
                    && chased.getY() < c.getY() - .4
                    && c.getAirSupply() > Math.max(c.getMaxAirSupply() / 2, waterOverhead(c) * TICKS_UP_A_BLOCK + 60);
            // The game's own sinking for a player's Shift in water (LivingEntity.goDownInWater).
            if (dives || sinks) c.setDeltaMovement(c.getDeltaMovement().add(0, -.04, 0));
            else super.tick();
        }
    }

    /** The float lifts him about a tenth of a block a tick; a little more for the way. */
    static final int TICKS_UP_A_BLOCK = 12;

    /** Blocks of water over the eyes, up to the air; NO_WAY_UP under a roof, or deeper than any breath reaches. */
    static int waterOverhead(net.minecraft.world.entity.Entity entity) {
        var pos = BlockPos.containing(entity.getEyePosition()).mutable();
        for (int blocks = 0; blocks < 32; blocks++, pos.move(0, 1, 0)) {
            if (entity.level().getFluidState(pos).is(FluidTags.WATER)) continue;
            return entity.level().getBlockState(pos).isAir() ? blocks : NO_WAY_UP;
        }
        return NO_WAY_UP;
    }
    static final int NO_WAY_UP = 1000;

    /** Nodes of deep water at his level he must still have ahead for the fast stroke; the last of the way is an ordinary one. */
    private static final int FAST_STROKE_AHEAD = 3;
    /** The fast stroke is held a few ticks more, as a player keeps Ctrl held: see stroke(). */
    private int fastStrokeUntil;
    /** Fallen behind a player swimming at the ordinary stroke, he catches up with the fast one to this many blocks. */
    private static final int CAUGHT_UP = 6;
    private boolean catchingUp;

    /**
     * A long way through deep water like a player holding Ctrl: sprinting, lying flat. The water then holds him back less (0.9
     * instead of 0.8 a tick) and he swims twice as fast; with the ordinary stroke the player sprint-swimming left him behind
     * (seen in the owner's game). Near the bank, the end of the way, in shallow water and out of it — the ordinary stroke, upright.
     */
    void stroke() {
        var path = c.getNavigation().getPath();
        // After a player swimming at the ordinary stroke, the ordinary stroke too, unless fallen far behind (as on foot: CompanionTravel
        // runs only after a sprinting player or a far one). The fast one caught him up in seconds, he stood up at the player's heels
        // and lay down again as the player swam on (issue 58). Once catching up, he keeps on until close: his ordinary stroke is a
        // little slower than the player's, and at the edge of «far» he lay down and stood up again every second.
        var player = c.getOwnerPlayer();
        BlockPos followed = player != null && c.travel().follows(player.blockPosition()) ? player.blockPosition() : null;
        catchingUp = followed != null && (c.travel().running() || catchingUp && c.distanceToSqr(player) > CAUGHT_UP * CAUGHT_UP);
        boolean paced = followed != null && c.level().getFluidState(followed).is(FluidTags.WATER) && !player.isSprinting() && !catchingUp;
        boolean ahead = c.isInWater() && !paced && swimAhead(path, followed);
        if (ahead) fastStrokeUntil = c.tickCount + 5;
        // Between two routes (one ends, the next comes a tick later), or bobbed out of the water by the float for a tick: still the
        // same swim, not a stop (28.10: else he stood up and lay down again there). A route taken away («стой», the player
        // reached) is a stop: upright at once.
        boolean held = c.isSprinting() && c.tickCount < fastStrokeUntil && (path != null && path.isDone() || !c.isInWater());
        boolean fast = (ahead || held) && !c.onGround() && !c.isPassenger() && c.isAlive();
        // The same flag runs on land (a long way, a sprinting player): set here, or this stroke would switch it off every tick.
        // Not wading: water at his feet, even before the game has noticed it, is walked through upright (knee-deep tests).
        boolean sprint = fast || !c.isInWater() && c.level().getFluidState(c.blockPosition()).isEmpty() && !c.isPassenger() && c.isAlive() && (c.travel().running() && !(c.getNavigation() instanceof CompanionNavigation slow && slow.slowsForJump()) || c.getNavigation() instanceof CompanionNavigation nav && nav.sprintsToJump()) && !(c.getNavigation() instanceof CompanionNavigation ladder && ladder.climbs());
        if (c.isSprinting() != sprint) c.setSprinting(sprint);
        if (c.getPose() == (fast ? Pose.STANDING : Pose.SWIMMING)) c.setPose(fast ? Pose.SWIMMING : Pose.STANDING);
        // At a sprint the game sinks nothing in water (Entity.getFluidFallingAdjustedMovement): the float lifted him till he lay on
        // the water and now and then out of it. The ordinary sinking keeps him in it at the usual depth. The game takes it off after
        // the water's drag (0.8), here it comes before: a fifth more, or the float still lifted him out now and then (2 of 100 swims).
        if (fast && c.isInWater()) c.setDeltaMovement(c.getDeltaMovement().add(0, -c.getGravity() / 16 / .8, 0));
    }

    /** {@code followed}: where the player he follows is now, or null. */
    private boolean swimAhead(Path path, BlockPos followed) {
        if (path == null || path.isDone()) return false;
        int ahead = Math.min(path.getNodeCount(), path.getNextNodeIndex() + FAST_STROKE_AHEAD);
        // A route the search cut short goes on from its end (water costs the search more, and its steps ran out after fourteen
        // blocks of the pool in the tests): only the real end of the way slows him down.
        // A route to where the player was is not the end either while he swims on past it (issue 58: else Oleg stood up at every
        // old route's end and lay down on the next, over and over).
        if (ahead - path.getNextNodeIndex() < FAST_STROKE_AHEAD && path.canReach()
                && (followed == null || followed.distSqr(path.getTarget()) <= FAST_STROKE_AHEAD * FAST_STROKE_AHEAD)) return false;
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
        /**
         * On a ladder the game's control hops whenever his feet are below the top of the ladder's block (it has a collision shape: a
         * mob climbs a slab so), and the hop is then held forward, deaf to the route, until the ground: off the ladder. Here he only
         * steers to the next point; up and down are the climb (CompanionJumps.climb) and the game's own hold of a climber.
         */
        private void climb() {
            operation = Operation.WAIT;
            double dx = wantedX - c.getX(), dz = wantedZ - c.getZ();
            if (dx * dx + dz * dz < 2.5e-7) { c.setZza(0); return; }
            c.setYRot(rotlerp(c.getYRot(), (float)(net.minecraft.util.Mth.atan2(dz, dx) * 57.2957763671875) - 90, 90));
            c.setSpeed((float)(speedModifier * c.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
            c.setZza(1);
        }

        @Override public void tick() {
            if (operation == Operation.JUMPING && (c.isInWater() || c.isInLava()) && !c.onGround()) operation = c.getNavigation().isDone() ? Operation.WAIT : Operation.MOVE_TO;
            if (c.onClimbable() && operation != Operation.WAIT) { climb(); return; }
            super.tick();
        }
    }
}
