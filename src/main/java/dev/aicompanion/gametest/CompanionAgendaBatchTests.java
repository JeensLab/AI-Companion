package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import dev.aicompanion.gametest.support.AgendaKit.Fixture;
import dev.aicompanion.gametest.support.CompanionFixtureCleanup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.AgendaKit.*;

import java.util.HashSet;
import java.util.Set;

/** Long goals (CompanionAgenda) and the furnace batches he parks: smelts, orphans and the flint and steel. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaBatchTests {
    // A goal left running after its test would go on working beside the batches that follow: clean up like the other suites.
    @AfterBatch(batch = "agenda_park") public static void afterPark(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_orphan") public static void afterOrphan(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_held_batch") public static void afterHeldBatch(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_blocked_batch") public static void afterBlockedBatch(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_flint") public static void afterFlint(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Raw beef cooks as his own parked batch while the next milestone already runs; the food plan is not started twice. */
    @GameTest(template = "tall_tree", batch = "agenda_park", timeoutTicks = 2000)
    public static void goalSmeltParksAndNextMilestoneRuns(GameTestHelper h) {
        var f = fixture(h); diamondsDone(f);
        f.c.setItem(2, new ItemStack(Items.BEEF, 16)); f.c.setItem(3, new ItemStack(Items.COAL, 6)); f.c.setItem(4, new ItemStack(Items.STICK, 4));
        furnaces(h);
        f.start("prepare_nether", 0);
        Set<Object> batches = new HashSet<>(); long[] smeltWork = {0}; boolean[] seen = {false, false};
        h.onEachTick(() -> {
            for (var batch : f.c.batches().summaries()) {
                if (batches.add(batch.id())) smeltWork[0] = Math.max(smeltWork[0], f.c.activeWorkId());
                if (!batch.orphan() || f.c.batches().state(batch.id()).status() != CompanionFurnaceBatches.Status.WAITING) continue;
                if (f.goal().counter("food")[0] != 0) seen[0] = true;
                // A new work of the goal while the beef still cooks: the next milestone runs beside the parked batch.
                if (CompanionAgenda.running(f.p.getUUID()) && f.c.activeWorkId() > smeltWork[0]) seen[1] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!seen[0], "The food plan ran again while its batch cooked: " + f.goal().counter("food")[0]);
            h.assertTrue(batches.size() == 1, "Not exactly one batch: " + batches.size());
            h.assertTrue(seen[1], "The next milestone did not run beside the parked batch: " + f.goal().state() + " " + f.goal().reason());
        });
    }

    /** A cooked batch of his own, collected at the goal's step boundary (his deeds are shut while the goal works): the goal needs those four ingots to end. */
    @GameTest(template = "tall_tree", batch = "agenda_orphan", timeoutTicks = 3600)
    public static void orphanIsCollectedWhileGoalRuns(GameTestHelper h) {
        var f = fixture(h); f.c.setItem(0, new ItemStack(Items.IRON_INGOT, 25)); f.c.setItem(1, new ItemStack(Items.STICK, 3));
        parkOrphan(f); f.start("gear_up", 1);
        boolean[] deed = {false};
        h.onEachTick(() -> deed[0] |= f.c.autonomy().running());
        h.succeedWhen(() -> {
            gearDone(f);
            h.assertTrue(!deed[0], "A deed of his own ran beside the goal");
            h.assertTrue(f.c.batches().summaries().isEmpty() && count(f.c, Items.RAW_IRON) == 0 && ((net.minecraft.world.Container) h.getBlockEntity(FURNACE)).isEmpty(),
                    "Batch or furnace left over: " + f.c.batches().summaries());
        });
    }

    /** A goal held by «иди за мной» lets go of the batch it parked: he collects it the ordinary way and the goal stays held. */
    @GameTest(template = "tall_tree", batch = "agenda_held_batch", timeoutTicks = 3000)
    public static void heldGoalReleasesItsParkedBatch(GameTestHelper h) {
        var f = fixture(h); parkOrphan(f);
        boolean[] held = {false, false};
        h.onEachTick(() -> {
            if (!held[0] && parked(f)) {
                f.start("gear_up", 1); f.order("follow");
                h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD, "Follow did not hold the goal: " + f.goal().state());
                held[0] = true;
            }
            held[1] |= CompanionAgenda.running(f.p.getUUID());
        });
        h.succeedWhen(() -> {
            h.assertTrue(held[0], "The batch never parked");
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 4 && f.c.batches().summaries().isEmpty(), "Not collected the ordinary way: " + f.c.getStoredItemsSummary());
            h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD && !held[1], "The held goal moved: " + f.goal().state());
        });
    }

    /** A goal that stands blocked («нужна твоя помощь») shuts nothing: the batch he parks after that is collected by his own deed. */
    @GameTest(template = "tall_tree", batch = "agenda_blocked_batch", timeoutTicks = 3000)
    public static void blockedGoalDoesNotFreezeDeeds(GameTestHelper h) {
        var f = fixture(h); diamondsDone(f);
        f.c.setItem(2, new ItemStack(Items.COBBLESTONE, 64)); f.c.setItem(3, new ItemStack(Items.COOKED_BEEF, 16)); f.c.setItem(4, new ItemStack(Items.STICK, 4));
        f.c.setItem(7, new ItemStack(Items.FLINT_AND_STEEL)); f.c.setItem(8, new ItemStack(Items.WATER_BUCKET));
        f.start("defeat_ender_dragon", 0);
        boolean[] seen = {false, false};
        h.onEachTick(() -> {
            if (seen[0]) { seen[1] |= f.c.autonomy().running(); return; }
            if (f.goal().state() != CompanionAgenda.State.BLOCKED) return;
            parkOrphan(f);
            seen[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(seen[0], "The goal never blocked");
            h.assertTrue(seen[1], "The batch was not collected by a deed of his own");
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 4 && f.c.batches().summaries().isEmpty(), "The blocked goal froze his deeds: " + f.c.getStoredItemsSummary() + " " + f.goal().state());
            h.assertTrue(f.goal().state() == CompanionAgenda.State.BLOCKED, "The goal is not blocked any more: " + f.goal().state() + " " + f.goal().reason());
        });
    }

    /** The last stage of prepare_nether is a real flint and steel: the ingot from the bag, the flint dug out of a gravel patch. */
    @GameTest(template = "tall_tree", batch = "agenda_flint", timeoutTicks = 4000)
    public static void netherPreparationEndsWithARealFlintAndSteel(GameTestHelper h) {
        var f = fixture(h); diamondsDone(f);
        f.c.setItem(2, new ItemStack(Items.COBBLESTONE, 64)); f.c.setItem(3, new ItemStack(Items.COOKED_BEEF, 16)); f.c.setItem(4, new ItemStack(Items.STICK, 4));
        f.c.setItem(5, new ItemStack(Items.IRON_INGOT));
        ItemStack shovel = new ItemStack(Items.STONE_SHOVEL);
        shovel.enchant(h.getLevel().registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), 3);
        f.c.setItem(6, shovel);
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.GRAVEL);
        f.start("prepare_nether", 0);
        h.succeedWhen(() -> {
            f.completed();
            h.assertTrue(count(f.c, Items.FLINT_AND_STEEL) == 1 && count(f.c, Items.IRON_INGOT) == 0, "The stage ended without a real flint and steel: " + f.c.getStoredItemsSummary());
            h.assertTrue(f.goal().counter("stock:flint_and_steel")[1] == 0, "A failure was counted for the flint and steel stage");
        });
    }

    private static final BlockPos FURNACE = new BlockPos(19, 1, 16);

    /** Four raw iron in the only furnace cook 800 ticks, so the smelt parks as a batch of his own (a deed's, not a queue task's). */
    private static void parkOrphan(Fixture f) {
        f.h.setBlock(FURNACE, Blocks.FURNACE);
        f.c.setItem(5, new ItemStack(Items.RAW_IRON, 4)); f.c.setItem(6, new ItemStack(Items.COAL, 2));
        f.h.assertTrue(CompanionEvents.executeAction(f.p, "smelt_items", "minecraft:raw_iron", 4).success(), "Smelt order not accepted");
        f.c.markDeedParkable(f.c.activeWorkId());
    }

    private static boolean parked(Fixture f) {
        var batches = f.c.batches().summaries();
        return batches.size() == 1 && batches.getFirst().orphan() && !f.c.hasActiveWorkOrder();
    }
}
