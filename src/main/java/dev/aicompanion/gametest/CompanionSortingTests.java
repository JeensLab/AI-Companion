package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionItemClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.aicompanion.entity.CompanionItemClass.*;

/**
 * sort_items: the bag, or the mixed chests too, laid out by item class. Real chests, real bag, real planks for the new chests; every
 * scenario counts the items in the bag and in all chests before and after, so nothing may be lost or made.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingTests {
    @GameTest(template = "empty", batch = "sorting")
    public static void itemClassesFollowTheRules(GameTestHelper h) {
        Map<CompanionItemClass, List<Item>> expected = Map.of(
                WOOD, List.of(Items.OAK_LOG, Items.STRIPPED_OAK_LOG, Items.OAK_PLANKS, Items.STICK, Items.OAK_SAPLING, Items.OAK_FENCE, Items.OAK_DOOR, Items.OAK_SLAB,
                        Items.OAK_STAIRS, Items.OAK_LEAVES, Items.CRAFTING_TABLE, Items.CHEST, Items.BARREL, Items.LADDER, Items.BAMBOO_BLOCK),
                STONE, List.of(Items.COBBLESTONE, Items.STONE, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRASS_BLOCK, Items.COARSE_DIRT, Items.GRAVEL,
                        Items.SAND, Items.RED_SAND, Items.GRANITE, Items.ANDESITE, Items.CLAY_BALL, Items.TERRACOTTA, Items.STONE_BRICKS, Items.COBBLESTONE_STAIRS,
                        Items.STONE_SLAB, Items.COBBLESTONE_WALL, Items.OBSIDIAN, Items.NETHERRACK, Items.BLACKSTONE, Items.SANDSTONE, Items.BRICK),
                ORES, List.of(Items.RAW_IRON, Items.IRON_INGOT, Items.GOLD_NUGGET, Items.COAL, Items.CHARCOAL, Items.DIAMOND, Items.EMERALD, Items.REDSTONE,
                        Items.LAPIS_LAZULI, Items.COPPER_INGOT, Items.IRON_ORE, Items.DEEPSLATE_DIAMOND_ORE, Items.NETHERITE_SCRAP, Items.QUARTZ, Items.AMETHYST_SHARD,
                        Items.IRON_BLOCK, Items.COAL_BLOCK, Items.RAW_COPPER),
                FOOD, List.of(Items.BREAD, Items.COOKED_BEEF, Items.APPLE, Items.GOLDEN_APPLE, Items.CARROT, Items.BAKED_POTATO, Items.MELON_SLICE, Items.COOKED_CHICKEN, Items.BEEF),
                TOOLS, List.of(Items.STONE_PICKAXE, Items.IRON_SWORD, Items.WOODEN_AXE, Items.LEATHER_HELMET, Items.IRON_CHESTPLATE, Items.BOW, Items.SHIELD,
                        Items.FISHING_ROD, Items.SHEARS, Items.ARROW, Items.TRIDENT, Items.FLINT_AND_STEEL),
                DROPS, List.of(Items.STRING, Items.BONE, Items.LEATHER, Items.FEATHER, Items.ROTTEN_FLESH, Items.GUNPOWDER, Items.SPIDER_EYE, Items.WHITE_WOOL,
                        Items.SLIME_BALL, Items.ENDER_PEARL, Items.INK_SAC, Items.BLAZE_ROD, Items.PHANTOM_MEMBRANE, Items.EGG),
                OTHER, List.of(Items.TORCH, Items.BOOK, Items.BUCKET, Items.PAPER, Items.WHEAT, Items.COMPASS, Items.FURNACE, Items.GLASS));
        for (var entry : expected.entrySet()) for (Item item : entry.getValue())
            h.assertTrue(CompanionItemClass.of(new ItemStack(item)) == entry.getKey(), item + " is " + CompanionItemClass.of(new ItemStack(item)) + ", not " + entry.getKey());
        h.assertTrue(expected.keySet().equals(EnumSet.allOf(CompanionItemClass.class)), "A class without a check");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "sorting")
    public static void commandsPlansAndRequestsKnowSortItems(GameTestHelper h) {
        for (String phrase : new String[]{"Олег, разложи вещи по сундукам", "рассортируй сумку", "разложи всё по сундукам", "разложи сумку по сундукам", "отсортируй мои вещи", "разложи вещи в сундуки"}) {
            PlannedAction action = CompanionWorkCommands.parse(phrase, "Олег");
            h.assertTrue(action != null && action.type().equals("sort_items") && action.item().equals("bag"), phrase + " -> " + action);
        }
        for (String phrase : new String[]{"Олег, наведи порядок в сундуках", "рассортируй сундуки", "отсортируй все сундуки", "приведи сундуки в порядок", "разложи вещи в сундуках по порядку", "наведи порядок в моих сундуках!"}) {
            PlannedAction action = CompanionWorkCommands.parse(phrase, "Олег");
            h.assertTrue(action != null && action.type().equals("sort_items") && action.item().equals("chests"), phrase + " -> " + action);
        }
        for (String phrase : new String[]{"не раскладывай вещи по сундукам", "разложи", "разложи вещи по сундукам и иди ко мне", "разложи по полочкам"})
            h.assertTrue(CompanionWorkCommands.parse(phrase, "Олег") == null, "Not a direct command: " + phrase);
        String command = CompanionLocalCommands.route("разложи вещи по сундукам", "Олег");
        h.assertTrue("companion work sort_items 1 bag".equals(command) && new PlannedAction("sort_items", 0, 1, "bag").equals(CompanionLocalCommands.action(command)), "Local command: " + command);
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"sort_items\"},{\"action\":\"sort_items\",\"item\":\"chests\",\"count\":5,\"location\":\"inside_house\"}]"));
        h.assertTrue(plan.get(0).item().equals("bag") && plan.get(0).count() == 1 && plan.get(1).item().equals("chests") && plan.get(1).count() == 1
                && plan.get(1).location().equals("inside_house"), "Codec: " + plan);
        for (String bad : new String[]{"[{\"action\":\"sort_items\",\"item\":\"minecraft:stone\"}]", "[{\"action\":\"sort_items\",\"item\":\"everything\"}]"}) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (IllegalStateException expectedFailure) { rejected = true; }
            h.assertTrue(rejected, "Accepted " + bad);
        }
        // The words of the player decide whether the chests are emptied, not a guess of the model.
        CompanionReply guessed = new CompanionReply("", List.of(new PlannedAction("sort_items", 0, 1, "chests")));
        h.assertTrue(CompanionPlanRequirements.apply("разложи вещи по сундукам", guessed).actions().getFirst().item().equals("bag"), "Chests tidied on a bag request");
        CompanionReply bag = new CompanionReply("", List.of(new PlannedAction("sort_items", 0, 1, "bag")));
        h.assertTrue(CompanionPlanRequirements.apply("Олег, наведи порядок в сундуках", bag).actions().getFirst().item().equals("chests"), "Tidy request lost");
        h.assertTrue(CompanionPlanRequirements.apply("не наводи порядок в сундуках", bag).actions().getFirst().item().equals("bag"), "A refusal changed the plan");
        h.assertTrue(CompanionDialogue.describe(new PlannedAction("sort_items", 0, 1, "chests")).contains("порядок в сундуках")
                && CompanionDialogue.acknowledge(plan.subList(0, 1)).contains("разложу"), "Spoken lines");
        h.succeed();
    }

    /** A chest of logs, an empty chest, a bag of stone, ore and logs: stone to the empty chest, ore to a new chest made of his planks. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "sorting")
    public static void bagIsLaidOutByClassIntoHoldingEmptyAndNewChests(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        BlockPos logsAt = new BlockPos(22, 1, 16), emptyAt = new BlockPos(22, 1, 20);
        chest(h, logsAt).setItem(0, new ItemStack(Items.OAK_LOG, 20));
        chest(h, emptyAt);
        oleg.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        oleg.setItem(1, new ItemStack(Items.DIRT, 12));
        oleg.setItem(2, new ItemStack(Items.IRON_INGOT, 6));
        oleg.setItem(3, new ItemStack(Items.COAL, 5));
        oleg.setItem(4, new ItemStack(Items.OAK_LOG, 6));
        oleg.setItem(5, new ItemStack(Items.OAK_PLANKS, 16));
        oleg.setItem(6, new ItemStack(Items.STONE_PICKAXE));
        oleg.setItem(7, new ItemStack(Items.BREAD, 5));
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        Map<Item, Integer> before = snapshot(h, oleg);
        var started = CompanionEvents.executeAction(owner(h, oleg), "sort_items", "bag", 1);
        h.assertTrue(started.success() && started.status() == dev.aicompanion.entity.CompanionActionResult.Status.ACCEPTED, "Order not accepted: " + started.message());
        h.succeedWhen(() -> {
            done(h, oleg);
            h.assertTrue(oleg.didLastTaskSucceed(), oleg.getLastTaskResult());
            List<BlockPos> chests = chests(h);
            h.assertTrue(chests.size() == 3 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Expected one new chest and one bench, chests: " + chests);
            h.assertTrue(count(box(h, logsAt), Items.OAK_LOG) == 26 && count(box(h, logsAt), Items.OAK_PLANKS) == 4 && onlyClass(box(h, logsAt), WOOD), "Wood: " + describe(box(h, logsAt)));
            h.assertTrue(count(box(h, emptyAt), Items.COBBLESTONE) == 40 && count(box(h, emptyAt), Items.DIRT) == 4 && onlyClass(box(h, emptyAt), STONE), "Stone: " + describe(box(h, emptyAt)));
            BlockPos made = chests.stream().filter(pos -> !pos.equals(logsAt) && !pos.equals(emptyAt)).findFirst().orElseThrow();
            h.assertTrue(count(box(h, made), Items.IRON_INGOT) == 6 && count(box(h, made), Items.COAL) == 5 && onlyClass(box(h, made), ORES), "Ore: " + describe(box(h, made)));
            h.assertTrue(made.distSqr(emptyAt) <= 25 || made.distSqr(logsAt) <= 25, "The new chest is not beside the others: " + made);
            h.assertTrue(count(oleg, Items.STONE_PICKAXE) == 1 && count(oleg, Items.BREAD) == 5 && count(oleg, Items.DIRT) == 8 && oleg.getMainHandItem().is(Items.WOODEN_SWORD),
                    "His tools, food and supports were put away: " + oleg.getStoredItemsSummary());
            h.assertTrue(count(oleg, Items.COBBLESTONE) + count(oleg, Items.IRON_INGOT) + count(oleg, Items.COAL) + count(oleg, Items.OAK_LOG) + count(oleg, Items.OAK_PLANKS) == 0, "Resources stayed in the bag");
            Map<Item, Integer> after = snapshot(h, oleg);
            for (Item item : before.keySet()) if (item != Items.OAK_PLANKS) h.assertTrue(before.get(item).equals(after.getOrDefault(item, 0)), item + " " + before.get(item) + " -> " + after.get(item));
            h.assertTrue(after.get(Items.OAK_PLANKS) == 16 - 12, "A chest is 8 planks and the bench 4: " + after.get(Items.OAK_PLANKS));
            String report = oleg.getLastTaskResult();
            h.assertTrue(report.contains("Разложил по сундукам") && report.contains("поставил новых сундуков: 1") && report.contains(h.absolutePos(emptyAt).toShortString())
                    && report.contains(h.absolutePos(logsAt).toShortString()) && report.contains(h.absolutePos(made).toShortString()) && report.contains("У себя оставил"), report);
        });
    }

    /** Two mixed chests: everything comes out, goes back by class, new chests are made of the wood that came out. Nothing is lost. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "sorting")
    public static void tidyingMixedChestsSortsByClassWithoutLossOrDuplication(GameTestHelper h) {
        CompanionEntity oleg = mixedChests(h);
        Map<Item, Integer> before = snapshot(h, oleg);
        var started = CompanionEvents.executeAction(owner(h, oleg), "sort_items", "chests", 1);
        h.assertTrue(started.success(), started.message());
        h.succeedWhen(() -> {
            done(h, oleg);
            h.assertTrue(oleg.didLastTaskSucceed(), oleg.getLastTaskResult());
            checkTidied(h, oleg, before);
        });
    }

    /** A bag too full for a chest at once: it takes in portions, puts away, takes the rest. A chest that is already sorted is left alone. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "sorting")
    public static void fullBagWorksInRoundsAndLeavesSortedChestsAlone(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        BlockPos mixedAt = new BlockPos(22, 1, 16), oreAt = new BlockPos(22, 1, 20), emptyAt = new BlockPos(26, 1, 18);
        Container mixed = chest(h, mixedAt), ores = chest(h, oreAt);
        chest(h, emptyAt);
        for (int slot = 0; slot < 7; slot++) { mixed.setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); mixed.setItem(slot + 7, new ItemStack(Items.OAK_LOG, 64)); }
        for (int slot = 0; slot < 4; slot++) { ores.setItem(slot, new ItemStack(Items.IRON_INGOT, 64)); ores.setItem(slot + 4, new ItemStack(Items.COAL, 64)); }
        for (int slot = 0; slot < 16; slot++) oleg.setItem(slot, new ItemStack(Items.STONE_PICKAXE));
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "chests", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            h.assertTrue(oleg.didLastTaskSucceed(), oleg.getLastTaskResult());
            h.assertTrue(chests(h).size() == 3 && blocks(h, Blocks.CRAFTING_TABLE) == 0, "No chest should have been made: " + chests(h));
            h.assertTrue(count(box(h, mixedAt), Items.OAK_LOG) == 7 * 64 && onlyClass(box(h, mixedAt), WOOD), "Wood: " + describe(box(h, mixedAt)));
            h.assertTrue(count(box(h, emptyAt), Items.COBBLESTONE) == 7 * 64 && onlyClass(box(h, emptyAt), STONE), "Stone: " + describe(box(h, emptyAt)));
            h.assertTrue(count(box(h, oreAt), Items.IRON_INGOT) == 4 * 64 && count(box(h, oreAt), Items.COAL) == 4 * 64, "The sorted chest was touched: " + describe(box(h, oreAt)));
            h.assertTrue(count(oleg, Items.STONE_PICKAXE) == 16, "His tools: " + oleg.getStoredItemsSummary());
            h.assertTrue(snapshot(h, oleg).equals(before), "Items lost or made: " + snapshot(h, oleg) + " vs " + before);
            String report = oleg.getLastTaskResult();
            h.assertTrue(report.contains("Вынул из сундуков " + 14 * 64 + " предметов"), "Only the mixed chest comes out: " + report);
        });
    }

    /** Already sorted chests stay as they are; the stranger in a mixed one goes where its class lives. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "sorting")
    public static void tidyingMovesOnlyWhatIsInTheWrongChest(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        BlockPos logsAt = new BlockPos(22, 1, 16), mixedAt = new BlockPos(22, 1, 20);
        chest(h, logsAt).setItem(0, new ItemStack(Items.OAK_LOG, 10));
        Container mixed = chest(h, mixedAt);
        mixed.setItem(0, new ItemStack(Items.OAK_LOG, 5));
        mixed.setItem(1, new ItemStack(Items.COBBLESTONE, 7));
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "chests", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            h.assertTrue(oleg.didLastTaskSucceed(), oleg.getLastTaskResult());
            h.assertTrue(chests(h).size() == 2 && count(box(h, logsAt), Items.OAK_LOG) == 15 && onlyClass(box(h, logsAt), WOOD), "Wood: " + describe(box(h, logsAt)));
            h.assertTrue(count(box(h, mixedAt), Items.COBBLESTONE) == 7 && onlyClass(box(h, mixedAt), STONE), "Stone: " + describe(box(h, mixedAt)));
            h.assertTrue(snapshot(h, oleg).equals(before) && oleg.isEmpty(), "Items lost or made, bag: " + oleg.getStoredItemsSummary());
            h.assertTrue(oleg.getLastTaskResult().contains("Вынул из сундуков 12 предметов"), oleg.getLastTaskResult());
        });
    }

    /** A double chest is one chest: its logs are counted once and it takes the bag's logs. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, batch = "sorting")
    public static void doubleChestIsOneDestination(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        BlockPos left = new BlockPos(23, 1, 16), right = left.east();
        h.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        Container pair = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, h.getBlockState(left), h.getLevel(), h.absolutePos(left), false);
        h.assertTrue(pair != null && pair.getContainerSize() == 54, "Fixture double chest was not joined");
        pair.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        pair.setItem(1, new ItemStack(Items.OAK_LOG, 36));
        oleg.setItem(0, new ItemStack(Items.OAK_LOG, 30));
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "bag", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            h.assertTrue(oleg.didLastTaskSucceed(), oleg.getLastTaskResult());
            h.assertTrue(count(pair, Items.OAK_LOG) == 130 && chests(h).size() == 2 && oleg.isEmpty(), "Logs " + count(pair, Items.OAK_LOG) + " in " + chests(h));
            h.assertTrue(snapshot(h, oleg).equals(Map.of(Items.OAK_LOG, before.get(Items.OAK_LOG))), "Items lost or made: " + snapshot(h, oleg));
            h.assertTrue(oleg.getLastTaskResult().contains("Разложил по сундукам 30 предметов"), oleg.getLastTaskResult());
        });
    }

    /** No chest anywhere and nothing to make one of: the stone stays with him and he says why. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, batch = "sorting")
    public static void noChestsAndNoMaterialsGivesAnHonestReport(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        oleg.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        oleg.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
        oleg.setItem(2, new ItemStack(Items.BREAD, 4));
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "bag", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            String report = oleg.getLastTaskResult();
            h.assertTrue(!oleg.didLastTaskSucceed() && report.contains("Ничего не разложил") && report.contains("камень и земля — 40") && report.contains("руды и слитки — 3"), report);
            h.assertTrue(chests(h).isEmpty() && blocks(h, Blocks.CRAFTING_TABLE) == 0 && snapshot(h, oleg).equals(before) && count(oleg, Items.BREAD) == 4, "The world or the bag changed: " + oleg.getStoredItemsSummary());
        });
    }

    /** Tidy with a single chest and no wood: the ore cannot get a chest of its own, so it goes back into the only one, and he says so. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, batch = "sorting")
    public static void tidyingWithoutAChestForEveryClassPutsThingsBackMixed(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        BlockPos at = new BlockPos(22, 1, 16);
        Container only = chest(h, at);
        only.setItem(0, new ItemStack(Items.COBBLESTONE, 30));
        only.setItem(1, new ItemStack(Items.COAL, 10));
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "chests", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            String report = oleg.getLastTaskResult();
            h.assertTrue(!oleg.didLastTaskSucceed() && report.contains("Без разбора вернул 10 предметов в сундук на " + h.absolutePos(at).toShortString()), report);
            h.assertTrue(count(only, Items.COBBLESTONE) == 30 && count(only, Items.COAL) == 10 && oleg.isEmpty() && chests(h).size() == 1 && snapshot(h, oleg).equals(before),
                    "Not everything is back in the chest: " + describe(only) + " bag " + oleg.getStoredItemsSummary());
        });
    }

    /**
     * «Стоп» in the middle of a tidy: what he took out of the chests is in his bag, nothing on the ground, nothing lost, and the stop says
     * so. What he holds is his again (food and tools stay with him), so the next order lays out the resources and leaves the bread.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600, batch = "sorting")
    public static void stoppingMidwayLosesNothingAndTheNextOrderFinishes(GameTestHelper h) {
        CompanionEntity oleg = mixedChests(h);
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "chests", 1).success(), "Order not accepted");
        long work = oleg.activeWorkId();
        boolean[] stopped = {false};
        boolean[] restarted = {false};
        h.onEachTick(() -> {
            if (!stopped[0] && box(h, new BlockPos(22, 1, 16)).isEmpty() && box(h, new BlockPos(22, 1, 20)).isEmpty()) {
                stopped[0] = true;
                oleg.stopGathering();
                h.assertTrue(!oleg.isWorking(), "Still working after the stop");
                h.assertTrue(snapshot(h, oleg).equals(before), "Lost or made on the stop: " + snapshot(h, oleg));
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, arena(h)).isEmpty(), "Something fell on the ground");
                var result = oleg.workResult(work);
                h.assertTrue(result != null && !result.success() && result.message().contains("Вынул из сундуков 99 предметов") && result.message().contains("Работа прервана"), "Stop report: " + result);
                CompoundTag saved = new CompoundTag();
                oleg.addAdditionalSaveData(saved);
                CompanionEntity restored = AICompanionMod.COMPANION.get().create(h.getLevel());
                restored.readAdditionalSaveData(saved);
                h.assertTrue(snapshot(h, restored).equals(before), "The bag did not survive a reload: " + snapshot(h, restored));
            } else if (stopped[0] && !restarted[0] && !oleg.isWorking()) {
                restarted[0] = true;
                h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "bag", 1).success(), "Second order not accepted");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && restarted[0], "Not stopped and restarted yet");
            done(h, oleg);
            h.assertTrue(oleg.didLastTaskSucceed(), oleg.getLastTaskResult());
            List<BlockPos> chests = chests(h);
            h.assertTrue(chests.size() == 4 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Two chests and two new ones expected: " + chests);
            Set<CompanionItemClass> kinds = EnumSet.noneOf(CompanionItemClass.class);
            for (BlockPos pos : chests) {
                h.assertTrue(!box(h, pos).isEmpty() && classCount(box(h, pos)) == 1, "Not one class in the chest at " + pos + ": " + describe(box(h, pos)));
                kinds.add(CompanionItemClass.of(firstStack(box(h, pos))));
            }
            h.assertTrue(kinds.equals(EnumSet.of(WOOD, STONE, ORES, DROPS)), "Classes in chests: " + kinds);
            h.assertTrue(count(oleg, Items.STONE_PICKAXE) == 1 && count(oleg, Items.BREAD) == 7 && count(oleg, Items.OAK_LOG) + count(oleg, Items.OAK_PLANKS) == 0, "His bag: " + oleg.getStoredItemsSummary());
            Map<Item, Integer> after = snapshot(h, oleg);
            for (Item item : before.keySet()) if (item != Items.OAK_LOG && item != Items.OAK_PLANKS) h.assertTrue(before.get(item).equals(after.getOrDefault(item, 0)), item + " " + before.get(item) + " -> " + after.get(item));
            int woodUsed = 4 * before.get(Items.OAK_LOG) + before.get(Items.OAK_PLANKS) - 4 * after.getOrDefault(Items.OAK_LOG, 0) - after.getOrDefault(Items.OAK_PLANKS, 0);
            h.assertTrue(woodUsed == 2 * 8 + 4, "Wood used: " + woodUsed + " planks, two chests and a bench are 20");
        });
    }

    /** The logs left after the chest and the bench are made stay with him as a spare: no second chest for one log. The default item is the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, batch = "sorting")
    public static void leftoverWoodAfterTheNewChestIsKeptNotMadeIntoAnotherChest(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        oleg.setItem(0, new ItemStack(Items.COBBLESTONE, 30));
        oleg.setItem(1, new ItemStack(Items.OAK_LOG, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            String report = oleg.getLastTaskResult();
            h.assertTrue(oleg.didLastTaskSucceed(), report);
            List<BlockPos> chests = chests(h);
            h.assertTrue(chests.size() == 1 && count(box(h, chests.getFirst()), Items.COBBLESTONE) == 30 && onlyClass(box(h, chests.getFirst()), STONE), "Chests: " + chests);
            h.assertTrue(4 * count(oleg, Items.OAK_LOG) + count(oleg, Items.OAK_PLANKS) == 16 - 12 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Wood left: " + oleg.getStoredItemsSummary());
            h.assertTrue(report.contains("поставил новых сундуков: 1") && report.contains("Остались у меня: дерево") && report.contains("как запас"), report);
        });
    }

    /** Like store_items: no wood and no chest, so he fells trees for it (an axe, a bench and a chest are five logs), makes both, lays the gravel in. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "sorting")
    public static void bagOrderFellsTreesForTheChestItNeeds(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(18, y, 18), Blocks.OAK_LOG);
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(23, y, 18), Blocks.OAK_LOG);
        // Gravel, not cobblestone: wood gathering makes a stone axe of cobblestone in the bag.
        oleg.setItem(0, new ItemStack(Items.GRAVEL, 30));
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "bag", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            String report = oleg.getLastTaskResult();
            h.assertTrue(oleg.didLastTaskSucceed(), report);
            h.assertTrue(h.getBlockState(new BlockPos(18, 1, 18)).isAir() && blocks(h, Blocks.CRAFTING_TABLE) == 1, "The trees were not felled for the chest: " + report);
            h.assertTrue(totalInChests(h, Items.GRAVEL) == 30 && count(oleg, Items.GRAVEL) == 0, "Gravel: " + totalInChests(h, Items.GRAVEL) + ", bag " + oleg.getStoredItemsSummary());
            for (BlockPos pos : chests(h)) h.assertTrue(classCount(box(h, pos)) == 1, "Not one class in the chest at " + pos + ": " + describe(box(h, pos)));
            h.assertTrue(report.contains("поставил новых сундуков: " + chests(h).size()) && report.contains("Камень и земля: 30"), report);
        });
    }

    /** A bag with no room cannot take a chest out: he says so and leaves the chest as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "sorting")
    public static void tidyWithAFullBagSaysSoAndChangesNothing(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        Container mixed = chest(h, new BlockPos(22, 1, 16));
        mixed.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        mixed.setItem(1, new ItemStack(Items.COAL, 5));
        for (int slot = 0; slot < 24; slot++) oleg.setItem(slot, new ItemStack(Items.STONE_PICKAXE));
        Map<Item, Integer> before = snapshot(h, oleg);
        h.assertTrue(CompanionEvents.executeAction(owner(h, oleg), "sort_items", "chests", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, oleg);
            String report = oleg.getLastTaskResult();
            h.assertTrue(!oleg.didLastTaskSucceed() && report.contains("Не разобрал сундуков: 1 (в сумке нет места"), report);
            h.assertTrue(snapshot(h, oleg).equals(before) && count(mixed, Items.COBBLESTONE) == 10 && count(mixed, Items.COAL) == 5 && chests(h).size() == 1, "Something changed: " + describe(mixed));
        });
    }

    /** The whole path of a model plan: codec, plan runner, server port, the result of the order's own work id (not the acknowledgement). */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "sorting")
    public static void modelPlanWaitsForTheRealSortingResult(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        BlockPos logsAt = new BlockPos(22, 1, 16), emptyAt = new BlockPos(22, 1, 20);
        chest(h, logsAt).setItem(0, new ItemStack(Items.OAK_LOG, 8));
        chest(h, emptyAt);
        oleg.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        oleg.setItem(1, new ItemStack(Items.OAK_LOG, 4));
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"sort_items\"}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), oleg.getOwnerId(), "gametest-sort-plan", () -> false));
        h.assertTrue(!future.isDone(), "The plan finished before any work");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + oleg.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(outcome.skipped().isEmpty() && outcome.report().contains("Разложил по сундукам 16 предметов"), outcome.report());
            String spoken = CompanionDialogue.summarize(plan, outcome.report());
            h.assertTrue(!spoken.isBlank() && spoken.contains("Разложил") && !spoken.matches("(?s).*-?\\d+, -?\\d+, -?\\d+.*"), "Spoken: " + spoken);
            h.assertTrue(count(box(h, logsAt), Items.OAK_LOG) == 12 && count(box(h, emptyAt), Items.COBBLESTONE) == 12 && oleg.isEmpty(), "Wrong layout: " + describe(box(h, logsAt)) + describe(box(h, emptyAt)));
        });
    }

    // ----- Fixtures -------------------------------------------------------------------------------------------------------------------

    /** Two mixed chests, his tools and a little food; wood only in the chests, so new chests are made of what comes out of them. */
    private static CompanionEntity mixedChests(GameTestHelper h) {
        CompanionEntity oleg = setup(h);
        Container a = chest(h, new BlockPos(22, 1, 16)), b = chest(h, new BlockPos(22, 1, 20));
        a.setItem(0, new ItemStack(Items.OAK_LOG, 16));
        a.setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        a.setItem(2, new ItemStack(Items.COAL, 8));
        a.setItem(3, new ItemStack(Items.BREAD, 4));
        b.setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
        b.setItem(1, new ItemStack(Items.IRON_INGOT, 6));
        b.setItem(2, new ItemStack(Items.BONE, 7));
        b.setItem(3, new ItemStack(Items.COBBLESTONE, 10));
        oleg.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        oleg.setItem(1, new ItemStack(Items.BREAD, 3));
        return oleg;
    }

    /** Every chest holds one class, each class its own chest; his own tools and bread stayed; the wood balance matches the chests made. */
    private static void checkTidied(GameTestHelper h, CompanionEntity oleg, Map<Item, Integer> before) {
        List<BlockPos> chests = chests(h);
        h.assertTrue(chests.size() == 5 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Two chests and three new ones expected: " + chests);
        Set<CompanionItemClass> kinds = EnumSet.noneOf(CompanionItemClass.class);
        for (BlockPos pos : chests) {
            h.assertTrue(!box(h, pos).isEmpty() && classCount(box(h, pos)) == 1, "Not one class in the chest at " + pos + ": " + describe(box(h, pos)));
            kinds.add(CompanionItemClass.of(firstStack(box(h, pos))));
        }
        h.assertTrue(kinds.equals(EnumSet.of(WOOD, STONE, ORES, FOOD, DROPS)), "Classes in chests: " + kinds);
        h.assertTrue(totalInChests(h, Items.COBBLESTONE) == 42 && totalInChests(h, Items.COAL) == 8 && totalInChests(h, Items.IRON_INGOT) == 6
                && totalInChests(h, Items.BONE) == 7 && totalInChests(h, Items.BREAD) == 4, "Counts in chests");
        h.assertTrue(count(oleg, Items.STONE_PICKAXE) == 1 && count(oleg, Items.BREAD) == 3 && count(oleg, Items.OAK_LOG) + count(oleg, Items.OAK_PLANKS) == 0, "His bag: " + oleg.getStoredItemsSummary());
        Map<Item, Integer> after = snapshot(h, oleg);
        for (Item item : before.keySet()) if (item != Items.OAK_LOG && item != Items.OAK_PLANKS) h.assertTrue(before.get(item).equals(after.getOrDefault(item, 0)), item + " " + before.get(item) + " -> " + after.get(item));
        int woodBefore = 4 * before.get(Items.OAK_LOG) + before.get(Items.OAK_PLANKS), woodAfter = 4 * after.getOrDefault(Items.OAK_LOG, 0) + after.getOrDefault(Items.OAK_PLANKS, 0);
        h.assertTrue(woodBefore - woodAfter == 3 * 8 + 4, "Wood used: " + (woodBefore - woodAfter) + " planks, three chests and a bench are 28");
        String report = oleg.getLastTaskResult();
        h.assertTrue(report.contains("Вынул из сундуков 99 предметов") && report.contains("поставил новых сундуков: 3"), report);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, arena(h)).isEmpty(), "Something lies on the ground");
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(oleg.getX() - 5, oleg.getY(), oleg.getZ() - 5);
        oleg.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        oleg.setSearchRadius(16);
        return oleg;
    }

    /** The whole 32 by 32 arena, to see that nothing lies on the ground. */
    private static AABB arena(GameTestHelper h) {
        return new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(32, 10, 32))));
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity oleg) { return h.getLevel().getServer().getPlayerList().getPlayer(oleg.getOwnerId()); }

    private static Container chest(GameTestHelper h, BlockPos pos) { h.setBlock(pos, Blocks.CHEST); return box(h, pos); }
    private static Container box(GameTestHelper h, BlockPos pos) { return (Container) h.getBlockEntity(pos); }

    private static List<BlockPos> chests(GameTestHelper h) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 3, 31))) if (h.getBlockState(pos).is(Blocks.CHEST)) found.add(pos.immutable());
        return found;
    }

    private static int blocks(GameTestHelper h, Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 3, 31))) if (h.getBlockState(pos).is(block)) found++;
        return found;
    }

    /** The bag, both hands and every chest block: the whole stock of items in the arena. */
    private static Map<Item, Integer> snapshot(GameTestHelper h, CompanionEntity oleg) {
        Map<Item, Integer> all = new HashMap<>();
        for (int slot = 0; slot < oleg.getContainerSize(); slot++) add(all, oleg.getItem(slot));
        add(all, oleg.getMainHandItem());
        add(all, oleg.getOffhandItem());
        for (BlockPos pos : chests(h)) if (h.getBlockEntity(pos) instanceof ChestBlockEntity chest) for (int slot = 0; slot < chest.getContainerSize(); slot++) add(all, chest.getItem(slot));
        return all;
    }

    private static void add(Map<Item, Integer> all, ItemStack stack) { if (!stack.isEmpty()) all.merge(stack.getItem(), stack.getCount(), Integer::sum); }

    private static int count(Container box, Item item) {
        int count = 0;
        for (int slot = 0; slot < box.getContainerSize(); slot++) if (box.getItem(slot).is(item)) count += box.getItem(slot).getCount();
        if (box instanceof CompanionEntity companion) for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (companion.getItemBySlot(hand).is(item)) count += companion.getItemBySlot(hand).getCount();
        return count;
    }

    private static int totalInChests(GameTestHelper h, Item item) {
        int total = 0;
        for (BlockPos pos : chests(h)) total += count(box(h, pos), item);
        return total;
    }

    private static boolean onlyClass(Container box, CompanionItemClass kind) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) if (!box.getItem(slot).isEmpty() && CompanionItemClass.of(box.getItem(slot)) != kind) return false;
        return true;
    }

    private static int classCount(Container box) {
        Set<CompanionItemClass> kinds = EnumSet.noneOf(CompanionItemClass.class);
        for (int slot = 0; slot < box.getContainerSize(); slot++) if (!box.getItem(slot).isEmpty()) kinds.add(CompanionItemClass.of(box.getItem(slot)));
        return kinds.size();
    }

    private static ItemStack firstStack(Container box) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) if (!box.getItem(slot).isEmpty()) return box.getItem(slot);
        return ItemStack.EMPTY;
    }

    private static String describe(Container box) {
        Map<Item, Integer> items = new HashMap<>();
        for (int slot = 0; slot < box.getContainerSize(); slot++) add(items, box.getItem(slot));
        return items.toString();
    }

    private static void done(GameTestHelper h, CompanionEntity oleg) {
        h.assertTrue(!oleg.isGatheringTask() && !oleg.isWorking(), "Work still active: " + oleg.getLastTaskResult() + " at " + oleg.position());
    }
}
