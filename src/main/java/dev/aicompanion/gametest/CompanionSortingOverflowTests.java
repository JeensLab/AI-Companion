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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;
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

    /** Ticket 08: 40 cells of stone, no chest but its own has room for all that is left of it: the rest goes into the two with room, nothing stays in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void stoneSpillsOverTwoChestsWhenNoneHasRoomForAllOfIt(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 11), b = chest(helper, 15), c = chest(helper, 7);
        for (int slot = 0; slot < 27; slot++) a.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 0; slot < 13; slot++) b.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 13; slot < 27; slot++) b.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 0; slot < 6; slot++) c.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 6; slot < 21; slot++) c.setItem(slot, new ItemStack(Items.PAPER, 64));
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before) && totals(companion).isEmpty(), "Items lost, doubled or left in the bag: " + totals(companion));
            helper.assertTrue(chestsHolding(chests, Group.WOOD) == 1 && chestsHolding(chests, Group.OTHER) == 1, "Wood or paper split: " + groups(a) + groups(b) + groups(c));
            helper.assertTrue(groups(a).equals(EnumSet.of(Group.STONE)), "The stone chest holds more: " + groups(a));
        });
    }

    /** Ticket 08: five groups share the last of three chests and take 30 cells of its 27; the three left over go where there is room. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void theSharedLastChestOverflowsIntoTheRoomOfTheOthers(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 11), b = chest(helper, 15), c = chest(helper, 7);
        fill(a, 0, 10, Items.COBBLESTONE); fill(a, 10, 5, Items.OAK_LOG); fill(a, 15, 4, Items.IRON_INGOT); fill(a, 19, 3, Items.BREAD); fill(a, 22, 3, Items.IRON_SWORD);
        fill(b, 0, 5, Items.COBBLESTONE); fill(b, 5, 9, Items.OAK_LOG); fill(b, 14, 4, Items.IRON_INGOT); fill(b, 18, 5, Items.BONE);
        fill(c, 0, 3, Items.BREAD); fill(c, 3, 3, Items.IRON_SWORD); fill(c, 6, 5, Items.PAPER);
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before) && totals(companion).isEmpty(), "Items lost, doubled or left in the bag: " + totals(companion));
            helper.assertTrue(groups(a).equals(EnumSet.of(Group.STONE)), "The stone chest holds more: " + groups(a));
            for (Group group : List.of(Group.WOOD, Group.ORE, Group.FOOD, Group.GEAR, Group.MOB)) helper.assertTrue(chestsHolding(chests, group) == 1, group + " is split");
        });
    }

    /**
     * Protective, ticket 08: a double chest farther than two of bread. Moving bread to the near chest could turn the plan over (it was made
     * from what the chests held at every opening) and leave 28 cells of wood, ore and paper for a chest of 27, depending on his route.
     * The layout is made once: bread takes both single chests, the rest shares the double one.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void breadMovingToTheNearChestDoesNotTurnThePlanOver(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 11), b = chest(helper, 9);
        var facing = net.minecraft.core.Direction.NORTH;
        BlockPos left = new BlockPos(14, 1, 13);
        helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(new BlockPos(15, 1, 13), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        Container d = ChestBlock.getContainer((ChestBlock)Blocks.CHEST, helper.getBlockState(left), helper.getLevel(), helper.absolutePos(left), true);
        fill(a, 0, 24, Items.BREAD); fill(b, 0, 10, Items.BREAD);
        fill(d, 0, 11, Items.OAK_LOG); fill(d, 11, 7, Items.IRON_INGOT); fill(d, 18, 10, Items.BREAD); fill(d, 28, 10, Items.PAPER);
        Container[] chests = {a, b, d}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before) && totals(companion).isEmpty(), "Items lost, doubled or left in the bag: " + totals(companion));
            helper.assertTrue(groups(a).equals(EnumSet.of(Group.FOOD)) && groups(b).equals(EnumSet.of(Group.FOOD)) && groups(d).equals(EnumSet.of(Group.WOOD, Group.ORE, Group.OTHER)),
                    "Not as laid out: " + groups(a) + groups(b) + groups(d));
        });
    }

    private static void fill(Container chest, int from, int cells, Item item) {
        for (int slot = from; slot < from + cells; slot++) chest.setItem(slot, new ItemStack(item, new ItemStack(item).getMaxStackSize()));
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
