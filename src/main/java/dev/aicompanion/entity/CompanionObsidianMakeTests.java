package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The block made of his own buckets when it does not set in time (the NOT_SET end of CompanionObsidian.make): the lava he poured is
 * his, so it is taken back into the empty bucket the pour left him instead of standing in the hole. The game sets lava under water at
 * once, so the branch is a guard — the test drives the take-back it does, with and without a bucket to take it.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionObsidianMakeTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theLavaOfABlockThatDidNotSetGoesBackIntoHisBucket(GameTestHelper h) {
        BlockPos cell = new BlockPos(10, 5, 10);
        h.setBlock(cell, Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, 6, 10);
        c.setItem(0, new ItemStack(Items.BUCKET, 1));
        h.assertTrue(CompanionObsidian.takeLavaBack(c, h.getLevel(), h.absolutePos(cell)), "The lava was not taken back");
        h.assertBlockPresent(Blocks.AIR, cell);
        h.assertTrue(count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.BUCKET) == 0,
                "Not his lava back in the bucket: " + c.getStoredItemsSummary());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void withoutABucketTheLavaStaysAndNoBucketIsInvented(GameTestHelper h) {
        BlockPos cell = new BlockPos(10, 5, 10);
        h.setBlock(cell, Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, 6, 10);
        h.assertTrue(!CompanionObsidian.takeLavaBack(c, h.getLevel(), h.absolutePos(cell)), "Lava taken with no bucket for it");
        h.assertBlockPresent(Blocks.LAVA, cell);
        h.assertTrue(count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 0,
                "A bucket appeared out of nothing: " + c.getStoredItemsSummary());
        h.succeed();
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        return count;
    }
}
