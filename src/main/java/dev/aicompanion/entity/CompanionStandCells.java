package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Where a column raised in a fight ({@link CompanionPillar#tickUp}) can stand: a component of the pillar with no state of its own. */
final class CompanionStandCells {
    /** How far from his cell it may be, how far the mobs it keeps away from are counted, the samples of the walk to it. */
    private static final int STAND_REACH = 2, STAND_LOOKOUT = 8, STRAIGHT_SAMPLES = 8;

    private final CompanionEntity owner;

    CompanionStandCells(CompanionEntity owner) { this.owner = owner; }

    /**
     * Where a column of this height can stand: his own cell, else the cell within {@link #STAND_REACH} on his level that he walks straight
     * to and that is the farthest from the mobs about (the roof of a cave is seldom even: run ct78, four cells of air over the corner he
     * was pinned in and five two cells away). Null when there is none.
     */
    BlockPos standFor(ServerLevel level, int height) {
        BlockPos feet = owner.blockPosition();
        if (stands(level, feet, height)) return feet.immutable();
        List<Monster> mobs = level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(STAND_LOOKOUT), LivingEntity::isAlive);
        BlockPos best = null;
        double bestGap = -1;
        for (int dx = -STAND_REACH; dx <= STAND_REACH; dx++) for (int dz = -STAND_REACH; dz <= STAND_REACH; dz++) {
            BlockPos cell = feet.offset(dx, 0, dz);
            if (dx == 0 && dz == 0 || !stands(level, cell, height) || !walksStraightTo(level, cell)) continue;
            double gap = Double.MAX_VALUE;
            for (var mob : mobs) gap = Math.min(gap, mob.distanceToSqr(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D));
            if (gap > bestGap) { best = cell.immutable(); bestGap = gap; }
        }
        return best;
    }

    boolean stands(ServerLevel level, BlockPos feet, int height) {
        return CompanionPillar.safeFoundation(level, feet.below()) && roomFor(level, feet, height) && flooredAround(level, feet)
                && level.getEntities(owner, new AABB(feet).expandTowards(0, height, 0), CompanionPillar::blocksSupport).isEmpty();
    }

    /**
     * A blow may throw him off the column: every cell around its foot is a wall or has a floor no more than a block lower, so that he
     * lands beside it and not down a pit (series p 20261031 d3 mobs: a column at the edge of a cavern, thrown off, three falls, dead),
     * and none of them holds lava or water, nor does the floor under it.
     */
    private boolean flooredAround(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos beside = feet.offset(dx, 0, dz);
            if (dx == 0 && dz == 0) continue;
            if (!level.getFluidState(beside).isEmpty() || !level.getFluidState(beside.below()).isEmpty()) return false;
            if (passable(level, beside) && !CompanionPillar.safeFoundation(level, beside.below()) && !CompanionPillar.safeFoundation(level, beside.below(2))) return false;
        }
        return true;
    }

    /** A cell he can pass: no collision shape and no fluid. */
    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /**
     * Room for the supports and for him on top of them: the cell at the feet takes a block (air or a plant), the cells above it up to his
     * head on the column are open. A roof right over that head is no obstacle: the jump for the last support ends under it.
     */
    boolean roomFor(ServerLevel level, BlockPos feet, int height) {
        for (int dy = 0; dy <= height + 1; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || dy < height && !state.isAir() && !state.canBeReplaced()) return false;
        }
        return true;
    }

    /** Nothing in the way of his body on the straight line to the middle of the cell, and a floor under every step of it. */
    private boolean walksStraightTo(ServerLevel level, BlockPos cell) {
        double dx = cell.getX() + 0.5D - owner.getX(), dz = cell.getZ() + 0.5D - owner.getZ();
        for (int step = 1; step <= STRAIGHT_SAMPLES; step++) {
            double part = step / (double) STRAIGHT_SAMPLES;
            AABB body = owner.getBoundingBox().move(dx * part, 0, dz * part);
            if (!level.noCollision(owner, body) || level.containsAnyLiquid(body)) return false;
            if (!CompanionPillar.safeFoundation(level, BlockPos.containing(owner.getX() + dx * part, owner.getY() - 0.5D, owner.getZ() + dz * part))) return false;
        }
        return true;
    }
}
