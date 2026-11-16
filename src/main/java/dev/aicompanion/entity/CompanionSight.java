package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What a player looking from an eye position can click: the cell of a block, within the reach of 4.5 blocks, with no other block in the way. */
public final class CompanionSight {
    private static final double REACH_SQR = 4.5 * 4.5;

    private CompanionSight() { }

    /** A block is in sight from the eye: the nearest point of its cell, its middle, and the middle of each face turned to the eye. */
    public static boolean visible(ServerLevel level, Entity viewer, Vec3 eye, BlockPos pos) {
        // A ray to the cube center alone is hidden by the finished wall beneath a roof tile or by an adjacent half-slab.
        double x = Math.clamp(eye.x, pos.getX() + .01, pos.getX() + .99);
        double z = Math.clamp(eye.z, pos.getZ() + .01, pos.getZ() + .99);
        double y = Math.clamp(eye.y, pos.getY() + .01, pos.getY() + .99);
        Vec3 center = pos.getCenter();
        for (Vec3 aim : new Vec3[]{new Vec3(x, y, z), new Vec3(x, pos.getY() + .49, z), center}) if (sees(level, viewer, eye, aim, pos)) return true;
        // Every aim above grazes the blocks round a cell that is open on one side only (the top of a wall between two beams seen at a slant):
        // the middle of each face the eye is in front of is aimed at, a hair inside the cell so a ray ending on a face next to a block hits that block first.
        for (Direction face : Direction.values()) {
            double front = (eye.x - center.x) * face.getStepX() + (eye.y - center.y) * face.getStepY() + (eye.z - center.z) * face.getStepZ();
            if (front > .5 && sees(level, viewer, eye, center.add(face.getStepX() * .48, face.getStepY() * .48, face.getStepZ() * .48), pos)) return true;
        }
        return false;
    }

    private static boolean sees(ServerLevel level, Entity viewer, Vec3 eye, Vec3 aim, BlockPos pos) {
        if (eye.distanceToSqr(aim) > REACH_SQR) return false;
        var hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
