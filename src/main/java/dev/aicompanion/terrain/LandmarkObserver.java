package dev.aicompanion.terrain;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionOre;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/** Opportunistic first-hit observations from a walker's eyes, distributed across server ticks. */
public final class LandmarkObserver {
    private static final int[] YAW = {0, -8, 8, -16, 16, -24, 24, -32, 32, -40, 40};
    private static final int[] PITCH = {0, -12, 12, -26, 26, -40, 40};
    private static final CompanionOre.Kind[] ORES = CompanionOre.Kind.values();
    private int column = YAW.length;
    private int delay;
    private Vec3 lastPosition;
    private float lastYaw, lastPitch;

    /** Call once per tick. Returns the number of sightlines sampled; an idle observer uses none. */
    public int tick(CompanionEntity c, BiConsumer<String, BlockPos> seen) {
        if (delay > 0) { delay--; return 0; }
        if (!c.onGround() || c.isPassenger() || c.isInWaterOrBubble() || c.isInLava()) return 0;
        float yaw = c.getYHeadRot(), pitch = c.getXRot();
        if (column == YAW.length) {
            if (lastPosition != null && c.position().distanceToSqr(lastPosition) < 4
                    && Math.abs(Mth.wrapDegrees(yaw - lastYaw)) < 22 && Math.abs(pitch - lastPitch) < 18) {
                delay = 3;
                return 0;
            }
            column = 0;
            lastPosition = c.position();
            lastYaw = yaw;
            lastPitch = pitch;
        }

        Frame frame = new Frame(c, Vec3.directionFromRotation(pitch, yaw));
        BlockPos roof = null;
        for (int tilt : PITCH) {
            Vec3 direction = Vec3.directionFromRotation(Mth.clamp(pitch + tilt, -89, 89), yaw + YAW[column]);
            BlockHitResult hit = frame.trace(frame.eye.add(direction.scale(frame.range)));
            if (!block(hit)) continue;
            BlockPos pos = hit.getBlockPos();
            BlockState state = c.level().getBlockState(pos);
            String ore = ore(state);
            if (ore != null) seen.accept(ore, pos.immutable());
            if (roof == null && hit.getDirection() == Direction.DOWN && TerrainSight.naturalRock(state)) roof = pos;
        }
        if (roof != null) {
            for (int gap = 2; gap <= 5; gap++) {
                BlockPos floor = roof.below(gap + 1);
                if (!frame.seesFloor(floor)) continue;
                if (frame.seesEntrance(floor, roof)) seen.accept("cave", floor.immutable());
                break;
            }
        }
        if (++column == YAW.length) delay = 9;
        return frame.rays;
    }

    /** An explicit look at the remembered spot; the target determines the viewing direction. */
    public static boolean stillVisible(CompanionEntity c, String kind, BlockPos pos) {
        Frame frame = new Frame(c, null);
        if (!kind.equals("cave")) {
            for (Direction face : Direction.values()) {
                Vec3 sample = Vec3.atCenterOf(pos).relative(face, .48);
                BlockHitResult hit = frame.trace(sample);
                if (block(hit) && hit.getBlockPos().equals(pos))
                    return kind.equals(ore(c.level().getBlockState(pos)));
            }
            return false;
        }
        if (!frame.seesFloor(pos)) return false;
        for (int gap = 2; gap <= 5; gap++) {
            BlockPos roof = pos.above(gap + 1);
            var hit = frame.trace(Vec3.atBottomCenterOf(roof).add(0, .02, 0));
            if (block(hit) && hit.getBlockPos().equals(roof) && hit.getDirection() == Direction.DOWN
                    && TerrainSight.naturalRock(c.level().getBlockState(roof)) && frame.seesEntrance(pos, roof)) return true;
        }
        return false;
    }

    /** Darkness, an unloaded chunk or another wall in front never disproves a remembered landmark. */
    public static boolean visiblyGone(CompanionEntity c, String kind, BlockPos pos) {
        Frame frame = new Frame(c, null);
        boolean cave = kind.equals("cave");
        BlockPos cell = cave ? pos.above() : pos;
        var hit = frame.trace(Vec3.atCenterOf(cell));
        if (hit == null) return false;
        if (!block(hit)) return !cave && (frame.eye.distanceToSqr(hit.getLocation()) <= 9
                || c.level().getMaxLocalRawBrightness(cell) >= 4);
        if (!cell.equals(hit.getBlockPos())) return false;
        BlockState visible = c.level().getBlockState(cell);
        if (!cave) return !kind.equals(ore(visible));
        if (!visible.isCollisionShapeFullBlock(c.level(), cell)) return false;
        // A single step on the old floor need not close the opening. Look at its headroom as well.
        BlockPos head = cell.above();
        var blockage = frame.trace(Vec3.atCenterOf(head));
        return block(blockage) && head.equals(blockage.getBlockPos()) && c.level().getBlockState(head).isCollisionShapeFullBlock(c.level(), head);
    }

    private static boolean block(BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }

    static String ore(BlockState state) {
        if (state.is(Blocks.NETHER_QUARTZ_ORE)) return "quartz";
        if (state.is(Blocks.ANCIENT_DEBRIS)) return "debris";
        for (CompanionOre.Kind kind : ORES) if (kind.is(state)) return kind.key();
        return null;
    }

    private static final class Frame {
        final CompanionEntity c;
        final Vec3 eye, forward;
        final int range;
        int rays;

        Frame(CompanionEntity c, Vec3 forward) {
            this.c = c;
            this.eye = c.getEyePosition();
            this.range = TerrainSight.range(c);
            this.forward = forward;
        }

        BlockHitResult trace(Vec3 end) {
            Vec3 delta = end.subtract(eye);
            double lengthSq = delta.lengthSqr();
            if (lengthSq > range * range + .001) return null;
            if (forward != null && forward.dot(delta) < .4 * Math.sqrt(lengthSq)) return null;
            rays++;
            return TerrainSight.visibleTrace(c, eye, end);
        }

        boolean seesFloor(BlockPos pos) {
            BlockHitResult hit = trace(Vec3.atBottomCenterOf(pos).add(0, .98, 0));
            if (!block(hit) || hit.getDirection() != Direction.UP || !hit.getBlockPos().equals(pos)) return false;
            var state = c.level().getBlockState(pos);
            return state.getFluidState().isEmpty() && state.isFaceSturdy(c.level(), pos, Direction.UP);
        }

        /** A rock arch has an exposed lip and a side reaching down to the opening. A ceiling alone is insufficient. */
        boolean seesEntrance(BlockPos floor, BlockPos roof) {
            var lip = trace(Vec3.atCenterOf(roof));
            if (!block(lip) || !lip.getDirection().getAxis().isHorizontal() || lip.getBlockPos().getY() != roof.getY()
                    || lip.getBlockPos().distSqr(roof) > 9 || !TerrainSight.naturalRock(c.level().getBlockState(lip.getBlockPos()))) return false;
            Direction side = lip.getDirection().getClockWise();
            for (int offset = -3; offset <= 3; offset += 6) {
                var flank = trace(Vec3.atCenterOf(floor.relative(side, offset).above()));
                if (!block(flank) || !flank.getDirection().getAxis().isHorizontal()) continue;
                BlockPos p = flank.getBlockPos();
                if (p.getY() >= floor.getY() + 1 && p.getY() < roof.getY() && p.distSqr(floor) <= 17
                        && TerrainSight.naturalRock(c.level().getBlockState(p))) return true;
            }
            return false;
        }
    }
}
