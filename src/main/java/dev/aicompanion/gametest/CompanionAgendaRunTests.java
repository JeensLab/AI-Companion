package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import dev.aicompanion.gametest.support.CompanionFixtureCleanup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.AgendaKit.*;

import java.util.UUID;

/** Long goals (CompanionAgenda): the gear probe, reloads, deaths, danger and the stop of a goal that cannot go on. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaRunTests {
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
    @AfterBatch(batch = "agenda_hurt") public static void afterHurt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_siege") public static void afterSiege(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_stop") public static void afterStop(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_blocked") public static void afterBlocked(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_quick") public static void afterQuick(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_death_loot") public static void afterDeathLoot(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_death_place") public static void afterDeathPlace(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

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
        f.c.setItem(5, new ItemStack(Items.FLINT_AND_STEEL)); f.c.setItem(6, new ItemStack(Items.WATER_BUCKET));
        f.start("defeat_ender_dragon", 0);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || f.goal().state() != CompanionAgenda.State.BLOCKED) return;
            done[0] = true;
            h.assertTrue(f.goal().reason().contains("10 обсидиана"), "The stop does not name the obsidian: " + f.goal().reason());
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

    private static int belongingsOnGround(GameTestHelper h, UUID owner) {
        return h.getLevel().getEntities(EntityTypeTest.forClass(ItemEntity.class),
                item -> item.getPersistentData().hasUUID("aicompanion:belongings") && item.getPersistentData().getUUID("aicompanion:belongings").equals(owner)).size();
    }
}
