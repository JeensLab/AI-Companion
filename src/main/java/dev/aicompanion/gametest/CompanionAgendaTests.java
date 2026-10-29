package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Long goals (CompanionAgenda): milestones from facts, run as his own work, through reloads, deaths and fights. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaTests {
    private static final Item[] IRON_ARMOR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static long gearClock;

    /** Night is where the probe is hardest: path torches are crafted from the bag when he is free in the dark, and the goal owns that wood. */
    @BeforeBatch(batch = "agenda_gear")
    public static void nightForGear(ServerLevel level) { gearClock = level.getDayTime(); level.setDayTime(18000); }
    @AfterBatch(batch = "agenda_gear")
    public static void endNightForGear(ServerLevel level) { level.setDayTime(gearClock); CompanionFixtureCleanup.afterAtomicTests(level); }

    // A goal left running after its test would go on working beside the batches that follow: clean up like the other suites.
    @AfterBatch(batch = "agenda_fallback") public static void afterFallback(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_reload") public static void afterReload(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_death") public static void afterDeath(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_danger") public static void afterDanger(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_park") public static void afterPark(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_hurt") public static void afterHurt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_siege") public static void afterSiege(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_stop") public static void afterStop(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_blocked") public static void afterBlocked(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_quick") public static void afterQuick(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_interrupt") public static void afterInterrupt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_plan_gap") public static void afterPlanGap(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_trailing") public static void afterTrailing(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_modes") public static void afterModes(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_sort") public static void afterSort(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_harvest") public static void afterHarvest(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_follow") public static void afterFollow(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_stop_hold") public static void afterStopHold(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_cancel") public static void afterCancel(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_new_goal") public static void afterNewGoal(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_defer") public static void afterDefer(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_late") public static void afterLate(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_first_call") public static void afterFirstCall(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_exhausted") public static void afterExhausted(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_death_loot") public static void afterDeathLoot(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_death_place") public static void afterDeathPlace(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_orphan") public static void afterOrphan(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_held_batch") public static void afterHeldBatch(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_blocked_batch") public static void afterBlockedBatch(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** The probe of the plan: raw iron and coal in the bag, a bench and furnaces near; nothing is handed out. */
    @GameTest(template = "tall_tree", batch = "agenda_gear", timeoutTicks = 6000)
    public static void pursueGoalGearUpEquipsIronSetFromRawIron(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.RAW_IRON, 29)); f.c.setItem(1, new ItemStack(Items.COAL, 6)); f.c.setItem(2, new ItemStack(Items.STICK, 3));
        furnaces(h);
        f.start("gear_up", 1);
        boolean[] parkedFree = {false};
        h.onEachTick(() -> {
            // While the batch cooks he is free: no work order holds him, the batch is his own.
            for (var batch : f.c.batches().summaries())
                if (batch.orphan() && f.c.batches().state(batch.id()).status() == CompanionFurnaceBatches.Status.WAITING && !f.c.hasActiveWorkOrder()) parkedFree[0] = true;
        });
        h.succeedWhen(() -> {
            f.completed();
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0 && count(f.c, Items.RAW_IRON) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
            h.assertTrue(parkedFree[0], "The goal's smelt did not park as his own batch");
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_fallback", timeoutTicks = 4000)
    public static void diamondTierFallsBackToIronWhenNoDiamondsNear(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.IRON_PICKAXE)); f.c.setItem(1, new ItemStack(Items.IRON_INGOT, 26)); f.c.setItem(2, new ItemStack(Items.STICK, 1));
        f.start("gear_up", 2);
        h.succeedWhen(() -> {
            f.completed();
            h.assertTrue(f.goal().fallback(), "No fallback to iron");
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.DIAMOND) == 0 && count(f.c, Items.IRON_INGOT) == 0, "Wrong material use: " + f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_reload", timeoutTicks = 3000)
    public static void reloadMidGoalMakesNoSecondPiece(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        boolean[] loaded = {false};
        h.onEachTick(() -> {
            if (!loaded[0] && count(f.c, Items.IRON_PICKAXE) == 1) { loaded[0] = true; f.reload(); }
        });
        h.succeedWhen(() -> {
            f.completed();
            h.assertTrue(loaded[0], "No reload in the middle of the goal");
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots left or a piece made twice: " + f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_death", timeoutTicks = 1600)
    public static void goalSurvivesCompanionDeathAndReconciles(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        UUID[] oldId = {null}; long[] work = {0, 0};
        h.onEachTick(() -> {
            if (oldId[0] == null) work[0] = Math.max(work[0], f.c.activeWorkId());
            if (oldId[0] == null && count(f.c, Items.IRON_PICKAXE) == 1) { oldId[0] = f.c.getUUID(); f.c.kill(); }
            CompanionEntity back = CompanionEvents.getOwnedCompanion(f.p);
            if (oldId[0] != null && work[1] == 0 && back != null && !back.getUUID().equals(oldId[0])) work[1] = back.activeWorkId();
        });
        h.succeedWhen(() -> {
            h.assertTrue(oldId[0] != null, "No death in the middle of the goal");
            CompanionEntity back = CompanionEvents.getOwnedCompanion(f.p);
            h.assertTrue(back != null && !back.getUUID().equals(oldId[0]), "He did not come back");
            var goal = f.goal();
            h.assertTrue(goal.deaths() == 1, "The death was not seen by the goal: " + goal.deaths());
            h.assertTrue(goal.state().outstanding() && goal.state() != CompanionAgenda.State.BLOCKED, "The goal ended with the death: " + goal.state() + " " + goal.reason());
            for (String key : new String[]{"materials:iron", "piece:iron:pickaxe", "piece:iron:sword"})
                h.assertTrue(goal.counter(key)[1] == 0, "The death counted as a failure of " + key);
            // The goal took up its work on the companion who came back, with work numbers that go on from his memory.
            h.assertTrue(work[1] > work[0], "No new work after the death, or its number started over: " + work[1] + " <= " + work[0]);
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_danger", timeoutTicks = 3000)
    public static void dangerPausesAndResumesByItself(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var enemy = h.spawn(EntityType.ZOMBIE, 25, 1, 25); enemy.setNoAi(true); enemy.setInvulnerable(true);
        // His own combat AI may drop a target it cannot hurt; the fight is held for the whole window and a pause seen at any second counts.
        boolean[] paused = {false};
        h.onEachTick(() -> {
            if (h.getTick() >= 15 && h.getTick() < 70 && enemy.isAlive()) f.c.setTarget(enemy);
            if (f.goal() != null && f.goal().state() == CompanionAgenda.State.PAUSED_DANGER) paused[0] = true;
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(paused[0], "The fight did not pause the goal: " + f.goal().state());
            enemy.discard(); f.c.setTarget(null);
        });
        h.succeedWhen(() -> { f.completed(); ironSetWorn(h, f.c); });
    }

    @GameTest(template = "tall_tree", batch = "agenda_quick", timeoutTicks = 200)
    public static void longGoalAndPickaxeOrderExcludeEachOther(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.COBBLESTONE, 6)); f.c.setItem(1, new ItemStack(Items.STICK, 4));
        h.assertTrue(CompanionEvents.executeAction(f.p, "goal_pickaxes", "", 1).status() == CompanionActionResult.Status.ACCEPTED, "Pickaxe order refused");
        var refused = CompanionEvents.executeAction(f.p, "pursue_goal", "gear_up", 1);
        h.assertTrue(refused.status() == CompanionActionResult.Status.BLOCKED && f.goal() == null, "A long goal started over the pickaxe order: " + refused.message());
        h.assertTrue(f.c.goals().state() == dev.aicompanion.entity.CompanionGoals.State.ACTIVE, "Asking for a long goal paused the pickaxe order");
        h.assertTrue(!CompanionEvents.executeAction(f.p, "pursue_goal", "no_such_goal", 1).success(), "Unknown goal accepted");
        h.assertTrue(!refused.message().contains("отмени цель"), "The way out is offered while the pickaxe order runs: " + refused.message());
        CompanionEvents.executeAction(f.p, "stop", "", 1);
        var paused = CompanionEvents.executeAction(f.p, "pursue_goal", "gear_up", 1);
        h.assertTrue(paused.status() == CompanionActionResult.Status.BLOCKED && f.c.goals().state() == dev.aicompanion.entity.CompanionGoals.State.PAUSED_PLAYER
                && paused.message().contains("Скажи «отмени цель»"), "The stopped pickaxe order gives no way out of the refusal: " + paused.message());
        h.succeed();
    }

    @GameTest(template = "tall_tree", batch = "agenda_quick", timeoutTicks = 200)
    public static void activeGoalHoldsDeedsRefusesPickaxesAndNamesWhatHeCannot(GameTestHelper h) {
        var f = fixture(h); f.start("defeat_ender_dragon", 0);
        h.assertTrue(CompanionAgenda.suppressesDeeds(f.c), "An active goal lets his own deeds run");
        h.assertTrue(CompanionEvents.executeAction(f.p, "goal_pickaxes", "", 1).status() == CompanionActionResult.Status.BLOCKED, "Pickaxe order started over a long goal");
        var said = CompanionEvents.executeAction(f.p, "goal_status", "", 1).message();
        h.assertTrue(said.contains("победа над драконом") && said.contains("Пока не умею") && said.contains("портал"), "Status hides what he cannot do: " + said);
        h.succeed();
    }

    @GameTest(template = "tall_tree", batch = "agenda_quick", timeoutTicks = 100)
    public static void agendaRecordSurvivesSaveAndRejectsDamage(GameTestHelper h) {
        var f = fixture(h); f.start("gear_up", 2);
        var server = h.getLevel().getServer();
        CompoundTag saved = CompanionAgendaData.get(server).save(new CompoundTag(), h.getLevel().registryAccess());
        var loaded = CompanionAgendaData.load(saved, h.getLevel().registryAccess()).active(f.p.getUUID());
        h.assertTrue(loaded != null && loaded.id().equals(f.goal().id()) && loaded.playbook().equals("gear_up") && loaded.tier() == 2
                && loaded.state() == CompanionAgenda.State.ACTIVE, "The goal did not survive a save");
        for (int i = 0; i < saved.getList("Active", 10).size(); i++) saved.getList("Active", 10).getCompound(i).putString("Playbook", "summon_items");
        h.assertTrue(CompanionAgendaData.load(saved, h.getLevel().registryAccess()).active(f.p.getUUID()) == null, "A damaged record was loaded");
        h.assertTrue(CompanionAgendaData.load(new CompoundTag(), h.getLevel().registryAccess()).active(f.p.getUUID()) == null, "An old world got a goal");
        h.succeed();
    }

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

    @GameTest(template = "tall_tree", batch = "agenda_hurt", timeoutTicks = 200)
    public static void lowHealthWithoutFoodDoesNotPauseTheGoal(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        boolean[] seen = {false, false};
        h.onEachTick(() -> {
            if (f.c.getHealth() > 4) f.c.setHealth(4);
            seen[0] |= f.goal().state() == CompanionAgenda.State.PAUSED_DANGER;
            seen[1] |= CompanionAgenda.running(f.p.getUUID());
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(!seen[0] && f.goal().state().suppressesDeeds(), "Low health without food paused the goal: " + f.goal().state());
            h.assertTrue(seen[1], "The goal did not run");
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_siege", timeoutTicks = 2000)
    public static void dangerThatNeverPassesBlocksTheGoal(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var enemy = h.spawn(EntityType.ZOMBIE, 25, 1, 25); enemy.setNoAi(true); enemy.setInvulnerable(true);
        h.onEachTick(() -> { if (f.c.getTarget() == null) f.c.setTarget(enemy); });
        h.succeedWhen(() -> {
            var goal = f.goal();
            h.assertTrue(goal.state() == CompanionAgenda.State.BLOCKED && !goal.reason().isBlank(), "An endless fight did not stop the goal: " + goal.state());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_stop", timeoutTicks = 3000)
    public static void neutralEndSpendsNoAttemptAndResumeClearsTheStop(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        int[] phase = {0, 0};
        h.onEachTick(() -> {
            if (phase[0] == 0 && CompanionAgenda.running(f.p.getUUID())) {
                CompanionEvents.executeAction(f.p, "stop", "", 1);
                h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD && f.c.autonomy().playerStopped(), "Stop did not hold the goal: " + f.goal().state());
                phase[0] = 1;
            } else if (phase[0] == 1 && ++phase[1] >= 60) {
                h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD, "The held goal moved: " + f.goal().state());
                for (var milestone : f.goal().book().milestones())
                    h.assertTrue(f.goal().counter(milestone.key())[0] == 0, "Stop spent an attempt of " + milestone.key());
                var resumed = CompanionEvents.executeAction(f.p, "goal_resume", "", 1);
                h.assertTrue(resumed.status() == CompanionActionResult.Status.ACCEPTED && f.goal().state() == CompanionAgenda.State.ACTIVE,
                        "Resume did not restart the goal: " + resumed.message());
                h.assertTrue(!f.c.autonomy().playerStopped(), "Resume left his stop in place");
                phase[0] = 2;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(phase[0] == 2, "No stop and resume");
            f.completed();
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_blocked", timeoutTicks = 300)
    public static void blockedGoalStaysCancellableAndYieldsToPickaxes(GameTestHelper h) {
        var f = fixture(h); diamondsDone(f);
        f.c.setItem(2, new ItemStack(Items.COBBLESTONE, 64)); f.c.setItem(3, new ItemStack(Items.COOKED_BEEF, 16)); f.c.setItem(4, new ItemStack(Items.STICK, 4));
        f.start("prepare_nether", 0);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || f.goal().state() != CompanionAgenda.State.BLOCKED) return;
            done[0] = true;
            h.assertTrue(f.goal().reason().contains("огниво"), "The stop does not name the flint: " + f.goal().reason());
            var pickaxes = CompanionEvents.executeAction(f.p, "goal_pickaxes", "", 1);
            h.assertTrue(pickaxes.status() == CompanionActionResult.Status.ACCEPTED, "A blocked goal held the pickaxe order: " + pickaxes.message());
            CompanionEvents.executeAction(f.p, "goal_resume", "", 1);
            h.assertTrue(f.goal().state() == CompanionAgenda.State.BLOCKED && f.c.goals().outstanding(), "«Продолжай» woke the long goal beside the pickaxe order");
            CompanionEvents.executeAction(f.p, "goal_cancel", "", 1);
            h.assertTrue(f.goal().state() == CompanionAgenda.State.CANCELLED && f.c.goals().outstanding(), "The first cancel did not end the long goal");
            CompanionEvents.executeAction(f.p, "goal_cancel", "", 1);
            h.assertTrue(!f.c.goals().outstanding(), "The second cancel did not end the pickaxe order");
            var none = CompanionEvents.executeAction(f.p, "goal_cancel", "", 1);
            h.assertTrue(!none.success() && none.message().contains("Нет цели"), "A third cancel found a goal: " + none.message());
            h.succeed();
        });
    }

    /** The player's order in the middle of a step: the goal stops at once, the order runs without a spent recovery, the goal comes back alone. */
    @GameTest(template = "tall_tree", batch = "agenda_interrupt", timeoutTicks = 3000)
    public static void playerOrderInterruptsAndGoalResumesItself(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.c.setItem(2, new ItemStack(Items.DIRT, 5)); f.start("gear_up", 1);
        UUID owner = f.p.getUUID();
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), owner, "agenda-interrupt", () -> false);
        AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> plan = new AtomicReference<>();
        int[] ticks = {0};
        boolean[] interrupted = {false};
        String[] handOver = {null};
        h.onEachTick(() -> {
            if (plan.get() == null) {
                if (CompanionAgenda.running(owner) && f.c.hasActiveWorkOrder())
                    plan.set(CompanionPlanRunner.run(List.of(new PlannedAction("give_items", 0, 5, "minecraft:dirt")), port));
                return;
            }
            if (plan.get().isDone()) {
                // The result of a finished work is kept only until the next work starts, and the goal starts many: read it right at the end.
                if (handOver[0] == null) {
                    long work = port.ownedWorkId();
                    handOver[0] = work > 0 && CompanionEvents.getOwnedCompanion(f.p).workResult(work).success() ? "" : "work " + work + ": " + plan.get().join().report();
                }
                return;
            }
            if (f.goal().state() == CompanionAgenda.State.INTERRUPTED) interrupted[0] = true;
            if (++ticks[0] > 3) h.assertTrue(!CompanionAgenda.running(owner), "The goal still ran beside the player's order, tick " + ticks[0]);
        });
        h.succeedWhen(() -> {
            h.assertTrue(plan.get() != null && plan.get().isDone(), "The player's order did not finish");
            var outcome = plan.get().join();
            h.assertTrue(interrupted[0], "The goal never reached INTERRUPTED");
            h.assertTrue(outcome.recoveries() == 0, "The order spent a recovery on the goal's work: " + outcome.report());
            var companion = CompanionEvents.getOwnedCompanion(f.p);
            h.assertTrue(handOver[0] != null && handOver[0].isEmpty(), "The hand-over was not confirmed: " + handOver[0]);
            int tossed = 0;
            for (var item : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, f.p.getBoundingBox().inflate(8)))
                if (item.getItem().is(Items.DIRT)) tossed += item.getItem().getCount();
            for (int i = 0; i < f.p.getInventory().getContainerSize(); i++) if (f.p.getInventory().getItem(i).is(Items.DIRT)) tossed += f.p.getInventory().getItem(i).getCount();
            h.assertTrue(tossed == 5 && count(companion, Items.DIRT) == 0, "The dirt did not reach the player: " + tossed + " / " + companion.getStoredItemsSummary());
            f.completed();
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
            for (var milestone : f.goal().book().milestones())
                h.assertTrue(f.goal().counter(milestone.key())[1] == 0, "A failure was counted for " + milestone.key());
        });
    }

    /** A craft, a pause, a craft and a trailing follow are one request: the goal takes no step in the gaps and comes back when it is over. */
    @GameTest(template = "tall_tree", batch = "agenda_plan_gap", timeoutTicks = 3000)
    public static void goalDoesNotStartBetweenStepsOfAPlayerPlan(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.c.setItem(2, new ItemStack(Items.OAK_PLANKS, 12)); f.start("gear_up", 1);
        var plan = playerPlan(f, CompanionAgenda.State.INTERRUPTED, new PlannedAction("craft_item", 0, 1, "minecraft:chest"), new PlannedAction("wait", 10, 1),
                new PlannedAction("craft_item", 0, 1, "minecraft:crafting_table"), new PlannedAction("follow", 0, 1));
        h.succeedWhen(() -> {
            h.assertTrue(plan.get() != null && plan.get().isDone(), "The player's plan did not finish");
            h.assertTrue(plan.get().join().recoveries() == 0, "The plan spent a recovery on the goal's work: " + plan.get().join().report());
            h.assertTrue(count(f.c, Items.CHEST) == 1 && count(f.c, Items.CRAFTING_TABLE) == 1, "The plan's crafts are missing: " + f.c.getStoredItemsSummary());
            gearDone(f);
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_trailing", timeoutTicks = 3000)
    public static void trailingFollowStillResumes(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.c.setItem(2, new ItemStack(Items.OAK_PLANKS, 8)); f.start("gear_up", 1);
        var plan = playerPlan(f, CompanionAgenda.State.INTERRUPTED, new PlannedAction("craft_item", 0, 1, "minecraft:chest"), new PlannedAction("follow", 0, 1));
        h.succeedWhen(() -> {
            h.assertTrue(plan.get() != null && plan.get().isDone(), "The player's plan did not finish");
            h.assertTrue(f.goal().state() != CompanionAgenda.State.HELD, "A follow after real work held the goal");
            h.assertTrue(count(f.c, Items.CHEST) == 1, "The craft is missing: " + f.c.getStoredItemsSummary());
            gearDone(f);
        });
    }

    /** «Разложи сундуки» is real work (review of #39): the goal steps aside, the player's chests are sorted with nothing lost, and the goal comes back. */
    @GameTest(template = "tall_tree", batch = "agenda_sort", timeoutTicks = 3000)
    public static void sortingInterruptsAndGoalResumes(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var chests = new net.minecraft.world.Container[2];
        for (int i = 0; i < 2; i++) { h.setBlock(new BlockPos(10 + 3 * i, 1, 13), Blocks.CHEST); chests[i] = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10 + 3 * i, 1, 13))); }
        chests[0].setItem(0, new ItemStack(Items.COBBLESTONE, 20)); chests[0].setItem(1, new ItemStack(Items.OAK_LOG, 6)); chests[0].setItem(2, new ItemStack(Items.BREAD, 3));
        chests[1].setItem(0, new ItemStack(Items.OAK_LOG, 10)); chests[1].setItem(1, new ItemStack(Items.COBBLESTONE, 5)); chests[1].setItem(2, new ItemStack(Items.APPLE, 2));
        var plan = playerPlan(f, CompanionAgenda.State.INTERRUPTED, new PlannedAction("sort_chests", 0, 1));
        h.succeedWhen(() -> {
            h.assertTrue(plan.get() != null && plan.get().isDone() && plan.get().join().report().contains("Разложил"), "The sorting did not finish: " + (plan.get() == null || !plan.get().isDone() ? "" : plan.get().join().report()));
            int cobble = 0, logs = 0, food = 0;
            for (var chest : chests) { cobble += chest.countItem(Items.COBBLESTONE); logs += chest.countItem(Items.OAK_LOG); food += chest.countItem(Items.BREAD) + chest.countItem(Items.APPLE); }
            h.assertTrue(cobble == 25 && logs == 16 && food == 5, "The player's things are not all in the chests: " + cobble + " / " + logs + " / " + food);
            gearDone(f);
        });
    }

    /** Review of PR #39: «собери урожай» is real work, so the goal stands aside for it and goes on by itself afterwards. */
    @GameTest(template = "tall_tree", batch = "agenda_harvest", timeoutTicks = 3000)
    public static void harvestInterruptsAndGoalResumes(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        for (int x = 7; x <= 8; x++) for (int z = 7; z <= 8; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, 1, z), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        }
        var plan = playerPlan(f, CompanionAgenda.State.INTERRUPTED, new PlannedAction("harvest_crops", 0, 64, "any"));
        h.succeedWhen(() -> {
            h.assertTrue(plan.get() != null && plan.get().isDone() && plan.get().join().report().contains("Собрал"), "The harvest did not finish: " + (plan.get() == null || !plan.get().isDone() ? "" : plan.get().join().report()));
            h.assertTrue(count(f.c, Items.WHEAT) >= 4, "No wheat in the bag: " + f.c.getStoredItemsSummary());
            gearDone(f);
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_modes", timeoutTicks = 600)
    public static void modesOnlyPlanHolds(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var plan = playerPlan(f, CompanionAgenda.State.HELD, new PlannedAction("follow", 0, 1));
        int[] after = {0};
        h.succeedWhen(() -> {
            h.assertTrue(plan.get() != null && plan.get().isDone(), "The follow plan did not finish");
            h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD && !CompanionAgenda.running(f.p.getUUID()), "The held goal moved: " + f.goal().state());
            h.assertTrue(++after[0] >= 60, "Watching the hold last");
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_follow", timeoutTicks = 3000)
    public static void followHoldsGoalUntilResume(GameTestHelper h) { holdsUntilResume(h, "follow"); }

    @GameTest(template = "tall_tree", batch = "agenda_stop_hold", timeoutTicks = 3000)
    public static void stopHoldsGoal(GameTestHelper h) { holdsUntilResume(h, "stop"); }

    /** «Отмени цель» ends the goal for good, while «стоп», «отмена» and «отмени задачу» stay the stop of the work. */
    @GameTest(template = "tall_tree", batch = "agenda_cancel", timeoutTicks = 600)
    public static void cancelPhraseCancels(GameTestHelper h) {
        String cancel = "companion goal cancel", defer = "companion goal defer", stop = "companion stop";
        String[][] routes = {{"Олег, отмени цель", cancel}, {"забудь про цель", cancel}, {"Олег, забудь про дракона", cancel}, {"отмени эту долгую цель", cancel},
                {"отмени, пожалуйста, цель", cancel}, {"отмени пожалуйста эту большую цель", cancel},
                {"отложи цель", defer}, {"отложи пока нашу цель", defer}, {"вернись к цели", "companion goal resume"}, {"как там цель", "companion goal status"},
                {"отмени и вернись к цели", "companion goal resume"}, {"отмени бег до цели", stop},
                {"продолжай", "companion goal resume"}, {"Олег, продолжай!", "companion goal resume"}, {"давай продолжай", "companion goal resume"},
                {"стоп", stop}, {"Олег, отмени задачу", stop}, {"отмена", stop}};
        for (String[] route : routes) {
            String routed = CompanionLocalCommands.route(route[0], "Олег");
            h.assertTrue(route[1].equals(routed), "«" + route[0] + "» went to " + routed + ", not " + route[1]);
        }
        for (String other : new String[] {"продолжай рассказ", "не продолжай", "Олег, продолжай заниматься своими делами"})
            h.assertTrue(!"companion goal resume".equals(CompanionLocalCommands.route(other, "Олег")), "«" + other + "» resumed the goal");
        for (String negation : new String[] {"не забудь убить дракона", "Олег, не забудь про цель", "не забудь про цель!"}) {
            String routed = CompanionLocalCommands.route(negation, "Олег");
            h.assertTrue(!cancel.equals(routed) && !defer.equals(routed), "«" + negation + "» ended or parked the goal: " + routed);
        }
        h.assertTrue(!CompanionLocalCommands.cancelsPlans(cancel) && !CompanionLocalCommands.cancelsPlans(defer) && CompanionLocalCommands.cancelsPlans(stop),
                "A goal phrase clears the player's queued plans, or a stop does not");
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        int[] ticks = {-1};
        h.onEachTick(() -> {
            if (ticks[0] < 0) {
                if (!f.working()) return;
                var action = CompanionLocalCommands.action(CompanionLocalCommands.route("Олег, отмени цель", "Олег"));
                var result = f.order(action.type());
                h.assertTrue(action.type().equals("goal_cancel") && result.success(), "The cancel was refused: " + action + " " + result.message());
                ticks[0] = 0;
            }
            h.assertTrue(f.goal().state() == CompanionAgenda.State.CANCELLED && !CompanionAgenda.running(f.p.getUUID()) && !f.c.hasActiveWorkOrder(),
                    "The cancelled goal went on: " + f.goal().state());
            if (++ticks[0] >= 100) h.succeed();
        });
    }

    /** A new goal takes the place of the working one, which waits in the one deferred slot and comes back when the new one is cancelled. */
    @GameTest(template = "tall_tree", batch = "agenda_new_goal", timeoutTicks = 3000)
    public static void newGoalDefersOld(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        UUID owner = f.p.getUUID();
        int[] phase = {0};
        h.onEachTick(() -> {
            if (phase[0] != 0 || !f.working()) return;
            var second = f.order("pursue_goal", "defeat_ender_dragon");
            var deferred = CompanionAgendaData.get(h.getLevel().getServer()).deferred(owner);
            h.assertTrue(second.status() == CompanionActionResult.Status.ACCEPTED && second.message().contains("отложил"), "The new goal did not say it deferred the old: " + second.message());
            h.assertTrue(f.goal().playbook().equals("defeat_ender_dragon") && deferred != null && deferred.playbook().equals("gear_up")
                    && deferred.state() == CompanionAgenda.State.HELD && !CompanionAgenda.running(owner), "The old goal was not put aside");
            h.assertTrue(f.order("goal_cancel").message().contains("Отложенная") && f.goal().state() == CompanionAgenda.State.CANCELLED, "The new goal was not cancelled");
            h.assertTrue(f.order("goal_resume").success() && f.goal().playbook().equals("gear_up") && CompanionAgendaData.get(h.getLevel().getServer()).deferred(owner) == null,
                    "The deferred goal did not come back: " + f.goal());
            phase[0] = 1;
        });
        h.succeedWhen(() -> { h.assertTrue(phase[0] == 1, "No new goal"); gearDone(f); });
    }

    /** «Отложи цель» moves the goal to the deferred slot; with a goal already there a second defer is refused and changes nothing. */
    @GameTest(template = "tall_tree", batch = "agenda_defer", timeoutTicks = 3000)
    public static void goalDeferParksRefusesASecondAndResumeRestores(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        UUID owner = f.p.getUUID();
        var data = CompanionAgendaData.get(h.getLevel().getServer());
        int[] phase = {0};
        h.onEachTick(() -> {
            if (phase[0] != 0 || !f.working()) return;
            f.order("follow");
            h.assertTrue(f.c.autonomy().held(), "The follow order did not hold him");
            var first = f.order("goal_defer");
            var parked = data.deferred(owner);
            h.assertTrue(first.success() && data.active(owner) == null && parked != null && parked.state() == CompanionAgenda.State.HELD
                    && !CompanionAgenda.running(owner) && !f.c.hasActiveWorkOrder(), "The goal was not put aside: " + first.message());
            h.assertTrue(f.c.autonomy().held(), "«Отложи цель» undid the player's follow order");
            f.start("defeat_ender_dragon", 0);
            var dragon = f.goal();
            var second = f.order("goal_defer");
            h.assertTrue(!second.success() && second.message().contains("Уже отложена") && data.active(owner) == dragon && dragon.state().outstanding() && data.deferred(owner) == parked,
                    "A second defer changed something or was not refused: " + second.message());
            f.order("goal_cancel");
            h.assertTrue(f.order("goal_resume").success() && f.goal() == parked && data.deferred(owner) == null && parked.state() == CompanionAgenda.State.ACTIVE, "Resume did not restore the deferred goal");
            phase[0] = 1;
        });
        h.succeedWhen(() -> { h.assertTrue(phase[0] == 1, "No defer"); gearDone(f); });
    }

    /** A step that timed out late must not cut the order of the player or the goal's next step that has taken the companion since. */
    @GameTest(template = "tall_tree", batch = "agenda_late", timeoutTicks = 200)
    public static void lateTimeoutDoesNotStopOtherWork(GameTestHelper h) {
        var f = fixture(h); ingots(f, h);
        h.assertTrue(f.order("craft_item", "minecraft:iron_pickaxe").success() && f.c.hasActiveWorkOrder(), "No work to protect: " + f.c.getLastTaskResult());
        long work = f.c.activeWorkId();
        h.assertTrue(work > 0, "The work has no id");
        h.assertTrue(!CompanionServerPlanPort.stopOwnWork(f.c, work + 1000) && !CompanionServerPlanPort.stopOwnWork(f.c, 0) && f.c.activeWorkId() == work,
                "A late timeout of another work stopped this one");
        h.assertTrue(CompanionServerPlanPort.stopOwnWork(f.c, work) && f.c.activeWorkId() != work, "The work's own timeout did not stop it");
        h.succeed();
    }

    /** The queue's first call reaches the server before any order: the goal's step must stop there, with no executeAction made. */
    @GameTest(template = "tall_tree", batch = "agenda_first_call", timeoutTicks = 600)
    public static void firstCallCancelsTheGoalRun(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        UUID owner = f.p.getUUID();
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), owner, "agenda-first-call", () -> false);
        int[] ticks = {0};
        h.onEachTick(() -> {
            if (ticks[0] == 0) {
                if (!(CompanionAgenda.running(owner) && f.c.hasActiveWorkOrder())) return;
                var action = new PlannedAction("give_items", 0, 5, "minecraft:dirt");
                port.progress(List.of(action), 0);
                port.state(action);
            }
            if (++ticks[0] == 4) { h.assertTrue(!CompanionAgenda.running(owner), "The goal's step ran on after the queue's first call"); h.succeed(); }
        });
    }

    /** «Продолжай» is a fresh start: a milestone that spent all its attempts gets them back instead of the goal refusing again. */
    @GameTest(template = "tall_tree", batch = "agenda_exhausted", timeoutTicks = 4000)
    public static void resumeResetsAnExhaustedMilestone(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        int attemptLimit = 12;
        for (var milestone : f.goal().book().milestones()) f.goal().counter(milestone.key())[0] = attemptLimit;
        int[] resumes = {0};
        h.onEachTick(() -> {
            if (f.goal().state() != CompanionAgenda.State.BLOCKED) return;
            h.assertTrue(f.goal().reason().contains("попытки исчерпаны"), "Blocked for another reason: " + f.goal().reason());
            var resumed = f.order("goal_resume");
            h.assertTrue(resumed.status() == CompanionActionResult.Status.ACCEPTED && f.goal().state() == CompanionAgenda.State.ACTIVE, "Resume refused: " + resumed.message());
            long fresh = f.goal().book().milestones().stream().filter(milestone -> f.goal().counter(milestone.key())[0] == 0).count();
            h.assertTrue(++resumes[0] > 1 || fresh == 1, "The first resume reset " + fresh + " milestones, not the one it goes on with");
        });
        h.succeedWhen(() -> { h.assertTrue(resumes[0] >= 1, "The goal never spent its attempts"); gearDone(f); });
    }

    /** He dies in the middle of the goal: what fell from him is fetched by the one who comes back, picked up for real, and the goal ends with exact counts. */
    @GameTest(template = "tall_tree", batch = "agenda_death_loot", timeoutTicks = 3600)
    public static void deathPicksUpOwnDrops(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        UUID[] oldId = {null}; int[] fallen = {0};
        h.onEachTick(() -> {
            if (oldId[0] != null || count(f.c, Items.IRON_PICKAXE) != 1) return;
            oldId[0] = f.c.getUUID(); f.c.kill();
            fallen[0] = belongingsOnGround(h, f.p.getUUID());
        });
        h.succeedWhen(() -> {
            h.assertTrue(oldId[0] != null && fallen[0] > 0, "His things did not fall tagged as the owner's: " + fallen[0]);
            var back = CompanionEvents.getOwnedCompanion(f.p);
            h.assertTrue(back != null && !back.getUUID().equals(oldId[0]), "He did not come back");
            gearDone(f);
            h.assertTrue(f.goal().deaths() == 1 && f.goal().deathPlace() == null, "Death or place not settled: " + f.goal().deaths() + " " + f.goal().deathPlace());
            h.assertTrue(belongingsOnGround(h, f.p.getUUID()) == 0, "His things are still on the ground");
        });
    }

    /** The place of a death is tried once: it survives a save, nothing found there is said, and the goal does not wait for it. */
    @GameTest(template = "tall_tree", batch = "agenda_death_place", timeoutTicks = 3000)
    public static void deathPlaceIsTriedOnceAndGoalGoesOn(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var server = h.getLevel().getServer();
        BlockPos place = h.absolutePos(new BlockPos(3, 1, 3));
        CompanionAgenda.died(server, f.p.getUUID(), h.getLevel().dimension(), place);
        var saved = CompanionAgendaData.get(server).save(new CompoundTag(), h.getLevel().registryAccess());
        var loaded = CompanionAgendaData.load(saved, h.getLevel().registryAccess()).active(f.p.getUUID());
        h.assertTrue(loaded != null && place.equals(loaded.deathPlace()), "The death place did not survive a save: " + (loaded == null ? null : loaded.deathPlace()));
        h.succeedWhen(() -> {
            gearDone(f);
            h.assertTrue(f.goal().deathPlace() == null, "The place was kept after its one try: " + f.goal().deathPlace());
            h.assertTrue(f.c.autonomy().said().stream().filter(line -> line.contains("Мои вещи остались там")).count() == 1, "Not said once: " + f.c.autonomy().said());
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
        f.start("prepare_nether", 0);
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

    /** A mode order or a stop holds the goal (it is not cancelled, nothing restarts it) until the player says «продолжай». */
    private static void holdsUntilResume(GameTestHelper h, String order) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        int[] phase = {0, 0};
        h.onEachTick(() -> {
            if (phase[0] == 0 && f.working()) {
                f.order(order);
                phase[0] = 1;
            } else if (phase[0] == 1) {
                h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD && !CompanionAgenda.running(f.p.getUUID()) && !f.c.hasActiveWorkOrder(),
                        order + " did not hold the goal and stop its work: " + f.goal().state());
                if (++phase[1] < 60) return;
                var resumed = f.order("goal_resume");
                h.assertTrue(resumed.status() == CompanionActionResult.Status.ACCEPTED && f.goal().state() == CompanionAgenda.State.ACTIVE, "Resume did not restart the goal: " + resumed.message());
                phase[0] = 2;
            }
        });
        h.succeedWhen(() -> { h.assertTrue(phase[0] == 2, "No hold and resume"); gearDone(f); });
    }

    /** Starts the player's plan once the goal's step runs; from the fourth tick on the goal holds no run and stays in {@code during}. */
    private static AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> playerPlan(Fixture f, CompanionAgenda.State during, PlannedAction... steps) {
        var plan = new AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>>();
        UUID owner = f.p.getUUID();
        int[] ticks = {0};
        f.h.onEachTick(() -> {
            if (plan.get() == null) {
                if (f.working()) plan.set(CompanionPlanRunner.run(List.of(steps), new CompanionServerPlanPort(f.h.getLevel().getServer(), owner, "agenda-plan", () -> false)));
                return;
            }
            if (plan.get().isDone() || ++ticks[0] <= 3) return;
            f.h.assertTrue(!CompanionAgenda.running(owner) && f.goal().state() == during, "The goal stepped in during the player's plan: " + f.goal().state() + ", tick " + ticks[0]);
        });
        return plan;
    }

    /** The end state of the gear_up probe: the iron set worn, every ingot spent exactly, no failure counted on the way. */
    private static void gearDone(Fixture f) {
        f.completed();
        ironSetWorn(f.h, f.c);
        f.h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
        for (var milestone : f.goal().book().milestones())
            f.h.assertTrue(f.goal().counter(milestone.key())[1] == 0, "A failure was counted for " + milestone.key());
    }

    // ---- Fixture ----

    /** Diamond tools carried and diamond armour worn: everything of prepare_nether before the food is already there. */
    private static void diamondsDone(Fixture f) {
        f.c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE)); f.c.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        Item[] armor = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) f.c.setItemSlot(ARMOR_SLOTS[i], new ItemStack(armor[i]));
    }

    private static void ironSetWorn(GameTestHelper h, CompanionEntity c) {
        for (int i = 0; i < ARMOR_SLOTS.length; i++)
            h.assertTrue(c.getItemBySlot(ARMOR_SLOTS[i]).is(IRON_ARMOR[i]), "Not worn: " + IRON_ARMOR[i] + " in " + ARMOR_SLOTS[i]);
        h.assertTrue(count(c, Items.IRON_PICKAXE) == 1 && count(c, Items.IRON_SWORD) == 1, "Tools: " + c.getStoredItemsSummary());
        for (Item piece : IRON_ARMOR) h.assertTrue(count(c, piece) == 0, "A second piece in the bag: " + piece);
    }

    private static void ingots(Fixture f, GameTestHelper h) {
        f.c.setItem(0, new ItemStack(Items.IRON_INGOT, 29)); f.c.setItem(1, new ItemStack(Items.STICK, 3));
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

    private static int belongingsOnGround(GameTestHelper h, UUID owner) {
        return h.getLevel().getEntities(EntityTypeTest.forClass(ItemEntity.class),
                item -> item.getPersistentData().hasUUID("aicompanion:belongings") && item.getPersistentData().getUUID("aicompanion:belongings").equals(owner)).size();
    }

    private static void furnaces(GameTestHelper h) {
        for (int z = 14; z <= 20; z += 2) h.setBlock(new BlockPos(19, 1, z), Blocks.FURNACE);
    }

    private static Fixture fixture(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(17, 1, 16), Blocks.CRAFTING_TABLE);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var p = survivalPlayer(h);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        return new Fixture(h, c, p);
    }

    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        return p;
    }

    static int count(CompanionEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) n += c.getItemBySlot(slot).getCount();
        return n;
    }

    private static final class Fixture {
        final GameTestHelper h; CompanionEntity c; final ServerPlayer p;
        Fixture(GameTestHelper h, CompanionEntity c, ServerPlayer p) { this.h = h; this.c = c; this.p = p; }
        void start(String goal, int tier) {
            var result = CompanionEvents.executeAction(p, "pursue_goal", goal, tier);
            h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        }
        CompanionAgendaData.Goal goal() { return CompanionAgenda.goal(h.getLevel().getServer(), p.getUUID()); }
        boolean working() { return CompanionAgenda.running(p.getUUID()) && c.hasActiveWorkOrder(); }
        CompanionActionResult order(String action) { return order(action, ""); }
        CompanionActionResult order(String action, String item) { return CompanionEvents.executeAction(p, action, item, 1); }
        void completed() {
            c = CompanionEvents.getOwnedCompanion(p);
            h.assertTrue(goal().state() == CompanionAgenda.State.COMPLETED, "Goal incomplete: " + goal().state() + " " + goal().reason() + " / " + c.getLastTaskResult());
        }
        void reload() {
            var saved = new CompoundTag(); c.saveWithoutId(saved); UUID uuid = c.getUUID();
            c.discard(); c = AICompanionMod.COMPANION.get().create(h.getLevel()); c.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(c) && c.getUUID().equals(uuid), "Reload duplicated the companion");
        }
    }
}
