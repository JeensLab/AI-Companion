package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFacts;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionDiamondGoalTests.*;

/**
 * «Продолжай» gives a blocked milestone fresh tries (and a repeated order for the same goal does not), the descent waits for food and
 * sticks, and an ore order that ended in a pit leaves it.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGoalResumeAndPitTests {
    private static final String GOAL = "find_diamonds";
    private static final int SPENT = 12, HUNT_TICKS = 1200, PIT_TIMEOUT = 2400, SURFACE = 7, FOOD_MIN = 4, STICKS_MIN = 4, STONE_PICKAXE_STONE = 3;

    @AfterBatch(batch = "resume_fresh") public static void afterFresh(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "resume_repeat") public static void afterRepeat(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "kit_gate") public static void afterGate(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "ore_pit") public static void afterPit(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** «Продолжай» after the attempts ran out: the milestone starts afresh, and a final stop of the mine is forgotten with the counters. */
    @GameTest(template = "marathon_cave", batch = "resume_fresh", timeoutTicks = 400)
    public static void explicitResumeGivesTheBlockedMilestoneFreshTries(GameTestHelper h) {
        Rig r = blockedGoal(h);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || state(h, r) != CompanionAgenda.State.BLOCKED) return;
            done[0] = true;
            CompanionEntity c = r.c();
            c.mineSite().start(c.blockPosition(), Direction.NORTH);
            for (int i = 0; i < 5; i++) c.mineSite().stopped(c.blockPosition(), 1);
            h.assertTrue(c.mineSite().stopFinal(), "The mine stop is not final before the resume");
            var resumed = CompanionEvents.executeAction(r.p(), "goal_resume", "", 1);
            h.assertTrue(resumed.status() == CompanionActionResult.Status.ACCEPTED && state(h, r) == CompanionAgenda.State.ACTIVE, "Resume did not restart: " + resumed.message());
            h.assertTrue(goal(h, r).book().milestones().stream().anyMatch(milestone -> goal(h, r).counter(milestone.key())[0] == 0), "Resume left every milestone without tries");
            h.assertTrue(!c.mineSite().stopFinal(), "Resume left the final stop of the mine");
            h.succeed();
        });
    }

    /** The model asking again for the goal that is already there is no «продолжай»: the spent attempts stay spent. */
    @GameTest(template = "marathon_cave", batch = "resume_repeat", timeoutTicks = 400)
    public static void aRepeatedGoalOrderDoesNotResetTheCounters(GameTestHelper h) {
        Rig r = blockedGoal(h);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || state(h, r) != CompanionAgenda.State.BLOCKED) return;
            done[0] = true;
            CompanionEvents.executeAction(r.p(), "pursue_goal", GOAL, 1);
            for (var milestone : goal(h, r).book().milestones())
                h.assertTrue(goal(h, r).counter(milestone.key())[0] == SPENT, "A repeated pursue_goal gave fresh tries to " + milestone.key());
            h.succeed();
        });
    }

    /** The top-up for the mine is the kit: without food or without sticks the minimum is not met, and the mine is not entered on a failed top-up. */
    @GameTest(template = "marathon_cave", batch = "kit_gate", timeoutTicks = 200)
    public static void theKitMinimumNeedsFoodAndSticks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var book = CompanionPlaybooks.of(GOAL, 1);
        var mine = book.milestones().getLast();
        h.assertTrue(!mine.kitMinimum(CompanionFacts.of(c)), "An empty bag meets the kit minimum");
        c.setItem(0, new ItemStack(Items.BREAD, FOOD_MIN)); c.setItem(1, new ItemStack(Items.STICK, STICKS_MIN - 1));
        h.assertTrue(!mine.kitMinimum(CompanionFacts.of(c)), "Food without enough sticks meets the kit minimum");
        c.setItem(1, new ItemStack(Items.STICK, STICKS_MIN)); c.setItem(0, new ItemStack(Items.BREAD, FOOD_MIN - 1));
        h.assertTrue(!mine.kitMinimum(CompanionFacts.of(c)), "Sticks without enough food meet the kit minimum");
        c.setItem(0, new ItemStack(Items.BREAD, FOOD_MIN));
        h.assertTrue(!mine.kitMinimum(CompanionFacts.of(c)), "Food and sticks without a bench and stone meet the kit minimum");
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE)); c.setItem(3, new ItemStack(Items.COBBLESTONE, STONE_PICKAXE_STONE));
        h.assertTrue(mine.kitMinimum(CompanionFacts.of(c)), "A full minimum is refused");
        h.succeed();
    }

    /** No food and no game to hunt: the top-up fails, and he does not go down with an empty stomach. */
    @GameTest(template = "marathon_cave", batch = "kit_gate", timeoutTicks = HUNT_TICKS + 100)
    public static void aFailedFoodTopUpDoesNotStartTheDescent(GameTestHelper h) {
        ItemStack[] bag = supplies(32, 4);
        bag[2] = ItemStack.EMPTY;
        Rig r = rig(h, false, bag);
        start(h, r, 1);
        h.runAtTickTime(HUNT_TICKS, h::succeed);
        h.onEachTick(() -> h.assertTrue(r.c().mineSite().opened() == 0, "Went down without food | " + report(h, r)));
    }

    /** A hunt that found no game at all changes nothing by waiting: with full health the goal stops at once and asks for food. */
    @GameTest(template = "marathon_cave", batch = "kit_gate", timeoutTicks = HUNT_TICKS + 300)
    public static void noGameAtAllStopsTheGoalAtOnceAskingForFood(GameTestHelper h) {
        ItemStack[] bag = supplies(32, 4);
        bag[2] = ItemStack.EMPTY;
        Rig r = rig(h, false, bag);
        start(h, r, 1);
        h.onEachTick(() -> {
            h.assertTrue(r.c().mineSite().opened() == 0, "Went down without food | " + report(h, r));
            if (state(h, r) != CompanionAgenda.State.BLOCKED) return;
            h.assertTrue(goal(h, r).reason().contains("Принеси еды") && !goal(h, r).reason().contains("рана"), "Not a hunger reason: " + goal(h, r).reason());
            h.succeed();
        });
    }

    /** A pit beside the entrance is no part of his mine: from it the surface kit (torches first) is still made. */
    @GameTest(template = "marathon_cave", batch = "kit_gate", timeoutTicks = 200)
    public static void aPitBesideTheEntranceIsNotTheMine(GameTestHelper h) {
        mountain(h, false);
        BlockPos pit = new BlockPos(CENTER + 12, GROUND - 6, CENTER);
        for (int y = GROUND - 6; y <= GROUND - 2; y++) h.setBlock(new BlockPos(pit.getX(), y, pit.getZ()), Blocks.AIR);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        c.mineSite().reached(h.absolutePos(new BlockPos(CENTER, GROUND, CENTER)));
        c.setPos(h.absolutePos(pit).getX() + .5, h.absolutePos(pit).getY(), h.absolutePos(pit).getZ() + .5);
        h.succeedWhen(() -> {
            var facts = CompanionFacts.of(c);
            h.assertTrue(facts.belowMine() && !facts.inMine(), "A pit 12 blocks from the stairs counts as the mine: below " + facts.belowMine() + ", in " + facts.inMine());
            var book = CompanionPlaybooks.of(GOAL, 1);
            var steps = book.milestones().getLast().method(facts, false, book.milestones());
            h.assertTrue(steps.stream().anyMatch(step -> step.item().equals("minecraft:torch")), "No torches asked for from the pit: " + steps);
        });
    }

    /**
     * The coal lies on the wall of a pit three deep: he drops into it, mines it, and cannot walk out. The order must not end with him
     * in the pit: he digs a stair up and reports from the surface.
     */
    @GameTest(template = "tall_tree", batch = "ore_pit", timeoutTicks = PIT_TIMEOUT)
    public static void anOreOrderLeavesThePitItEndedIn(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int x = 8; x <= 24; x++) for (int z = 8; z <= 24; z++) for (int y = 1; y < SURFACE; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        for (int y = 4; y < SURFACE; y++) h.setBlock(new BlockPos(20, y, 20), Blocks.AIR);
        h.setBlock(new BlockPos(21, 4, 20), Blocks.COAL_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, SURFACE, 16);
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        bind(h, c, 3);
        h.assertTrue(c.startWork("mine_ore", "coal", 1).success(), "Ore order not accepted");
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (c.hasActiveWorkOrder()) { started[0] = true; return; }
            if (!started[0]) return;
            int y = c.blockPosition().getY() - h.absolutePos(BlockPos.ZERO).getY();
            h.assertTrue(count(c, Items.COAL) >= 1, "No coal | " + c.getLastTaskResult());
            h.assertTrue(y >= SURFACE, "The order ended in the pit, at " + y + " | " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /** The goal with every milestone's attempts spent: it blocks on the first second. */
    private static Rig blockedGoal(GameTestHelper h) {
        Rig r = rig(h, false);
        start(h, r, 1);
        for (var milestone : goal(h, r).book().milestones()) goal(h, r).counter(milestone.key())[0] = SPENT;
        return r;
    }
}
