package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionSortGroups;
import dev.aicompanion.entity.CompanionSortGroups.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionSortingTests.chest;
import static dev.aicompanion.gametest.CompanionSortingTests.chestsHolding;
import static dev.aicompanion.gametest.CompanionSortingTests.count;
import static dev.aicompanion.gametest.CompanionSortingTests.describe;
import static dev.aicompanion.gametest.CompanionSortingTests.groups;
import static dev.aicompanion.gametest.CompanionSortingTests.inOrder;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;
import static dev.aicompanion.gametest.CompanionSortingTests.totals;

/** Sorting the chests when they are full, big or far from him (issue 32, ticket 06a): the exchange at a full chest, the size of a group, the set around the player, the limit of trips. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingFullTests {
    /** Two full chests and three free bag cells: he swaps a stack of the bag for one of the chest at each opening, as a player would with the cursor. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void fullChestsAreSortedThroughThreeFreeBagCells(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        for (int slot = 0; slot < companion.getContainerSize() - 3; slot++) companion.setItem(slot, new ItemStack(Items.STICK, 1 + slot));
        Container a = chest(helper, 9), b = chest(helper, 12);
        for (int slot = 0; slot < 14; slot++) a.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 14; slot < 27; slot++) a.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 0; slot < 13; slot++) b.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 13; slot < 27; slot++) b.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        Container[] chests = {a, b}; Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(totals(companion).equals(bagBefore), "His own bag changed: " + totals(companion));
            for (Container chest : chests) helper.assertTrue(groups(chest).size() == 1, "A chest holds several groups: " + groups(chest));
            helper.assertTrue(chestsHolding(chests, Group.STONE) == 1 && chestsHolding(chests, Group.WOOD) == 1, "A group is in two chests");
        });
    }

    /** One group of 35 cells of different single things does not fit one chest: it fills two, the log goes to the stone, nothing is left in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void manyDifferentThingsSpillIntoTheNextChest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        List<Item> other = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) if (item != Items.AIR && other.size() < 35 && CompanionSortGroups.of(new ItemStack(item)) == Group.OTHER) other.add(item);
        helper.assertTrue(other.size() == 35, "Not enough items of the group");
        Container a = chest(helper, 9), b = chest(helper, 12), c = chest(helper, 15);
        for (int slot = 0; slot < 3; slot++) a.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 0; slot < 20; slot++) b.setItem(slot, new ItemStack(other.get(slot)));
        for (int slot = 0; slot < 15; slot++) c.setItem(slot, new ItemStack(other.get(20 + slot)));
        c.setItem(15, new ItemStack(Items.OAK_LOG, 3));
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before) && totals(companion).equals(bagBefore), "Items lost, doubled or left in the bag: " + companion.getLastTaskResult());
            helper.assertTrue(chestsHolding(chests, Group.OTHER) == 2 && !groups(a).contains(Group.OTHER), "The other things are not in two chests: " + groups(a) + groups(b) + groups(c));
            helper.assertTrue(groups(a).equals(EnumSet.of(Group.STONE, Group.WOOD)) && inOrder(a), "The log is not with the stone: " + describe(a));
        });
    }

    /** The group is as big as its chest: a double one of 54 cells takes 40 cells of stone alone, the wood keeps its own. */
    @GameTest(template = "empty")
    public static void plannerTakesTheSizeFromTheChestChosenForTheGroup(GameTestHelper helper) {
        var plan = CompanionSortingTests.plan(List.of(new CompanionSortGroups.Chest(0, 1, 27, Map.of(Group.WOOD, 10.0)), new CompanionSortGroups.Chest(1, 2, 54, Map.of(Group.STONE, 30.0)), new CompanionSortGroups.Chest(2, 3, 27, Map.of())), Map.of(Group.STONE, 10.0));
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(1)) && plan.get(Group.WOOD).equals(List.of(0)), "The double chest was not enough for 40 cells: " + plan);
        helper.succeed();
    }

    /** Oleg stands 15 blocks from the player and the search radius is 16: the set is what lies around the player, all three chests. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void theSetIsAroundThePlayerWhereverHeStands(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setSearchRadius(16);
        Container[] chests = CompanionSortingTests.mixedThree(helper);
        var far = helper.absolutePos(new BlockPos(16, 1, 28));
        companion.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(companion.getLastTaskResult().startsWith("Разложил по 3 сундукам"), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
            for (Group group : Group.values()) helper.assertTrue(chestsHolding(chests, group) <= 1, group + " is in several chests");
        });
    }

    /** Something keeps putting a bone back into the stone chest (a hopper, the player): he does not circle until the common time-out but gives up with the reason. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void endlessCirclingStopsAtTheLimitOfTrips(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 9), b = chest(helper, 12);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 10)); a.setItem(1, new ItemStack(Items.BONE, 3)); b.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        Container[] chests = {a, b}; Map<Item, Integer> before = totals(companion, chests);
        helper.onEachTick(() -> {
            if (!companion.hasActiveWorkOrder() || count(a, Items.BONE) > 0 || count(b, Items.BONE) == 0) return;
            for (int slot = 0; slot < b.getContainerSize(); slot++) if (b.getItem(slot).is(Items.BONE)) { a.setItem(a.getItem(1).isEmpty() ? 1 : 2, b.getItem(slot)); b.setItem(slot, ItemStack.EMPTY); break; }
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still circling: " + companion.getLastTaskResult());
            helper.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().startsWith("Не получилось разложить всё"), "Not an honest answer: " + companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
        });
    }
}
