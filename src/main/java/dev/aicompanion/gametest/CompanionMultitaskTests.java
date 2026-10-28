package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAutonomy;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import dev.aicompanion.entity.CompanionGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.CompanionMultitaskSupport.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Several requests at once: a long smelt hands its furnaces to a batch and the queue runs the next request, through the same link the game uses. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMultitaskTests {
    /** A: eight raw beef in one furnace, about 80 seconds of cooking, so the batch parks; B: four logs. B finishes while A waits. */
    @GameTest(template = "tall_tree", batch = "multitask_parallel", timeoutTicks = 4200)
    public static void smeltParksWhileAnotherRequestRuns(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        for (int y = 1; y <= 5; y++) { h.setBlock(new BlockPos(20, y, 12), Blocks.OAK_LOG); h.setBlock(new BlockPos(20, y, 20), Blocks.OAK_LOG); }
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef")), "gametest-multitask-smelt", () -> false);
        var wood = link.submit(List.of(new PlannedAction("gather_wood", 0, 4)), "gametest-multitask-wood", () -> false);
        boolean[] woodDone = {false}, during = {false};
        h.onEachTick(() -> {
            if (!wood.isDone() || woodDone[0]) return;
            woodDone[0] = true;
            during[0] = status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING;
        });
        h.succeedWhen(() -> {
            h.assertTrue(smelt.isDone() && wood.isDone(), "Requests still running: smelt=" + status(link, "smelt_items") + " wood=" + status(link, "gather_wood")
                    + " " + companion.getLastTaskResult());
            h.assertTrue(during[0], "The wood was not gathered while the smelt waited in its furnace");
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED && status(link, "gather_wood") == CompanionTaskQueue.Status.COMPLETED,
                    "Not both completed: " + smelt.join() + " | " + wood.join());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 8 && logs(companion) >= 4, "Bag: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty(), "Something left in the furnace");
            h.assertTrue(companion.batches().summaries().isEmpty() && !companion.isWorking(), "Batch or work left over: " + companion.batches().summaries());
            link.close();
        });
    }

    /** With nothing else to do he follows the player to where he went, says how far the cooking is, and walks back to the furnace only when everything is ready. */
    @GameTest(template = "tall_tree", batch = "multitask_alone", timeoutTicks = 4200)
    public static void smeltWithNothingElseFollowsThePlayerAndComesBackWhenReady(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef")), "gametest-multitask-alone", () -> false);
        var player = owner(h, companion);
        BlockPos far = h.absolutePos(new BlockPos(3, 1, 3));
        int[] stage = {0};
        boolean[] early = {false};
        h.onEachTick(() -> {
            if (stage[0] == 0 || smelt.isDone()) return;
            boolean cooking = companion.batches().summaries().size() == 1 && status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING;
            var batch = companion.batches().summaries().isEmpty() ? null : companion.batchState(companion.batches().summaries().getFirst().id());
            if (cooking && batch != null && batch.status() == CompanionFurnaceBatches.Status.WAITING && companion.isWorking()) early[0] = true;
            if (status(link, "smelt_items") == CompanionTaskQueue.Status.RUNNING && batch != null && batch.status() == CompanionFurnaceBatches.Status.WAITING) early[0] = true;
        });
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                player.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                h.assertTrue(CompanionEvents.executeAction(player, "follow", "", 1).success(), "The follow order was refused");
                h.assertTrue(companion.autonomy().held() && !companion.autonomy().halted(), "Following must hold him but not as a stop");
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING && companion.distanceTo(player) <= 6
                        && companion.getLastTaskResult().contains("готово"), "Not following yet: " + companion.distanceTo(player) + " " + companion.getLastTaskResult());
                stage[0] = 2;
            }
            h.assertTrue(smelt.isDone(), "Smelt still running: " + status(link, "smelt_items") + " " + companion.getLastTaskResult());
            h.assertTrue(!early[0], "He went back to work before the batch was ready");
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED, "Not completed: " + smelt.join());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 8 && container(h, furnace).isEmpty(), "Bag: " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /** Nine raw iron go into two furnaces and one finishes long before the other: he stays away until the last item is ready. */
    @GameTest(template = "tall_tree", batch = "multitask_not_back", timeoutTicks = 3200)
    public static void notBackBeforeTheWholeBatchIsDone(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos first = new BlockPos(19, 1, 16), second = new BlockPos(19, 1, 18);
        h.setBlock(first, Blocks.FURNACE); h.setBlock(second, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 9)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 9, "minecraft:raw_iron")), "gametest-multitask-notback", () -> false);
        boolean[] waited = {false}, partial = {false}, early = {false};
        h.onEachTick(() -> {
            var batch = batchState(companion);
            if (batch == null || batch.status() != CompanionFurnaceBatches.Status.WAITING) return;
            boolean parked = status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING;
            waited[0] |= parked;
            if (waited[0] && (!parked || companion.isWorking())) early[0] = true;
            if (parked && batch.ready() > 0 && batch.ready() < batch.total()) partial[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(smelt.isDone(), "Smelt still running: " + status(link, "smelt_items") + " " + companion.getLastTaskResult());
            h.assertTrue(waited[0] && partial[0], "Never waited with one furnace done and the other cooking: waited=" + waited[0] + " partial=" + partial[0]);
            h.assertTrue(!early[0], "He started collecting while a furnace still cooked");
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED && count(companion, Items.IRON_INGOT) == 9,
                    "Not completed: " + smelt.join() + " " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, first).isEmpty() && container(h, second).isEmpty(), "Something left in a furnace");
            link.close();
        });
    }

    /** Four beef and four pork: the first kind parks, its collection starts the second, which parks again; the player hears one report. */
    @GameTest(template = "tall_tree", batch = "multitask_two_meats", timeoutTicks = 4800)
    public static void twoMeatKindsParkTwiceAndReportOnce(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.PORKCHOP, 4)); companion.setItem(2, new ItemStack(Items.COAL, 4));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, PlannedAction.ALL_CARRIED, CompanionGame.ANY_MEAT)), "gametest-multitask-meats", () -> false);
        Set<Long> batches = new HashSet<>();
        int[] parks = {0};
        boolean[] orphaned = {false}, wasWaiting = {false};
        h.onEachTick(() -> {
            for (var batch : companion.batches().summaries()) { batches.add(batch.id()); orphaned[0] |= batch.orphan(); }
            boolean waiting = status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING;
            if (waiting && !wasWaiting[0]) parks[0]++;
            wasWaiting[0] = waiting;
        });
        h.succeedWhen(() -> {
            h.assertTrue(smelt.isDone(), "Smelt still running: " + status(link, "smelt_items") + " parks=" + parks[0] + " " + companion.getLastTaskResult());
            String report = smelt.join();
            h.assertTrue(parks[0] == 2 && batches.size() == 2 && !orphaned[0], "Parks=" + parks[0] + " batches=" + batches + " orphaned=" + orphaned[0]);
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED, "Not completed: " + report);
            h.assertTrue(report.contains("Пожарил и забрал 8 порций мяса (видов: 2)") && report.indexOf("Пожарил") == report.lastIndexOf("Пожарил") && !report.contains("Заложил"),
                    "Not one final line: " + report);
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && count(companion, Items.COOKED_PORKCHOP) == 4, "Bag: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(), "Furnace or batch left over");
            link.close();
        });
    }

    /**
     * The player cancels twice. First right after the park, while the request has not even noticed it yet; then in the middle of the wait,
     * with some product already cooked. Either way the product stays in the furnace, the report says where, and the furnace is free again.
     */
    @GameTest(template = "tall_tree", batch = "multitask_cancel_parked", timeoutTicks = 3600)
    public static void cancelledParkedSmeltLeavesItInTheFurnaceAndSaysWhere(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos first = new BlockPos(19, 1, 16), second = new BlockPos(19, 1, 18);
        h.setBlock(first, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 4));
        CompanionParkLink link = link(h, companion);
        var early = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef")), "gametest-multitask-cancel-early", () -> false);
        List<CompletableFuture<String>> late = new ArrayList<>();
        long[] ids = {0, 0};
        int[] stage = {0}, cooked = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(!companion.batches().summaries().isEmpty(), "Not parked yet: " + companion.getLastTaskResult());
                ids[0] = link.tasks().getFirst().id();
                h.assertTrue(link.cancel(ids[0]), "The cancel was refused");
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(early.isDone(), "The cancelled request is still running");
                h.assertTrue(status(link, ids[0]) == CompanionTaskQueue.Status.CANCELLED && early.join().contains(h.absolutePos(first).toShortString()),
                        "The report does not name the furnace: " + early.join());
                h.assertTrue(companion.batches().summaries().isEmpty() && !companion.isWorking(), "Batch or work left over: " + companion.batches().summaries());
                h.assertTrue(!container(h, first).isEmpty(), "The furnace was emptied");
                h.setBlock(first, Blocks.AIR); h.setBlock(second, Blocks.FURNACE);
                companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 4));
                late.add(link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef")), "gametest-multitask-cancel-late", () -> false));
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                ids[1] = link.tasks().stream().filter(view -> view.status() == CompanionTaskQueue.Status.WAITING).map(CompanionTaskQueue.View::id).findFirst().orElse(0L);
                h.assertTrue(ids[1] > 0, "Not parked again: " + companion.getLastTaskResult());
                cooked[0] = container(h, second).getItem(2).getCount();
                h.assertTrue(cooked[0] >= 1, "Nothing cooked yet");
                h.assertTrue(link.cancel(ids[1]), "The second cancel was refused");
                stage[0] = 3;
            }
            h.assertTrue(late.getFirst().isDone(), "The second cancelled request is still running");
            h.assertTrue(status(link, ids[1]) == CompanionTaskQueue.Status.CANCELLED && late.getFirst().join().contains(h.absolutePos(second).toShortString()),
                    "The report does not name the furnace: " + late.getFirst().join());
            h.assertTrue(companion.batches().summaries().isEmpty(), "The furnace is still reserved: " + companion.batches().summaries());
            h.assertTrue(container(h, second).getItem(2).getCount() >= cooked[0] && count(companion, Items.COOKED_BEEF) == 0,
                    "The product left the furnace: " + container(h, second).getItem(2) + " bag " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /** One plan: the raw iron cooks about 80 seconds, so it parks; the wood is gathered meanwhile, then the pickaxe is crafted from the real ingots. */
    @GameTest(template = "tall_tree", batch = "multitask_run_ahead", timeoutTicks = 6000)
    public static void ironSmeltRunsWhileWoodIsGatheredThenPickaxeIsCrafted(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        for (int y = 1; y <= 5; y++) { h.setBlock(new BlockPos(20, y, 12), Blocks.OAK_LOG); h.setBlock(new BlockPos(20, y, 20), Blocks.OAK_LOG); }
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2)); companion.setItem(2, new ItemStack(Items.STICK, 2));
        CompanionParkLink link = link(h, companion);
        var plan = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:raw_iron"), new PlannedAction("gather_wood", 0, 4),
                new PlannedAction("craft_item", 0, 1, "minecraft:iron_pickaxe")), "gametest-multitask-run-ahead", () -> false);
        boolean[] woodWhileCooking = {false}, waitedAfterWood = {false};
        h.onEachTick(() -> {
            boolean cooking = !companion.batches().summaries().isEmpty() && count(companion, Items.IRON_INGOT) == 0;
            woodWhileCooking[0] |= cooking && logs(companion) >= 4;
            waitedAfterWood[0] |= cooking && logs(companion) >= 4 && status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING;
        });
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone(), "Plan still running: " + status(link, "smelt_items") + " logs=" + logs(companion) + " " + companion.getLastTaskResult());
            String report = plan.join();
            h.assertTrue(woodWhileCooking[0], "The wood was not gathered while the iron cooked");
            h.assertTrue(waitedAfterWood[0], "He did not wait for the batch after the wood");
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED, "Not completed: " + report);
            h.assertTrue(count(companion, Items.IRON_PICKAXE) == 1 && count(companion, Items.IRON_INGOT) == 5 && count(companion, Items.RAW_IRON) == 0,
                    "Bag: " + companion.getStoredItemsSummary());
            String[] lines = report.split("\n");
            h.assertTrue(lines.length == 3 && lines[0].startsWith("Переплавил") && lines[1].startsWith("Закончил сбор") && lines[2].startsWith("Скрафтил"),
                    "Report is not in the order of the plan: " + report);
            h.assertTrue(container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(), "Furnace or batch left over");
            link.close();
        });
    }

    /** The hunt would bring the meat the furnace already cooks, so it waits for the collection: the cows are alive while the batch exists. */
    @GameTest(template = "tall_tree", batch = "multitask_hunt_after", timeoutTicks = 6000)
    public static void huntAfterMeatSmeltIsNotRunAhead(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        walls(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        var cows = List.of(h.spawn(EntityType.COW, 24, 1, 12), h.spawn(EntityType.COW, 24, 1, 20));
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var plan = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef"), new PlannedAction("hunt_animals", 0, 1, "minecraft:cow")),
                "gametest-multitask-hunt-after", () -> false);
        boolean[] batchSeen = {false}, cowDiedWithBatch = {false};
        h.onEachTick(() -> {
            if (companion.batches().summaries().isEmpty()) return;
            batchSeen[0] = true;
            cowDiedWithBatch[0] |= cows.stream().anyMatch(cow -> !cow.isAlive()) || count(companion, Items.BEEF) > 0;
        });
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone(), "Plan still running: " + status(link, "smelt_items") + " " + companion.getLastTaskResult());
            h.assertTrue(batchSeen[0] && !cowDiedWithBatch[0], "The hunt ran while the batch was in the furnace: seen=" + batchSeen[0]);
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED, "Not completed: " + plan.join());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 8 && cows.stream().filter(net.minecraft.world.entity.Entity::isAlive).count() == 1,
                    "Bag: " + companion.getStoredItemsSummary() + " cows alive: " + cows.stream().filter(net.minecraft.world.entity.Entity::isAlive).count());
            link.close();
        });
    }

    /** «Get four iron ingots» with four raw iron: the chain parks and waits for its own smelt; it does not order the ore again from the empty bag. */
    @GameTest(template = "tall_tree", batch = "multitask_obtain_wait", timeoutTicks = 4800)
    public static void obtainChainWaitsForItsOwnSmeltWithoutReplanning(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        List<BlockPos> ore = List.of(new BlockPos(22, 1, 12), new BlockPos(23, 1, 12), new BlockPos(24, 1, 12));
        ore.forEach(pos -> h.setBlock(pos, Blocks.IRON_ORE));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var plan = link.submit(List.of(new PlannedAction("obtain_item", 0, 4, "minecraft:iron_ingot")), "gametest-multitask-obtain-wait", () -> false);
        long id = link.tasks().getFirst().id();
        boolean[] waited = {false}, oreMined = {false};
        h.onEachTick(() -> {
            waited[0] |= status(link, id) == CompanionTaskQueue.Status.WAITING;
            oreMined[0] |= ore.stream().anyMatch(pos -> h.getBlockState(pos).isAir()) || count(companion, Items.RAW_IRON) > 4;
        });
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone(), "Plan still running: " + status(link, id) + " " + companion.getLastTaskResult());
            h.assertTrue(waited[0] && !oreMined[0], "waited=" + waited[0] + " oreMined=" + oreMined[0] + " report=" + plan.join());
            h.assertTrue(status(link, id) == CompanionTaskQueue.Status.COMPLETED, "Not completed: " + plan.join());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 4 && count(companion, Items.RAW_IRON) == 0, "Bag: " + companion.getStoredItemsSummary());
            h.assertTrue(ore.stream().noneMatch(pos -> h.getBlockState(pos).isAir()), "An ore was mined");
            link.close();
        });
    }

    /** The step that ran ahead fails (no pickaxe): the batch is still collected first, the smelt line comes before the one stop line, and the meat is in the bag. */
    @GameTest(template = "tall_tree", batch = "multitask_failed_after_park", timeoutTicks = 6000)
    public static void failedStepAfterParkStillCollects(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        for (int y = 1; y <= 3; y++) for (int x = 22; x <= 24; x++) h.setBlock(new BlockPos(x, y, 12), Blocks.STONE);
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var plan = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef"), new PlannedAction("gather_stone", 0, 8)),
                "gametest-multitask-failed-after-park", () -> false);
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone(), "Plan still running: " + status(link, "smelt_items") + " " + companion.getLastTaskResult());
            String report = plan.join();
            String[] lines = report.split("\n");
            h.assertTrue(lines.length >= 2 && lines[0].startsWith("Переплавил"), "The smelt line is not first: " + report);
            h.assertTrue(lines[lines.length - 1].startsWith("План остановлен:") && report.indexOf("План остановлен:") == report.lastIndexOf("План остановлен:"),
                    "Not one stop line, last: " + report);
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 8, "Bag: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(), "Furnace or batch left over: " + report);
            link.close();
        });
    }

    /** A later request ends with a «wait» step while the batch cooks: that pause is no «стой», so the first request still collects its product. */
    @GameTest(template = "tall_tree", batch = "multitask_plan_wait", timeoutTicks = 4800)
    public static void planWaitStepDoesNotStallAParkedCollect(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        for (int y = 1; y <= 5; y++) { h.setBlock(new BlockPos(20, y, 12), Blocks.OAK_LOG); h.setBlock(new BlockPos(20, y, 20), Blocks.OAK_LOG); }
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-plan-wait-a", () -> false);
        var wood = link.submit(List.of(new PlannedAction("gather_wood", 0, 2), new PlannedAction("wait", 1, 1)), "gametest-multitask-plan-wait-b", () -> false);
        h.succeedWhen(() -> {
            h.assertTrue(wood.isDone() && smelt.isDone(), "Requests still running: smelt=" + status(link, "smelt_items") + " wood=" + status(link, "gather_wood")
                    + " halted=" + companion.autonomy().halted() + " " + companion.getLastTaskResult());
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED && status(link, "gather_wood") == CompanionTaskQueue.Status.COMPLETED,
                    "Not both completed: " + smelt.join() + " | " + wood.join());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && !companion.autonomy().halted(), "Bag: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(), "Furnace or batch left over");
            link.close();
        });
    }

    /** A «стой» that does not cancel the request (a server command) holds the collection back only for a while; then the product is fetched and the report is honest. */
    @GameTest(template = "tall_tree", batch = "multitask_halt_bounded", timeoutTicks = 3600)
    public static void serverStopWhileWaitingIsBounded(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        link.setHaltedCapTicks(100);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-halt-bounded", () -> false);
        long id = link.tasks().getFirst().id();
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (stopped[0] || status(link, id) != CompanionTaskQueue.Status.WAITING) return;
            stopped[0] = true;
            CompanionEvents.executeAction(owner(h, companion), "stop", "", 1);
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && companion.autonomy().halted(), "The stop was not given");
            h.assertTrue(smelt.isDone(), "Still waiting: " + status(link, id) + " " + companion.getLastTaskResult());
            h.assertTrue(status(link, id) == CompanionTaskQueue.Status.COMPLETED && smelt.join().startsWith("Переплавил"), "Not reported: " + smelt.join());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && companion.autonomy().halted(), "Bag or hold: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(), "Furnace or batch left over");
            link.close();
        });
    }

    /** The run-ahead step is stopped by a server «стоп» (the plan itself is not cancelled): the smelt still gets its line in the report and stays in its furnace. */
    @GameTest(template = "tall_tree", batch = "multitask_stopped_ahead", timeoutTicks = 3600)
    public static void stoppedRunAheadStepStillReportsTheSmelt(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        for (int y = 1; y <= 3; y++) for (int x = 22; x <= 24; x++) h.setBlock(new BlockPos(x, y, 12), Blocks.STONE);
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var plan = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef"), new PlannedAction("gather_stone", 0, 8)),
                "gametest-multitask-stopped-ahead", () -> false);
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (stopped[0] || plan.isDone() || !companion.isWorking() || link.tasks().isEmpty() || link.tasks().getFirst().step() < 1) return;
            stopped[0] = true;
            CompanionEvents.executeAction(owner(h, companion), "stop", "", 1);
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && plan.isDone(), "Plan still running: " + status(link, "smelt_items") + " " + companion.getLastTaskResult());
            String[] lines = plan.join().split("\n");
            h.assertTrue(lines.length >= 2 && lines[0].startsWith("Заложил") && lines[0].endsWith("Продукт остался в печи."), "The smelt line is missing: " + plan.join());
            h.assertTrue(lines[lines.length - 1].startsWith("План остановлен:") && plan.join().indexOf("План остановлен:") == plan.join().lastIndexOf("План остановлен:"),
                    "Not one stop line, last: " + plan.join());
            h.assertTrue(container(h, furnace).getItem(0).getCount() + container(h, furnace).getItem(2).getCount() > 0, "The furnace was emptied by the stop");
            link.close();
        });
    }

    /** A: three raw iron cook about 30 seconds, so the batch parks; B: three 20-second waits beside the furnace. The cooked batch is fetched at the boundary after it is ready, before B is over. */
    @GameTest(template = "tall_tree", batch = "multitask_ready_near", timeoutTicks = 3600)
    public static void readyFurnaceNearIsCollectedBetweenSteps(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 3, "minecraft:raw_iron")), "gametest-multitask-near-a", () -> false);
        var waits = link.submit(List.of(new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1)),
                "gametest-multitask-near-b", () -> false);
        boolean[] collectedFirst = {false}, readyWhileWaiting = {false};
        h.onEachTick(() -> {
            var batch = batchState(companion);
            readyWhileWaiting[0] |= batch != null && batch.status() == CompanionFurnaceBatches.Status.READY && !waits.isDone() && !smelt.isDone();
            collectedFirst[0] |= smelt.isDone() && !waits.isDone();
        });
        h.succeedWhen(() -> {
            h.assertTrue(smelt.isDone() && waits.isDone(), "Requests still running: smelt=" + status(link, "smelt_items") + " waits=" + status(link, "wait") + " " + companion.getLastTaskResult());
            h.assertTrue(readyWhileWaiting[0], "The batch was never ready while the waits ran");
            h.assertTrue(collectedFirst[0], "The cooked iron waited for the end of the other request");
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED && status(link, "wait") == CompanionTaskQueue.Status.COMPLETED,
                    "Not both completed: " + smelt.join() + " | " + waits.join());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 3 && container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(), "Bag: " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /** Same, but the first request takes him 26 blocks from the furnace: the cooked batch waits there until that request is over. */
    @GameTest(template = "tall_tree", batch = "multitask_ready_far", timeoutTicks = 4800)
    public static void readyFurnaceFarWaitsForTheEndOfTheRequest(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(1, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        BlockPos farSpot = h.absolutePos(new BlockPos(30, 1, 16));
        owner(h, companion).teleportTo(farSpot.getX() + 0.5, farSpot.getY(), farSpot.getZ() + 0.5);
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 3, "minecraft:raw_iron")), "gametest-multitask-far-a", () -> false);
        var away = link.submit(List.of(new PlannedAction("come", 0, 1), new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1)),
                "gametest-multitask-far-b", () -> false);
        boolean[] collectedFirst = {false}, readyWhileFar = {false};
        h.onEachTick(() -> {
            var batch = batchState(companion);
            boolean far = Math.abs(companion.getX() - h.absolutePos(furnace).getX()) > CompanionAutonomy.LEASH;
            readyWhileFar[0] |= batch != null && batch.status() == CompanionFurnaceBatches.Status.READY && far && !away.isDone() && !smelt.isDone();
            collectedFirst[0] |= smelt.isDone() && !away.isDone();
        });
        h.succeedWhen(() -> {
            h.assertTrue(smelt.isDone() && away.isDone(), "Requests still running: smelt=" + status(link, "smelt_items") + " away=" + status(link, "come") + " " + companion.getLastTaskResult());
            h.assertTrue(readyWhileFar[0], "The batch was never ready while he was far from it");
            h.assertTrue(!collectedFirst[0], "The far furnace was visited before the other request was over");
            h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.COMPLETED && status(link, "come") == CompanionTaskQueue.Status.COMPLETED,
                    "Not both completed: " + smelt.join() + " | " + away.join());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 3 && container(h, furnace).isEmpty(), "Bag: " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /**
     * Two cooked batches at one checkpoint: the far one was parked first (the lower task id) and is ready too, but only the near one
     * is fetched before the running request is over.
     */
    @GameTest(template = "tall_tree", batch = "multitask_ready_near_and_far", timeoutTicks = 5400)
    public static void onlyTheNearOfTwoReadyBatchesIsFetchedBetweenSteps(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos farFurnace = new BlockPos(2, 1, 16), nearFurnace = new BlockPos(30, 1, 16);
        h.setBlock(farFurnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.RAW_COPPER, 3)); companion.setItem(2, new ItemStack(Items.COAL, 4));
        CompanionParkLink link = link(h, companion);
        List<CompletableFuture<String>> requests = new ArrayList<>();
        requests.add(link.submit(List.of(new PlannedAction("smelt_items", 0, 3, "minecraft:raw_iron")), "gametest-multitask-nf-far", () -> false));
        boolean[] readyTogether = {false}, nearFirst = {false}, farEarly = {false};
        h.onEachTick(() -> {
            if (requests.size() < 3) return;
            var far = batchState(companion, "minecraft:raw_iron");
            var near = batchState(companion, "minecraft:raw_copper");
            boolean waiting = !requests.get(2).isDone();
            readyTogether[0] |= waiting && far != null && near != null && far.status() == CompanionFurnaceBatches.Status.READY && near.status() == CompanionFurnaceBatches.Status.READY;
            nearFirst[0] |= waiting && requests.get(1).isDone();
            farEarly[0] |= waiting && requests.get(0).isDone();
        });
        h.succeedWhen(() -> {
            if (requests.size() == 1) {
                h.assertTrue(smeltStatus(link, "minecraft:raw_iron") == CompanionTaskQueue.Status.WAITING, "The far batch is not parked yet: " + companion.getLastTaskResult());
                BlockPos spot = h.absolutePos(new BlockPos(28, 1, 16));
                companion.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                owner(h, companion).teleportTo(spot.getX() - 1.5, spot.getY(), spot.getZ() - 1.5);
                h.setBlock(nearFurnace, Blocks.FURNACE);
                requests.add(link.submit(List.of(new PlannedAction("smelt_items", 0, 3, "minecraft:raw_copper")), "gametest-multitask-nf-near", () -> false));
                requests.add(link.submit(List.of(new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1),
                        new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1), new PlannedAction("wait", 20, 1)), "gametest-multitask-nf-waits", () -> false));
            }
            h.assertTrue(requests.size() == 3 && requests.stream().allMatch(CompletableFuture::isDone), "Requests still running: far=" + smeltStatus(link, "minecraft:raw_iron")
                    + " near=" + smeltStatus(link, "minecraft:raw_copper") + " " + companion.getLastTaskResult());
            h.assertTrue(readyTogether[0], "The two batches were never ready together while the waits ran");
            h.assertTrue(nearFirst[0], "The near batch waited for the end of the other request");
            h.assertTrue(!farEarly[0], "The far batch was fetched before the other request was over");
            h.assertTrue(count(companion, Items.IRON_INGOT) == 3 && count(companion, Items.COPPER_INGOT) == 3 && companion.batches().summaries().isEmpty(), "Bag: " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /** The decision itself: only a cooked batch in his own dimension, within the leash and a few blocks of height, is worth a visit between two steps. */
    @GameTest(template = "empty", batch = "multitask_dimension", timeoutTicks = 100)
    public static void furnaceInAnotherDimensionWaits(GameTestHelper h) {
        BlockPos from = new BlockPos(100, 64, 100);
        var ready = CompanionFurnaceBatches.Status.READY;
        h.assertTrue(CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, new BlockPos(105, 64, 95), false, true), from), "A near cooked furnace is not worth a visit");
        h.assertTrue(CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, new BlockPos(124, 72, 76), false, true), from), "The edge of the zone is refused");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, null, false, false), from), "A furnace in another dimension is visited");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, new BlockPos(105, 64, 95), false, false), from), "Another dimension, even with matching coordinates, is visited");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, null, false, true), from), "A batch without an open furnace is visited");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, new BlockPos(125, 64, 100), false, true), from), "A furnace beyond the leash is visited");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(ready, 3, 3, new BlockPos(100, 73, 100), false, true), from), "A furnace nine blocks up is visited");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(new CompanionFurnaceBatches.State(CompanionFurnaceBatches.Status.WAITING, 1, 3, new BlockPos(105, 64, 95), false, true), from), "A batch still cooking is visited");
        h.assertTrue(!CompanionServerPlanPort.worthAVisit(null, from), "A vanished batch is visited");
        h.succeed();
    }

    /** Records the order of the steps and of the checkpoints the runner opens between them (a pure runner test, no world needed). */
    private static final class CheckpointPort implements CompanionPlanRunner.Port {
        final List<String> calls = new ArrayList<>();
        @Override public String trace() { return "gametest-checkpoint"; }
        @Override public boolean cancelled() { return false; }
        @Override public CompletableFuture<CompanionPlanRunner.State> state(PlannedAction action) { return CompletableFuture.completedFuture(new CompanionPlanRunner.State(false, 0)); }
        @Override public CompletableFuture<CompanionPlanRunner.Attempt> run(PlannedAction action) {
            calls.add(action.type());
            return CompletableFuture.completedFuture(new CompanionPlanRunner.Attempt(CompanionActionResult.ok("Готово."), true));
        }
        @Override public CompletableFuture<Void> pause(int seconds) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Boolean> checkpoint() { calls.add("checkpoint"); return CompletableFuture.completedFuture(false); }
    }
    /** A placed bench or furnace is used from where it was made: the slot is not given away between place_block and the crafts or smelts after it. */
    @GameTest(template = "empty", batch = "multitask_checkpoint_placement", timeoutTicks = 100)
    public static void noCheckpointBetweenPlacementAndItsUse(GameTestHelper h) {
        var port = new CheckpointPort();
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("craft_item", 0, 1, "minecraft:stick"), new PlannedAction("place_block", 0, 1, "minecraft:crafting_table"),
                new PlannedAction("craft_item", 0, 1, "minecraft:wooden_pickaxe"), new PlannedAction("craft_item", 0, 1, "minecraft:wooden_axe")), port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Runner did not finish");
            h.assertTrue(port.calls.equals(List.of("craft_item", "checkpoint", "place_block", "craft_item", "craft_item")), "Wrong checkpoints: " + port.calls);
        });
    }
    /** The same for a placed furnace: every smelt of the run stays closed, and a step that does not use it opens the checkpoint again. */
    @GameTest(template = "empty", batch = "multitask_checkpoint_furnace_run", timeoutTicks = 100)
    public static void noCheckpointInsideAFurnaceRun(GameTestHelper h) {
        var port = new CheckpointPort();
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("place_block", 0, 1, "minecraft:furnace"), new PlannedAction("smelt_items", 0, 1, "minecraft:raw_iron"),
                new PlannedAction("smelt_items", 0, 1, "minecraft:raw_copper"), new PlannedAction("give_items", 0, 1, "minecraft:iron_ingot")), port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Runner did not finish");
            h.assertTrue(port.calls.equals(List.of("place_block", "smelt_items", "smelt_items", "checkpoint", "give_items")), "Wrong checkpoints: " + port.calls);
        });
    }
    /** verify_item counts the bag as the plan left it: no collector fills it in between. */
    @GameTest(template = "empty", batch = "multitask_checkpoint_verify", timeoutTicks = 100)
    public static void noCheckpointBeforeVerify(GameTestHelper h) {
        var port = new CheckpointPort();
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("craft_item", 0, 1, "minecraft:stick"), new PlannedAction("craft_item", 0, 1, "minecraft:wooden_pickaxe"),
                new PlannedAction("verify_item", 0, 1, "minecraft:wooden_pickaxe")), port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Runner did not finish");
            h.assertTrue(port.calls.equals(List.of("craft_item", "checkpoint", "craft_item", "verify_item")), "Wrong checkpoints: " + port.calls);
        });
    }
    /** The line the model reads about his furnaces names the furnace and the progress while the batch is parked, and is gone once the product is collected. */
    @GameTest(template = "tall_tree", batch = "multitask_furnace_line", timeoutTicks = 3600)
    public static void furnaceLineNamesTheParkedBatchAndItsReadiness(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-furnace-line");
        CompanionEntity companion = rig.companion();
        BlockPos at = h.absolutePos(rig.furnace());
        String position = at.getX() + "," + at.getY() + "," + at.getZ();
        boolean[] parked = {false};
        h.onEachTick(() -> {
            if (companion.batches().summaries().isEmpty()) return;
            String line = companion.getFurnacesForAi();
            parked[0] = true;
            h.assertTrue(line.startsWith("в печах:") && line.contains(position) && line.contains("готово") && line.contains("из 4"), "The furnace line is wrong: '" + line + "'");
        });
        h.succeedWhen(() -> {
            String line = companion.getFurnacesForAi();
            h.assertTrue(rig.smelt().isDone(), "Smelt still running: " + line);
            h.assertTrue(parked[0], "The batch was never parked");
            h.assertTrue(line.isEmpty(), "The furnace line outlived the batch: '" + line + "'");
            rig.link().close();
        });
    }
    /** «Отмена» and «отмени…» in the chat are the player's cancel and release the smelt; «стоп» and «стой» only stop him, so the batch is orphaned. Through the word check the chat screen uses. */
    @GameTest(template = "tall_tree", batch = "multitask_chat_cancel", timeoutTicks = 4800)
    public static void chatCancelWordsReleaseTheBatchAndStopWordsOrphanIt(GameTestHelper h) {
        for (String word : List.of("Отмена", "Отмени задачу", "отмени"))
            h.assertTrue("companion stop".equals(CompanionLocalCommands.route(word, "Олег")) && isCancel(word), "«" + word + "» is not a cancel");
        for (String word : List.of("Стоп", "Стой", "Остановись"))
            h.assertTrue(CompanionLocalCommands.route(word, "Олег") != null && !isCancel(word), "«" + word + "» is a cancel");
        Rig rig = smeltRig(h, "gametest-multitask-chat-cancel");
        CompanionEntity companion = rig.companion();
        CompanionParkLink link = rig.link();
        long first = link.tasks().getFirst().id();
        BlockPos place = h.absolutePos(rig.furnace());
        long[] second = {-1};
        int[] stage = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(link, first) == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                link.cancelAll(false, isCancel("Отмени задачу"));
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(rig.smelt().isDone() && status(link, first) == CompanionTaskQueue.Status.CANCELLED, "The request did not end: " + status(link, first));
                h.assertTrue(rig.smelt().join().contains(place.toShortString()) && companion.batches().summaries().isEmpty(), "Not released: " + rig.smelt().join() + " " + companion.batches().summaries());
                h.assertTrue(!container(h, rig.furnace()).isEmpty() && count(companion, Items.COOKED_BEEF) == 0, "The product left the furnace: " + companion.getStoredItemsSummary());
                h.setBlock(rig.furnace(), Blocks.AIR); h.setBlock(rig.furnace(), Blocks.FURNACE);
                companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
                link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-chat-stop", () -> false);
                second[0] = link.tasks().stream().mapToLong(CompanionTaskQueue.View::id).filter(id -> id != first).findFirst().orElseThrow();
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(status(link, second[0]) == CompanionTaskQueue.Status.WAITING, "Not parked the second time: " + companion.getLastTaskResult());
                link.cancelAll(false, isCancel("Стоп"));
                stage[0] = 3;
            }
            var batches = companion.batches().summaries();
            h.assertTrue(status(link, second[0]) == CompanionTaskQueue.Status.CANCELLED && batches.size() == 1 && batches.getFirst().orphan(), "The stop did not orphan the batch: " + batches);
            link.close();
        });
    }

    private static boolean isCancel(String word) {
        return CompanionLocalCommands.PLAYER_CANCEL.equals(CompanionLocalCommands.cancelReason(word, CompanionLocalCommands.route(word, "Олег")));
    }

    /** The companion is saved and read back while a request awaits its batch (a respawn, a reloaded chunk): the batch stays the request's, he does not collect it from under it as an orphan. */
    @GameTest(template = "tall_tree", batch = "multitask_reload_owned", timeoutTicks = 4800)
    public static void reloadedCompanionLeavesAnAwaitedBatchToItsTask(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-reload-owned");
        CompanionEntity companion = rig.companion();
        boolean[] reloaded = {false}, autonomyWent = {false};
        h.onEachTick(() -> { if (reloaded[0] && (companion.autonomy().running() || !companion.autonomy().said().isEmpty())) autonomyWent[0] = true; });
        h.succeedWhen(() -> {
            if (!reloaded[0]) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                companion.readAdditionalSaveData(saved);
                h.assertTrue(companion.batches().summaries().size() == 1, "The batch was lost on reading: " + companion.batches().summaries());
                reloaded[0] = true;
            }
            h.assertTrue(rig.smelt().isDone(), "The request is still waiting: " + companion.getLastTaskResult());
            String report = rig.smelt().join();
            h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.COMPLETED && !report.contains("Партии плавки нет") && !report.contains("План остановлен"), "The request failed: " + report);
            h.assertTrue(!autonomyWent[0], "He collected it as an orphan: " + companion.autonomy().said());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, rig.furnace()).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected once: " + companion.getStoredItemsSummary() + " " + companion.batches().summaries());
            rig.link().close();
        });
    }
    private static int logs(CompanionEntity companion) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++)
            if (BuiltInRegistries.ITEM.getKey(companion.getItem(slot).getItem()).getPath().endsWith("_log")) count += companion.getItem(slot).getCount();
        return count;
    }
}
