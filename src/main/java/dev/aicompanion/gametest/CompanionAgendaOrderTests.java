package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionEvents;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Long goals (CompanionAgenda) beside the player's orders: interruptions, holds, cancel, defer and resume. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaOrderTests {
    // A goal left running after its test would go on working beside the batches that follow: clean up like the other suites.
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
}
