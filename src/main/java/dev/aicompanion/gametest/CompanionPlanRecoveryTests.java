package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionPlanRunner.Attempt;
import dev.aicompanion.client.CompanionPlanRunner.Decision;
import dev.aicompanion.client.CompanionPlanRunner.Outcome;
import dev.aicompanion.client.CompanionPlanRunner.State;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Plan chains: bounded repeats only on server evidence, honest stop with the list of steps not done. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPlanRecoveryTests {
    private static final State IDLE = new State(false, -1);

    @GameTest(template = "empty")
    public static void recoveryDecisionsRequireServerEvidence(GameTestHelper h) {
        var stone = new PlannedAction("gather_stone", 0, 8);
        var craft = new PlannedAction("craft_item", 0, 1, "minecraft:stone_pickaxe");
        var timeout = new Attempt(CompanionActionResult.timeout("t"), true);
        var progress = CompanionPlanRunner.decide(stone, timeout, new State(false, 2), new State(false, 5), List.of(), false, 0, false);
        h.assertTrue(progress.decision() == Decision.RETRY && progress.retry().count() == 5 && progress.retry().type().equals("gather_stone"),
                "Timeout after progress must repeat only the remaining count: " + progress);
        h.assertTrue(CompanionPlanRunner.decide(stone, timeout, new State(false, 4), new State(false, 4), List.of(), false, 0, false).decision() == Decision.STOP,
                "Timeout without progress was repeated");
        h.assertTrue(CompanionPlanRunner.decide(stone, timeout, new State(false, 0), new State(false, 9), List.of(), false, 0, false).decision() == Decision.CONTINUE,
                "Collected target was not accepted");
        h.assertTrue(CompanionPlanRunner.decide(stone, timeout, IDLE, IDLE, List.of(), false, 0, false).decision() == Decision.STOP,
                "Unmeasured timeout was repeated");
        var busy = new Attempt(CompanionActionResult.failed("Сначала нужно завершить или отменить текущую работу."), false);
        h.assertTrue(CompanionPlanRunner.decide(craft, busy, new State(true, -1), new State(true, -1), List.of(), false, 0, false).decision() == Decision.RETRY,
                "Busy rejection was not retried");
        h.assertTrue(CompanionPlanRunner.decide(craft, busy, IDLE, IDLE, List.of(), false, 0, false).decision() == Decision.STOP,
                "Plain rejection was retried");
        h.assertTrue(CompanionPlanRunner.decide(craft, new Attempt(CompanionActionResult.failed("x"), true), new State(true, -1), new State(true, -1), List.of(), false, 0, false)
                .decision() == Decision.STOP, "Failure during real work was retried as busy");
        h.assertTrue(CompanionPlanRunner.decide(stone, new Attempt(CompanionActionResult.blocked("b"), true), new State(false, 0), new State(false, 3), List.of(), false, 0, false)
                .decision() == Decision.STOP, "Blocked step was retried");
        h.assertTrue(CompanionPlanRunner.decide(stone, timeout, new State(false, 2), new State(false, 5), List.of(), false, 0, true).decision() == Decision.STOP,
                "Cancelled plan was retried");
        h.assertTrue(CompanionPlanRunner.decide(stone, new Attempt(CompanionActionResult.cancelled("c"), true), IDLE, IDLE, List.of(), false, 0, false)
                .decision() == Decision.STOP, "Cancelled step was retried");
        h.assertTrue(CompanionPlanRunner.decide(stone, timeout, new State(false, 2), new State(false, 5), List.of(), true, 0, false).decision() == Decision.STOP,
                "Step was repeated twice");
        h.assertTrue(CompanionPlanRunner.decide(stone, timeout, new State(false, 2), new State(false, 5), List.of(), false, CompanionPlanRunner.MAX_RECOVERIES, false)
                .decision() == Decision.STOP, "Plan recovery budget ignored");
        h.assertTrue(CompanionPlanRunner.decide(new PlannedAction("gather_wood", 0, 6), new Attempt(CompanionActionResult.failed("мало"), true), IDLE, IDLE,
                List.of(craft), false, 0, false).decision() == Decision.CONTINUE, "Partial wood before craft no longer continues");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chainRepeatsRemainderThenStopsAndNamesSkippedSteps(GameTestHelper h) {
        var plan = List.of(new PlannedAction("gather_stone", 0, 4), new PlannedAction("craft_item", 0, 1, "minecraft:stone_pickaxe"),
                new PlannedAction("craft_item", 0, 1, "minecraft:stone_sword"), new PlannedAction("craft_item", 0, 1, "minecraft:stone_shovel"));
        var port = new ScriptedPort();
        port.add(CompanionActionResult.timeout("Не успел."), true, 3);
        port.add(CompanionActionResult.ok("Добыл 1 камень."), true, 1);
        port.add(CompanionActionResult.ok("Кирка готова."), true, 0);
        port.add(CompanionActionResult.failed("Не хватает материалов."), true, 0);
        var future = CompanionPlanRunner.run(plan, port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Runner did not finish");
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("gather_stone:4", "gather_stone:1", "craft_item:1", "craft_item:1")), "Wrong step sequence: " + port.calls);
            h.assertTrue(outcome.recoveries() == 1 && outcome.skipped().equals(plan.subList(3, 4)), "Wrong recovery accounting: " + outcome);
            h.assertTrue(outcome.report().contains("Повтор шага") && outcome.report().contains("План остановлен: Не хватает материалов.")
                    && outcome.report().endsWith(CompanionDialogue.SKIPPED_PREFIX + CompanionDialogue.describe(plan.get(3))), outcome.report());
            String spoken = CompanionDialogue.summarize(plan, outcome.report());
            h.assertTrue(spoken.equals("На всё пока не хватает материалов."), spoken);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void planRecoveryBudgetIsBounded(GameTestHelper h) {
        var plan = List.of(new PlannedAction("gather_dirt", 0, 4), new PlannedAction("gather_dirt", 0, 4), new PlannedAction("gather_dirt", 0, 4));
        var port = new ScriptedPort();
        for (int i = 0; i < 2; i++) { port.add(CompanionActionResult.timeout("Медленно."), true, 1); port.add(CompanionActionResult.ok("Ок."), true, 3); }
        port.add(CompanionActionResult.timeout("Медленно."), true, 1);
        var future = CompanionPlanRunner.run(plan, port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Runner did not finish");
            Outcome outcome = future.join();
            h.assertTrue(port.calls.size() == 5 && outcome.recoveries() == CompanionPlanRunner.MAX_RECOVERIES && outcome.skipped().isEmpty()
                    && outcome.report().contains("План остановлен: Медленно."), port.calls + " " + outcome);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancelledPlanDoesNotRepeatOrListSkippedSteps(GameTestHelper h) {
        var plan = List.of(new PlannedAction("gather_stone", 0, 4), new PlannedAction("craft_item", 0, 1, "minecraft:stone_pickaxe"));
        var port = new ScriptedPort();
        port.add(CompanionActionResult.cancelled("План отменён."), true, 2);
        var future = CompanionPlanRunner.run(plan, port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "Runner did not finish");
            Outcome outcome = future.join();
            h.assertTrue(port.calls.size() == 1 && outcome.recoveries() == 0 && outcome.skipped().isEmpty()
                    && !outcome.report().contains(CompanionDialogue.SKIPPED_PREFIX), outcome.toString());
            h.assertTrue(CompanionDialogue.summarize(plan, outcome.report()).equals("Ладно, остановился."), "Cancel summary changed");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void busyCompanionFinishesEarlierWorkThenRunsWholeRealChain(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var p = h.makeMockServerPlayerInLevel(); p.teleportTo(c.getX() - 4, c.getY(), c.getZ() - 4); c.setOwnerId(p.getUUID());
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p); c.setSearchRadius(16);
        // With an axe in hand the earlier wood job does not craft (and place a bench for) its own tool.
        c.setItem(0, new ItemStack(Items.OAK_LOG, 4)); c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        h.setBlock(new BlockPos(19, 1, 16), Blocks.OAK_LOG);
        c.startGatheringLogs(1);
        h.assertTrue(c.hasActiveWorkOrder(), "Earlier work did not start");
        var plan = List.of(new PlannedAction("place_block", 0, 1, "minecraft:crafting_table"),
                new PlannedAction("craft_item", 0, 1, "minecraft:wooden_pickaxe"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), p.getUUID(), "gametest-busy-chain", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "Chain still running: " + c.getLastTaskResult());
            h.assertTrue(!future.isCompletedExceptionally(), "Chain failed with exception");
            Outcome outcome = future.join();
            h.assertTrue(outcome.recoveries() == 1 && outcome.skipped().isEmpty() && !outcome.report().contains("План остановлен"), outcome.toString());
            int tables = 0; for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 5, 31))) if (h.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) tables++;
            h.assertTrue(tables == 1 && count(c, Items.WOODEN_PICKAXE) == 1, "Real postconditions failed: tables=" + tables + " pickaxes=" + count(c, Items.WOODEN_PICKAXE) + " bag=" + c.getStoredItemsSummary());
            h.assertTrue(h.getBlockState(new BlockPos(19, 1, 16)).isAir(), "Earlier work was cancelled instead of finished");
        });
    }

    /** The bag changes between the steps of an obtain_item (here: logs arrive after the ore), so the chain is planned again. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainDropsStepsTheBagMadeNeedless(GameTestHelper h) {
        var port = new BagPort();
        port.logsAppearAfterOre = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "craft_item:1", "verify_item:1")) && port.helmets == 1 && outcome.skipped().isEmpty(),
                    port.calls + " " + outcome);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainDigsUpWhatTheBagLostOnTheWay(GameTestHelper h) {
        var port = new BagPort();
        port.pickaxeEatsIngot = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "mine_ore:1", "gather_wood:1", "craft_item:1", "verify_item:1")) && port.helmets == 1
                    && outcome.skipped().isEmpty(), port.calls + " " + outcome);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainPlansAroundAFailedStepThatGainedSomething(GameTestHelper h) {
        var port = new BagPort();
        port.mineFailsOnce = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.size() >= 2 && port.calls.subList(0, 2).equals(List.of("mine_ore:2", "mine_ore:1")) && port.helmets == 1 && outcome.skipped().isEmpty()
                    && !outcome.report().contains("План остановлен"), port.calls + " " + outcome);
        });
    }

    /** A step that fails and is planned again without any gain stops the order with its own reason. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainStopsWhenTheSameStepFailsWithoutGain(GameTestHelper h) {
        var port = new BagPort();
        port.neverMines = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2")) && outcome.report().contains("План остановлен: Руды рядом нет.")
                    && outcome.skipped().stream().noneMatch(a -> a.type().equals("mine_ore")), port.calls + " " + outcome);
        });
    }

    /** A wood search that finds nothing goes on to the craft in an ordinary plan; inside obtain_item it must not be searched again and again. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainStopsWhenNoTreeIsFoundAgain(GameTestHelper h) {
        var port = new BagPort();
        port.noTrees = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "gather_wood:1")) && outcome.report().contains("План остановлен: Деревьев рядом нет."),
                    port.calls + " " + outcome);
        });
    }

    /** Steps that succeed without ever changing the bag cannot loop: one obtain_item has a ceiling. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainStepsHaveACeiling(GameTestHelper h) {
        var port = new BagPort();
        port.mineChangesNothing = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.size() == CompanionPlanRunner.MAX_OBTAIN_STEPS && outcome.report().contains("План остановлен"), port.calls.size() + " " + outcome);
        });
    }

    private static final PlannedAction OBTAIN_HELMET = new PlannedAction("obtain_item", 0, 1, "minecraft:iron_helmet");

    /** A bag in the fake: ore gives ingots, a helmet takes two ingots and a log. expand plans from it like the server planner. */
    private static final class BagPort implements CompanionPlanRunner.Port {
        final List<String> calls = new ArrayList<>();
        int logs, ingots, helmets;
        boolean logsAppearAfterOre, pickaxeEatsIngot, mineFailsOnce, neverMines, mineChangesNothing, noTrees;
        @Override public String trace() { return "gametest-bag"; }
        @Override public boolean cancelled() { return false; }
        @Override public CompletableFuture<State> state(PlannedAction action) { return CompletableFuture.completedFuture(IDLE); }
        @Override public CompletableFuture<Void> pause(int seconds) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<CompanionPlanRunner.Expansion> expand(PlannedAction action) {
            List<PlannedAction> steps = new ArrayList<>();
            if (helmets < 1) {
                if (ingots < 2) steps.add(new PlannedAction("mine_ore", 0, 2 - ingots, "minecraft:iron_ore"));
                if (logs < 1) steps.add(new PlannedAction("gather_wood", 0, 1));
                steps.add(new PlannedAction("craft_item", 0, 1, action.item()));
            }
            steps.add(new PlannedAction("verify_item", 0, action.count(), action.item()));
            return CompletableFuture.completedFuture(new CompanionPlanRunner.Expansion(steps, null));
        }
        @Override public CompletableFuture<Attempt> run(PlannedAction action) {
            calls.add(action.type() + ":" + action.count());
            CompanionActionResult result = switch (action.type()) {
                case "mine_ore" -> mine(action.count());
                case "gather_wood" -> {
                    if (noTrees) yield CompanionActionResult.failed("Деревьев рядом нет.");
                    logs++; yield CompanionActionResult.ok("Дерево.");
                }
                case "craft_item" -> {
                    if (ingots < 2 || logs < 1) yield CompanionActionResult.failed("Не хватает материалов.");
                    ingots -= 2; logs--; helmets++;
                    yield CompanionActionResult.ok("Скрафтил.");
                }
                default -> helmets >= 1 ? CompanionActionResult.ok("Готово.") : CompanionActionResult.failed("Добыл не всё.");
            };
            return CompletableFuture.completedFuture(new Attempt(result, true));
        }
        private CompanionActionResult mine(int count) {
            if (neverMines) return CompanionActionResult.failed("Руды рядом нет.");
            if (mineChangesNothing) return CompanionActionResult.ok("Ничего.");
            if (mineFailsOnce) { mineFailsOnce = false; ingots++; return CompanionActionResult.failed("Руды рядом больше нет."); }
            ingots += count;
            if (logsAppearAfterOre) logs += 3;
            if (pickaxeEatsIngot) { pickaxeEatsIngot = false; ingots--; }
            return CompanionActionResult.ok("Добыл.");
        }
    }

    private static int count(CompanionEntity c, Item item) {
        int n = 0; for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) n += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) n += c.getItemBySlot(slot).getCount();
        return n;
    }

    /** Scripted server answers: each run consumes one result and adds its pickups to the progress counter. */
    private static final class ScriptedPort implements CompanionPlanRunner.Port {
        private record Answer(CompanionActionResult result, boolean started, int gained) { }
        private final Deque<Answer> answers = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();
        private int progress;
        void add(CompanionActionResult result, boolean started, int gained) { answers.add(new Answer(result, started, gained)); }
        @Override public String trace() { return "gametest-scripted"; }
        @Override public boolean cancelled() { return false; }
        @Override public CompletableFuture<State> state(PlannedAction action) { return CompletableFuture.completedFuture(new State(false, progress)); }
        @Override public CompletableFuture<Attempt> run(PlannedAction action) {
            calls.add(action.type() + ":" + action.count());
            Answer answer = answers.isEmpty() ? new Answer(CompanionActionResult.failed("Сценарий закончился."), false, 0) : answers.removeFirst();
            progress += answer.gained();
            return CompletableFuture.completedFuture(new Attempt(answer.result(), answer.started()));
        }
        @Override public CompletableFuture<Void> pause(int seconds) { return CompletableFuture.completedFuture(null); }
    }
}
