package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Where a dirt column goes for a block out of reach from the ground. */
final class CompanionScaffoldColumns {
    record Column(BlockPos ground, int levels, double score) { }
    /** The route cells of this tick went elsewhere: not «no column», ask again next tick. */
    static final Column BUSY = new Column(BlockPos.ZERO, 0, 0);

    private CompanionScaffoldColumns() { }

    /**
     * The lowest column of dirt, on ground he can walk to, from whose top the block (and its other half, if any) is placed: within reach,
     * in sight, off its cell. {@code floorY} is the level of the building's floor; {@code cells} the building's own cells; {@code tried} the
     * feet of the columns put up since the last block was set down: one that did not help is not put up again.
     */
    static Column find(CompanionHouse house, CompanionEntity owner, ServerLevel level, int floorY, BlockPos pos, BlockPos second, Set<BlockPos> cells, Set<BlockPos> tried) {
        List<Column> options = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy : new int[]{0, -1, 1, -2}) {
            BlockPos ground = new BlockPos(pos.getX() + dx, floorY + dy, pos.getZ() + dz);
            if (!level.hasChunkAt(ground) || cells.contains(ground) || tried.contains(ground) || !level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP)) continue;
            for (int levels = 1; levels <= CompanionScaffold.MAX_HEIGHT; levels++) {
                BlockPos block = ground.above(levels - 1);
                // The column itself: free cells, not the building's.
                if (cells.contains(block) || !level.getFluidState(block).isEmpty() || !level.getBlockState(block).canBeReplaced() || !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) break;
                BlockPos feet = ground.above(levels);
                if (cells.contains(feet) || cells.contains(feet.above()) || !level.hasChunkAt(feet.above()) || !level.noCollision(new AABB(feet.getX(), feet.getY(), feet.getZ(), feet.getX() + 1, feet.getY() + 2, feet.getZ() + 1))) continue;
                AABB body = new AABB(ground.getX() + .2, feet.getY(), ground.getZ() + .2, ground.getX() + .8, feet.getY() + owner.getBbHeight(), ground.getZ() + .8);
                if (body.intersects(new AABB(pos)) || second != null && body.intersects(new AABB(second))) continue;
                if (!house.visible(level, Vec3.atBottomCenterOf(feet).add(0, owner.getEyeHeight(), 0), pos)) continue;
                options.add(new Column(ground.immutable(), levels, levels * 1000D + owner.distanceToSqr(ground.getCenter())));
                break;
            }
        }
        options.sort(Comparator.comparingDouble(Column::score));
        // The way to the foot of the column is asked of the first few only: each is a search.
        for (Column option : options.subList(0, Math.min(5, options.size()))) {
            int busy = owner.work.route.busyAnswers();
            if (owner.work.route.toCells(Set.of(option.ground())) != null) return option;
            if (owner.work.route.busyAnswers() != busy) return BUSY;
        }
        return null;
    }
}
