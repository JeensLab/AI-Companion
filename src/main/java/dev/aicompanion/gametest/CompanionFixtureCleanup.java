package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionChunks;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** Finished fixtures must not keep walking/loading chunks during the next batch's thirty-minute scenario. */
@GameTestHolder(AICompanionMod.MOD_ID)
public final class CompanionFixtureCleanup {
    private static final long MORNING = 1000L;

    @AfterBatch(batch = "defaultBatch")
    public static void afterAtomicTests(ServerLevel level) { clean(level); }
    @AfterBatch(batch = "marathon")
    public static void afterMarathons(ServerLevel level) { clean(level); }
    @AfterBatch(batch = "autonomy")
    public static void afterAutonomy(ServerLevel level) { clean(level); }
    @AfterBatch(batch = "autonomy_night")
    public static void afterNight(ServerLevel level) { clean(level); }
    @AfterBatch(batch = "autonomy_session")
    public static void afterSessions(ServerLevel level) { clean(level); }

    static void clean(ServerLevel level) {
        if (!(level.getServer() instanceof GameTestServer)) return;
        List<CompanionEntity> completed = new ArrayList<>();
        for (var entity : level.getAllEntities()) if (entity instanceof CompanionEntity companion) completed.add(companion);
        for (var companion : completed) {
            companion.setLifestyle(false, false);
            CompanionChunks.release(companion.getUUID());
            companion.discard();
        }
        for (var player : List.copyOf(level.getServer().getPlayerList().getPlayers()))
            if (player.getGameProfile().getName().equals("test-mock-player")) level.getServer().getPlayerList().remove(player);
        CompanionChunks.stop();
        clearTrees(level);
        // The server keeps its daylight cycle and a batch restores the clock "as it was": a late batch would start at night.
        level.setDayTime(MORNING);
    }

    /**
     * Structures of finished batches stay in the world beside the next batch, and a goal looks for wood up to 64 blocks away:
     * a trunk left standing there is a tree he walks to (and fails to reach) in a test that has none of its own.
     */
    @SuppressWarnings("unchecked")
    private static void clearTrees(ServerLevel level) {
        Iterable<ChunkHolder> holders;
        try {
            holders = (Iterable<ChunkHolder>) ObfuscationReflectionHelper.findMethod(ChunkMap.class, "getChunks").invoke(level.getChunkSource().chunkMap);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot list the loaded chunks", e);
        }
        for (ChunkHolder holder : holders) {
            ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
            if (chunk == null) continue;
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                LevelChunkSection section = chunk.getSection(index);
                if (section.hasOnlyAir() || !section.maybeHas(CompanionFixtureCleanup::isTree)) continue;
                int bottom = level.getSectionYFromSectionIndex(index) << 4;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    if (!isTree(section.getBlockState(x, y, z))) continue;
                    level.setBlock(new BlockPos(chunk.getPos().getMinBlockX() + x, bottom + y, chunk.getPos().getMinBlockZ() + z),
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    private static boolean isTree(BlockState state) { return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES); }
}
