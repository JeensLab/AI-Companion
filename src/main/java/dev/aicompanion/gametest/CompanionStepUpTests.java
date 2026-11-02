package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The forest endurance run, minute 92: from the foot of his stairs the route took the first step up diagonally, past a niche with a
 * low ceiling. The jump drifted into the niche and his head met the ceiling, again and again, until the watchdog ended the order.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStepUpTests {
    @AfterBatch(batch = "step_up")
    public static void afterStepUp(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    @GameTest(template = "tall_tree", timeoutTicks = 900, batch = "step_up")
    public static void noDiagonalJumpPastALowCeiling(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 14; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 7 ? Blocks.STONE : Blocks.AIR);
        air(h, 12, 12, 2, 5); // the foot of the stairs
        air(h, 12, 11, 2, 5); // the way round
        air(h, 11, 12, 2, 3); // the niche: a stone ceiling two blocks over its floor
        air(h, 11, 11, 3, 5); // the first step up
        air(h, 10, 11, 4, 6);
        air(h, 9, 11, 5, 7);
        air(h, 8, 11, 6, 8);
        air(h, 7, 11, 7, 9);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 11, 2, 12);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos surface = h.absolutePos(new BlockPos(5, 8, 11));
        owner.teleportTo(surface.getX() + .5, surface.getY(), surface.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(32);
        BlockPos chest = new BlockPos(5, 8, 13);
        h.setBlock(chest, Blocks.CHEST);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        companion.startWork("store_items", "all", 1);
        h.succeedWhen(() -> h.assertTrue(!((ChestBlockEntity) h.getBlockEntity(chest)).isEmpty(),
                "Stuck at " + h.relativePos(companion.blockPosition()) + ", y " + companion.getY()));
    }

    private static void air(GameTestHelper h, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }
}
