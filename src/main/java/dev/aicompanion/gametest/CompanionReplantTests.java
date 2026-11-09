package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A felled tree gets a sapling of its wood back on the stump from his bag (idea RYVTB, #105). */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionReplantTests {
    /** An oak of three logs on grass, two oak saplings in the bag: the logs in the bag, an oak sapling on the stump, one sapling spent. */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void anOakStumpGetsASaplingFromHisBag(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.OAK_LOG, 18, 16, 1, 3);
        c.setItem(0, new ItemStack(Items.OAK_SAPLING, 2));
        c.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.OAK_LOG) == 3 && c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(h.getBlockState(new BlockPos(18, 1, 16)).is(Blocks.OAK_SAPLING), "No sapling on the stump: " + h.getBlockState(new BlockPos(18, 1, 16)));
            h.assertTrue(count(c, Items.OAK_SAPLING) == 1, "Not exactly one sapling spent: " + c.getStoredItemsSummary());
        });
    }

    /** The player turned it off: the same oak is felled and the stump stays bare, both saplings kept. */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void switchedOffTheStumpStaysBare(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setReplantSaplings(false);
        tree(h, Blocks.OAK_LOG, 18, 16, 1, 3);
        c.setItem(0, new ItemStack(Items.OAK_SAPLING, 2));
        c.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.OAK_LOG) == 3 && c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(h.getBlockState(new BlockPos(18, 1, 16)).isAir() && count(c, Items.OAK_SAPLING) == 2,
                    "Planted with the switch off: " + h.getBlockState(new BlockPos(18, 1, 16)) + ", bag " + c.getStoredItemsSummary());
        });
    }

    /** A dark oak grows only from a square of four: with three saplings its four stumps stay bare and the three are kept. */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void aDarkOakWithThreeSaplingsStaysBare(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.DARK_OAK_LOG, 18, 16, 2, 2);
        c.setItem(0, new ItemStack(Items.DARK_OAK_SAPLING, 3));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        c.startGatheringLogs(8);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.DARK_OAK_LOG) == 8 && c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.DARK_OAK_SAPLING) == 0 && count(c, Items.DARK_OAK_SAPLING) == 3,
                    "A dark oak planted short of four: " + blocks(h, Blocks.DARK_OAK_SAPLING) + " planted, bag " + c.getStoredItemsSummary());
        });
    }

    /** With four dark oak saplings the square of stumps gets all four back. */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void aDarkOakGetsItsSquareFromFour(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.DARK_OAK_LOG, 18, 16, 2, 2);
        c.setItem(0, new ItemStack(Items.DARK_OAK_SAPLING, 4));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        c.startGatheringLogs(8);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.DARK_OAK_LOG) == 8 && c.didLastTaskSucceed(), c.getLastTaskResult());
            for (int x = 18; x <= 19; x++) for (int z = 16; z <= 17; z++)
                h.assertTrue(h.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DARK_OAK_SAPLING), "No sapling at " + x + "," + z + ": bag " + c.getStoredItemsSummary());
            h.assertTrue(count(c, Items.DARK_OAK_SAPLING) == 0, "Saplings left over: " + c.getStoredItemsSummary());
        });
    }

    /** Review 09.11: with no axe he sets his own bench by the trunk to make one; his bench is not the player's building, the square goes down. */
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void hisOwnBenchByTheTreeDoesNotStopTheSquare(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.DARK_OAK_LOG, 18, 16, 2, 3);
        c.setItem(0, new ItemStack(Items.DARK_OAK_SAPLING, 4));
        c.startGatheringLogs(6);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.CRAFTING_TABLE) == 1, "He made no bench of his own: " + c.getStoredItemsSummary());
            h.assertTrue(blocks(h, Blocks.DARK_OAK_SAPLING) == 4 && count(c, Items.DARK_OAK_SAPLING) == 0,
                    "His own bench kept the square bare: " + blocks(h, Blocks.DARK_OAK_SAPLING) + " planted, bag " + c.getStoredItemsSummary());
        });
    }

    /** Only the felled tree's own wood: an oak with nothing but birch saplings in the bag stays bare, the birch ones are kept. */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void aSaplingOfAnotherWoodIsNotUsed(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.OAK_LOG, 18, 16, 1, 3);
        c.setItem(0, new ItemStack(Items.BIRCH_SAPLING, 2));
        c.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.OAK_LOG) == 3 && c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(h.getBlockState(new BlockPos(18, 1, 16)).isAir() && count(c, Items.BIRCH_SAPLING) == 2,
                    "Another wood planted: " + h.getBlockState(new BlockPos(18, 1, 16)) + ", bag " + c.getStoredItemsSummary());
        });
    }

    /** A giant spruce and two saplings: short of its square it gets one sapling, which grows an ordinary spruce. */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void aGiantSpruceShortOfFourGetsOne(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.SPRUCE_LOG, 18, 16, 2, 2);
        c.setItem(0, new ItemStack(Items.SPRUCE_SAPLING, 2));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        c.startGatheringLogs(8);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.SPRUCE_LOG) == 8 && c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.SPRUCE_SAPLING) == 1 && count(c, Items.SPRUCE_SAPLING) == 1,
                    "Not exactly one sapling for the spruce: " + blocks(h, Blocks.SPRUCE_SAPLING) + " planted, bag " + c.getStoredItemsSummary());
        });
    }

    /** By the player's building (planks two blocks off the stump) the stump stays bare. */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void byThePlayersBuildingTheStumpStaysBare(GameTestHelper h) {
        CompanionEntity c = setup(h);
        tree(h, Blocks.OAK_LOG, 18, 16, 1, 3);
        for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(18, y, 18), Blocks.OAK_PLANKS);
        c.setItem(0, new ItemStack(Items.OAK_SAPLING, 2));
        c.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(count(c, Items.OAK_LOG) == 3 && c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(h.getBlockState(new BlockPos(18, 1, 16)).isAir() && count(c, Items.OAK_SAPLING) == 2,
                    "Planted by the building: " + h.getBlockState(new BlockPos(18, 1, 16)) + ", bag " + c.getStoredItemsSummary());
        });
    }

    /** The switch lives in the save with him; an old save has it on. */
    @GameTest(template = "empty")
    public static void theSaplingSwitchIsSavedAndAnOldSaveHasItOn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        h.assertTrue(c.replantSaplings(), "The switch is not on by default");
        c.setReplantSaplings(false);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        other.load(c.saveWithoutId(new CompoundTag()));
        h.assertTrue(!other.replantSaplings(), "The switch was not saved");
        CompanionEntity third = h.spawn(AICompanionMod.COMPANION.get(), 12, 1, 12);
        third.setReplantSaplings(false);
        third.load(new CompoundTag());
        h.assertTrue(third.replantSaplings(), "An old save loaded with the switch off");
        h.succeed();
    }

    /** A trunk of side×side columns, height logs tall, each column on a grass block. */
    private static void tree(GameTestHelper h, Block log, int x, int z, int side, int height) {
        for (int dx = 0; dx < side; dx++) for (int dz = 0; dz < side; dz++) {
            h.setBlock(new BlockPos(x + dx, 0, z + dz), Blocks.GRASS_BLOCK);
            for (int y = 1; y <= height; y++) h.setBlock(new BlockPos(x + dx, y, z + dz), log);
        }
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking() && !companion.isRecoveringSupports(), "Work still active: " + companion.getLastTaskResult());
    }

    private static int blocks(GameTestHelper h, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 3, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
