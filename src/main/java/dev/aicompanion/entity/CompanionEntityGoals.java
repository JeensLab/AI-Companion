package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

final class CompanionEntityGoals {
    private CompanionEntityGoals() {}

    /** Descent survives command changes, interrupted mining and world reloads. */
    static final class RecoverSupportsGoal extends Goal {
        final CompanionEntity c;
        RecoverSupportsGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.temporaryPillar.isRecovering() && c.inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.getNavigation().stop(); }
        @Override public void tick() {
            if (c.temporaryPillar.tickCleanup()) c.syncWorkState();
        }
    }

    static final class WorkGoal extends Goal {
        final CompanionEntity c;
        WorkGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.work.isActive() && !c.gatheringLogs && !c.gatheringDirt && c.canWorkNow(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { if (canUse()) c.work.tick(); }
        @Override public void stop() { c.work.pause(); c.getNavigation().stop(); }
    }

    static final class PickupLootGoal extends Goal {
        final CompanionEntity c;
        PickupLootGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return c.loot.canChase(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { c.loot.tickChase(); }
        @Override public void stop() { c.loot.pause(); c.getNavigation().stop(); }
    }

    static final class RecoverRouteGoal extends Goal {
        final CompanionEntity c;
        RecoverRouteGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.recovery.canStart(); }
        @Override public boolean canContinueToUse() { return c.recovery.active() && c.canCollectItems(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.recovery.begin(); }
        @Override public void tick() { c.recovery.tick(); }
        @Override public void stop() { c.recovery.cancel(); c.getNavigation().stop(); }
    }

    static final class RoamGoal extends Goal {
        final CompanionEntity c;
        private int nextAttempt;
        RoamGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return c.roaming && c.inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { nextAttempt = 0; }
        @Override public void tick() {
            if (!(c.level() instanceof ServerLevel serverLevel)) return;
            if (!c.onGround() && !c.isInWater()) return;
            if (!c.getNavigation().isDone() && !c.getNavigation().isStuck()) return;
            if (c.tickCount < nextAttempt) return;
            nextAttempt = c.tickCount + 60;
            if (c.roamOrigin == null) c.roamOrigin = c.blockPosition();
            Set<BlockPos> destinations = new HashSet<>();
            Set<BlockPos> nearbyDestinations = new HashSet<>();
            double preferredDistance = Math.max(8.0D, c.searchRadius * 0.7D);
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = c.roamOrigin.getX() + c.getRandom().nextInt(c.searchRadius * 2 + 1) - c.searchRadius;
                int z = c.roamOrigin.getZ() + c.getRandom().nextInt(c.searchRadius * 2 + 1) - c.searchRadius;
                if (serverLevel.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                BlockPos feet = findStandingPosition(serverLevel, x, z);
                if (feet == null) continue;
                if (feet.distSqr(c.blockPosition()) < 64 || feet.distSqr(c.roamOrigin) > (double) c.searchRadius * c.searchRadius) continue;
                nearbyDestinations.add(feet);
                if (feet.distSqr(c.blockPosition()) >= preferredDistance * preferredDistance) destinations.add(feet);
            }
            Path path = c.getNavigation().createPath(destinations, 0);
            if (path == null || !path.canReach()) path = c.getNavigation().createPath(nearbyDestinations, 0);
            if (path != null && path.canReach()) c.getNavigation().moveTo(path, 0.9D);
        }

        private BlockPos findStandingPosition(ServerLevel serverLevel, int x, int z) {
            // Prefer the current ground level. A heightmap can point to an inaccessible
            // tree canopy or a roof while there is a perfectly walkable route underneath.
            for (int dy = 0; dy <= 12; dy++) {
                BlockPos above = new BlockPos(x, c.blockPosition().getY() + dy, z);
                if (isWalkable(serverLevel, above)) return above;
                if (dy > 0) {
                    BlockPos below = new BlockPos(x, c.blockPosition().getY() - dy, z);
                    if (isWalkable(serverLevel, below)) return below;
                }
            }
            return null;
        }

        private boolean isWalkable(ServerLevel serverLevel, BlockPos feet) {
            return serverLevel.getFluidState(feet).isEmpty() && serverLevel.getFluidState(feet.above()).isEmpty()
                    && serverLevel.getBlockState(feet.below()).isFaceSturdy(serverLevel, feet.below(), Direction.UP)
                    && serverLevel.getBlockState(feet).getCollisionShape(serverLevel, feet).isEmpty()
                    && serverLevel.getBlockState(feet.above()).getCollisionShape(serverLevel, feet.above()).isEmpty();
        }
        @Override public void stop() { c.getNavigation().stop(); }
    }

    static final class FollowPlayerGoal extends Goal {
        private final CompanionEntity companion;
        private ServerPlayer owner;

        FollowPlayerGoal(CompanionEntity companion) {
            this.companion = companion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (companion.autonomy.staysAtWork() || (!companion.following && !companion.protecting) || companion.inventoryViewers > 0 || companion.ownerId == null || !(companion.level() instanceof ServerLevel serverLevel)) {
                return false;
            }
            owner = serverLevel.getServer().getPlayerList().getPlayer(companion.ownerId);
            return owner != null && owner.level() == companion.level()
                    && companion.distanceToSqr(owner) > (companion.protecting ? 16 : 64);
        }

        @Override
        public boolean canContinueToUse() {
            return !companion.autonomy.staysAtWork() && (companion.following || companion.protecting) && companion.inventoryViewers == 0 && owner != null && owner.isAlive()
                    && owner.level() == companion.level() && companion.distanceToSqr(owner) > (companion.protecting ? 6.25 : 16.0D);
        }

        @Override
        public void tick() {
            if (owner == null) return;
            companion.getLookControl().setLookAt(owner, 20.0F, 30.0F);
            // Like a player who lost sight of a friend: on foot, in legs round walls and over ridges, as far as the way goes and with
            // no time limit while he is in this world; never a teleport (marathon 23.10). With no way, he says so once and waits
            // where he stands until the player moves, then tries again.
            if (waitingFor != null) {
                if (owner.blockPosition().distSqr(waitingFor) < 9) return;
                waitingFor = null;
            }
            if (!companion.onGround() && !companion.isInWater()) return;
            var step = companion.work.route.follow(owner.blockPosition(), FOLLOW_NEAR);
            if (step == CompanionTravel.Step.WALKING) { travelling = true; return; }
            travelling = false;
            companion.travel().reset();
            if (step == CompanionTravel.Step.FAILED) {
                companion.getNavigation().stop();
                waitingFor = owner.blockPosition();
                if (!told) { told = true; companion.autonomySay("Не могу пройти к тебе. Подожду, пока ты сдвинешься."); }
            } else told = false;
        }

        @Override
        public void stop() {
            owner = null; waitingFor = null; told = false;
            if (travelling) { companion.travel().reset(); travelling = false; }
            companion.getNavigation().stop();
        }
        private boolean travelling, told;
        // Where the player was when there was no way to him; he waits until the player is elsewhere.
        private BlockPos waitingFor;
        private static final int FOLLOW_NEAR = 3;
    }
}
