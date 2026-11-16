package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A short walk order must move him along the heading, not report «walked» from the first tick. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCourseShortTests {
    @GameTest(template = "large_search", timeoutTicks = 600, skyAccess = true)
    public static void threeBlocksAreWalkedNotSkipped(GameTestHelper h) { walks(h, 3); }

    @GameTest(template = "large_search", timeoutTicks = 600, skyAccess = true)
    public static void oneBlockIsWalkedToo(GameTestHelper h) { walks(h, 1); }

    /** He ends on the count along the heading (the old end was three blocks short: at once for 1-3), not aside of the line. */
    private static void walks(GameTestHelper h, int blocks) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 20, 2, 64);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(17, 2, 64));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(60);
        double startX = companion.getX(), startZ = companion.getZ();
        h.assertTrue(CompanionEvents.executeAction(owner, "walk_heading", "east", blocks).success(), "Walk not accepted: " + companion.getLastTaskResult());
        long id = companion.activeWorkId();
        h.onEachTick(() -> owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ()));
        double[] endedAt = {Double.NaN, 0};
        h.onEachTick(() -> { if (Double.isNaN(endedAt[0]) && !companion.isWorking()) { endedAt[0] = companion.getX(); endedAt[1] = companion.getZ(); } });
        h.succeedWhen(() -> {
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && !companion.isWorking(), "Still walking");
            double along = endedAt[0] - startX, aside = Math.abs(endedAt[1] - startZ);
            h.assertTrue(result.success() && along >= blocks - .5 && along <= blocks + 2 && aside <= 1,
                    "Said «" + result.message() + "» after " + along + " blocks along, " + aside + " aside");
        });
    }
}
