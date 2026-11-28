package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class CompanionParrotShoulder {
    private static final String FIRST_LANDING = "aicompanion:perched_on_oleg";
    private static final double RECORD_RANGE = 3.46; // Vanilla Parrot.aiStep's audible radius.

    private CompanionParrotShoulder() {}

    static Vec3 offset(CompanionEntity owner, float scale) {
        double height = owner.getBbHeight() * 0.77;
        if (owner.campfirePose() != CompanionCampfire.STANDING) height -= 0.55 * scale;
        return new Vec3(0.39 * scale, height, 0).yRot((float) -Math.toRadians(owner.yBodyRot));
    }

    static void approach(Parrot bird, CompanionEntity owner, boolean repath) {
        Vec3 shoulder = owner.getPassengerRidingPosition(bird);
        if (owner.isVehicle() || owner.isInWaterOrBubble() || !bird.hasLineOfSight(owner)
                || bird.distanceToSqr(shoulder) > 9) {
            if (repath && bird.distanceToSqr(owner) > 4) bird.getNavigation().moveTo(owner, 1.1);
            return;
        }
        // Use the bird's own flight controller for the last stretch right up to his shoulder.
        bird.getNavigation().stop();
        if (!bird.level().noCollision(bird, bird.getBoundingBox().move(shoulder.subtract(bird.position())))) return;
        if (bird.distanceToSqr(shoulder) > 0.25) {
            bird.getMoveControl().setWantedPosition(shoulder.x, shoulder.y, shoulder.z, 0.8);
            return;
        }
        if (bird.startRiding(owner) && !bird.getPersistentData().getBoolean(FIRST_LANDING)) {
            bird.getPersistentData().putBoolean(FIRST_LANDING, true);
            owner.petSay("Гляди, сам прилетел! У меня на плече сидит.");
            owner.observeEvent("parrot", "Попугай впервые сел мне на плечо.");
        }
    }

    static Vec3 safeDismount(CompanionEntity owner, Parrot bird) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var pos = owner.blockPosition().relative(direction);
            if (!owner.level().hasChunkAt(pos)) continue;
            var safe = DismountHelper.findSafeDismountLocation(bird.getType(), owner.level(), pos, true);
            if (safe != null) return safe;
        }
        return DismountHelper.findSafeDismountLocation(bird.getType(), owner.level(), owner.blockPosition(), true);
    }

    static void positioned(CompanionEntity owner, Parrot bird, BlockPos previous) {
        var data = owner.getEntityData();
        if (owner.level() instanceof ServerLevel level) {
            if (!bird.blockPosition().equals(previous)) {
                CompanionPetBonds.get(level.getServer(), CompanionPet.PARROT).observe(level, bird, owner.getOwnerId());
            }
            if (owner.isInWaterOrBubble() || !owner.isAlive() || bird.isOrderedToSit()
                    || !level.noBlockCollision(bird, bird.getBoundingBox())) {
                bird.stopRiding();
                return;
            }
            bird.setInSittingPose(true);
            // Also notice a record that was already playing when he walked into earshot.
            if (owner.tickCount % 20 == 0) data.set(CompanionEntity.DATA_SHOULDER_RECORD, playingRecord(level, bird));
        }
        var record = data.get(CompanionEntity.DATA_SHOULDER_RECORD);
        if (!owner.campfireTune().isEmpty()) bird.setRecordPlayingNearby(owner.blockPosition(), true);
        else if (record.isPresent()) bird.setRecordPlayingNearby(record.get(), true);
        else if (bird.isPartyParrot()) bird.setRecordPlayingNearby(bird.blockPosition(), false);
    }

    static void dismounted(CompanionEntity owner, Parrot bird) {
        bird.setInSittingPose(bird.isOrderedToSit());
        owner.getEntityData().set(CompanionEntity.DATA_SHOULDER_RECORD, Optional.empty());
    }

    private static Optional<BlockPos> playingRecord(ServerLevel level, Parrot bird) {
        return BlockPos.findClosestMatch(bird.blockPosition(), 4, 4, pos ->
                pos.closerToCenterThan(bird.position(), RECORD_RANGE) && level.hasChunkAt(pos)
                        && level.getBlockState(pos).is(Blocks.JUKEBOX)
                        && level.getBlockEntity(pos) instanceof JukeboxBlockEntity box && box.getSongPlayer().isPlaying());
    }
}
