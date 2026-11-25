package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The GameTest server ticks flat out while light is worked out on a thread of its own, so rock a fixture has just laid stays lit by the sky
 * for as many ticks as the other tests of the batch keep that thread busy: a carved cell "sees the sky", a cave counts as the surface, a
 * tunnel as bright. A real server gives the thread its 50 ms; here every tick waits for it, so light is up to date in all tests at any load.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTestLight {
    // A chunk nobody loads queues behind the work of every loaded one.
    private static final int UNLOADED_CHUNK = 1_870_000;
    private static final int ROCK_SIDE = 30, ROCK_TOP = 22, CARVED_Y = 10, CARVED_XZ = 15;

    private CompanionTestLight() {
    }

    @SubscribeEvent
    public static void awaitLight(ServerTickEvent.Post event) {
        if (!(event.getServer() instanceof GameTestServer server)) return;
        for (ServerLevel level : server.getAllLevels()) {
            ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
            CompletableFuture<?> settled = light.waitForPendingTasks(UNLOADED_CHUNK, UNLOADED_CHUNK);
            while (!settled.isDone()) {
                light.tryScheduleUpdate();
                // parkNanos sleeps a timer tick on Windows, and this round trip happens every tick.
                Thread.yield();
            }
        }
    }

    /** The cell of a rock laid a tick ago is dark, however much light work the rest of the batch has made. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aCellCarvedInFreshRockSeesNoSkyOnTheNextTick(GameTestHelper h) {
        BlockPos carved = new BlockPos(CARVED_XZ, CARVED_Y, CARVED_XZ);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(ROCK_SIDE, ROCK_TOP, ROCK_SIDE)))
            h.getLevel().setBlock(h.absolutePos(pos), Blocks.STONE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(h.absolutePos(carved), Blocks.AIR.defaultBlockState(), 2 | 16);
        h.runAfterDelay(1, () -> {
            h.assertTrue(!h.getLevel().canSeeSky(h.absolutePos(carved)), "A cell deep in the rock still sees the sky");
            h.succeed();
        });
    }
}
