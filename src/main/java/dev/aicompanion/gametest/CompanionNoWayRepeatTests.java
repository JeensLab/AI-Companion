package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Skyblock probe 15.11: a falling player moves 3+ blocks every other tick, and every move was a new place to say
 * "no way to you" (24 lines in 3 s). He says it once while the player falls.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNoWayRepeatTests {
    private static final int ISLAND_TOP = 22; // top layer y; the island is three layers of soil in the air

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void aFallingPlayerIsToldNoWayOnlyOnce(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 30; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : Blocks.AIR);
        for (int x = 12; x <= 18; x++) for (int z = 12; z <= 18; z++) for (int y = ISLAND_TOP - 2; y <= ISLAND_TOP; y++)
            h.setBlock(new BlockPos(x, y, z), y == ISLAND_TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 14, ISLAND_TOP + 1, 15);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(15, ISLAND_TOP + 1, 15));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setFollowing(true);
        BlockPos floor = h.absolutePos(new BlockPos(24, 2, 15));
        // Off the edge and down 20 blocks in a second. A mock player has no physics: onGround is set by hand (false in the air).
        h.runAtTickTime(20, () -> owner.teleportTo(floor.getX() + .5, owner.getY(), floor.getZ() + .5));
        int[] said = {0}, saidFalling = {0};
        int[] serial = {companion.autonomyLineSerial()};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t > 20 && t <= 40) { owner.teleportTo(owner.getX(), Math.max(floor.getY(), owner.getY() - 1), owner.getZ()); owner.setOnGround(false); owner.fallDistance = t - 20; }
            if (t > 40) { owner.setOnGround(true); owner.fallDistance = 0; } // landed
            if (companion.autonomyLineSerial() != serial[0]) {
                serial[0] = companion.autonomyLineSerial();
                if (companion.autonomyLine().startsWith("Не могу пройти к тебе")) { said[0]++; if (t <= 40) saidFalling[0]++; }
            }
        });
        // Silent while he falls, told once when he has landed, and not again before the 10 s of the repeat could pass.
        h.runAtTickTime(100, () -> {
            h.assertTrue(saidFalling[0] == 0, "No-way lines said during the fall: " + saidFalling[0]);
            h.assertTrue(said[0] == 1, "No-way lines said after the landing: " + said[0]);
            h.succeed();
        });
    }
}
