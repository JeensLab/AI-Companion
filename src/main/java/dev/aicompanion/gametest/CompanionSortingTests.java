package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sorting the chests next to the player by group (issue 32): the order, the plan, the groups and the phrases that start it. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingTests {
    // ---- the work, in the world ----

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void threeMixedChestsEndWithEachGroupInOneChest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still sorting: " + companion.getLastTaskResult());
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(totals(companion).equals(bagBefore), "His own bag changed: " + totals(companion));
            for (Group group : Group.values()) helper.assertTrue(chestsHolding(chests, group) <= 1, group + " is in " + chestsHolding(chests, group) + " chests");
            String said = companion.getLastTaskResult();
            helper.assertTrue(said.startsWith("Разложил по 3 сундукам: ") && said.matches("(?s).*камень и земля[^;]* — во? .*") && !said.contains("в ближнем, ") &&!said.matches("(?s).*\\d+, -?\\d+, -?\\d+.*") && !said.contains("minecraft:"), "The report is not what the player should hear: " + said);
        });
    }

    /** The wood already lay in one chest: it is never carried away from there, even for a moment. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void groupAlreadyInOneChestStaysThere(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 9), b = chest(helper, 12), c = chest(helper, 15);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 64)); a.setItem(1, new ItemStack(Items.COBBLESTONE, 64)); a.setItem(2, new ItemStack(Items.BONE, 3));
        for (int slot = 0; slot < 3; slot++) b.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        b.setItem(3, new ItemStack(Items.COBBLESTONE, 5));
        c.setItem(0, new ItemStack(Items.BREAD, 10));
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests);
        int[] lowest = {Integer.MAX_VALUE};
        helper.onEachTick(() -> lowest[0] = Math.min(lowest[0], count(b, Items.OAK_LOG)));
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(b, Items.OAK_LOG) == 192 && lowest[0] == 192, "The wood left its chest: lowest " + lowest[0]);
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
            for (Group group : Group.values()) helper.assertTrue(chestsHolding(chests, group) <= 1, group + " is in several chests");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void twoChestsFiveGroupsBiggestGroupAloneRestTogether(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = chest(helper, 12), b = chest(helper, 15);
        for (int slot = 0; slot < 4; slot++) a.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        a.setItem(4, new ItemStack(Items.BONE, 3)); a.setItem(5, new ItemStack(Items.BREAD, 5));
        b.setItem(0, new ItemStack(Items.OAK_LOG, 10)); b.setItem(1, new ItemStack(Items.IRON_ORE, 3)); b.setItem(2, new ItemStack(Items.APPLE, 2)); b.setItem(3, new ItemStack(Items.COBBLESTONE, 4));
        Container[] chests = {a, b}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
            Container stone = count(a, Items.COBBLESTONE) > 0 ? a : b, rest = stone == a ? b : a;
            helper.assertTrue(count(stone, Items.COBBLESTONE) == 260 && groups(stone).equals(EnumSet.of(Group.STONE)), "Stone chest not alone: " + groups(stone));
            helper.assertTrue(count(rest, Items.COBBLESTONE) == 0 && groups(rest).equals(EnumSet.of(Group.WOOD, Group.ORE, Group.FOOD, Group.MOB)), "The other four not together: " + groups(rest));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void moreStoneThanOneChestHoldsTakesTwoChestsUnmixed(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItem(0, new ItemStack(Items.STICK, 7));
        Container a = chest(helper, 9), b = chest(helper, 12), c = chest(helper, 15);
        for (int slot = 0; slot < 14; slot++) a.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 14; slot < 27; slot++) a.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 0; slot < 13; slot++) b.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 13; slot < 18; slot++) b.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 0; slot < 13; slot++) c.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        c.setItem(13, new ItemStack(Items.BREAD, 5));
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
            helper.assertTrue(chestsHolding(chests, Group.STONE) == 2, "Stone is in " + chestsHolding(chests, Group.STONE) + " chests");
            for (Container chest : chests) if (groups(chest).contains(Group.STONE)) helper.assertTrue(groups(chest).equals(EnumSet.of(Group.STONE)), "Stone mixed with " + groups(chest));
            helper.assertTrue(groups(c).equals(EnumSet.of(Group.WOOD, Group.FOOD)), "Wood and food not together in the last chest: " + groups(c));
        });
    }

    /** What the player sees: he walks between the chests, makes the gesture at them, carries things in his bag in between, and the bag is as before in the end. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void sortingIsVisibleWalkingOpeningAndCarrying(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); mixedThree(helper); bagOf(companion);
        Map<Item, Integer> bagBefore = totals(companion);
        boolean[] carried = {false}, gesture = {false}; double[] walked = {0}; Vec3[] last = {companion.position()};
        helper.onEachTick(() -> {
            if (!companion.hasActiveWorkOrder()) return;
            if (!totals(companion).equals(bagBefore)) carried[0] = true;
            if (companion.actionAnimation() != 0) gesture[0] = true;
            walked[0] += companion.position().distanceTo(last[0]); last[0] = companion.position();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(carried[0], "The bag never held anything carried between the chests");
            helper.assertTrue(gesture[0], "No gesture at the chests");
            helper.assertTrue(walked[0] > 3, "He did not walk between the chests: " + walked[0]);
            helper.assertTrue(totals(companion).equals(bagBefore), "The carried things are still in his bag: " + totals(companion));
        });
    }

    /** The player takes what he carries out of his bag mid-way (his inventory screen): nothing he no longer holds goes into a chest. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void thingsTakenFromHisBagMidWayAreNotDoubled(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), taken = new HashMap<>();
        helper.onEachTick(() -> {
            if (!taken.isEmpty() || !companion.hasActiveWorkOrder()) return;
            for (int slot = 0; slot < companion.getContainerSize(); slot++) {
                ItemStack stack = companion.getItem(slot);
                if (stack.isEmpty() || stack.is(Items.STICK) || stack.is(Items.DIAMOND)) continue;
                taken.merge(stack.getItem(), stack.getCount(), Integer::sum); companion.setItem(slot, ItemStack.EMPTY);
            }
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!taken.isEmpty(), "He never carried anything");
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still sorting: " + companion.getLastTaskResult());
            Map<Item, Integer> now = totals(companion, chests);
            taken.forEach((item, count) -> now.merge(item, count, Integer::sum));
            helper.assertTrue(now.equals(before), "Items doubled or lost: " + now + " vs " + before);
        });
    }

    /** One chest next to the player: the order inside it, put in place by opening it, nothing carried out. */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void oneChestIsPutInOrderInside(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); bagOf(companion); Container a = chest(helper, 12);
        for (int i = 0; i < 5; i++) a.setItem(i * 2, new ItemStack(Items.COBBLESTONE, 10 + i));
        a.setItem(1, new ItemStack(Items.OAK_LOG, 20)); a.setItem(3, new ItemStack(Items.IRON_ORE, 8)); a.setItem(5, new ItemStack(Items.OAK_LOG, 30)); a.setItem(7, new ItemStack(Items.COAL, 4));
        Map<Item, Integer> before = totals(companion, a), bagBefore = totals(companion);
        boolean[] gesture = {false};
        helper.onEachTick(() -> { if (companion.actionAnimation() != 0) gesture[0] = true; });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(gesture[0], "He never opened the chest");
            helper.assertTrue(totals(companion, a).equals(before) && totals(companion).equals(bagBefore), "Items moved to or from the bag");
            helper.assertTrue(count(a, Items.COBBLESTONE) == 60 && stacksOf(a, Items.COBBLESTONE) == 1 && stacksOf(a, Items.OAK_LOG) == 1, "Stacks not merged: " + describe(a));
            helper.assertTrue(inOrder(a), "Not in order inside: " + describe(a));
            helper.assertTrue(companion.getLastTaskResult().startsWith("Разложил по 1 сундуку"), companion.getLastTaskResult());
        });
    }

    /** The last step: a chest the trips never open (its group is already in it) is put in order too. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void chestsTheTripsDidNotOpenAreTidiedAtTheEnd(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); bagOf(companion);
        Container a = chest(helper, 9), b = chest(helper, 12), c = chest(helper, 15);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 20)); a.setItem(1, new ItemStack(Items.BONE, 3)); a.setItem(2, new ItemStack(Items.COBBLESTONE, 30));
        b.setItem(0, new ItemStack(Items.OAK_LOG, 10)); b.setItem(5, new ItemStack(Items.BIRCH_PLANKS, 5)); b.setItem(9, new ItemStack(Items.OAK_LOG, 20));
        c.setItem(2, new ItemStack(Items.BREAD, 3)); c.setItem(7, new ItemStack(Items.BREAD, 4));
        Container[] chests = {a, b, c}; Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before) && totals(companion).equals(bagBefore), "Items lost, doubled or left in the bag");
            for (Container chest : chests) helper.assertTrue(inOrder(chest) && stacksOf(chest, Items.OAK_LOG) <= 1 && stacksOf(chest, Items.BREAD) <= 1 && stacksOf(chest, Items.COBBLESTONE) <= 1, "Not in order inside: " + describe(chest));
        });
    }

    /** Protective: refused on main as well (no chest in the set). A chest farther than six blocks from the player is not in it and is left alone. */
    @GameTest(template = "tall_tree", timeoutTicks = 1000)
    public static void noChestInRangeIsRefusedAndTheFarOneUntouched(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container far = chest(helper, 27);
        far.setItem(0, new ItemStack(Items.COBBLESTONE, 10)); far.setItem(1, new ItemStack(Items.OAK_LOG, 3)); far.setItem(2, new ItemStack(Items.COBBLESTONE, 5));
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && !companion.didLastTaskSucceed(), "Not refused: " + companion.getLastTaskResult());
            helper.assertTrue(companion.getLastTaskResult().equals("Рядом с тобой сундуков не вижу."), companion.getLastTaskResult());
            helper.assertTrue(count(far, Items.COBBLESTONE) == 15 && stacksOf(far, Items.COBBLESTONE) == 2 && far.getItem(1).is(Items.OAK_LOG), "The far chest was touched: " + describe(far));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1000)
    public static void fullBagIsRefusedAtOnceAndChestsUntouched(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper);
        for (int slot = 0; slot < companion.getContainerSize() - 2; slot++) companion.setItem(slot, new ItemStack(Items.STICK, 1 + slot));
        Map<Item, Integer> before = totals(companion, chests), chestsBefore = totals(chests[0], chests[1], chests[2]);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && !companion.didLastTaskSucceed(), "Not refused: " + companion.getLastTaskResult());
            helper.assertTrue(companion.getLastTaskResult().equals("Сумка забита, сначала сложить мои вещи?"), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before) && totals(chests[0], chests[1], chests[2]).equals(chestsBefore) && chests[0].getItem(0).is(Items.COBBLESTONE) && chests[0].getItem(1).is(Items.OAK_LOG), "Something was touched");
        });
    }

    /** Protective: passes since ticket 01. A double chest is one chest of the set (54 cells, its right half is not taken again); sorted together with a single one. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void doubleChestIsOneChestOfFiftyFourCells(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); bagOf(companion);
        var facing = net.minecraft.core.Direction.NORTH;
        BlockPos left = new BlockPos(9, 1, 13);
        helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(new BlockPos(10, 1, 13), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        Container big = ChestBlock.getContainer((ChestBlock)Blocks.CHEST, helper.getBlockState(left), helper.getLevel(), helper.absolutePos(left), true);
        Container single = chest(helper, 14);
        helper.assertTrue(big != null && big.getContainerSize() == 54, "Not a double chest");
        for (int slot = 0; slot < 30; slot++) big.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        for (int slot = 30; slot < 40; slot++) big.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        big.setItem(40, new ItemStack(Items.BREAD, 5)); single.setItem(0, new ItemStack(Items.BREAD, 5)); single.setItem(1, new ItemStack(Items.COBBLESTONE, 7));
        Container[] chests = {big, single}; Map<Item, Integer> before = totals(companion, chests);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(companion.getLastTaskResult().startsWith("Разложил по 2 сундукам"), companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
            helper.assertTrue(groups(big).equals(EnumSet.of(Group.STONE)) && count(big, Items.COBBLESTONE) == 1927, "The big chest is not for stone alone: " + groups(big));
            helper.assertTrue(groups(single).equals(EnumSet.of(Group.WOOD, Group.FOOD)) && inOrder(big) && inOrder(single), "The single chest: " + describe(single));
        });
    }

    /** Protective: a trapped chest and a barrel are not in the set, whatever lies in them. */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void trappedChestAndBarrelAreLeftAlone(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); bagOf(companion);
        Container a = chest(helper, 9);
        helper.setBlock(new BlockPos(12, 1, 13), Blocks.TRAPPED_CHEST); helper.setBlock(new BlockPos(15, 1, 13), Blocks.BARREL);
        Container trap = (Container)helper.getBlockEntity(new BlockPos(12, 1, 13)), barrel = (Container)helper.getBlockEntity(new BlockPos(15, 1, 13));
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 10)); a.setItem(1, new ItemStack(Items.OAK_LOG, 5)); a.setItem(2, new ItemStack(Items.COBBLESTONE, 20));
        trap.setItem(0, new ItemStack(Items.BONE, 3)); trap.setItem(1, new ItemStack(Items.COBBLESTONE, 9)); barrel.setItem(0, new ItemStack(Items.BREAD, 4)); barrel.setItem(1, new ItemStack(Items.BONE, 2)); barrel.setItem(2, new ItemStack(Items.BREAD, 1));
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(stacksOf(a, Items.COBBLESTONE) == 1 && inOrder(a), "The plain chest is not in order: " + describe(a));
            helper.assertTrue(trap.getItem(0).is(Items.BONE) && trap.getItem(1).is(Items.COBBLESTONE) && trap.getItem(0).getCount() == 3 && trap.getItem(1).getCount() == 9, "The trapped chest was touched: " + describe(trap));
            helper.assertTrue(barrel.getItem(0).is(Items.BREAD) && barrel.getItem(1).is(Items.BONE) && barrel.getItem(2).is(Items.BREAD) && count(barrel, Items.BREAD) == 5, "The barrel was touched: " + describe(barrel));
        });
    }

    /** A chest under a solid block does not open (for the player either): it is not in the set, and the report does not call it gone. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void chestUnderABlockIsNotInTheSet(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper);
        helper.setBlock(new BlockPos(15, 2, 13), Blocks.STONE);
        Map<Item, Integer> blocked = totals(chests[2]);
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(companion.getLastTaskResult().startsWith("Разложил по 2 сундукам") && !companion.getLastTaskResult().contains("Пропустил"), companion.getLastTaskResult());
            helper.assertTrue(totals(chests[2]).equals(blocked), "The chest under the block was touched");
        });
    }

    // ---- pure functions ----

    @GameTest(template = "empty")
    public static void groupsOfTypicalItems(GameTestHelper helper) {
        Map<Group, List<Item>> expected = new EnumMap<>(Group.class);
        expected.put(Group.STONE, List.of(Items.COBBLESTONE, Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.GRASS_BLOCK));
        expected.put(Group.WOOD, List.of(Items.OAK_LOG, Items.BIRCH_PLANKS, Items.STICK, Items.STRIPPED_SPRUCE_LOG));
        expected.put(Group.ORE, List.of(Items.IRON_ORE, Items.RAW_IRON, Items.GOLD_INGOT, Items.COAL, Items.CHARCOAL, Items.DIAMOND, Items.REDSTONE, Items.EMERALD, Items.LAPIS_LAZULI, Items.QUARTZ, Items.COPPER_INGOT));
        expected.put(Group.FOOD, List.of(Items.BREAD, Items.COOKED_BEEF, Items.APPLE, Items.GOLDEN_CARROT));
        expected.put(Group.GEAR, List.of(Items.IRON_PICKAXE, Items.DIAMOND_SWORD, Items.IRON_CHESTPLATE, Items.BOW, Items.WOODEN_AXE, Items.SHIELD));
        expected.put(Group.MOB, List.of(Items.ROTTEN_FLESH, Items.BONE, Items.BONE_MEAL, Items.STRING, Items.GUNPOWDER, Items.SPIDER_EYE, Items.FEATHER, Items.LEATHER, Items.SLIME_BALL));
        expected.put(Group.OTHER, List.of(Items.TORCH, Items.GLASS, Items.BUCKET, Items.ARROW));
        expected.forEach((group, items) -> { for (Item item : items) helper.assertTrue(CompanionSortGroups.of(new ItemStack(item)) == group, item + " is " + CompanionSortGroups.of(new ItemStack(item)) + ", not " + group); });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerGivesTheGroupTheChestWhereItIsMost(GameTestHelper helper) {
        var plan = plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 5.0)), chestInfo(1, 2, Map.of(Group.WOOD, 4.0, Group.STONE, 1.0)), chestInfo(2, 3, Map.of())), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(0)) && plan.get(Group.WOOD).equals(List.of(1)), "Wrong plan: " + plan);
        helper.assertTrue(plan.size() == 2, "Chests without a group got one: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerTakesTheNearerChestOnATie(GameTestHelper helper) {
        var plan = plan(List.of(chestInfo(0, 5, Map.of(Group.STONE, 3.0)), chestInfo(1, 2, Map.of(Group.STONE, 3.0))), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(1)), "The farther chest won a tie: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerWithFewerChestsThanGroupsGivesBigOnesTheirOwnAndTheRestTheLast(GameTestHelper helper) {
        var plan = plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 10.0, Group.FOOD, 1.0)), chestInfo(1, 2, Map.of(Group.WOOD, 6.0, Group.MOB, 1.0))), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(0)), "The biggest group has no chest of its own: " + plan);
        helper.assertTrue(plan.get(Group.WOOD).equals(List.of(1)) && plan.get(Group.FOOD).equals(List.of(1)) && plan.get(Group.MOB).equals(List.of(1)), "The rest not together in the last chest: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerSpillsAGroupThatDoesNotFitIntoTheNextChest(GameTestHelper helper) {
        var plan = plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 14.0, Group.WOOD, 13.0)), chestInfo(1, 2, Map.of(Group.STONE, 13.0, Group.WOOD, 5.0)),
                chestInfo(2, 3, Map.of(Group.STONE, 13.0))), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(0, 1)) && plan.get(Group.WOOD).equals(List.of(2)), "Wrong spill: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerCountsWhatIsCarriedIntoTheSizeOfAGroup(GameTestHelper helper) {
        // Stone is the bigger group in the chests, but wood in the bag makes wood the bigger one: it picks first (chest 0, where it is), stone gets the other.
        var loaded = plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 3.0, Group.WOOD, 2.5)), chestInfo(1, 2, Map.of(Group.STONE, 2.5))), Map.of(Group.WOOD, 5.0));
        helper.assertTrue(loaded.get(Group.WOOD).equals(List.of(0)) && loaded.get(Group.STONE).equals(List.of(1)), "The load was not counted: " + loaded);
        var plain = plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 3.0, Group.WOOD, 2.5)), chestInfo(1, 2, Map.of(Group.STONE, 2.5))), Map.of());
        helper.assertTrue(plain.get(Group.STONE).equals(List.of(0)) && plain.get(Group.WOOD).equals(List.of(1)), "Plan without the load: " + plain);
        helper.succeed();
    }

    // ---- phrases and plans ----

    @GameTest(template = "empty")
    public static void phrasesStartTheSortingAndOthersDoNot(GameTestHelper helper) {
        for (String phrase : List.of("разложи сундуки", "Олег, разложи сундуки", "разложи по сундукам", "отсортируй сундуки", "отсортируй вещи в сундуках", "наведи порядок в сундуках", "Разложи сундуки!")) {
            PlannedAction action = CompanionWorkCommands.parse(phrase, "Олег");
            helper.assertTrue(action != null && action.type().equals("sort_chests") && action.count() == 1, "Not sort_chests: " + phrase);
            String route = CompanionLocalCommands.route(phrase, "Олег");
            helper.assertTrue(route != null && route.startsWith("companion work sort_chests 1"), "Not routed locally: " + phrase + " -> " + route);
        }
        for (String phrase : List.of("поставь сундук", "сложи вещи в сундук", "забери всё из сундука", "что в сундуке?", "разложи")) {
            PlannedAction action = CompanionWorkCommands.parse(phrase, "Олег");
            helper.assertTrue(action == null || !action.type().equals("sort_chests"), "Wrongly sort_chests: " + phrase);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void codecAcceptsSortChests(GameTestHelper helper) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"sort_chests\"}]"));
        helper.assertTrue(plan.size() == 1 && plan.get(0).type().equals("sort_chests") && plan.get(0).count() == 1 && plan.get(0).item().isEmpty(), "Not accepted as is: " + plan);
        helper.succeed();
    }

    /** Protective: these are rejected on main as well (an unknown action); they must stay so once the action exists. */
    @GameTest(template = "empty")
    public static void codecRejectsSortChestsWithHouseOrManyCounts(GameTestHelper helper) {
        for (String bad : List.of("[{\"action\":\"sort_chests\",\"location\":\"inside_house\"}]", "[{\"action\":\"sort_chests\",\"count\":2}]",
                "[{\"action\":\"follow\"},{\"action\":\"sort_chests\",\"count\":3}]")) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "Accepted: " + bad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sortingHasItsOwnConfirmationAndTaskName(GameTestHelper helper) {
        var sort = List.of(new PlannedAction("sort_chests", 0, 1));
        helper.assertTrue("Сейчас разложу по сундукам.".equals(CompanionDialogue.acknowledge(sort)), "Confirmation: " + CompanionDialogue.acknowledge(sort));
        helper.assertTrue("разложить по сундукам".equals(CompanionDialogue.describe(sort.get(0))), "Task name: " + CompanionDialogue.describe(sort.get(0)));
        String report = "Разложил по 3 сундукам: камень и земля — в ближнем; дерево — во втором; еда, остальное — в третьем.";
        helper.assertTrue(CompanionDialogue.summarize(sort, report).equals(report), "The report is cut or replaced: " + CompanionDialogue.summarize(sort, report));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void promptNamesSortChestsAndKeepsItApartFromStoreItems(GameTestHelper helper) {
        String prompt = OpenRouterClient.buildSystemPrompt(OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша", Map.of()), true, "лучший друг");
        helper.assertTrue(prompt.contains("sort_chests"), "sort_chests missing from the prompt");
        helper.assertTrue(prompt.matches("(?s).*sort_chests, не store_items.*"), "The rule «sort_chests, not store_items» is missing");
        helper.succeed();
    }

    /** The request of the stream (28.10) is an order the model must plan; the question before it is not. */
    @GameTest(template = "empty")
    public static void sortRequestsMakeTheModelPlan(GameTestHelper helper) {
        for (String order : List.of("Вот я поставил три сундука рядом. Отсортируй в каждом сундуке разные предметы.", "Разложи вещи по сундукам", "Наведи порядок в сундуках, пожалуйста"))
            helper.assertTrue(OpenRouterClient.requiresAction(order), "Not an order: " + order);
        helper.assertTrue(!OpenRouterClient.requiresAction("А ты бы сам смог сундуки сортировать?"), "A question taken for an order");
        for (String chat : List.of("Ты всё разложил по сундукам?", "Ты уже отсортировал сундуки?", "Наведи порядок в доме", "Спасибо, что рассортировал сундук"))
            helper.assertTrue(!OpenRouterClient.requiresAction(chat), "A talk taken for an order: " + chat);
        helper.succeed();
    }

    // ---- helpers ----

    static Container[] mixedThree(GameTestHelper helper) {
        Container a = chest(helper, 9), b = chest(helper, 12), c = chest(helper, 15);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 64)); a.setItem(1, new ItemStack(Items.OAK_LOG, 20)); a.setItem(2, new ItemStack(Items.IRON_ORE, 8)); a.setItem(3, new ItemStack(Items.BREAD, 5));
        b.setItem(0, new ItemStack(Items.BONE, 6)); b.setItem(1, new ItemStack(Items.COBBLESTONE, 40)); b.setItem(2, new ItemStack(Items.DIRT, 10)); b.setItem(3, new ItemStack(Items.COOKED_BEEF, 4)); b.setItem(4, new ItemStack(Items.OAK_LOG, 12));
        c.setItem(0, new ItemStack(Items.IRON_ORE, 12)); c.setItem(1, new ItemStack(Items.OAK_LOG, 30)); c.setItem(2, new ItemStack(Items.BONE, 3)); c.setItem(3, new ItemStack(Items.APPLE, 2));
        return new Container[]{a, b, c};
    }
    static void bagOf(CompanionEntity companion) { companion.setItem(0, new ItemStack(Items.STICK, 7)); companion.setItem(1, new ItemStack(Items.DIAMOND, 3)); }
    /** The plan for chests that hold the cells they say and a load of these cells: the volume of a group is both together. */
    static Map<Group, List<Integer>> plan(List<CompanionSortGroups.Chest> chests, Map<Group, Double> carried) {
        Map<Group, Double> volume = new EnumMap<>(Group.class);
        for (var chest : chests) chest.cells().forEach((group, cells) -> volume.merge(group, cells, Double::sum));
        carried.forEach((group, cells) -> volume.merge(group, cells, Double::sum));
        return CompanionSortGroups.plan(chests, volume);
    }
    private static CompanionSortGroups.Chest chestInfo(int index, double distance, Map<Group, Double> cells) { return new CompanionSortGroups.Chest(index, distance, 27, cells); }

    static EnumSet<Group> groups(Container chest) {
        EnumSet<Group> found = EnumSet.noneOf(Group.class);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) found.add(CompanionSortGroups.of(chest.getItem(slot)));
        return found;
    }
    static int chestsHolding(Container[] chests, Group group) { int n = 0; for (Container chest : chests) if (groups(chest).contains(group)) n++; return n; }
    static Map<Item, Integer> totals(Container... containers) {
        Map<Item, Integer> sum = new HashMap<>();
        for (Container container : containers) for (int slot = 0; slot < container.getContainerSize(); slot++) { ItemStack stack = container.getItem(slot); if (!stack.isEmpty()) sum.merge(stack.getItem(), stack.getCount(), Integer::sum); }
        return sum;
    }
    static Map<Item, Integer> totals(CompanionEntity companion, Container[] chests) {
        Container[] all = new Container[chests.length + 1]; System.arraycopy(chests, 0, all, 0, chests.length); all[chests.length] = companion;
        return totals(all);
    }
    private static int stacksOf(Container bag, Item item) { int n = 0; for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) n++; return n; }
    static String describe(Container chest) { StringBuilder sb = new StringBuilder(); for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) sb.append(slot).append(':').append(chest.getItem(slot)).append(' '); return sb.toString(); }
    /** Filled from the first cell without gaps, by group and then by item. */
    static boolean inOrder(Container chest) {
        ItemStack previous = ItemStack.EMPTY; boolean gap = false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) { gap = true; continue; }
            if (gap) return false;
            if (!previous.isEmpty()) {
                int g = CompanionSortGroups.of(previous).compareTo(CompanionSortGroups.of(stack));
                if (g > 0 || g == 0 && BuiltInRegistries.ITEM.getId(previous.getItem()) > BuiltInRegistries.ITEM.getId(stack.getItem())) return false;
            }
            previous = stack;
        }
        return true;
    }
    static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); return companion;
    }
    static Container chest(GameTestHelper helper, int x) { BlockPos pos = new BlockPos(x, 1, 13); helper.setBlock(pos, Blocks.CHEST); return (Container)helper.getBlockEntity(pos); }
    static int count(Container bag, Item item) { int count = 0; for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount(); return count; }
}
