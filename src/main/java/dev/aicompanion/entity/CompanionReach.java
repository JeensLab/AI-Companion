package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one answer to «how does he get to this block and work it», asked by every place that chooses a target — the obtain chain
 * and the harvest path now, the ore, lying items, chests and building still on their own checks. The answer is a way, not a
 * yes: WALK a cell he can stand and work it from, ACROSS a walk farther than his own search (legs, CompanionTravel), UP a
 * column of his own soil with the steps it takes, DOWN the block under his feet (its drop rests in the hole he steps into),
 * or NONE. Phrasing cannot decide the outcome when every chooser asks the same question, and a promise is only as large as
 * what these ways really give (PATH7, #105: «добудь обсидиан» promised a portal frame's ten from one block ten blocks above,
 * and the harvest path never reached a pumpkin hanging there).
 */
public final class CompanionReach {
    public enum Way { WALK, ACROSS, UP, DOWN, NONE }

    /** The way to the block and, for UP, the least steps of soil a column to it takes (0 otherwise). */
    public record How(Way way, int steps) {
        public boolean yes() { return way != Way.NONE; }
        public boolean up() { return way == Way.UP; }
    }

    /** What the world about him really gives of an item: how many it yields by these ways, and the soil a column still asks. */
    public record Available(int count, int soil) {
        public boolean some() { return count > 0; }
    }

    private CompanionReach() { }

    /**
     * How he gets to this block and works it. {@code reach}: how close a stand cell must be; {@code forTheDrop}: the drop must
     * rest where he can pick it up from that cell; {@code allowDugSoil}: soil he can dig about him counts for a column;
     * {@code maxSteps}: the most steps a column may take (a leaf crown keeps its short one).
     */
    public static How how(CompanionEntity c, ServerLevel level, BlockPos block, double reach, boolean forTheDrop, boolean allowDugSoil, int maxSteps) {
        if (!level.hasChunkAt(block) || level.getBlockState(block).isAir()) return new How(Way.NONE, 0);
        BlockPos feet = c.blockPosition();
        // The block under his feet is worked from where he stands: its drop rests in the hole and he steps down into it.
        if (block.equals(feet.below()) && CompanionMiningNavigation.visible(c, c.getEyePosition(), block)
                && (!forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, feet))) return new How(Way.DOWN, 0);
        // On the bed of the water he works it from the water over it, as the swimmer stands at the surface (CompanionSwimming):
        // the dry stand cells of standCells are no use there, and a block below him is no way up.
        boolean wet = !level.getFluidState(block).isEmpty() || !level.getFluidState(block.above()).isEmpty();
        var stands = new java.util.HashSet<BlockPos>();
        if (wet) {
            for (int dy = 1; dy <= 11; dy++) {
                BlockPos cell = block.above(dy);
                if (!level.getFluidState(cell).is(FluidTags.WATER)) break;
                if (CompanionMiningNavigation.pickupStand(c, cell)) { stands.add(cell); break; }
            }
        } else {
            // A block with a body is worked from a cell beside it, not from on top of it; a flower, a cane or a vine has no body
            // for the eye to rest on: it is worked from a cell the ray reaches it from (the picking's own rule, CompanionPicking).
            boolean solid = !level.getBlockState(block).getCollisionShape(level, block).isEmpty();
            if (solid) {
                stands.addAll(CompanionMiningNavigation.standCells(c, block, reach, false, true));
                if (forTheDrop) stands.removeIf(stand -> !CompanionMiningNavigation.dropCollectable(level, block, stand));
            } else {
                for (int dy = -2; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = block.offset(dx, dy, dz);
                    if (!CompanionMiningNavigation.standable(c, cell) || level.getBlockState(cell).is(net.minecraft.world.level.block.Blocks.COBWEB)) continue;
                    Vec3 eye = Vec3.atBottomCenterOf(cell).add(0, c.getEyeHeight(), 0);
                    if (level.clip(new net.minecraft.world.level.ClipContext(eye, block.getCenter(),
                            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, c)).getType()
                            == net.minecraft.world.phys.HitResult.Type.MISS) stands.add(cell);
                }
            }
        }
        if (!stands.isEmpty()) {
            // A block farther than his own search is a walk in legs, not a lost one (CompanionTravel).
            return new How(block.distSqr(feet) > (double) c.getSearchRadius() * c.getSearchRadius() ? Way.ACROSS : Way.WALK, 0);
        }
        // No stand cell reaches it: up a column of his own soil, at most sixteen blocks above his feet and four across.
        // The way up is the player's switch («Строить себе путь»): off, no column is raised.
        if (!c.buildWay()) return new How(Way.NONE, 0);
        int rise = block.getY() - feet.getY();
        if (rise < 2 || rise > CompanionPillar.WAY_HEIGHT) return new How(Way.NONE, 0);
        double dx = block.getX() + .5D - c.getX(), dz = block.getZ() + .5D - c.getZ();
        if (dx * dx + dz * dz > 16.0D) return new How(Way.NONE, 0);
        int limit = Math.clamp(maxSteps, 1, CompanionPillar.WAY_SUPPORTS);
        int carried = c.temporaryPillar.soilCarried();
        int least = c.temporaryPillar.leastSteps(block, limit);
        if (least <= 0) return new How(Way.NONE, 0);
        if (least <= carried || allowDugSoil && soilAbout(c, level)) return new How(Way.UP, least);
        return new How(Way.NONE, 0);
    }

    /** How many of these blocks he can work by these ways, and the soil a column still asks (dug soil counts when allowed). */
    public static Available available(CompanionEntity c, ServerLevel level, List<BlockPos> blocks, double reach, boolean forTheDrop, boolean allowDugSoil, int maxSteps) {
        int count = 0, steps = 0;
        for (BlockPos block : blocks) {
            How how = how(c, level, block, reach, forTheDrop, allowDugSoil, maxSteps);
            if (!how.yes()) continue;
            count++;
            if (how.up()) steps = Math.max(steps, how.steps());
        }
        int soil = steps <= 0 ? 0 : Math.max(0, steps - c.temporaryPillar.soilCarried());
        if (soil > 0 && !(allowDugSoil && soilAbout(c, level))) soil = 0;
        return new Available(count, soil);
    }

    /**
     * Whether a block of soil stands within the dig's reach, where he may take it: the soil for a column (the dig's own rule,
     * the site of a building and all, CompanionDigGoal).
     */
    static boolean soilAbout(CompanionEntity c, ServerLevel level) {
        BlockPos origin = c.blockPosition();
        for (BlockPos pos : CompanionLogSearch.find(level, origin, 12, BlockTags.DIRT)) {
            if (!c.leashAllows(pos) || !CompanionGround.SOIL.matches(level.getBlockState(pos))) continue;
            if (pos.getY() < origin.getY() - 1 || pos.getY() > origin.getY() + 5 || pos.distSqr(origin) > 12 * 12) continue;
            if (!CompanionGround.SOIL.safeCell(level, pos, c) || !exposed(level, pos)) continue;
            return true;
        }
        return false;
    }

    /** A face of the block open to the air: the dig takes only a block he can come at from a stand. */
    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            if (level.hasChunkAt(beside) && level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) return true;
        }
        return false;
    }
}
