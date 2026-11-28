package dev.aicompanion.terrain;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionHostiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** One survey from one position. Only the first visible face of each sightline contributes evidence. */
final class TerrainSight {
    final Set<BlockPos> ground = new LinkedHashSet<>();
    final Set<BlockPos> openAtFoot = new LinkedHashSet<>();
    final Set<BlockPos> water = new LinkedHashSet<>();
    final Set<BlockPos> shallow = new LinkedHashSet<>();
    final Set<BlockPos> roofs = new LinkedHashSet<>();
    final Set<BlockPos> walls = new LinkedHashSet<>();
    private final Set<BlockPos> identified = new LinkedHashSet<>();
    private final Set<BlockPos> spruce = new LinkedHashSet<>(), podzol = new LinkedHashSet<>(), snow = new LinkedHashSet<>(),
            sand = new LinkedHashSet<>(), cactus = new LinkedHashSet<>(), birch = new LinkedHashSet<>(), leaves = new LinkedHashSet<>();
    BlockPos monster;
    BlockPos nearestOre;
    String nearestOreKind;

    void look(CompanionEntity c, Direction forward, int first, int last) {
        BlockPos start = c.blockPosition();
        int range = range(c);
        if (first == 0) {
            int reach = range;
            for (Monster mob : c.level().getEntitiesOfClass(Monster.class, c.getBoundingBox().inflate(reach))) {
                Vec3 offset = mob.position().subtract(c.position());
                double depth = offset.x * forward.getStepX() + offset.z * forward.getStepZ();
                double side = offset.x * forward.getStepZ() - offset.z * forward.getStepX();
                if (depth <= 0 || Math.abs(side) > depth || !CompanionHostiles.observed(c, mob, reach)) continue;
                if (c.distanceToSqr(mob) > 9 && c.level().getMaxLocalRawBrightness(mob.blockPosition()) < 4) continue;
                if (monster == null || start.distSqr(mob.blockPosition()) < start.distSqr(monster)) monster = mob.blockPosition();
            }
        }
        for (int ahead = first; ahead <= Math.min(range, last); ahead++) {
            int width = Math.min(4, ahead);
            for (int across = -width; across <= width; across++) {
                BlockPos column = start.relative(forward, ahead).relative(forward.getClockWise(), across);
                // Looking down at the floor as well as across the horizon and up at overhangs and foliage.
                for (int dy : HEIGHTS) {
                    Vec3 end = Vec3.atBottomCenterOf(column.above(dy)).add(forward.getStepX() * .38, -.035, forward.getStepZ() * .38);
                    if (end.distanceToSqr(c.getEyePosition()) <= range * range) see(c, end);
                }
            }
        }
    }

    private static final int[] HEIGHTS = {-7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 6};

