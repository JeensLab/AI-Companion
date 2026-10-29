package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionSortGroups.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionSortingTests.chest;
import static dev.aicompanion.gametest.CompanionSortingTests.chestsHolding;
import static dev.aicompanion.gametest.CompanionSortingTests.groups;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;
import static dev.aicompanion.gametest.CompanionSortingTests.totals;

/** A group that spills into a second chest shared with another group (issue 32, ticket 07): the spill must not fail the work. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingOverflowTests {
    /** The near chest has room for the wood the far, full one holds: stone and wood trade places through the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void spilledStoneAndMisplacedWoodTradePlacesInFullChests(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container near = chest(helper, 11), far = chest(helper, 15);
        for (int slot = 0; slot < 10; slot++) near.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 10; slot < 23; slot++) near.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 0; slot < 20; slot++) far.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 20; slot < 27; slot++) far.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        Container[] chests = {near, far}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(totals(companion).isEmpty(), "His bag is not as before: " + totals(companion));
            helper.assertTrue(chestsHolding(chests, Group.WOOD) == 1, "Wood is in " + chestsHolding(chests, Group.WOOD) + " chests");
            helper.assertTrue(groups(far).equals(EnumSet.of(Group.STONE)), "The far chest is not stone only: " + groups(far));
        });
    }

    /** The wood of the third chest must go into the full second one, which holds one stone of the spill: the stone goes to the chest with room. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void woodReachesItsFullChestWhenTheSpilledStoneMovesAway(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 11), b = chest(helper, 15), c = chest(helper, 7);
        for (int slot = 0; slot < 27; slot++) a.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 0; slot < 26; slot++) b.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        b.setItem(26, new ItemStack(Items.COBBLESTONE, 64));
        c.setItem(0, new ItemStack(Items.OAK_LOG, 1)); c.setItem(1, new ItemStack(Items.PAPER, 1));
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(totals(companion).isEmpty(), "His bag is not as before: " + totals(companion));
            helper.assertTrue(chestsHolding(chests, Group.WOOD) == 1, "Wood is in " + chestsHolding(chests, Group.WOOD) + " chests");
            helper.assertTrue(groups(a).equals(EnumSet.of(Group.STONE)), "The full stone chest is not stone only: " + groups(a));
        });
    }

    /** Eight empty chests: the closing tidy of each is no «trip», and the answer is «nothing to sort», not «things keep changing». */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void manyEmptyChestsAreNothingToSortNotAFailure(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        for (int x : new int[]{7, 9, 11, 13, 15}) helper.setBlock(new BlockPos(x, 1, 13), Blocks.CHEST);
        for (int x : new int[]{8, 10, 12}) helper.setBlock(new BlockPos(x, 1, 15), Blocks.CHEST);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still working: " + companion.getLastTaskResult());
            helper.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().startsWith("В сундуках пусто, раскладывать нечего."), companion.getLastTaskResult());
        });
    }
}
