package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.entity.CompanionAutonomy;
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

/** His own deeds that park a batch: the next deed runs, and a batch never counts as a failure. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOrphanDeedTests {
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
}
