package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionSortingTests.chest;
import static dev.aicompanion.gametest.CompanionSortingTests.count;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;

/** «Put the things away» goes to the chest where the same group already lies (issue 32), and the old way for what has no such chest. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStoreGroupsTests {
    /** The stream: «он всё складывает в один сундук». The logs chest is the farther one from him (the old order goes by nearness to Oleg), so the old order put everything into the stone chest. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void storedThingsGoToTheChestOfTheirGroup(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container stone = chest(helper, 15), wood = chest(helper, 12);
        stone.setItem(0, new ItemStack(Items.COBBLESTONE, 10)); wood.setItem(0, new ItemStack(Items.OAK_LOG, 10));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5)); companion.setItem(1, new ItemStack(Items.OAK_LOG, 5));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(stone, Items.COBBLESTONE) == 15 && count(stone, Items.OAK_LOG) == 0, "Stone chest: " + count(stone, Items.COBBLESTONE) + " stone, " + count(stone, Items.OAK_LOG) + " logs");
            helper.assertTrue(count(wood, Items.OAK_LOG) == 15 && count(wood, Items.COBBLESTONE) == 0, "Wood chest: " + count(wood, Items.OAK_LOG) + " logs, " + count(wood, Items.COBBLESTONE) + " stone");
            helper.assertTrue(count(companion, Items.COBBLESTONE) == 0 && count(companion, Items.OAK_LOG) == 0, "Something stayed in the bag: " + companion.getStoredItemsSummary());
        });
    }

    /** Bones have no chest of their group: they go the old way (the nearest chest with room), while the logs find theirs. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void thingWithNoChestOfItsGroupGoesTheOldWayWhileOthersFindTheirs(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container first = chest(helper, 15), second = chest(helper, 12);
        first.setItem(0, new ItemStack(Items.DIAMOND, 3)); second.setItem(0, new ItemStack(Items.OAK_LOG, 10));
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 5)); companion.setItem(1, new ItemStack(Items.BONE, 4));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(second, Items.OAK_LOG) == 15 && count(first, Items.OAK_LOG) == 0, "Logs not with the logs");
            helper.assertTrue(count(first, Items.BONE) == 4 && count(first, Items.DIAMOND) == 3, "Bones did not go the old way to the nearest chest: " + count(first, Items.BONE));
            helper.assertTrue(count(companion, Items.BONE) + count(companion, Items.OAK_LOG) == 0, "Something stayed in the bag: " + companion.getStoredItemsSummary());
        });
    }

    /** Protective: a thing no chest has a group for lands where it always did, in the nearest chest with room. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void thingsOfNoPresentGroupKeepTheOldOrder(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container near = chest(helper, 15), far = chest(helper, 12);
        near.setItem(0, new ItemStack(Items.DIAMOND, 3)); far.setItem(0, new ItemStack(Items.BONE, 2));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5)); companion.setItem(1, new ItemStack(Items.OAK_LOG, 5));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(near, Items.COBBLESTONE) == 5 && count(near, Items.OAK_LOG) == 5 && count(far, Items.COBBLESTONE) + count(far, Items.OAK_LOG) == 0, "Not put in the nearest chest");
        });
    }

    /** Protective: the chest of their group has room for three only — those go there, the rest finds a place by the old order, nothing is lost. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void fullChestOfTheGroupSendsTheRestTheOldWay(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container stone = chest(helper, 15), other = chest(helper, 12);
        for (int slot = 0; slot < stone.getContainerSize(); slot++) stone.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        stone.setItem(0, new ItemStack(Items.COBBLESTONE, 61));
        other.setItem(0, new ItemStack(Items.DIAMOND, 3));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(stone, Items.COBBLESTONE) == 27 * 64 && count(other, Items.COBBLESTONE) == 7 && count(companion, Items.COBBLESTONE) == 0,
                    "Stone lost or doubled: " + count(stone, Items.COBBLESTONE) + " + " + count(other, Items.COBBLESTONE) + " + " + count(companion, Items.COBBLESTONE));
        });
    }

    /** The bag's reserve of eight soil stays put when the chest of the group takes the rest (the old order put the logs into the soil chest). */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void soilReserveStaysInTheBagWhenAGroupChestTakesTheRest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container stone = chest(helper, 15), wood = chest(helper, 12);
        stone.setItem(0, new ItemStack(Items.DIRT, 1)); wood.setItem(0, new ItemStack(Items.OAK_LOG, 1));
        companion.setItem(0, new ItemStack(Items.DIRT, 12)); companion.setItem(1, new ItemStack(Items.OAK_LOG, 3));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.DIRT) == 8 && count(stone, Items.DIRT) == 5 && count(wood, Items.OAK_LOG) == 4, "Reserve or groups wrong: bag " + companion.getStoredItemsSummary());
        });
    }

    /** «Поставь сундук и сложи в него вещи»: the chest he set down himself is filled, not the old one 20 blocks away where stone already lies. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void storeFillsTheChestHeJustPlaced(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        BlockPos oldPos = new BlockPos(2, 1, 2);
        helper.setBlock(oldPos, Blocks.CHEST);
        Container old = (Container)helper.getBlockEntity(oldPos);
        old.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        companion.setItem(0, new ItemStack(Items.CHEST));
        companion.setItem(1, new ItemStack(Items.COBBLESTONE, 12));
        helper.assertTrue(new BlockPos(16, 1, 16).distSqr(oldPos) > 19 * 19, "The old chest has to stand about 20 blocks away");
        companion.startWork("place_chest", "", 1);
        boolean[] storeStarted = {false};
        helper.onEachTick(() -> {
            if (!storeStarted[0] && !companion.hasActiveWorkOrder()) { storeStarted[0] = true; companion.startWork("store_items", "stone", 1); }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(storeStarted[0] && !companion.hasActiveWorkOrder(), "Still working: " + companion.getLastTaskResult());
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            BlockPos fresh = null; int chests = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31)))
                if (!pos.equals(oldPos) && helper.getBlockState(pos).is(Blocks.CHEST)) { chests++; fresh = pos.immutable(); }
            helper.assertTrue(chests == 1 && fresh != null, "Chests beside the old one: " + chests);
            helper.assertTrue(count((Container)helper.getBlockEntity(fresh), Items.COBBLESTONE) == 12,
                    "The chest he placed is not filled: " + count((Container)helper.getBlockEntity(fresh), Items.COBBLESTONE));
            helper.assertTrue(count(old, Items.COBBLESTONE) == 10, "The old chest was filled instead: " + count(old, Items.COBBLESTONE));
            helper.assertTrue(count(companion, Items.COBBLESTONE) == 0, "Stone stayed in the bag: " + companion.getStoredItemsSummary());
        });
    }

    /** No chest anywhere around: he crafts one, sets it down and fills that same chest within one step. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void storeFillsTheChestHeCraftedAndPlacedInOneStep(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_PLANKS, 12));
        companion.setItem(1, new ItemStack(Items.COBBLESTONE, 12));
        companion.startWork("store_items", "stone", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            BlockPos chest = null; int chests = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31)))
                if (helper.getBlockState(pos).is(Blocks.CHEST)) { chests++; chest = pos.immutable(); }
            helper.assertTrue(chests == 1 && chest != null, "Chests he set down: " + chests);
            helper.assertTrue(count((Container)helper.getBlockEntity(chest), Items.COBBLESTONE) == 12, "The chest he placed is not filled: " + companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.COBBLESTONE) == 0, "Stone stayed in the bag: " + companion.getStoredItemsSummary());
        });
    }
}
