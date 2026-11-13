package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A player gets the bowl back from stew; so does he. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStewBowlTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aBowlIsLeftAfterEatingStew(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setFollowing(false);
        companion.setItem(0, new ItemStack(Items.MUSHROOM_STEW, 1));
        companion.setHealth(4.0F);
        h.succeedWhen(() -> {
            h.assertTrue(count(companion, Items.MUSHROOM_STEW) == 0, "He has not eaten the stew yet");
            int bowls = count(companion, Items.BOWL);
            for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(4.0D)))
                if (item.getItem().is(Items.BOWL)) bowls += item.getItem().getCount();
            h.assertTrue(bowls == 1, "Expected the bowl back, found " + bowls + ": " + companion.getStoredItemsSummary());
        });
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }
}
