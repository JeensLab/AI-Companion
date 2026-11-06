package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.CompanionMultitaskSupport.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** An orphaned furnace batch and the player's stop: when he collects it by himself and when he leaves it. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOrphanStopTests {
    /** «Стоп» ends the request without the player cancelling it: the batch is orphaned, not released, and the product stays where it is while it still cooks. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan", timeoutTicks = 2400)
    public static void stoppedPlanOrphansItsBatchAndItIsCollected(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 8)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 8, "minecraft:beef")), "gametest-multitask-orphan", () -> false);
        long[] stoppedAt = {-1};
        h.succeedWhen(() -> {
            if (stoppedAt[0] < 0) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                link.cancelAll(false);
                stoppedAt[0] = h.getTick();
            }
            h.assertTrue(smelt.isDone() && status(link, "smelt_items") == CompanionTaskQueue.Status.CANCELLED, "The request did not end: " + status(link, "smelt_items"));
            var batches = companion.batches().summaries();
            h.assertTrue(batches.size() == 1 && batches.getFirst().orphan(), "The batch is not an orphan: " + batches);
            h.assertTrue(h.getTick() - stoppedAt[0] >= 120, "Looking again a little later");
            h.assertTrue(!container(h, furnace).isEmpty() && count(companion, Items.COOKED_BEEF) == 0 && !companion.isWorking(),
                    "The furnace was touched: " + companion.getStoredItemsSummary());
            link.close();
        });
    }

    /** A plan that ended without the player's cancel leaves its smelt an orphan; with nothing to do he goes for it himself, with deeds switched off too, and tells the result. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_idle", timeoutTicks = 3600)
    public static void orphanIsCollectedWhenIdle(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-orphan-idle", () -> false);
        boolean[] stopped = {false}, orphaned = {false};
        h.onEachTick(() -> { if (stopped[0] && companion.batches().summaries().stream().anyMatch(CompanionFurnaceBatches.Summary::orphan)) orphaned[0] = true; });
        h.succeedWhen(() -> {
            if (!stopped[0]) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                link.cancelAll(false);
                stopped[0] = true;
            }
            h.assertTrue(orphaned[0] && !companion.autonomy().enabled(), "The batch was not an orphan or deeds were on");
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            h.assertTrue(!companion.autonomy().said().isEmpty(), "No report to the player: " + companion.getLastTaskResult());
            // Issue 61: the report is said aloud, so it tells what he took, not the furnace's place and the whole bag.
            String said = companion.autonomyLine();
            h.assertTrue(said.contains("забрал 4") && !said.contains("сумке") && !said.contains("Печь") && !said.matches("(?s).*\\d+, -?\\d+, -?\\d+.*"),
                    "Not a short report: " + said);
            link.close();
        });
    }

    /** After «стоп» he stands, whatever is waiting for him; a call to follow lets him go for it. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_stop", timeoutTicks = 3600)
    public static void orphanIsNotCollectedAfterStop(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-orphan-stop", () -> false);
        long[] readyAt = {-1};
        int[] stage = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                link.cancelAll(false);
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "stop", "", 1).success(), "The stop was refused");
                h.assertTrue(companion.autonomy().halted(), "The stop did not halt him");
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                var batch = batchState(companion);
                h.assertTrue(batch != null && batch.orphan() && batch.status() == CompanionFurnaceBatches.Status.READY, "Orphan not ready yet: " + batch);
                readyAt[0] = h.getTick(); stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(h.getTick() - readyAt[0] >= 160, "Still looking whether he stays");
                h.assertTrue(!companion.isWorking() && companion.batches().summaries().size() == 1 && count(companion, Items.COOKED_BEEF) == 0 && !container(h, furnace).isEmpty(),
                        "He went for it after the stop: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "follow", "", 1).success(), "The call was refused");
                stage[0] = 3;
            }
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, furnace).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected after the call: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            link.close();
        });
    }

    /** «Стой» while the batch cooks: when it is ready he does not walk off to collect it (that would undo the order); the player's cancel still frees the furnace. */
    @GameTest(template = "tall_tree", batch = "multitask_held", timeoutTicks = 3600)
    public static void collectWaitsWhileTheCompanionIsHeld(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        var smelt = link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-held", () -> false);
        long id = link.tasks().getFirst().id();
        long[] readyAt = {-1};
        int[] stage = {0};
        boolean[] collected = {false};
        h.onEachTick(() -> {
            if (readyAt[0] >= 0 && stage[0] < 3 && status(link, id) != CompanionTaskQueue.Status.WAITING) collected[0] = true;
        });
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(link, id) == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "stop", "", 1).success(), "The stop was refused");
                h.assertTrue(companion.autonomy().held(), "The stop did not hold him");
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                var batch = batchState(companion);
                h.assertTrue(batch != null && batch.status() == CompanionFurnaceBatches.Status.READY, "Batch not ready yet: " + batch);
                readyAt[0] = h.getTick(); stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(h.getTick() - readyAt[0] >= 160, "Still looking whether he stays");
                h.assertTrue(!collected[0] && status(link, id) == CompanionTaskQueue.Status.WAITING && !companion.isWorking() && !companion.batches().summaries().isEmpty(),
                        "He went to collect while held: " + status(link, id) + " " + companion.getLastTaskResult());
                h.assertTrue(companion.autonomy().held() && count(companion, Items.COOKED_BEEF) == 0, "The hold was undone or the product taken: " + companion.getStoredItemsSummary());
                h.assertTrue(link.cancel(id), "The cancel was refused");
                stage[0] = 3;
            }
            h.assertTrue(smelt.isDone() && status(link, id) == CompanionTaskQueue.Status.CANCELLED, "The cancelled request is still running");
            h.assertTrue(smelt.join().contains(h.absolutePos(furnace).toShortString()) && companion.batches().summaries().isEmpty(), "Not released: " + smelt.join());
            h.assertTrue(container(h, furnace).getItem(2).getCount() == 4, "The product left the furnace");
            link.close();
        });
    }

    /** While a request of the player is running he leaves the cooked orphan alone: the moment between two of its steps (no work order, following) is not his free time. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_queue_busy", timeoutTicks = 3600)
    public static void orphanWaitsForAQueuedRequest(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-orphan-busy");
        CompanionEntity companion = rig.companion();
        CompanionParkLink link = rig.link();
        List<CompletableFuture<String>> plan = new ArrayList<>();
        String[] violation = {null};
        h.onEachTick(() -> {
            if (plan.isEmpty() || plan.getFirst().isDone()) return;
            companion.setFollowing(true);
            if (violation[0] == null && (companion.isWorking() || companion.autonomy().running()))
                violation[0] = "He went for the orphan during the request: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult();
        });
        h.succeedWhen(() -> {
            if (plan.isEmpty()) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                link.cancelAll(false);
                plan.add(link.submit(List.of(new PlannedAction("follow", 0, 1), new PlannedAction("wait", 30, 1), new PlannedAction("wait", 30, 1)), "gametest-multitask-orphan-busy-plan", () -> false));
            }
            h.assertTrue(violation[0] == null, String.valueOf(violation[0]));
            h.assertTrue(plan.getFirst().isDone(), "The request is still running");
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, rig.furnace()).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected after the request: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            link.close();
        });
    }

    /** The player's «стоп» outlives a plan's «wait» step: the orphan is collected only after the player calls him again. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_wait_stop", timeoutTicks = 4800)
    public static void planWaitStepDoesNotLiftAPlayerStopForOrphans(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-orphan-wait-stop");
        CompanionEntity companion = rig.companion();
        CompanionParkLink link = rig.link();
        List<CompletableFuture<String>> plan = new ArrayList<>();
        long[] doneAt = {-1};
        int[] stage = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                link.cancelAll(false);
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "stop", "", 1).success(), "The stop was refused");
                h.assertTrue(companion.autonomy().playerStopped(), "The stop did not stick");
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                var batch = batchState(companion);
                h.assertTrue(batch != null && batch.orphan() && batch.status() == CompanionFurnaceBatches.Status.READY, "Orphan not ready yet: " + batch);
                plan.add(link.submit(List.of(new PlannedAction("wait", 2, 1), new PlannedAction("wait", 2, 1)), "gametest-multitask-orphan-wait-stop-plan", () -> false));
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(plan.getFirst().isDone(), "The plan is still running");
                companion.setFollowing(true);
                doneAt[0] = h.getTick(); stage[0] = 3;
            }
            if (stage[0] == 3) {
                h.assertTrue(h.getTick() - doneAt[0] >= 160, "Still looking whether he stays");
                h.assertTrue(companion.autonomy().playerStopped() && !companion.isWorking() && count(companion, Items.COOKED_BEEF) == 0 && companion.batches().summaries().size() == 1,
                        "The plan's pause lifted the stop: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "follow", "", 1).success(), "The call was refused");
                stage[0] = 4;
            }
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, rig.furnace()).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected after the call: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            link.close();
        });
    }
}
