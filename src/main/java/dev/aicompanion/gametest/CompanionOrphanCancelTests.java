package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.CompanionMultitaskSupport.*;

import java.util.List;

/** A collection cut short by a cancel, a failure or a timeout: the batch, the furnace and the word said about it. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOrphanCancelTests {
    /** The collection is cut short without the player: the product still in the furnaces becomes an orphan again and is collected, not forgotten. */
    @GameTest(template = "tall_tree", batch = "multitask_cancelled_collect", timeoutTicks = 3600)
    public static void cancelledCollectBecomesAnOrphan(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-cancelled-collect", () -> false);
        long id = link.tasks().getFirst().id();
        boolean[] parked = {false}, cut = {false}, orphaned = {false};
        h.onEachTick(() -> {
            if (!companion.batches().summaries().isEmpty()) { if (!parked[0]) awayFromFurnace(h, companion); parked[0] = true; }
            // His own collection of the orphan can begin in the very tick it becomes one, so the batch is then in his hands, not in the list.
            if (cut[0]) { if (companion.autonomy().running() || companion.batches().summaries().stream().anyMatch(CompanionFurnaceBatches.Summary::orphan)) orphaned[0] = true; }
            else if (parked[0] && companion.batches().summaries().isEmpty() && status(link, id) == CompanionTaskQueue.Status.RUNNING && companion.isWorking()) {
                link.cancelAll(false);
                cut[0] = true;
                if (companion.batches().summaries().stream().anyMatch(CompanionFurnaceBatches.Summary::orphan)) orphaned[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(cut[0], "The collection never started: " + status(link, id) + " " + companion.getLastTaskResult());
            h.assertTrue(orphaned[0], "The cut collection left no orphan behind");
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected afterwards: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            link.close();
        });
    }

    /** The player cancels while he walks to the furnace to collect: the batch is already taken back from the server, yet the report still says where the product is. */
    @GameTest(template = "tall_tree", batch = "multitask_cancel_collect", timeoutTicks = 3600)
    public static void cancelDuringCollectNamesTheFurnace(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-cancel-collect", () -> false);
        long id = link.tasks().getFirst().id();
        boolean[] parked = {false}, asked = {false};
        h.onEachTick(() -> {
            if (!companion.batches().summaries().isEmpty()) parked[0] = true;
            else if (parked[0] && !asked[0] && status(link, id) == CompanionTaskQueue.Status.RUNNING && companion.isWorking()) { asked[0] = link.cancel(id); }
        });
        h.succeedWhen(() -> {
            h.assertTrue(asked[0], "The collection never started: " + status(link, id) + " " + companion.getLastTaskResult());
            h.assertTrue(smelt.isDone() && status(link, id) == CompanionTaskQueue.Status.CANCELLED, "The request did not end: " + status(link, id));
            h.assertTrue(smelt.join().contains(h.absolutePos(furnace).toShortString()), "The report does not name the furnace: " + smelt.join());
            h.assertTrue(!companion.isWorking() && companion.batches().summaries().isEmpty(), "Work or batch left over");
            link.close();
        });
    }

    /** The player cancels the collection and the queue's word reaches the companion late: until it does he must not take the batch back as an orphan and finish what was cancelled. */
    @GameTest(template = "tall_tree", batch = "multitask_cancel_final", timeoutTicks = 3600)
    public static void playerCancelDuringCollectIsNeverUndone(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-cancel-final");
        CompanionEntity companion = rig.companion();
        CompanionParkLink link = rig.link();
        link.setSettleDelayTicks(100);
        long id = link.tasks().getFirst().id();
        long[] cancelledAt = {-1};
        boolean[] parked = {false};
        String[] violation = {null};
        h.onEachTick(() -> {
            if (!companion.batches().summaries().isEmpty()) { if (!parked[0]) awayFromFurnace(h, companion); parked[0] = true; }
            if (cancelledAt[0] < 0) {
                if (parked[0] && companion.batches().summaries().isEmpty() && status(link, id) == CompanionTaskQueue.Status.RUNNING && companion.isWorking()) {
                    link.cancel(id);
                    cancelledAt[0] = h.getTick();
                }
            } else if (violation[0] == null && h.getTick() - cancelledAt[0] >= 5
                    && (companion.isWorking() || companion.autonomy().running() || count(companion, Items.COOKED_BEEF) > 0))
                violation[0] = "He went on after the cancel at tick +" + (h.getTick() - cancelledAt[0]) + ": " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult();
        });
        h.succeedWhen(() -> {
            h.assertTrue(cancelledAt[0] >= 0, "The collection never started: " + status(link, id) + " " + companion.getLastTaskResult());
            h.assertTrue(violation[0] == null, String.valueOf(violation[0]));
            h.assertTrue(h.getTick() - cancelledAt[0] >= 140, "Looking again a little later");
            h.assertTrue(rig.smelt().isDone() && rig.smelt().join().contains(h.absolutePos(rig.furnace()).toShortString()), "The report does not name the furnace: " + rig.smelt());
            h.assertTrue(companion.batches().summaries().isEmpty() && companion.autonomy().said().isEmpty(), "Batch left or he spoke: " + companion.autonomy().said());
            h.assertTrue(container(h, rig.furnace()).getItem(2).getCount() == 4 && count(companion, Items.COOKED_BEEF) == 0, "The product was taken: " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /** His own collections cut short are counted, and the count outlives a save: the third failure gives the batch up with a word, the product stays in the furnace. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_failures", timeoutTicks = 4800)
    public static void orphanFailuresSurviveASave(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-orphan-failures");
        CompanionEntity companion = rig.companion();
        int[] interrupts = {0};
        boolean[] cut = {false};
        String[] wrong = {null};
        h.onEachTick(() -> {
            if (!cut[0]) {
                if (status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING) { rig.link().cancelAll(false); awayFromFurnace(h, companion); cut[0] = true; }
                return;
            }
            if (interrupts[0] >= 3 || wrong[0] != null || !companion.autonomy().running() || !companion.isWorking()) return;
            companion.stopGathering();
            interrupts[0]++;
            if (failures(companion) != interrupts[0]) { wrong[0] = "Failures after interrupt " + interrupts[0] + ": " + failures(companion); return; }
            if (interrupts[0] == 2) {
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                companion.readAdditionalSaveData(saved);
                if (failures(companion) != 2) wrong[0] = "The count did not survive the save: " + failures(companion);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
            h.assertTrue(interrupts[0] == 3, "Interrupted only " + interrupts[0] + " times: " + companion.getLastTaskResult());
            h.assertTrue(companion.batches().summaries().isEmpty() && companion.autonomy().said().stream().anyMatch(line -> line.contains("Не вышло забрать")),
                    "Not given up with a word: " + companion.batches().summaries() + " " + companion.autonomy().said());
            h.assertTrue(container(h, rig.furnace()).getItem(2).getCount() == 4 && count(companion, Items.COOKED_BEEF) == 0, "The product was taken: " + companion.getStoredItemsSummary());
            rig.link().close();
        });
    }

    /** A plan's own collection ends without the product (it timed out): the plan told the player, so he does not go for the batch again on his own. */
    @GameTest(template = "tall_tree", batch = "multitask_plan_collect_failed", timeoutTicks = 3600)
    public static void planFailedCollectIsNotRetriedByHimself(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        CompanionTaskQueue queue = new CompanionTaskQueue(steps -> false, link);
        List<PlannedAction> plan = List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef"));
        CompanionServerPlanPort[] port = {null};
        queue.submit(plan, task -> {
            port[0] = new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-multitask-plan-collect-failed", task::cancelled, task::progress, task, link);
            task.onCancel(port[0]::cancelWork);
            return CompanionPlanRunner.run(plan, port[0]).thenApply(outcome -> new CompanionTaskQueue.Result(outcome.report(), true));
        });
        long[] batchId = {0}, endedAt = {-1};
        h.onEachTick(() -> {
            queue.tick();
            var batches = companion.batches().summaries();
            if (!batches.isEmpty()) { if (batchId[0] == 0) awayFromFurnace(h, companion); batchId[0] = batches.getFirst().id(); }
            else if (batchId[0] > 0 && endedAt[0] < 0 && companion.isWorking()) {
                companion.stopGathering();
                port[0].collected(batchId[0]);
                endedAt[0] = h.getTick();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(endedAt[0] >= 0, "The collection never started: " + companion.getLastTaskResult());
            h.assertTrue(h.getTick() - endedAt[0] >= 200, "Looking again a little later");
            h.assertTrue(companion.batches().summaries().isEmpty() && !companion.isWorking(), "Batch kept or work going: " + companion.batches().summaries());
            h.assertTrue(container(h, furnace).getItem(2).getCount() == 4 && count(companion, Items.COOKED_BEEF) == 0, "He collected it himself: " + companion.getStoredItemsSummary());
            queue.cancelAll(false);
            link.close();
        });
    }

    /** Far from the furnace, so a collection has a long way to walk and a cancel or an interruption comes before the product is taken. */
    private static void awayFromFurnace(GameTestHelper h, CompanionEntity companion) {
        BlockPos spot = h.absolutePos(new BlockPos(3, 1, 3));
        companion.moveTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5);
        companion.getNavigation().stop();
        owner(h, companion).teleportTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 3.5);
    }
}
