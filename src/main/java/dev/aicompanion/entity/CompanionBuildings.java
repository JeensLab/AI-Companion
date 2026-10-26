package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/**
 * Telling a player's building from nature (review 22.10, К2: a log cabin was «trees», a cobblestone wall was «stone»).
 * A log is a tree when it has a living crown; logs without one that touch crafted blocks (planks, doors, glass, torches…)
 * are a building. Lone logs without either stay trees, as before.
 */
final class CompanionBuildings {
    private CompanionBuildings() { }

    /** Blocks nobody finds in the wild next to logs or stone: made at a bench or placed by hand. */
    static boolean crafted(BlockState state) {
        return state.is(BlockTags.PLANKS) || state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS)
                || state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.STONE_BRICKS) || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES) || state.is(Blocks.BRICKS) || state.is(Blocks.LADDER)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.FURNACE) || state.is(Blocks.SMOOTH_STONE);
    }

    static boolean nearCrafted(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos near = pos.relative(side);
            if (level.hasChunkAt(near) && (crafted(level.getBlockState(near)) || level.getBlockEntity(near) != null)) return true;
        }
        return false;
    }

    /** Natural leaves (not placed by a player) or a nether crown right by this log. */
    static boolean crownBeside(ServerLevel level, BlockPos log) {
        for (BlockPos pos : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) return true;
            if (state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT)) return true;
        }
        return false;
    }

    /**
     * Logs set among leaves a player placed (persistent), with no living crown anywhere: a player's planting or decoration, not a
     * tree of the wild. Only a deed of his own leaves them alone (Толик 23.10: «не трогает … посаженное мной»).
     */
    static boolean decorated(ServerLevel level, Collection<BlockPos> logs) {
        boolean placedLeaves = false;
        for (BlockPos log : logs) {
            if (crownBeside(level, log)) return false;
            for (BlockPos pos : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
                if (!level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                placedLeaves |= state.getBlock() instanceof LeavesBlock && state.getValue(LeavesBlock.PERSISTENT);
            }
        }
        return placedLeaves;
    }

    /** A connected group of logs is a building: no living crown anywhere on it, and it touches crafted blocks. */
    static boolean builtLogs(ServerLevel level, Collection<BlockPos> logs) {
        boolean touchesCrafted = false;
        for (BlockPos log : logs) {
            if (crownBeside(level, log)) return false;
            touchesCrafted |= nearCrafted(level, log);
        }
        return touchesCrafted;
    }
}
