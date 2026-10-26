package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's staircase down: each step clears head, body and the next foot cell in one direction, so the
 * mob can walk down and back up. Soil and stone are both dug; stone dug on the way is already the harvest.
 * Never a vertical shaft: every step moves one block sideways for each block down.
 */
final class CompanionQuarry {
    static final int MAX_DEPTH = 6;
    /** cells in digging order; start is the surface cell the stairs begin from (and the way back out). */
    record Plan(List<BlockPos> cells, BlockPos start, java.util.Set<BlockPos> supports, int stoneCells) { }
    private CompanionQuarry() { }

    static Plan find(CompanionEntity owner, int wantedStone) {
        return find(owner, wantedStone, java.util.Set.of());
    }

    static Plan find(CompanionEntity owner, int wantedStone, java.util.Set<BlockPos> rejected) {
        ServerLevel level = (ServerLevel)owner.level();
        BlockPos origin = owner.blockPosition();
        for (int radius = 1; radius <= 12; radius += radius < 4 ? 1 : 2) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
            for (int dy : new int[]{0, -1, 1, -2, 2}) {
                BlockPos start = origin.offset(dx, dy, dz);
                if (!CompanionMiningNavigation.standable(owner, start) || !diggable(level.getBlockState(start.below()))) continue;
                if (owner.autonomy().running() && !CompanionMiningNavigation.canWalkBackUp(owner, start, owner.autonomy().center().getY())) continue;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    List<BlockPos> cells = new ArrayList<>();
                    java.util.Set<BlockPos> supports = new java.util.HashSet<>();
                    supports.add(start.below().immutable());
                    int stone = 0;
                    for (int depth = 1; depth <= MAX_DEPTH && stone < Math.max(1, wantedStone); depth++) {
                        BlockPos foot = start.relative(direction, depth).below(depth);
                        // Clear the jump envelope as well as the standing body. Two-high diagonal
                        // stairs can be mined from above but cannot be walked back out by this mob.
                        List<BlockPos> step = new ArrayList<>();
                        int stepStone = 0;
                        boolean safe = level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP);
                        for (BlockPos pos : new BlockPos[]{foot.above(2), foot.above(), foot}) {
                            if (!safe) break;
                            if (rejected.contains(pos) || !owner.leashAllows(pos) || !safeCell(level, pos) || underSomeone(owner, pos)) { safe = false; break; }
                            BlockState state = level.getBlockState(pos);
                            if (CompanionWork.isStone(state)) { step.add(pos.immutable()); stepStone++; }
                            else if (soil(state)) step.add(pos.immutable());
                            else if (!state.isAir() && !state.canBeReplaced()) safe = false;
                        }
                        if (!safe) break;
                        cells.addAll(step); stone += stepStone; supports.add(foot.below().immutable());
                    }
                    if (stone == 0 || cells.isEmpty() || cells.size() > 3 * MAX_DEPTH) continue;
                    if (owner.autonomy().running() && !CompanionMiningNavigation.preservesAutonomousReturn(owner, cells.getFirst())) continue;
                    var path = owner.getNavigation().createPath(start, 0);
                    if (path != null && path.canReach()) return new Plan(List.copyOf(cells), start.immutable(), java.util.Set.copyOf(supports), stone);
                }
            }
        }
        return null;
    }

    /**
     * Someone (the player, an animal) stands on this cell or in it: stairs are not dug from under their feet (autonomy 23.10,
     * «долина»: the stairs led under the player standing by, the step under him could never be dug, he stood ninety seconds).
     */
    private static boolean underSomeone(CompanionEntity owner, BlockPos pos) {
        return !owner.level().getEntities(owner, new net.minecraft.world.phys.AABB(pos).expandTowards(0, 1.1, 0),
                entity -> entity instanceof net.minecraft.world.entity.LivingEntity && entity.isAlive()).isEmpty();
    }

    static boolean diggable(BlockState state) { return soil(state) || CompanionWork.isStone(state); }
    static boolean soil(BlockState state) { return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT); }
    static boolean safeCell(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
        if (CompanionLighting.supportsLight(level, pos)) return false;
        for (Direction direction : Direction.values()) {
            BlockPos nearby = pos.relative(direction);
            if (!level.hasChunkAt(nearby) || !level.getFluidState(nearby).isEmpty() || level.getBlockEntity(nearby) != null
                    || level.getBlockState(nearby).is(Blocks.CRAFTING_TABLE) || level.getBlockState(nearby).getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
        }
        for (int up = 1; up <= 6; up++) {
            BlockPos above = pos.above(up);
            if (!level.hasChunkAt(above)) return false;
            BlockState cover = level.getBlockState(above);
            if (!cover.isAir() && !soil(cover) && !CompanionWork.isStone(cover)
                    && !cover.canBeReplaced() && !cover.is(net.minecraft.tags.BlockTags.LEAVES)) return false;
        }
        return level.getBlockEntity(pos) == null && level.getFluidState(pos).isEmpty();
    }
}
