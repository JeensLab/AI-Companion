package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionChunks;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The furnace like a player uses it: raw material and fuel in, real cooking time, the real product taken out. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFurnaceTests {
    /** Three raw iron take thirty seconds of burning; one coal is enough and the other stays in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void rawIronIsSmeltedInNearbyFurnaceWithCoal(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 3).success(), "Smelt order not accepted");
        long[] loadedAt = {-1};
        h.onEachTick(() -> { if (loadedAt[0] < 0 && container(h, furnace).getItem(0).is(Items.RAW_IRON)) loadedAt[0] = h.getTick(); });
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(loadedAt[0] >= 0 && h.getTick() - loadedAt[0] >= 3 * 200, "Ingots without real cooking time: loaded at " + loadedAt[0] + ", now " + h.getTick());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 3 && count(companion, Items.RAW_IRON) == 0, "Wrong product: " + companion.getStoredItemsSummary());
            h.assertTrue(count(companion, Items.COAL) == 1, "Expected exactly one coal burnt: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty(), "Something left in the furnace");
        });
    }

    /** No furnace anywhere: one is made from real cobblestone at the bench, set down, and fed with planks. The product was named. */
    @GameTest(template = "tall_tree", timeoutTicks = 1400)
    public static void withoutFurnaceOwnIsMadeFromStoneAndPlanksBurn(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 8)); companion.setItem(1, new ItemStack(Items.SAND, 2));
        companion.setItem(2, new ItemStack(Items.OAK_PLANKS, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:glass", 2).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 1 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Expected one own furnace and no extra bench");
            h.assertTrue(count(companion, Items.GLASS) == 2 && count(companion, Items.SAND) == 0 && count(companion, Items.COBBLESTONE) == 0,
                    "Wrong materials: " + companion.getStoredItemsSummary());
            h.assertTrue(count(companion, Items.OAK_PLANKS) == 2, "Two planks burn four hundred ticks: " + companion.getStoredItemsSummary());
        });
    }

    /** Smelting cobblestone without a furnace: the new furnace must not eat the stone that was to go inside it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1400)
    public static void cobblestoneForTheFurnaceIsNotTheStoneToSmelt(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 12)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:cobblestone", 4).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.STONE) == 4 && count(companion, Items.COBBLESTONE) == 0 && blocks(h, Blocks.FURNACE) == 1,
                    "Stone or furnace wrong: " + companion.getStoredItemsSummary());
        });
    }

    /** Someone's raw gold waits in a furnace: it stays untouched, Oleg sets down the furnace from his bag instead. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void furnaceInUseByPlayerIsLeftAlone(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos busy = new BlockPos(19, 1, 16); h.setBlock(busy, Blocks.FURNACE);
        container(h, busy).setItem(0, new ItemStack(Items.RAW_GOLD, 5));
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 2)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        companion.setItem(2, new ItemStack(Items.FURNACE));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 2).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(container(h, busy).getItem(0).is(Items.RAW_GOLD) && container(h, busy).getItem(0).getCount() == 5
                    && container(h, busy).getItem(1).isEmpty(), "Player's furnace was touched");
            h.assertTrue(blocks(h, Blocks.FURNACE) == 2 && count(companion, Items.FURNACE) == 0 && count(companion, Items.IRON_INGOT) == 2,
                    "Own furnace not used: " + companion.getStoredItemsSummary());
        });
    }

    /** Nothing to burn in the bag: he chops wood first and burns the logs, the raw iron waits in the bag meanwhile. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void withoutFuelHeChopsWoodFirst(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(14, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(19, y, 16), Blocks.OAK_LOG);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 2).success(), "Smelt order not accepted");
        boolean[] rawWaitedInBag = {true}, chopped = {false};
        h.onEachTick(() -> {
            chopped[0] |= count(companion, Items.OAK_LOG) > 0;
            if (!chopped[0] && container(h, furnace).getItem(0).is(Items.RAW_IRON)) rawWaitedInBag[0] = false;
        });
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 2 && count(companion, Items.RAW_IRON) == 0, "Wrong product: " + companion.getStoredItemsSummary());
            h.assertTrue(rawWaitedInBag[0], "Raw iron went into a cold furnace before there was fuel");
            h.assertTrue(container(h, furnace).isEmpty(), "Something left in the furnace");
        });
    }

    /** "Stop" while the furnace works: nothing vanishes, the rest is in the furnace and the result says where. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void stopWhileSmeltingLosesNothing(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 4).success(), "Smelt order not accepted");
        long work = companion.activeWorkId();
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (!stopped[0] && container(h, furnace).getItem(2).is(Items.IRON_INGOT)) {
                CompanionEvents.executeAction(owner(h, companion), "stop", "", 1);
                stopped[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && !companion.isWorking(), "Not stopped yet");
            CompanionActionResult result = companion.workResult(work);
            h.assertTrue(result != null && result.status() == CompanionActionResult.Status.CANCELLED && result.message().contains("печи"), "Result does not say where the iron is: " + result);
            Container oven = container(h, furnace);
            int iron = count(companion, Items.RAW_IRON) + count(companion, Items.IRON_INGOT) + oven.getItem(0).getCount() + oven.getItem(2).getCount();
            h.assertTrue(iron == 4, "Iron lost or duplicated: " + iron);
        });
    }

    /** Asked for five with two in the bag: two are smelt, and the shortfall is said, not hidden behind a success. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void shortfallIsSmeltAndReportedHonestly(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(19, 1, 16), Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 2)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 5).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("только 2"), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 2 && count(companion, Items.RAW_IRON) == 0, "Wrong product: " + companion.getStoredItemsSummary());
        });
    }

    /** Nothing that smelts into the named item, or no raw material at all: refused at once, nothing touched. */
    @GameTest(template = "tall_tree")
    public static void impossibleSmeltIsRefusedAtOnce(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.COAL, 3));
        var owner = owner(h, companion);
        h.assertTrue(!CompanionEvents.executeAction(owner, "smelt_items", "minecraft:diamond_sword", 1).success(), "Unsmeltable item accepted");
        h.assertTrue(!CompanionEvents.executeAction(owner, "smelt_items", "minecraft:raw_iron", 1).success(), "Smelting without raw material accepted");
        h.assertTrue(!companion.isWorking() && count(companion, Items.COAL) == 3, "Refused order changed something");
        h.succeed();
    }

    /** The whole path a model plan takes: codec, plan runner, server port, verified result of its own workId. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void modelPlanWaitsForTheRealFurnaceResult(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"smelt_items\",\"item\":\"minecraft:charcoal\",\"count\":1}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-smelt-plan", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(outcome.skipped().isEmpty() && outcome.report().contains("Переплавил"), outcome.report());
            h.assertTrue(CompanionDialogue.summarize(plan, outcome.report()).equals("Готово, переплавил и забрал из печи."), "Summary changed");
            // One log is the raw material, one log burns (300 ticks cover one item), one stays.
            h.assertTrue(count(companion, Items.CHARCOAL) == 1 && count(companion, Items.OAK_LOG) == 1, "Logs or charcoal wrong: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty(), "Something left in the furnace");
        });
    }

    /** 24 raw iron and spare furnaces in the bag: three furnaces of eight work at once, not one furnace for four minutes. */
    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void bigBatchIsSpreadOverSeveralFurnaces(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos free = new BlockPos(19, 1, 16); h.setBlock(free, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 24)); companion.setItem(1, new ItemStack(Items.COAL, 3));
        companion.setItem(2, new ItemStack(Items.FURNACE, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 24).success(), "Smelt order not accepted");
        long[] firstLoad = {-1};
        h.onEachTick(() -> {
            if (firstLoad[0] >= 0) return;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31)))
                if (h.getBlockState(pos).is(Blocks.FURNACE) && container(h, pos).getItem(0).is(Items.RAW_IRON)) { firstLoad[0] = h.getTick(); return; }
        });
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("в 3 печах"), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 24 && count(companion, Items.RAW_IRON) == 0 && count(companion, Items.COAL) == 0,
                    "Wrong materials: " + companion.getStoredItemsSummary());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 3 && count(companion, Items.FURNACE) == 2, "Expected the free furnace plus two from the bag: " + companion.getStoredItemsSummary());
            h.assertTrue(firstLoad[0] >= 0 && h.getTick() - firstLoad[0] <= 8 * 200 + 600, "Not in parallel: " + (h.getTick() - firstLoad[0]) + " ticks");
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31)))
                if (h.getBlockState(pos).is(Blocks.FURNACE)) h.assertTrue(container(h, pos).isEmpty(), "Something left in a furnace at " + pos);
        });
    }

    /** Game log 22.10: «переплавь железо» with 64 raw iron smelt a single piece. Without a number it is everything carried. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void withoutNumberEverythingCarriedIsSmelt(GameTestHelper h) {
        var one = List.of(new PlannedAction("smelt_items", 0, 1, "minecraft:raw_iron"));
        h.assertTrue(CompanionPlanRequirements.apply("переплавь железо", new CompanionReply("ок", one)).actions().getFirst().count() == PlannedAction.ALL_CARRIED, "No number kept one piece");
        var most = List.of(new PlannedAction("smelt_items", 0, 63, "minecraft:raw_iron"));
        h.assertTrue(CompanionPlanRequirements.apply("переплавь всё железо, что у тебя есть", new CompanionReply("ок", most)).actions().getFirst().count() == PlannedAction.ALL_CARRIED, "«Всё» not everything");
        var five = List.of(new PlannedAction("smelt_items", 0, 5, "minecraft:raw_iron"));
        h.assertTrue(CompanionPlanRequirements.apply("переплавь 5 железа", new CompanionReply("ок", five)).actions().getFirst().count() == 5, "Named number changed");
        h.assertTrue(CompanionPlanRequirements.apply("переплавь один кусок железа", new CompanionReply("ок", one)).actions().getFirst().count() == 1, "«Один» changed");
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"smelt_items\",\"item\":\"minecraft:raw_iron\"}]")).getFirst().count() == PlannedAction.ALL_CARRIED, "Codec: no count is not everything");
        h.assertTrue(CompanionWorkCommands.parse("переплавь железо", "Олег").count() == PlannedAction.ALL_CARRIED, "Local command without number");

        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(19, 1, 16), Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 5)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", PlannedAction.ALL_CARRIED).success(), "Smelt-all order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && !companion.getLastTaskResult().contains("только"), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 5 && count(companion, Items.RAW_IRON) == 0, "Not everything smelt: " + companion.getStoredItemsSummary());
        });
    }

    @GameTest(template = "empty")
    public static void smeltProtocolAndLocalCommand(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"smelt_items\",\"item\":\"minecraft:raw_iron\",\"count\":5}]"));
        h.assertTrue(plan.size() == 1 && plan.getFirst().type().equals("smelt_items") && plan.getFirst().count() == 5, "Smelt step lost");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"smelt_items\",\"count\":5}]")); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "Smelt step without an item accepted");
        PlannedAction local = CompanionWorkCommands.parse("Олег, переплавь 5 железа", "Олег");
        h.assertTrue(local != null && local.type().equals("smelt_items") && local.item().equals("minecraft:raw_iron") && local.count() == 5, "Local command: " + local);
        h.assertTrue(CompanionServerPlanPort.limitSeconds(new PlannedAction("smelt_items", 0, 64, "minecraft:raw_iron"))
                > CompanionServerPlanPort.WORK_LIMIT_SECONDS + 64 * 10, "Client gives up before 64 items can cook");
        h.assertTrue(CompanionDialogue.describe(plan.getFirst()).startsWith("переплавить"), "Skipped-step wording");
        h.succeed();
    }

    /**
     * Four iron take 800 ticks: he hands them to the furnace, is free meanwhile, and the later collection reports like a smelt he stood through.
     * In the full run the furnace's chunk sometimes loads its block entities only after 1300 ticks (the furnace stands cold till then): time for that too.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void parkedBatchIsCollectedWithTheSameReport(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        long[] collect = {0};
        h.succeedWhen(() -> {
            if (collect[0] == 0) {
                CompanionActionResult parked = companion.workResult(batch);
                h.assertTrue(parked != null && parked.status() == CompanionActionResult.Status.PARKED && !parked.success() && !companion.isWorking(), "Not parked: " + parked);
                h.assertTrue(parked.message().contains("вернусь") && parked.evidence().workId() == batch, "Park report: " + parked);
                h.assertTrue(isReady(companion, batch), "Batch not ready yet: " + companion.batchState(batch));
                var state = companion.batchState(batch);
                h.assertTrue(state.ready() == 4 && state.total() == 4 && state.nearest() != null, "Ready counts: " + state);
                h.assertTrue(container(h, furnace).getItem(2).is(Items.IRON_INGOT) && container(h, furnace).getItem(2).getCount() == 4, "The product is not in the furnace");
                collect[0] = collect(h, companion, batch);
            }
            h.assertTrue(companion.workResult(collect[0]) != null && !companion.isWorking(), "Collecting");
            CompanionActionResult result = companion.workResult(collect[0]);
            h.assertTrue(result.success() && result.message().contains("Переплавил 4 «" + name(Items.RAW_IRON) + "» и забрал 4 «" + name(Items.IRON_INGOT) + "»"), "Report: " + result);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 4 && count(companion, Items.RAW_IRON) == 0, "Wrong materials: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty() && companion.batchState(batch) == null, "Furnace not emptied or batch kept");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void twoItemSmeltDoesNotPark(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 2)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        long work = startParked(h, companion, "minecraft:raw_iron", 2);
        boolean[] everParked = {false};
        h.onEachTick(() -> { if (companion.batchState(work) != null) everParked[0] = true; });
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(work);
            h.assertTrue(!everParked[0] && result.status() == CompanionActionResult.Status.SUCCESS, "Short smelt was parked or failed: " + result);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 2 && container(h, furnace).isEmpty(), "Wrong materials: " + companion.getStoredItemsSummary());
        });
    }

    /** Handing over is no failure and no end of the job: the field bench stays until the collection, nothing is written to the deeds. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void parkDoesNotJournalAFailureOrPackTheBench(GameTestHelper h) throws Exception {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        BlockPos bench = new BlockPos(18, 1, 17); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        var benches = CompanionEntity.class.getDeclaredField("fieldBenches"); benches.setAccessible(true);
        @SuppressWarnings("unchecked") List<BlockPos> fieldBenches = (List<BlockPos>) benches.get(companion);
        fieldBenches.add(h.absolutePos(bench));
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        int deeds = companion.deedCount();
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        long[] collect = {0};
        h.succeedWhen(() -> {
            if (collect[0] == 0) {
                h.assertTrue(isReady(companion, batch), "Batch not ready yet: " + companion.batchState(batch));
                h.assertTrue(isParked(companion, batch), "Not parked: " + companion.workResult(batch));
                h.assertTrue(companion.deedCount() == deeds && !companion.getDeedsForAi().contains("Прервал"), "Park wrote a deed: " + companion.getDeedsForAi());
                h.assertTrue(h.getBlockState(bench).is(Blocks.CRAFTING_TABLE) && count(companion, Items.CRAFTING_TABLE) == 0 && fieldBenches.contains(h.absolutePos(bench)), "The bench was packed at the park");
                collect[0] = collect(h, companion, batch);
            }
            done(h, companion);
            h.assertTrue(companion.workResult(collect[0]).success(), "Collection: " + companion.workResult(collect[0]));
            h.assertTrue(count(companion, Items.CRAFTING_TABLE) == 1 && h.getBlockState(bench).isAir(), "The bench was not picked up after the collection");
        });
    }

    /** Nine iron go into two furnaces, 5 and 4: the batch is ready only when the slower one is done too. */
    @GameTest(template = "tall_tree", timeoutTicks = 2200)
    public static void batchIsNotReadyUntilEveryLotIsDone(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos first = new BlockPos(19, 1, 16), second = new BlockPos(19, 1, 18);
        h.setBlock(first, Blocks.FURNACE); h.setBlock(second, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 9)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 9);
        boolean[] partial = {false}, early = {false};
        h.onEachTick(() -> {
            var state = companion.batchState(batch);
            if (state == null) return;
            boolean oneEmpty = container(h, first).getItem(0).isEmpty() != container(h, second).getItem(0).isEmpty();
            if (state.status() == CompanionFurnaceBatches.Status.WAITING && oneEmpty && state.ready() > 0 && state.ready() < state.total()) partial[0] = true;
            if (state.status() == CompanionFurnaceBatches.Status.READY && !(container(h, first).getItem(0).isEmpty() && container(h, second).getItem(0).isEmpty())) early[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(isReady(companion, batch), "Batch not ready yet: " + companion.batchState(batch));
            var state = companion.batchState(batch);
            h.assertTrue(partial[0] && !early[0], "Ready while a furnace still cooked, or the finished furnace never seen waiting: partial=" + partial[0] + " early=" + early[0]);
            h.assertTrue(state.ready() == 9 && state.total() == 9, "Ready counts: " + state);
            h.assertTrue(container(h, first).getItem(2).getCount() + container(h, second).getItem(2).getCount() == 9, "Not all product is in the furnaces");
        });
    }

    /** A furnace that ran dry counts as done for the batch; the collection refuels it and hands it over again. */
    @GameTest(template = "tall_tree", timeoutTicks = 2800)
    public static void outOfFuelCountsAsReadyAndRefuelParksAgain(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        container(h, furnace).setItem(1, new ItemStack(Items.OAK_PLANKS, 1));
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        long[] stage = {0, 0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isReady(companion, batch), "Dry furnace is not ready: " + companion.batchState(batch));
                h.assertTrue(companion.batchState(batch).ready() < 4 && !container(h, furnace).getItem(0).isEmpty() && container(h, furnace).getItem(1).isEmpty(),
                        "Furnace was not left dry with iron: " + companion.batchState(batch));
                companion.setItem(0, new ItemStack(Items.COAL, 2));
                stage[1] = collect(h, companion, batch); companion.markParkable(stage[1]); stage[0] = 1;
            }
            if (stage[0] == 1) {
                CompanionActionResult again = companion.workResult(stage[1]);
                h.assertTrue(again != null && again.status() == CompanionActionResult.Status.PARKED, "Not parked again: " + again);
                h.assertTrue(!container(h, furnace).getItem(1).isEmpty() || h.getBlockState(furnace).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT), "Not refuelled");
                h.assertTrue(companion.batchState(batch) == null && companion.batchState(stage[1]) != null, "Batch not handed on under the new work");
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(isReady(companion, stage[1]), "Second batch not ready yet: " + companion.batchState(stage[1]));
                stage[1] = collect(h, companion, stage[1]); stage[0] = 3;
            }
            done(h, companion);
            h.assertTrue(companion.workResult(stage[1]).success() && count(companion, Items.IRON_INGOT) == 4, companion.workResult(stage[1]) + " / " + companion.getStoredItemsSummary());
        });
    }

    /** The batch sat done for longer than a furnace may make nothing: the collection must not mistake that for a stalled furnace. */
    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void collectAfterLongReadyIsNotCalledStalled(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        long batch = startParked(h, companion, "minecraft:raw_iron", 3);
        long[] readyAt = {-1, 0};
        h.succeedWhen(() -> {
            if (readyAt[0] < 0) { h.assertTrue(isReady(companion, batch), "Batch not ready yet"); readyAt[0] = h.getTick(); }
            if (readyAt[1] == 0) {
                h.assertTrue(h.getTick() - readyAt[0] > 2 * 200 + 20 * 30 + 100, "Leaving the batch alone");
                readyAt[1] = collect(h, companion, batch);
            }
            done(h, companion);
            CompanionActionResult result = companion.workResult(readyAt[1]);
            h.assertTrue(result.success() && !result.message().contains("перестала плавить") && count(companion, Items.IRON_INGOT) == 3, result + " / " + companion.getStoredItemsSummary());
        });
    }

    /** A parked furnace holds a finished product: another smelt must not load into it and mix its own count with the batch. */
    @GameTest(template = "tall_tree", timeoutTicks = 2800)
    public static void parkedFurnaceIsNotTakenBySecondSmelt(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos parkedFurnace = new BlockPos(18, 1, 16), spare = new BlockPos(20, 1, 16);
        h.setBlock(parkedFurnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        long[] stage = {0, 0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isReady(companion, batch), "Batch not ready yet");
                h.setBlock(spare, Blocks.FURNACE);
                companion.setItem(2, new ItemStack(Items.RAW_IRON, 2));
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 2).success(), "Second smelt not accepted");
                stage[1] = companion.activeWorkId(); stage[0] = 1;
            }
            if (stage[0] == 1) {
                done(h, companion);
                CompanionActionResult second = companion.workResult(stage[1]);
                h.assertTrue(second.success() && second.message().contains(h.absolutePos(spare).toShortString()), "Second smelt: " + second);
                h.assertTrue(container(h, parkedFurnace).getItem(2).getCount() == 4 && count(companion, Items.IRON_INGOT) == 2, "The parked product was touched: " + companion.getStoredItemsSummary());
                stage[0] = 2; stage[1] = collect(h, companion, batch);
            }
            done(h, companion);
            h.assertTrue(companion.workResult(stage[1]).success() && count(companion, Items.IRON_INGOT) == 6, companion.workResult(stage[1]) + " / " + companion.getStoredItemsSummary());
        });
    }

    /** The furnace is gone while parked: the batch is lost, and the collection says so instead of reporting iron nobody has. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void brokenFurnaceWhileParkedIsReportedNotInvented(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        long[] collect = {0};
        h.succeedWhen(() -> {
            if (collect[0] == 0) {
                CompanionActionResult parked = companion.workResult(batch);
                h.assertTrue(parked != null && parked.status() == CompanionActionResult.Status.PARKED, "Not parked: " + parked);
                h.setBlock(furnace, Blocks.AIR);
                h.assertTrue(companion.batchState(batch).status() == CompanionFurnaceBatches.Status.LOST, "Lost furnace not noticed: " + companion.batchState(batch));
                collect[0] = collect(h, companion, batch);
            }
            done(h, companion);
            CompanionActionResult result = companion.workResult(collect[0]);
            h.assertTrue(!result.success() && result.message().contains("пропала"), "Report: " + result);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 0 && count(companion, Items.RAW_IRON) == 0, "Iron out of nowhere: " + companion.getStoredItemsSummary());
        });
    }

    /** Two of the four ingots were taken by someone else while parked: what is left is reported as less than asked, nothing is duplicated. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void playerTookProductWhileParked(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        long[] collect = {0};
        h.succeedWhen(() -> {
            if (collect[0] == 0) {
                h.assertTrue(isReady(companion, batch), "Batch not ready yet");
                container(h, furnace).removeItem(2, 2);
                collect[0] = collect(h, companion, batch);
            }
            done(h, companion);
            CompanionActionResult result = companion.workResult(collect[0]);
            h.assertTrue(!result.success() && !result.message().contains("забрал 4"), "Claimed more than was there: " + result);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 2 && container(h, furnace).isEmpty(), "Wrong materials: " + companion.getStoredItemsSummary());
        });
    }

    /** Far from the furnace the companion's own chunks are not the furnace's chunk: it joins his lease while the batch lives and leaves with it. */
    // Own batch: the companion is moved far away, into the area of neighbouring tests, whose cleanup would discard it.
    @GameTest(template = "tall_tree", batch = "furnace_far_lease", timeoutTicks = 1600)
    public static void parkedFurnaceChunkKeepsTicking(GameTestHelper h) {
        CompanionEntity companion = setup(h); companion.setLifestyle(true, false);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        BlockPos far = h.absolutePos(new BlockPos(16 + 120, 1, 16));
        int[] stage = {0}, before = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isParked(companion, batch), "Not parked: " + companion.workResult(batch));
                companion.setFollowing(false);
                level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "Far chunk not loaded yet");
                level.setBlock(far.below(), Blocks.BEDROCK.defaultBlockState(), 3);
                companion.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                before[0] = container(h, furnace).getItem(2).getCount();
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(CompanionChunks.ticketCount(companion.getUUID()) == 26, "Furnace chunk not in the lease: " + CompanionChunks.ticketCount(companion.getUUID()));
                h.assertTrue(container(h, furnace).getItem(2).getCount() > before[0], "Furnace did not cook while he was away");
                companion.releaseBatch(batch);
                stage[0] = 3;
            }
            h.assertTrue(CompanionChunks.ticketCount(companion.getUUID()) == 25, "Lease not shrunk after the release: " + CompanionChunks.ticketCount(companion.getUUID()));
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        });
    }

    /** A short order started right after the park hides nothing: the runner polls once a second and still reads PARKED for the parked work. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void parkedResultSurvivesANewWorkStart(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        int[] stage = {0}; long[] collect = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isParked(companion, batch), "Not parked: " + companion.workResult(batch));
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "come", "", 1).success(), "Short order not accepted");
                h.assertTrue(companion.activeWorkId() > batch, "The short order did not take over the work slot");
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(isParked(companion, batch) && companion.workResult(batch).evidence().workId() == batch, "The park was hidden: " + companion.workResult(batch));
                h.assertTrue(isReady(companion, batch), "Batch not ready yet");
                collect[0] = collect(h, companion, batch); stage[0] = 2;
            }
            done(h, companion);
            CompanionActionResult result = companion.workResult(collect[0]);
            h.assertTrue(result.success() && count(companion, Items.IRON_INGOT) == 4, result + " / " + companion.getStoredItemsSummary());
        });
    }

    /**
     * He is more than two chunks away when the collection starts. The batch's lease on the furnace chunk must carry over to the
     * collecting work, or the chunk would unload on the way and the furnace would be reported gone. The walk itself is not asserted:
     * the test world does not give the pathing a reliable far route.
     */
    // Own batch for the same reason as parkedFurnaceChunkKeepsTicking.
    @GameTest(template = "tall_tree", batch = "furnace_far_collect", timeoutTicks = 3000)
    public static void collectFromAnUnloadedFurnaceChunkWalksThere(GameTestHelper h) {
        CompanionEntity companion = setup(h); companion.setLifestyle(true, false);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        long batch = startParked(h, companion, "minecraft:raw_iron", 3);
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        int distance = 56;
        BlockPos furnaceAt = h.absolutePos(furnace), far = h.absolutePos(new BlockPos(16 + distance, 1, 16));
        int[] stage = {0}; long[] collectedAt = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isParked(companion, batch), "Not parked: " + companion.workResult(batch));
                var player = owner(h, companion);
                player.teleportTo(player.getX(), player.getY(), player.getZ() + 300);
                companion.setFollowing(false);
                level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "Far chunk not loaded yet");
                companion.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(level.hasChunk((far.getX() - 32) >> 4, far.getZ() >> 4), "The way ahead is not loaded yet");
                h.assertTrue(isReady(companion, batch), "Batch not ready yet");
                collect(h, companion, batch); collectedAt[0] = h.getTick(); stage[0] = 3;
            }
            if (stage[0] == 3) {
                h.assertTrue(h.getTick() - collectedAt[0] >= 40, "Walking");
                h.assertTrue(companion.distanceToSqr(furnaceAt.getCenter()) > 40 * 40, "He should still be far from the furnace");
                var player = owner(h, companion);
                // The size of the work area around him depends on how near the owner is; the furnace chunk must be leased either way.
                h.assertTrue(CompanionChunks.leases(companion.getUUID(), ChunkPos.asLong(furnaceAt)), "Furnace chunk dropped from the lease: " + CompanionChunks.ticketCount(companion.getUUID())
                        + " tickets, he is at " + companion.blockPosition() + ", the owner at " + player.blockPosition() + ", batch " + companion.workResult(batch));
                h.assertTrue(level.hasChunk(furnaceAt.getX() >> 4, furnaceAt.getZ() >> 4), "Furnace chunk unloaded");
            }
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        });
    }

    /** The batch was ready only because the furnace made nothing for too long: the collection takes what is there at once and does not wait out the stall again. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void stalledBatchIsEmptiedWithoutWaitingAgain(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(20, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        long batch = startParked(h, companion, "minecraft:raw_iron", 3);
        int[] stage = {0}; long[] collect = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isParked(companion, batch), "Not parked: " + companion.workResult(batch));
                container(h, furnace).setItem(2, new ItemStack(Items.STONE, 64));
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(isReady(companion, batch), "Batch not ready yet");
                collect[0] = collect(h, companion, batch); stage[0] = 2;
            }
            done(h, companion);
            CompanionActionResult result = companion.workResult(collect[0]);
            h.assertTrue(!result.success() && result.message().contains("перестала плавить"), "Report: " + result);
            h.assertTrue(count(companion, Items.RAW_IRON) == 3 && count(companion, Items.IRON_INGOT) == 0, "Wrong materials: " + companion.getStoredItemsSummary());
        });
    }

    /** A cell diagonally behind the bench shows the furnace only past the bench's corner: the stand cell and the reach check must call that in sight alike. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800, batch = "furnace_pit_sight")
    public static void readyFurnaceIsCollectedFromACellBesideTheBench(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(10, 2, 20), bench = new BlockPos(10, 2, 19), cell = new BlockPos(9, 1, 18);
        h.setBlock(new BlockPos(10, 1, 20), Blocks.STONE); h.setBlock(new BlockPos(10, 1, 19), Blocks.STONE);
        h.setBlock(furnace, Blocks.FURNACE); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        long batch = startParked(h, companion, "minecraft:raw_iron", 4);
        int[] stage = {0}; long[] collect = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(isParked(companion, batch) && isReady(companion, batch), "Batch not ready yet: " + companion.batchState(batch));
                BlockPos at = h.absolutePos(cell);
                companion.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
                collect[0] = collect(h, companion, batch); stage[0] = 1;
            }
            done(h, companion);
            CompanionActionResult result = companion.workResult(collect[0]);
            h.assertTrue(result.success(), "Collecting from " + cell + ": " + result);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 4 && container(h, furnace).isEmpty(), "Not taken: " + companion.getStoredItemsSummary());
        });
    }

    private static long startParked(GameTestHelper h, CompanionEntity companion, String item, int count) {
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", item, count).success(), "Smelt order not accepted");
        long work = companion.activeWorkId();
        companion.markParkable(work);
        return work;
    }
    private static boolean isParked(CompanionEntity companion, long work) {
        CompanionActionResult result = companion.workResult(work);
        return result != null && result.status() == CompanionActionResult.Status.PARKED;
    }
    private static boolean isReady(CompanionEntity companion, long batch) {
        var state = companion.batchState(batch);
        return state != null && state.status() == CompanionFurnaceBatches.Status.READY;
    }
    private static long collect(GameTestHelper h, CompanionEntity companion, long batch) {
        CompanionActionResult started = CompanionEvents.executeAction(owner(h, companion), "collect_smelt", String.valueOf(batch), 1);
        h.assertTrue(started.success(), "Collection refused: " + started);
        return companion.activeWorkId();
    }
    private static String name(Item item) { return new ItemStack(item).getHoverName().getString(); }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    private static Container container(GameTestHelper h, BlockPos pos) { return (Container) h.getBlockEntity(pos); }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int blocks(GameTestHelper h, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
