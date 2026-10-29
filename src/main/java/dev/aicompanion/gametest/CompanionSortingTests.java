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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
        var plan = CompanionSortGroups.plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 5.0)), chestInfo(1, 2, Map.of(Group.WOOD, 4.0, Group.STONE, 1.0)), chestInfo(2, 3, Map.of())), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(0)) && plan.get(Group.WOOD).equals(List.of(1)), "Wrong plan: " + plan);
        helper.assertTrue(plan.size() == 2, "Chests without a group got one: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerTakesTheNearerChestOnATie(GameTestHelper helper) {
        var plan = CompanionSortGroups.plan(List.of(chestInfo(0, 5, Map.of(Group.STONE, 3.0)), chestInfo(1, 2, Map.of(Group.STONE, 3.0))), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(1)), "The farther chest won a tie: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerWithFewerChestsThanGroupsGivesBigOnesTheirOwnAndTheRestTheLast(GameTestHelper helper) {
        var plan = CompanionSortGroups.plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 10.0, Group.FOOD, 1.0)), chestInfo(1, 2, Map.of(Group.WOOD, 6.0, Group.MOB, 1.0))), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(0)), "The biggest group has no chest of its own: " + plan);
        helper.assertTrue(plan.get(Group.WOOD).equals(List.of(1)) && plan.get(Group.FOOD).equals(List.of(1)) && plan.get(Group.MOB).equals(List.of(1)), "The rest not together in the last chest: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerSpillsAGroupThatDoesNotFitIntoTheNextChest(GameTestHelper helper) {
        var plan = CompanionSortGroups.plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 14.0, Group.WOOD, 13.0)), chestInfo(1, 2, Map.of(Group.STONE, 13.0, Group.WOOD, 5.0)),
                chestInfo(2, 3, Map.of(Group.STONE, 13.0))), Map.of());
        helper.assertTrue(plan.get(Group.STONE).equals(List.of(0, 1)) && plan.get(Group.WOOD).equals(List.of(2)), "Wrong spill: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plannerCountsWhatIsCarriedIntoTheSizeOfAGroup(GameTestHelper helper) {
        // Stone is the bigger group in the chests, but wood in the bag makes wood the bigger one: it picks first (chest 0, where it is), stone gets the other.
        var loaded = CompanionSortGroups.plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 3.0, Group.WOOD, 2.5)), chestInfo(1, 2, Map.of(Group.STONE, 2.5))), Map.of(Group.WOOD, 5.0));
        helper.assertTrue(loaded.get(Group.WOOD).equals(List.of(0)) && loaded.get(Group.STONE).equals(List.of(1)), "The load was not counted: " + loaded);
        var plain = CompanionSortGroups.plan(List.of(chestInfo(0, 1, Map.of(Group.STONE, 3.0, Group.WOOD, 2.5)), chestInfo(1, 2, Map.of(Group.STONE, 2.5))), Map.of());
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
        helper.succeed();
    }

    // ---- helpers ----

    private static Container[] mixedThree(GameTestHelper helper) {
        Container a = chest(helper, 9), b = chest(helper, 12), c = chest(helper, 15);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 64)); a.setItem(1, new ItemStack(Items.OAK_LOG, 20)); a.setItem(2, new ItemStack(Items.IRON_ORE, 8)); a.setItem(3, new ItemStack(Items.BREAD, 5));
        b.setItem(0, new ItemStack(Items.BONE, 6)); b.setItem(1, new ItemStack(Items.COBBLESTONE, 40)); b.setItem(2, new ItemStack(Items.DIRT, 10)); b.setItem(3, new ItemStack(Items.COOKED_BEEF, 4)); b.setItem(4, new ItemStack(Items.OAK_LOG, 12));
        c.setItem(0, new ItemStack(Items.IRON_ORE, 12)); c.setItem(1, new ItemStack(Items.OAK_LOG, 30)); c.setItem(2, new ItemStack(Items.BONE, 3)); c.setItem(3, new ItemStack(Items.APPLE, 2));
        return new Container[]{a, b, c};
    }
    private static void bagOf(CompanionEntity companion) { companion.setItem(0, new ItemStack(Items.STICK, 7)); companion.setItem(1, new ItemStack(Items.DIAMOND, 3)); }
    private static CompanionSortGroups.Chest chestInfo(int index, double distance, Map<Group, Double> cells) { return new CompanionSortGroups.Chest(index, distance, 27, cells); }

    private static EnumSet<Group> groups(Container chest) {
        EnumSet<Group> found = EnumSet.noneOf(Group.class);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) found.add(CompanionSortGroups.of(chest.getItem(slot)));
        return found;
    }
    private static int chestsHolding(Container[] chests, Group group) { int n = 0; for (Container chest : chests) if (groups(chest).contains(group)) n++; return n; }
    private static Map<Item, Integer> totals(Container... containers) {
        Map<Item, Integer> sum = new HashMap<>();
        for (Container container : containers) for (int slot = 0; slot < container.getContainerSize(); slot++) { ItemStack stack = container.getItem(slot); if (!stack.isEmpty()) sum.merge(stack.getItem(), stack.getCount(), Integer::sum); }
        return sum;
    }
    private static Map<Item, Integer> totals(CompanionEntity companion, Container[] chests) {
        Container[] all = new Container[chests.length + 1]; System.arraycopy(chests, 0, all, 0, chests.length); all[chests.length] = companion;
        return totals(all);
    }
    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); return companion;
    }
    private static Container chest(GameTestHelper helper, int x) { BlockPos pos = new BlockPos(x, 1, 13); helper.setBlock(pos, Blocks.CHEST); return (Container)helper.getBlockEntity(pos); }
    private static int count(Container bag, Item item) { int count = 0; for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount(); return count; }
}
