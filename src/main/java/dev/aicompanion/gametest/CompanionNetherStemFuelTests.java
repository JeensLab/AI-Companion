package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Nether stems do not burn, nor do the planks made of them: short of fuel, he must not turn them into planks. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNetherStemFuelTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void crimsonStemsAreNotMadeIntoFuelPlanks(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        h.setBlock(new BlockPos(20, 1, 16), Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3));
        companion.setItem(1, new ItemStack(Items.CRIMSON_STEM, 4));
        companion.setItem(2, new ItemStack(Items.WOODEN_AXE)); // with an axe in the bag, the only planks he could make are fuel
        h.assertTrue(CompanionEvents.executeAction(player, "smelt_items", "minecraft:raw_iron", 3).success(), "Smelt order not accepted");
        int[] planks = {0};
        h.onEachTick(() -> planks[0] = Math.max(planks[0], count(companion, Items.CRIMSON_PLANKS)));
        h.startSequence().thenIdle(120).thenExecute(() ->
                h.assertTrue(planks[0] == 0, "Crimson stems were made into " + planks[0] + " planks that do not burn: " + companion.getStoredItemsSummary()))
          .thenSucceed();
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }
}
