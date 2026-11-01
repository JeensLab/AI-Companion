package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the straight push of the move control may take him: it is a line to the middle of a cell, and over a ledge, a corner of a shelf or a gap
 * in the floor that line leaves the ground he stands on (m9b/void-2: a push across a corner, 38 blocks of cave under it).
 */
final class CompanionExpeditionLedge {
    private static final double SAMPLE = .2, EDGE = .02, SETTLED = .001;

    private CompanionExpeditionLedge() { }

    /**
     * The point to push him to for the cell {@code to}: its middle while the line there is held up by ground all the way, else the middle of the
     * nearest cell beside him that brings him closer along ground that holds. Null when there is none: he waits, the way round or the patience decides.
     */
    static Vec3 aim(CompanionEntity owner, BlockPos to) {
        Vec3 centre = Vec3.atBottomCenterOf(to);
        if (!owner.onGround() || owner.isInWater() || owner.onClimbable() || !(owner.level() instanceof ServerLevel level)) return centre;
        double half = owner.getBbWidth() / 2 - EDGE;
        Vec3 from = owner.position();
        if (held(level, half, from, centre)) return centre;
        BlockPos here = owner.blockPosition();
        Vec3 best = null;
        double bestDistance = from.distanceToSqr(centre);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos step = here.offset(dx, 0, dz);
                if (dx == 0 && dz == 0 || !standable(level, step)) continue;
                Vec3 point = Vec3.atBottomCenterOf(step);
                double distance = point.distanceToSqr(centre);
                if (distance < bestDistance && held(level, half, from, point)) { best = point; bestDistance = distance; }
            }
        }
        return best;
    }

    private static boolean standable(ServerLevel level, BlockPos step) {
        BlockPos floor = step.below();
        return level.hasChunkAt(step) && CompanionShaftPlan.open(level, step) && CompanionShaftPlan.open(level, step.above())
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    /** Every sample of the line has ground under some part of his footprint, at the floor height of either end or between. */
    private static boolean held(ServerLevel level, double half, Vec3 from, Vec3 to) {
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(to.x - from.x, to.z - from.z) / SAMPLE));
        int low = Mth.floor(Math.min(from.y, to.y) + SETTLED) - 1, high = Mth.floor(Math.max(from.y, to.y) + SETTLED) - 1;
        for (int i = 0; i <= samples; i++) {
            double x = from.x + (to.x - from.x) * i / samples, z = from.z + (to.z - from.z) * i / samples;
            if (!footing(level, x, z, half, low, high)) return false;
        }
        return true;
    }

    private static boolean footing(ServerLevel level, double x, double z, double half, int low, int high) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = Mth.floor(x + sx * half), cz = Mth.floor(z + sz * half);
                for (int y = low; y <= high; y++) {
                    BlockPos pos = new BlockPos(cx, y, cz);
                    if (!level.hasChunkAt(pos) || level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) return true;
                }
            }
        }
        return false;
    }
}