    private void see(CompanionEntity c, Vec3 end) {
        Level level = c.level();
        BlockPos from = c.blockPosition(), to = BlockPos.containing(end);
        BlockHitResult hit = visibleTrace(c, c.getEyePosition(), end);
        if (hit == null) return;
        if (hit.getType() != HitResult.Type.BLOCK) {
            if (to.getY() == from.getY() - 1 && level.getMaxLocalRawBrightness(to) >= 4) openAtFoot.add(to);
            return;
        }
        BlockPos p = hit.getBlockPos().immutable();
        BlockState state = level.getBlockState(p);
        if (identified.add(p)) {
            if (nearestOre == null || from.distSqr(p) < from.distSqr(nearestOre)) {
                String ore = LandmarkObserver.ore(state);
                if (ore != null) {
                    nearestOre = p;
                    nearestOreKind = ore;
                }
            }
            if (state.is(Blocks.SPRUCE_LEAVES) || state.is(Blocks.SPRUCE_LOG)) spruce.add(p);
            if (state.is(Blocks.PODZOL)) podzol.add(p);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) snow.add(p);
            if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) sand.add(p);
            if (state.is(Blocks.CACTUS)) cactus.add(p);
            if (state.is(Blocks.BIRCH_LOG)) birch.add(p);
            if (state.is(Blocks.BIRCH_LEAVES)) leaves.add(p);
        }
        boolean naturalRock = naturalRock(state);
        if (hit.getDirection() == Direction.DOWN && naturalRock) roofs.add(p);
        if (hit.getDirection().getAxis().isHorizontal() && (naturalRock || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(BlockTags.TERRACOTTA))) walls.add(p);
        if (hit.getDirection() != Direction.UP) return;
        if (state.getFluidState().is(FluidTags.WATER) && state.getCollisionShape(level, p).isEmpty()) {
            water.add(p);
            // Look through the surface along this very sightline; a bank or a wall can hide the bottom.
            BlockHitResult bottom = clip(c, end, ClipContext.Fluid.NONE);
            BlockPos bed = bottom.getBlockPos();
            if (bottom.getType() == HitResult.Type.BLOCK && bottom.getDirection() == Direction.UP
                    && bed.getY() == p.getY() - 1 && bottom.getLocation().distanceToSqr(hit.getLocation()) <= 25
                    && level.getBlockState(bed).isFaceSturdy(level, bed, Direction.UP)) shallow.add(bed.above());
        } else if (state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, p)
                && !state.is(BlockTags.LEAVES) && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS)) ground.add(p);
    }

    private static BlockHitResult clip(CompanionEntity c, Vec3 end, ClipContext.Fluid fluid) {
        return c.level().clip(new ClipContext(c.getEyePosition(), end, ClipContext.Block.VISUAL, fluid, c));
    }

    static int range(CompanionEntity c) {
        return c.hasEffect(MobEffects.BLINDNESS) ? 3 : c.level().isRaining() ? 9 : 12;
    }

    static boolean naturalRock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER);
    }

    /** Null is unknown. A miss is returned only for an unobstructed ray through loaded chunks. */
    static BlockHitResult visibleTrace(CompanionEntity c, Vec3 eye, Vec3 end) {
        Level level = c.level();
        BlockPos from = c.blockPosition(), to = BlockPos.containing(end);
        if (!level.hasChunksAt(Math.min(from.getX(), to.getX()), Math.min(from.getZ(), to.getZ()),
                Math.max(from.getX(), to.getX()), Math.max(from.getZ(), to.getZ()))) return null;
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, c));
        if (hit.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(hit.getLocation()) > 9
                && level.getMaxLocalRawBrightness(hit.getBlockPos().relative(hit.getDirection())) < 4) return null;
        return hit;
    }

    private record BiomeClue(BlockPos at, String landscape, String evidence) { }

    private static void match(java.util.List<BiomeClue> clues, Set<BlockPos> plants, Set<BlockPos> surfaces,
                              String landscape, String evidence) {
        for (BlockPos plant : plants) {
            if (surfaces.stream().anyMatch(p -> p.distSqr(plant) <= 36)) {
                clues.add(new BiomeClue(plant, landscape, evidence));
                return;
            }
        }
    }

    String biome(BlockPos origin) {
        var clues = new java.util.ArrayList<BiomeClue>();
        match(clues, spruce, snow, "снежную тайгу", "ели и снег");
        if (clues.isEmpty()) match(clues, spruce, podzol, "тайгу", "ели и подзол");
        match(clues, cactus, sand, "пустыню", "кактусы и песок");
        match(clues, birch, leaves, "лес с берёзами", "берёзовые стволы и листву");
        if (clues.isEmpty()) {
            if (!spruce.isEmpty()) return "Вижу ель. По одному дереву биом не определю.";
            if (!snow.isEmpty()) return "Вижу снег. Остальных примет мало.";
            return "";
        }
        BiomeClue first = clues.getFirst();
        for (BiomeClue other : clues.subList(1, clues.size())) {
            if (other.at.distSqr(first.at) >= 25)
                return "Вижу " + first.evidence + " " + TerrainReading.where(origin, first.at) + ", а "
                        + other.evidence + " " + TerrainReading.where(origin, other.at) + ". Похоже на переход между биомами.";
        }
        if (clues.size() > 1) return "Приметы разных биомов здесь перемешаны. Границу пока не различу.";
        return "Вижу " + first.evidence + ". Похоже на " + first.landscape + ".";
    }
}
