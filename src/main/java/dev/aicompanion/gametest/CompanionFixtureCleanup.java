package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionChunks;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;

/** Finished fixtures must not keep walking/loading chunks during the next batch's thirty-minute scenario. */
@GameTestHolder(AICompanionMod.MOD_ID)
public final class CompanionFixtureCleanup {
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

    private static void clean(ServerLevel level) {
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
    }
}
