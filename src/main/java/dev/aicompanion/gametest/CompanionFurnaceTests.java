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
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
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
