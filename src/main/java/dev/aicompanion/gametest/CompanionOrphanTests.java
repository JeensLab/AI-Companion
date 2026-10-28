package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionAutonomy;
import dev.aicompanion.entity.CompanionChunks;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.CompanionMultitaskSupport.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A parked smelt that outlives its request or his deed: orphans, saves, cancels and his own cooking, through the same link the game uses. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOrphanTests {
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

    /** The batch is written with the companion, read back as an orphan with its recipe looked up again, and handed to the one who comes back after his death. */
    @GameTest(template = "tall_tree", batch = "multitask_nbt", timeoutTicks = 4800)
    public static void batchSurvivesNbtRoundTripAndRespawn(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-multitask-nbt", () -> false);
        var owner = owner(h, companion);
        var oldId = companion.getUUID();
        int[] stage = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(link, "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                var summary = companion.batches().summaries().getFirst();
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                h.assertTrue(saved.contains("FurnaceBatches"), "The batch is not written");
                var loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
                loaded.readAdditionalSaveData(saved);
                var back = loaded.batches().summaries();
                h.assertTrue(back.size() == 1 && back.getFirst().id() == summary.id() && back.getFirst().orphan()
                        && back.getFirst().input().equals("minecraft:beef") && back.getFirst().output().equals("minecraft:cooked_beef"), "Batch not read back: " + back);
                var state = loaded.batchState(summary.id());
                h.assertTrue(state != null && state.total() == 4, "Lots not read back: " + state);
                // The world no longer smelts the input: the batch cannot be tended and is not made up.
                saved.getList("FurnaceBatches", 10).getCompound(0).putString("Input", "minecraft:diamond");
                var lost = AICompanionMod.COMPANION.get().create(h.getLevel());
                lost.readAdditionalSaveData(saved);
                h.assertTrue(lost.batches().summaries().isEmpty(), "A batch without a recipe was kept");
                link.cancelAll(false);
                companion.kill();
                stage[0] = 1;
            }
            var back = CompanionEvents.getOwnedCompanion(owner);
            h.assertTrue(back != null && back.isAlive() && !back.getUUID().equals(oldId), "He did not come back");
            h.assertTrue(count(back, Items.COOKED_BEEF) == 4 && container(h, furnace).isEmpty() && back.batches().summaries().isEmpty(),
                    "Not collected by the one who came back: " + back.batches().summaries() + " " + back.getStoredItemsSummary() + " " + back.getLastTaskResult());
            link.close();
        });
    }

    /** A save made before batches existed has no such field: it loads as it always did, with nothing parked. */
    @GameTest(template = "empty", batch = "multitask_old_save", timeoutTicks = 100)
    public static void oldSaveWithoutBatchesLoads(GameTestHelper h) {
        var companion = AICompanionMod.COMPANION.get().create(h.getLevel());
        companion.setItem(0, new ItemStack(Items.COAL, 3));
        CompoundTag saved = new CompoundTag();
        companion.addAdditionalSaveData(saved);
        h.assertTrue(!saved.contains("FurnaceBatches"), "Nothing parked, yet a field was written");
        var loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        loaded.readAdditionalSaveData(saved);
        h.assertTrue(loaded.batches().summaries().isEmpty() && count(loaded, Items.COAL) == 3, "The old save did not load as before: " + loaded.getStoredItemsSummary());
        h.succeed();
    }

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

    /** A cooked orphan in the Overworld waits while the player is in the Nether: no failure counted, no word said, and it is collected when the player is back. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_nether", timeoutTicks = 4800)
    public static void orphanWaitsWhilePlayerIsInAnotherLevel(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-orphan-nether");
        CompanionEntity companion = rig.companion();
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No second dimension to test with");
        var player = owner(h, companion);
        var home = player.serverLevel();
        long[] awayAt = {-1};
        int[] stage = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                rig.link().cancelAll(false);
                player.teleportTo(nether, companion.getX(), 70, companion.getZ(), 0, 0);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                var batch = batchState(companion);
                h.assertTrue(batch != null && batch.orphan() && batch.status() == CompanionFurnaceBatches.Status.READY, "Orphan not ready yet: " + batch);
                awayAt[0] = h.getTick(); stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(h.getTick() - awayAt[0] >= 200, "Still looking whether he waits");
                h.assertTrue(failures(companion) == 0 && companion.autonomy().said().isEmpty() && companion.batches().summaries().size() == 1 && count(companion, Items.COOKED_BEEF) == 0,
                        "He tried or spoke while the player was away: failures=" + failures(companion) + " " + companion.autonomy().said());
                player.teleportTo(home, companion.getX() - 3, companion.getY(), companion.getZ() - 3, 0, 0);
                stage[0] = 3;
            }
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 4 && container(h, rig.furnace()).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected after the player came back: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            rig.link().close();
        });
    }

    /** An orphan whose dimension no longer exists can never be collected: it is released at once with a word, the furnace is left as it is. */
    @GameTest(template = "tall_tree", batch = "multitask_orphan_missing_dimension", timeoutTicks = 3600)
    public static void orphanInAMissingDimensionIsReleased(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-orphan-nowhere");
        CompanionEntity companion = rig.companion();
        boolean[] changed = {false};
        h.succeedWhen(() -> {
            if (!changed[0]) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                rig.link().cancelAll(false);
                h.assertTrue(companion.batches().summaries().size() == 1, "No orphan: " + companion.batches().summaries());
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                saved.getList("FurnaceBatches", 10).getCompound(0).putString("Dimension", "aicompanion:nowhere");
                companion.readAdditionalSaveData(saved);
                changed[0] = true;
            }
            h.assertTrue(companion.batches().summaries().isEmpty(), "Still kept: " + companion.batches().summaries());
            h.assertTrue(companion.autonomy().said().stream().anyMatch(line -> line.contains("измерении, которого больше нет")), "No word said: " + companion.autonomy().said());
            h.assertTrue(!container(h, rig.furnace()).isEmpty() && count(companion, Items.COOKED_BEEF) == 0 && !companion.isWorking(), "The furnace was touched: " + companion.getStoredItemsSummary());
            rig.link().close();
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

    /** His own meat is a smelt that parks like a plan's: he leaves the furnace, the batch is his orphan, no failure is recorded, and he collects it. */
    @GameTest(template = "tall_tree", batch = "multitask_auto_cook", timeoutTicks = 4800)
    public static void autonomousCookParksAndTheNextDeedRuns(GameTestHelper h) {
        CompanionEntity companion = deedBag(h, new ItemStack(Items.BEEF, 3));
        boolean[] cooked = {false}, free = {false};
        h.onEachTick(() -> {
            boolean cooking = companion.autonomy().deedName().contains("пожарить");
            boolean waiting = companion.batches().summaries().stream().anyMatch(CompanionFurnaceBatches.Summary::orphan);
            if (cooking && !waiting) cooked[0] = true;
            if (waiting && !cooking) free[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(cooked[0] && free[0], "Did not run, park and move on: cooked=" + cooked[0] + " free=" + free[0] + " " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 3 && container(h, furnaceAt(h)).isEmpty() && companion.batches().summaries().isEmpty(),
                    "Not collected: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            noFailureOf(h, companion, "COOK");
            companion.autonomy().setEnabled(false);
        });
    }

    /** The same for raw iron; extra iron picked up while the batch cooks does not start a second smelt of the same kind. */
    @GameTest(template = "tall_tree", batch = "multitask_auto_iron", timeoutTicks = 4800)
    public static void autonomousIronSmeltParksAndIsNotChosenTwice(GameTestHelper h) {
        CompanionEntity companion = deedBag(h, new ItemStack(Items.RAW_IRON, 3));
        boolean[] parked = {false}, ended = {false}, second = {false};
        h.onEachTick(() -> {
            boolean waiting = companion.batches().summaries().stream().anyMatch(CompanionFurnaceBatches.Summary::orphan);
            if (waiting && !parked[0]) { parked[0] = true; companion.setItem(6, new ItemStack(Items.RAW_IRON, 1)); }
            boolean smelting = companion.autonomy().deedName().contains("переплавить");
            if (waiting && !smelting) ended[0] = true;
            second[0] |= ended[0] && waiting && smelting;
        });
        h.succeedWhen(() -> {
            h.assertTrue(parked[0] && !second[0], "Did not park, or smelted twice: parked=" + parked[0] + " second=" + second[0]);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 3 && companion.batches().summaries().isEmpty() && container(h, furnaceAt(h)).isEmpty(),
                    "Not collected: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            noFailureOf(h, companion, "SMELT_IRON");
            companion.autonomy().setEnabled(false);
        });
    }

    /**
     * A cooked batch of his own is collected before a new deed begins, even when it is finished just before the second in which that deed
     * would start: wood is wanted, a tree stands there, and yet the first deed is the collection.
     */
    @GameTest(template = "tall_tree", batch = "multitask_ready_first", timeoutTicks = 4800)
    public static void readyOwnBatchIsCollectedBeforeANewDeed(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-ready-first");
        CompanionEntity companion = rig.companion();
        int[] stage = {0, 0};
        String[] first = {""};
        h.onEachTick(() -> {
            // Everything is timed on the same point of the second, so the count of his seconds is exact: S1..S5 follow the enabling, the fifth starts a deed.
            boolean mid = companion.tickCount % 20 == 10;
            if (stage[0] == 1 && mid) { companion.autonomy().setEnabled(true); stage[0] = 2; }
            else if (stage[0] == 2 && mid && ++stage[1] == 4) {
                var oven = container(h, rig.furnace());
                oven.setItem(0, ItemStack.EMPTY); oven.setItem(2, new ItemStack(Items.COOKED_BEEF, 4));
                stage[0] = 3;
            }
            if (stage[0] == 3 && first[0].isEmpty()) first[0] = companion.autonomy().deedName();
        });
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                rig.link().cancelAll(false);
                h.assertTrue(batchState(companion) != null && batchState(companion).orphan(), "The batch is not an orphan");
                stage[0] = 1;
            }
            h.assertTrue(stage[0] == 3 && count(companion, Items.COOKED_BEEF) == 4 && companion.batches().summaries().isEmpty(),
                    "Not collected: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult());
            h.assertTrue(first[0].contains("забрать"), "The first deed after the batch was ready was «" + first[0] + "»");
            companion.autonomy().setEnabled(false);
            rig.link().close();
        });
    }

    /** His collection is on a break (it was refused): the cooked batch waits for the break to be over, and the deeds do not wait for it. */
    @GameTest(template = "tall_tree", batch = "multitask_collect_refused", timeoutTicks = 1500)
    public static void refusedCollectDoesNotBlockTheDeeds(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-collect-refused");
        CompanionEntity companion = rig.companion();
        String[] first = {""};
        boolean[] collectedFirst = {false};
        int[] stage = {0};
        h.onEachTick(() -> {
            if (stage[0] != 2 || !first[0].isEmpty()) return;
            first[0] = companion.autonomy().deedName();
            collectedFirst[0] = count(companion, Items.COOKED_BEEF) > 0;
        });
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                rig.link().cancelAll(false);
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                saved.getCompound("AutonomyBackoff").putInt("COLLECT", CompanionAutonomy.FAILURE_BREAK_TICKS);
                companion.readAdditionalSaveData(saved);
                companion.autonomy().setEnabled(true);
                var oven = container(h, rig.furnace());
                oven.setItem(0, ItemStack.EMPTY); oven.setItem(2, new ItemStack(Items.COOKED_BEEF, 4));
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(batchState(companion) != null && batchState(companion).status() == CompanionFurnaceBatches.Status.READY, "The batch is not ready: " + batchState(companion));
                stage[0] = 2;
            }
            h.assertTrue(!first[0].isEmpty(), "No deed began while the collection was on a break: " + companion.getLastTaskResult());
            h.assertTrue(!first[0].contains("забрать") && !collectedFirst[0] && companion.batches().summaries().size() == 1, "The first deed was «" + first[0] + "»");
            companion.autonomy().setEnabled(false);
            rig.link().close();
        });
    }

    /** A batch that cannot progress here (its furnaces are in another dimension) does not hold back his own cooking. */
    @GameTest(template = "tall_tree", batch = "multitask_stalled_batch", timeoutTicks = 1500)
    public static void batchThatCannotProgressDoesNotBlockCooking(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-stalled-batch");
        CompanionEntity companion = rig.companion();
        String[] first = {""};
        boolean[] changed = {false};
        h.onEachTick(() -> {
            if (changed[0] && first[0].isEmpty()) first[0] = companion.autonomy().deedName();
        });
        h.succeedWhen(() -> {
            if (!changed[0]) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                rig.link().cancelAll(false);
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                saved.getList("FurnaceBatches", 10).getCompound(0).putString("Dimension", "minecraft:the_nether");
                companion.readAdditionalSaveData(saved);
                h.assertTrue(companion.batches().summaries().size() == 1, "The batch is gone: " + companion.batches().summaries());
                companion.setItem(0, new ItemStack(Items.OAK_LOG, CompanionAutonomy.WOOD_STOCK)); companion.setItem(1, new ItemStack(Items.COBBLESTONE, CompanionAutonomy.STONE_STOCK));
                companion.setItem(2, new ItemStack(Items.STONE_PICKAXE)); companion.setItem(3, new ItemStack(Items.COAL, 2));
                companion.setItem(4, new ItemStack(Items.FURNACE)); companion.setItem(5, new ItemStack(Items.BEEF, 3));
                companion.autonomy().setEnabled(true);
                changed[0] = true;
            }
            h.assertTrue(!first[0].isEmpty(), "No deed began: " + companion.getLastTaskResult());
            h.assertTrue(first[0].contains("пожарить"), "The first deed was «" + first[0] + "»");
            companion.autonomy().setEnabled(false);
            rig.link().close();
        });
    }

    /** A cooked batch of his waits for its collection (refused), long past the stall window: it still holds back a second cooking. */
    @GameTest(template = "tall_tree", batch = "multitask_ready_blocks_cook", timeoutTicks = 1500)
    public static void readyButUncollectedBatchStillBlocksASecondCook(GameTestHelper h) {
        Rig rig = smeltRig(h, "gametest-multitask-ready-blocks-cook");
        CompanionEntity companion = rig.companion();
        String[] first = {""};
        boolean[] changed = {false};
        h.onEachTick(() -> {
            if (changed[0] && first[0].isEmpty()) first[0] = companion.autonomy().deedName();
        });
        h.succeedWhen(() -> {
            if (!changed[0]) {
                h.assertTrue(status(rig.link(), "smelt_items") == CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                rig.link().cancelAll(false);
                CompoundTag saved = new CompoundTag();
                companion.addAdditionalSaveData(saved);
                CompoundTag lot = saved.getList("FurnaceBatches", 10).getCompound(0).getList("Lots", 10).getCompound(0);
                lot.putInt("Ready", lot.getInt("Count")); lot.putLong("ProgressTick", -100000L);
                saved.getCompound("AutonomyBackoff").putInt("COLLECT", CompanionAutonomy.FAILURE_BREAK_TICKS);
                companion.readAdditionalSaveData(saved);
                var oven = container(h, rig.furnace());
                oven.setItem(0, ItemStack.EMPTY); oven.setItem(2, new ItemStack(Items.COOKED_BEEF, lot.getInt("Count")));
                companion.setItem(0, new ItemStack(Items.OAK_LOG, CompanionAutonomy.WOOD_STOCK)); companion.setItem(1, new ItemStack(Items.COBBLESTONE, CompanionAutonomy.STONE_STOCK));
                companion.setItem(2, new ItemStack(Items.STONE_PICKAXE)); companion.setItem(3, new ItemStack(Items.COAL, 2));
                companion.setItem(4, new ItemStack(Items.FURNACE)); companion.setItem(5, new ItemStack(Items.BEEF, 3));
                companion.autonomy().setEnabled(true);
                changed[0] = true;
            }
            h.assertTrue(!first[0].isEmpty(), "No deed began: " + companion.getLastTaskResult());
            h.assertTrue(!first[0].contains("пожарить") && companion.batches().summaries().size() == 1, "The first deed was «" + first[0] + "» with " + companion.batches().summaries());
            companion.autonomy().setEnabled(false);
            rig.link().close();
        });
    }

    /** A save with damaged or hand-edited furnace batches loads without a crash and without anything made up; the batches, lots, counts and the chunks they hold stay within bounds. */
    @GameTest(template = "tall_tree", batch = "multitask_bad_nbt", timeoutTicks = 400)
    public static void malformedFurnaceBatchTagsLoadSafely(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setLifestyle(true, false);
        BlockPos near = h.absolutePos(new BlockPos(19, 1, 16)), far = new BlockPos(33_000_000, 1, 0);
        h.assertTrue(!h.getLevel().getWorldBorder().isWithinBounds(far), "The far position is inside the border");
        CompoundTag number = new CompoundTag(); number.putInt(KEY, 7);
        h.assertTrue(loaded(h, number).batches().summaries().isEmpty(), "A batch came out of a number");
        ListTag junk = new ListTag(); junk.add(StringTag.valueOf("batch"));
        CompoundTag notBatches = new CompoundTag(); notBatches.put(KEY, junk);
        h.assertTrue(loaded(h, notBatches).batches().summaries().isEmpty(), "A batch came out of strings");
        h.assertTrue(withBatches(h, savedBatch(1, junk)).batches().summaries().isEmpty(), "A batch came out of lots of the wrong type");
        h.assertTrue(withBatches(h, savedBatch(2, new ListTag())).batches().summaries().isEmpty(), "A batch came out of no lots");
        CompoundTag noId = savedBatch(3, listOf(savedLot(near, 4))); noId.remove("Id");
        h.assertTrue(withBatches(h, noId).batches().summaries().isEmpty(), "A batch came out without an id");
        CompanionEntity huge = withBatches(h, savedBatch(4, listOf(savedLot(near, Integer.MAX_VALUE))));
        h.assertTrue(huge.batches().summaries().size() == 1 && huge.batchState(4).total() <= 64 * 36, "The lot count is not bounded: " + huge.batchState(4));
        ListTag many = new ListTag();
        for (int i = 0; i < 1000; i++) many.add(savedLot(near, 4));
        CompanionEntity crowded = withBatches(h, savedBatch(5, many));
        h.assertTrue(crowded.batches().summaries().size() == 1 && crowded.batchState(5).total() <= 16 * 4, "The lots are not bounded: " + crowded.batchState(5));
        CompoundTag[] flood = new CompoundTag[1000];
        for (int i = 0; i < flood.length; i++) flood[i] = savedBatch(100 + i, listOf(savedLot(near, 4)));
        CompanionEntity swamped = withBatches(h, flood);
        h.assertTrue(!swamped.batches().summaries().isEmpty() && swamped.batches().summaries().size() <= 64, "The batches are not bounded: " + swamped.batches().summaries().size());
        for (CompanionEntity read : List.of(huge, crowded, swamped))
            h.assertTrue(count(read, Items.BEEF) + count(read, Items.COOKED_BEEF) == 0, "An item was made: " + read.getStoredItemsSummary());
        CompoundTag saved = new CompoundTag();
        companion.addAdditionalSaveData(saved);
        saved.put(KEY, listOf(savedBatch(6, listOf(savedLot(near, 4), savedLot(far, 4)))));
        companion.readAdditionalSaveData(saved);
        long farChunk = ChunkPos.asLong(far);
        h.succeedWhen(() -> {
            h.assertTrue(CompanionChunks.ticketCount(companion.getUUID()) > 0, "No lease yet");
            h.assertTrue(!CompanionChunks.leases(companion.getUUID(), farChunk), "A chunk beyond the world border was leased");
            h.assertTrue(CompanionChunks.leases(companion.getUUID(), ChunkPos.asLong(near)), "The chunk of the furnace in the border was not leased");
        });
    }

    private static final String KEY = "FurnaceBatches";

    private static CompoundTag savedLot(BlockPos pos, int count) {
        CompoundTag lot = new CompoundTag();
        lot.putLong("Pos", pos.asLong()); lot.putInt("Count", count); lot.putBoolean("Loaded", true);
        return lot;
    }

    private static CompoundTag savedBatch(long id, ListTag lots) {
        CompoundTag batch = new CompoundTag();
        batch.putLong("Id", id); batch.putString("Dimension", "minecraft:overworld"); batch.putString("Input", "minecraft:beef"); batch.putString("Status", "WAITING");
        batch.put("Lots", lots);
        return batch;
    }

    private static ListTag listOf(CompoundTag... tags) {
        ListTag list = new ListTag();
        for (CompoundTag tag : tags) list.add(tag);
        return list;
    }

    private static CompanionEntity withBatches(GameTestHelper h, CompoundTag... batches) {
        CompoundTag tag = new CompoundTag();
        tag.put(KEY, listOf(batches));
        return loaded(h, tag);
    }

    private static CompanionEntity loaded(GameTestHelper h, CompoundTag saved) {
        var read = AICompanionMod.COMPANION.get().create(h.getLevel());
        read.readAdditionalSaveData(saved);
        return read;
    }

    /** Carries what the first deed needs and nothing that comes before it: logs and stone at their stock, a pickaxe, fuel and a furnace to set down. */
    private static CompanionEntity deedBag(GameTestHelper h, ItemStack raw) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, CompanionAutonomy.WOOD_STOCK)); companion.setItem(1, new ItemStack(Items.COBBLESTONE, CompanionAutonomy.STONE_STOCK));
        companion.setItem(2, new ItemStack(Items.STONE_PICKAXE)); companion.setItem(3, new ItemStack(Items.COAL, 2));
        companion.setItem(4, new ItemStack(Items.FURNACE)); companion.setItem(5, raw);
        companion.autonomy().setEnabled(true);
        return companion;
    }

    private static BlockPos furnaceAt(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 1; y <= 3; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.FURNACE)) return new BlockPos(x, y, z);
        throw new AssertionError("No furnace was set down");
    }

    /** Parking is no failure: no break for the deed and no «не справился» among the things he said. */
    private static void noFailureOf(GameTestHelper h, CompanionEntity companion, String deed) {
        CompoundTag tag = new CompoundTag();
        companion.addAdditionalSaveData(tag);
        h.assertTrue(!tag.getCompound("AutonomyBackoff").contains(deed), deed + " went into a break");
        h.assertTrue(companion.autonomy().said().stream().noneMatch(line -> line.contains("не справился")), "He called it a failure: " + companion.autonomy().said());
    }

    /** Far from the furnace, so a collection has a long way to walk and a cancel or an interruption comes before the product is taken. */
    private static void awayFromFurnace(GameTestHelper h, CompanionEntity companion) {
        BlockPos spot = h.absolutePos(new BlockPos(3, 1, 3));
        companion.moveTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5);
        companion.getNavigation().stop();
        owner(h, companion).teleportTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 3.5);
    }
}
