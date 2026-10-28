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

    /** A step that fails twice in a row without any gain stops the order with its own reason. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainStopsWhenTheSameStepFailsWithoutGain(GameTestHelper h) {
        var port = new BagPort();
        port.neverMines = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "mine_ore:2")) && outcome.report().contains("План остановлен: Руды рядом нет.")
                    && outcome.skipped().stream().noneMatch(a -> a.type().equals("mine_ore")), port.calls + " " + outcome);
        });
    }

    /** A given-up «get it» skips only the later steps naming that item; a step that fails outside it still stops the plan. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void givenUpObtainSkipsOnlyItsOwnItem(GameTestHelper h) {
        var port = new BagPort();
        port.neverMines = true;
        var equip = new PlannedAction("equip_item", 0, 1, "minecraft:iron_helmet", "head");
        var follow = new PlannedAction("follow", 0, 1);
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET, equip, follow), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "mine_ore:2", "follow:1")) && outcome.skipped().equals(List.of(OBTAIN_HELMET, equip))
                    && outcome.report().contains(CompanionDialogue.PARTIAL_PREFIX + "«" + CompanionDialogue.itemName("minecraft:iron_helmet") + "»: Руды рядом нет.") && !outcome.report().contains("План остановлен")
                    && outcome.report().endsWith(CompanionDialogue.SKIPPED_PREFIX + CompanionDialogue.describeAll(List.of(OBTAIN_HELMET, equip))), port.calls + " " + outcome);
            String said = CompanionDialogue.summarize(List.of(OBTAIN_HELMET, equip, follow), outcome.report());
            h.assertTrue(said.contains("Остальное сделал") && said.contains(CompanionDialogue.itemName("minecraft:iron_helmet")) && said.contains("руды") && !said.contains("Пропустил"), said);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aStepOutsideTheObtainStillStopsThePlan(GameTestHelper h) {
        var port = new BagPort();
        port.neverMines = true;
        var follow = new PlannedAction("follow", 0, 1);
        var later = new PlannedAction("gather_stone", 0, 1);
        port.followFails = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET, follow, later, later), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "mine_ore:2", "follow:1")) && outcome.report().contains("План остановлен: Не пошёл.")
                    && outcome.skipped().contains(later), port.calls + " " + outcome);
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
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "gather_wood:1", "gather_wood:1")) && outcome.report().contains("План остановлен: Деревьев рядом нет."),
                    port.calls + " " + outcome);
        });
    }

    /** A step that succeeds and changes nothing twice in a row stops the order, with a reason of its own, not the success message. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainStopsWhenASuccessfulStepChangesNothing(GameTestHelper h) {
        var port = new BagPort();
        port.mineChangesNothing = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "mine_ore:2")) && outcome.report().contains("План остановлен: Два раза подряд ничего не прибавилось."), port.calls.size() + " " + outcome);
        });
    }

    /** Game 28.10 idea: a cow that drops no leather is a successful step with an unchanged bag; one such step must not give the part up. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainGoesOnAfterOneStepThatGainedNothing(GameTestHelper h) {
        var port = new BagPort();
        port.mineGainsNothingOnce = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.size() >= 2 && port.calls.subList(0, 2).equals(List.of("mine_ore:2", "mine_ore:2")) && port.helmets == 1 && outcome.skipped().isEmpty()
                    && !outcome.report().contains("План остановлен"), port.calls + " " + outcome);
        });
    }

    /** A step that gains nothing, a step that gains, and a step that gains nothing again are not «twice in a row». */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aGainBetweenTwoEmptyStepsResetsTheCount(GameTestHelper h) {
        var port = new BagPort();
        port.need = 4;
        port.emptyHalfEmpty = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.helmets == 1 && port.calls.subList(0, 4).equals(List.of("mine_ore:4", "mine_ore:4", "mine_ore:2", "mine_ore:2")) && outcome.skipped().isEmpty()
                    && !outcome.report().contains("План остановлен"), port.calls + " " + outcome);
        });
    }

    /** The last part given up is named too, with the reason once: no stop line, no «rest done». */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theLastGivenUpPartIsNamedAndTheReasonSaidOnce(GameTestHelper h) {
        var port = new BagPort();
        port.neverMines = true;
        var chest = new PlannedAction("obtain_item", 0, 1, "minecraft:iron_chestplate");
        var plan = List.of(OBTAIN_HELMET, chest);
        var future = CompanionPlanRunner.run(plan, port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            String said = CompanionDialogue.summarize(plan, outcome.report());
            h.assertTrue(said.equals("Не вышло: «" + CompanionDialogue.itemName("minecraft:iron_helmet") + "», «" + CompanionDialogue.itemName("minecraft:iron_chestplate")
                    + "» — больше доступной руды поблизости не нашёл."), port.calls + " " + outcome + " | " + said);
            // A single obtain that fails stays the old stop line.
            h.assertTrue(!outcome.report().startsWith("План остановлен"), outcome.report());
        });
    }

    /** One obtain alone that fails is the old single stop line, without a given-up line before it. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aSingleFailedObtainIsStillOneStopLine(GameTestHelper h) {
        var port = new BagPort();
        port.neverMines = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(!outcome.report().contains(CompanionDialogue.PARTIAL_PREFIX) && outcome.report().contains("План остановлен: Руды рядом нет."), outcome.report());
        });
    }

    /** The plan changes after every step but the bag never gets what is needed: the ceiling still ends the order. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainStepsHaveACeiling(GameTestHelper h) {
        var port = new BagPort();
        port.mineChangesNothing = true;
        port.layoutDrifts = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.size() == CompanionPlanRunner.MAX_OBTAIN_STEPS && outcome.report().contains("План остановлен: слишком много шагов"), port.calls.size() + " " + outcome);
        });
    }

    /** Ore over 16 is dug in pieces: a failed piece that gained something changes the plan, so the order goes on. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void obtainGoesOnAfterAPartialPieceOfBigOre(GameTestHelper h) {
        var port = new BagPort();
        port.need = 20;
        port.mineFailsOnce = true;
        port.failGain = 3;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:16", "mine_ore:16", "mine_ore:1", "gather_wood:1", "craft_item:1", "verify_item:1")) && port.helmets == 1
                    && outcome.skipped().isEmpty() && !outcome.report().contains("План остановлен"), port.calls + " " + outcome);
        });
    }

    /** A blocked step inside «get it» gives up that part only; an independent step after it still runs. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blockedStepInObtainSkipsOnlyThatPart(GameTestHelper h) {
        var port = new BagPort();
        port.mineBlocked = true;
        var follow = new PlannedAction("follow", 0, 1);
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET, follow), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "follow:1")) && outcome.skipped().contains(OBTAIN_HELMET)
                    && outcome.report().contains(CompanionDialogue.PARTIAL_PREFIX) && !outcome.report().contains("План остановлен"), port.calls + " " + outcome);
        });
    }

    /** A craft inside the chain refused for shortage is not turned into «get it» again: the chain is bounded by the no-gain stop. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void craftShortageInsideObtainIsNotSubstituted(GameTestHelper h) {
        var port = new BagPort();
        port.craftShortage = true;
        var future = CompanionPlanRunner.run(List.of(OBTAIN_HELMET), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.calls.equals(List.of("mine_ore:2", "gather_wood:1", "craft_item:1", "craft_item:1")) && port.expands == 5
                    && outcome.report().contains("План остановлен: Не хватает материалов."), port.calls + " expands=" + port.expands + " " + outcome);
        });
    }

    /** Two parts given up and a later stop: both things named once, a short reason, no steps and no coordinates. */
    @GameTest(template = "empty")
    public static void summaryNamesEveryGivenUpPartWithAShortReason(GameTestHelper h) {
        var plan = List.of(new PlannedAction("obtain_item", 0, 1, "minecraft:iron_chestplate"), new PlannedAction("obtain_item", 0, 1, "minecraft:iron_leggings"),
                new PlannedAction("obtain_item", 0, 1, "minecraft:iron_boots"), new PlannedAction("follow", 0, 1));
        String ore = "Руды рядом больше нет. Известные места: x=10 y=20 z=30. Цепочка: mine_ore 3, craft_item 1.";
        String chest = CompanionDialogue.itemName("minecraft:iron_chestplate"), legs = CompanionDialogue.itemName("minecraft:iron_leggings");
        String report = CompanionDialogue.PARTIAL_PREFIX + "«" + chest + "»: " + ore + "\n" + CompanionDialogue.PARTIAL_PREFIX + "«" + legs + "»: " + ore
                + "\n" + CompanionDialogue.SKIPPED_PREFIX + "достать железные ботинки, получить «Железные поножи»";
        String rest = CompanionDialogue.summarize(plan, report);
        h.assertTrue(rest.startsWith("Остальное сделал.") && rest.contains("«" + chest + "»") && rest.contains("«" + legs + "»")
                && rest.contains(" — больше доступной руды поблизости не нашёл.") && !rest.contains("x=10") && !rest.contains("Пропустил") && !rest.contains("Цепочка")
                && rest.length() <= 200, rest);
        String late = CompanionDialogue.summarize(plan, report.replace("\n" + CompanionDialogue.SKIPPED_PREFIX, "\nПлан остановлен: Не хватает материалов.\n" + CompanionDialogue.SKIPPED_PREFIX));
        h.assertTrue(late.startsWith("На всё пока не хватает материалов.") && late.indexOf("Не вышло") > 0 && late.indexOf("Не вышло") < late.indexOf("«" + chest + "»") && late.contains("«" + legs + "»")
                && !late.contains("x=10") && !late.contains("Пропустил") && late.length() <= 200, late);
        h.succeed();
    }

    /** A refusal of the planner is said in the engine's own words, not as «С этим пока не справился»; at most two reasons, 200 characters. */
    @GameTest(template = "empty")
    public static void summarySaysThePlannersOwnRefusal(GameTestHelper h) {
        var plan = List.of(new PlannedAction("obtain_item", 0, 1, "minecraft:iron_helmet"), new PlannedAction("follow", 0, 1));
        String helmet = CompanionDialogue.itemName("minecraft:iron_helmet");
        String said = CompanionDialogue.summarize(plan, CompanionDialogue.PARTIAL_PREFIX + "«" + helmet + "»: Не знаю, где взять «Нить».");
        h.assertTrue(said.equals("Остальное сделал. Не вышло: «" + helmet + "» — не знаю, где взять «Нить»."), said);
        String many = CompanionDialogue.summarize(plan, CompanionDialogue.PARTIAL_PREFIX + "«" + helmet + "»: слишком много шагов ради одного предмета.");
        h.assertTrue(many.contains("слишком много шагов ради одного предмета.") && !many.contains("не справился"), many);
        String long_ = CompanionDialogue.summarize(plan, CompanionDialogue.PARTIAL_PREFIX + "«" + helmet + "»: " + "Очень длинное объяснение без ключевых слов. ".repeat(4));
        h.assertTrue(long_.contains("не справился"), long_);
        String[] items = {"minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots"};
        String[] whys = {"Руды рядом больше нет.", "Не хватает материалов.", "Не знаю, где взять «Нить».", "Руды рядом больше нет."};
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < items.length; i++) report.append(i > 0 ? "\n" : "").append(CompanionDialogue.PARTIAL_PREFIX).append("«").append(CompanionDialogue.itemName(items[i])).append("»: ").append(whys[i]);
        String four = CompanionDialogue.summarize(plan, report.toString());
        h.assertTrue(four.length() <= 200 && four.contains("руды") && four.contains("материалов") && !four.contains("Нить") && four.contains(CompanionDialogue.itemName(items[3])), four.length() + " " + four);
        h.succeed();
    }

    /** The give-up lines of the game run 27.10 word for word: three things named, the reason is ore out of safe reach. */
    @GameTest(template = "empty")
    public static void summaryOfTheArmourRunNamesThePartsAndTheOre(GameTestHelper h) {
        String bag = " В сумке: 3 Доски из тёмного дуба, 31 Бревно тёмного дуба, 1 Деревянная кирка, 2 Железный шлем, 32 Булыжник, 2 Палка.";
        var parts = List.of("minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots");
        int[] ore = { 16, 14, 8 };
        List<PlannedAction> plan = new ArrayList<>(List.of(new PlannedAction("obtain_item", 0, 2, "minecraft:iron_helmet")));
        StringBuilder report = new StringBuilder("Готово: «Железный шлем» у меня, 2 шт.");
        for (int i = 0; i < parts.size(); i++) {
            plan.add(new PlannedAction("obtain_item", 0, 2, parts.get(i)));
            report.append("\n").append(CompanionDialogue.PARTIAL_PREFIX).append("«").append(CompanionDialogue.itemName(parts.get(i))).append("»: ")
                    .append("Добыл 0 из ").append(ore[i]).append(" блоков «железная руда»: в сумке прибавилось 0 «Рудное железо». Больше доступной руды «железная руда» рядом нет. ")
                    .append("У 3 блока рядом вода или лава — их не трогал. К 83 не нашёл безопасного хода: не глубже 8 блоков, без гравия, песка и жидкостей рядом.").append(bag);
        }
        report.append("\nНадел: Железный шлем.\nОтдал тебе 1 «Железный шлем».");
        String said = CompanionDialogue.summarize(plan, report.toString());
        h.assertTrue(said.equals("Остальное сделал. Не вышло: «" + CompanionDialogue.itemName(parts.get(0)) + "», «" + CompanionDialogue.itemName(parts.get(1))
                + "», «" + CompanionDialogue.itemName(parts.get(2)) + "» — до оставшейся руды безопасно не добраться."), said);
        h.succeed();
    }

    /** PR #29: a crafted order that he also put on and handed over ended «Можешь посмотреть в сумке» — the things were no longer there. */
    @GameTest(template = "empty")
    public static void aCraftedAndHandedOverOrderSaysItWasGiven(GameTestHelper h) {
        var craft = new PlannedAction("craft_item", 0, 2, "minecraft:iron_helmet");
        var give = new PlannedAction("give_items", 0, 1, "minecraft:iron_helmet");
        String report = "Скрафтил 2 «Железный шлем».\nНадел: Железный шлем.\nОтдал тебе 1 «Железный шлем».";
        String both = CompanionDialogue.summarize(List.of(craft, new PlannedAction("equip_item", 0, 1, "minecraft:iron_helmet"), give), report);
        String given = CompanionDialogue.summarize(List.of(craft, give), "Скрафтил 2 «Железный шлем».\nОтдал тебе 2 «Железный шлем».");
        h.assertTrue(both.equals("Готово: сделал, своё надел, твоё отдал.") && given.equals("Готово, сделал и отдал тебе."), both + " / " + given);
        h.assertTrue(CompanionDialogue.summarize(List.of(craft), "Скрафтил 2 «Железный шлем».").equals("Готово, сделал. Можешь посмотреть в сумке."),
                "A craft without a hand-over changed");
        h.succeed();
    }

    /** A shortage refusal of a craft becomes «get it» on (carried + asked); any other refusal of a craft stays a stop. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void craftShortageBecomesObtainOnTopOfTheBag(GameTestHelper h) {
        var port = new CraftRefusalPort(CompanionActionResult.shortage("Не хватает материалов."));
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("craft_item", 0, 2, "minecraft:iron_helmet")), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.obtained.equals(List.of("minecraft:iron_helmet:3")) && port.calls.equals(List.of("craft_item", "verify_item"))
                    && !outcome.report().contains("План остановлен"), port.obtained + " " + port.calls + " " + outcome);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void craftRefusedForOtherReasonsIsNotObtained(GameTestHelper h) {
        var port = new CraftRefusalPort(CompanionActionResult.failed("В моей сумке нет места."));
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("craft_item", 0, 2, "minecraft:iron_helmet")), port);
        h.succeedWhen(() -> {
            Outcome outcome = future.join();
            h.assertTrue(port.obtained.isEmpty() && port.calls.equals(List.of("craft_item")) && outcome.report().contains("План остановлен: В моей сумке нет места."),
                    port.obtained + " " + port.calls + " " + outcome);
        });
    }

    /** One helmet carried; the first craft is refused with the given result, and obtain_item is just recorded. */
    private static final class CraftRefusalPort implements CompanionPlanRunner.Port {
        final List<String> calls = new ArrayList<>(), obtained = new ArrayList<>();
        private final CompanionActionResult refusal;
        CraftRefusalPort(CompanionActionResult refusal) { this.refusal = refusal; }
        @Override public String trace() { return "gametest-craft"; }
        @Override public boolean cancelled() { return false; }
        @Override public CompletableFuture<State> state(PlannedAction action) { return CompletableFuture.completedFuture(IDLE); }
        @Override public CompletableFuture<Void> pause(int seconds) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Integer> carried(String item) { return CompletableFuture.completedFuture(1); }
        @Override public CompletableFuture<CompanionPlanRunner.Expansion> expand(PlannedAction action) {
            obtained.add(action.item() + ":" + action.count());
            return CompletableFuture.completedFuture(new CompanionPlanRunner.Expansion(List.of(new PlannedAction("verify_item", 0, action.count(), action.item())), null));
        }
        @Override public CompletableFuture<Attempt> run(PlannedAction action) {
            calls.add(action.type());
            return CompletableFuture.completedFuture(action.type().equals("craft_item") ? new Attempt(refusal, false) : new Attempt(CompanionActionResult.ok("Готово."), true));
        }
    }

    private static final PlannedAction OBTAIN_HELMET = new PlannedAction("obtain_item", 0, 1, "minecraft:iron_helmet");

    /** A bag in the fake: ore gives ingots, a helmet takes two ingots and a log. expand plans from it like the server planner. */
    private static final class BagPort implements CompanionPlanRunner.Port {
        final List<String> calls = new ArrayList<>();
        int logs, ingots, helmets;
        int need = 2, failGain = 1, expands;
        boolean mineGainsNothingOnce, emptyHalfEmpty;
        int mines;
        boolean logsAppearAfterOre, pickaxeEatsIngot, mineFailsOnce, neverMines, mineChangesNothing, noTrees, followFails, layoutDrifts, mineBlocked, craftShortage;
        @Override public String trace() { return "gametest-bag"; }
        @Override public boolean cancelled() { return false; }
        @Override public CompletableFuture<State> state(PlannedAction action) { return CompletableFuture.completedFuture(IDLE); }
        @Override public CompletableFuture<Void> pause(int seconds) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<CompanionPlanRunner.Expansion> expand(PlannedAction action) {
            expands++;
            List<PlannedAction> steps = new ArrayList<>();
            if (helmets < 1) {
                // Ore over 16 is planned in pieces, as CompanionObtain does.
                int left = need - ingots + (layoutDrifts ? calls.size() : 0);
                for (; left > 0; left -= 16) steps.add(new PlannedAction("mine_ore", 0, Math.min(16, left), "minecraft:iron_ore"));
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
                    if (craftShortage) { shortageRefused = true; yield CompanionActionResult.shortage("Не хватает материалов."); }
                    if (ingots < need || logs < 1) yield CompanionActionResult.failed("Не хватает материалов.");
                    ingots -= need; logs--; helmets++;
                    yield CompanionActionResult.ok("Скрафтил.");
                }
                case "follow" -> followFails ? CompanionActionResult.failed("Не пошёл.") : CompanionActionResult.ok("Иду.");
                default -> helmets >= 1 ? CompanionActionResult.ok("Готово.") : CompanionActionResult.failed("Добыл не всё.");
            };
            boolean refused = shortageRefused; shortageRefused = false;
            return CompletableFuture.completedFuture(new Attempt(result, !refused));
        }
        private boolean shortageRefused;
        private CompanionActionResult mine(int count) {
            if (mineBlocked) return CompanionActionResult.blocked("Дальше не пройти.");
            if (neverMines) return CompanionActionResult.failed("Руды рядом нет.");
            if (mineChangesNothing) return CompanionActionResult.ok("Ничего.");
            if (mineGainsNothingOnce) { mineGainsNothingOnce = false; return CompanionActionResult.ok("Ничего не выпало."); }
            if (emptyHalfEmpty) {
                int n = mines++;
                if (n == 0 || n == 2) return CompanionActionResult.ok("Ничего не выпало.");
                if (n == 1) { ingots += count / 2; return CompanionActionResult.ok("Добыл часть."); }
            }
            if (mineFailsOnce) { mineFailsOnce = false; ingots += failGain; return CompanionActionResult.failed("Руды рядом больше нет."); }
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
