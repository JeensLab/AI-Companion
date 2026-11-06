package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
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

import java.util.List;

/** The batch against the world: saves and old saves, another dimension and damaged tags. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOrphanWorldTests {
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
}
