package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.aicompanion.gametest.CompanionDiamondGoalTests.*;

/**
 * The flow of the long goal find_diamonds around the mine step: the kit made before the first stair, a second step after the
 * supplies ran out, a step without new cells, a hand-over mid-tunnel and a death in the mine. Arenas as in CompanionDiamondGoalTests.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDiamondGoalFlowTests {
    private static final int TIMEOUT = 24000, EMPTY_BAG_TIMEOUT = 36000, WORN_USES_LEFT = 4, BREAD_GIVEN = 3, BENCH_DX = 3;
    private static final int TORCHES = 16, STONE_PICKAXES = 2, FAILURES = 3, DIAMONDS = 2;

    @AfterBatch(batch = "flow_empty_bag") public static void afterEmptyBag(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "flow_second_step") public static void afterSecondStep(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "flow_failed_step") public static void afterFailedStep(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "flow_bread") public static void afterBread(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "flow_death") public static void afterDeath(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Bare hands and bread only; wood, coal and iron stand on the grass: the kit is made first, and only then is the first cell opened. */
    @GameTest(template = "marathon_cave", batch = "flow_empty_bag", timeoutTicks = EMPTY_BAG_TIMEOUT)
    public static void emptyBagGetsPickaxeTorchesAndStonePicksBeforeTheFirstStair(GameTestHelper h) {
        mountain(h, false);
        resources(h);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.setItem(0, new ItemStack(Items.BREAD, 8));
        Rig r = bind(h, c, 3);
        start(h, r, 1);
        long[] ready = {-1, -1, -1};
        h.runAtTickTime(EMPTY_BAG_TIMEOUT - 5, () -> h.fail("Out of time, kit at " + Arrays.toString(ready) + " | " + report(h, r)));
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
            if (ready[0] < 0 && count(c, Items.IRON_PICKAXE) >= 1) ready[0] = h.getTick();
            if (ready[1] < 0 && count(c, Items.TORCH) >= TORCHES) ready[1] = h.getTick();
            if (ready[2] < 0 && count(c, Items.STONE_PICKAXE) >= STONE_PICKAXES) ready[2] = h.getTick();
            if (c.mineSite().opened() == 0) return;
            h.assertTrue(ready[0] >= 0 && ready[1] >= 0 && ready[2] >= 0, "The first mine cell opened before the kit: iron pickaxe at " + ready[0]
                    + ", torches at " + ready[1] + ", stone pickaxes at " + ready[2] + " | " + report(h, r));
            h.succeed();
        });
    }

    /**
     * Two worn stone pickaxes break on the first stairs, he climbs, tops up the stone pickaxes and the torches on the surface and goes
     * back down the same staircase: the stands of the first step stay the prefix of the mine and the goal is never blocked.
     */
    @GameTest(template = "marathon_cave", batch = "flow_second_step", timeoutTicks = TIMEOUT)
    public static void aSecondStepAfterTheSuppliesRanOutTopsUpAndGoesDown(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        ItemStack[] bag = {new ItemStack(Items.IRON_PICKAXE), worn(), worn(), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, TORCHES),
                new ItemStack(Items.COAL, 8), new ItemStack(Items.STICK, 24), new ItemStack(Items.COBBLESTONE, 16)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        h.setBlock(new BlockPos(CENTER + BENCH_DX, GROUND + 1, CENTER), Blocks.CRAFTING_TABLE);
        Rig r = bind(h, c, 3);
        start(h, r, 1);
        int[] phase = {0}, deepest = {GROUND}, openedAfterFirst = {0};
        List<BlockPos> firstStands = new ArrayList<>();
        BlockPos[] entrance = {null};
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time in phase " + phase[0] + " | " + report(h, r)));
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped in phase " + phase[0] + ": " + report(h, r));
            int y = rel(h, c.blockPosition()).getY();
            if (phase[0] == 0) {
                deepest[0] = Math.min(deepest[0], y);
                if (c.mineSite().opened() > 0 && count(c, Items.STONE_PICKAXE) == 0) {
                    entrance[0] = c.mineSite().entrance(); firstStands.addAll(c.mineSite().stands()); openedAfterFirst[0] = c.mineSite().opened(); phase[0] = 1;
                }
                return;
            }
            if (phase[0] == 1) {
                if (count(c, Items.STONE_PICKAXE) < STONE_PICKAXES) return;
                h.assertTrue(y >= GROUND, "The stone pickaxes were topped up underground, at " + y + " | " + report(h, r));
                phase[0] = 2;
            }
            var stands = c.mineSite().stands();
            h.assertTrue(entrance[0].equals(c.mineSite().entrance()) && stands.size() >= firstStands.size() && stands.subList(0, firstStands.size()).equals(firstStands),
                    "A new staircase instead of the old one | " + report(h, r));
            if (y < GROUND) h.assertTrue(count(c, Items.TORCH) >= TORCHES && count(c, Items.STONE_PICKAXE) >= STONE_PICKAXES,
                    "Went down again with " + count(c, Items.TORCH) + " torches and " + count(c, Items.STONE_PICKAXE) + " stone pickaxes | " + report(h, r));
            if (y <= deepest[0] && c.mineSite().opened() > openedAfterFirst[0]) h.succeed();
        });
    }

    /** Bedrock where the staircase would go: every step opens nothing, each one is a failure, and the third blocks the goal with a reason. */
    @GameTest(template = "tall_tree", batch = "flow_failed_step", timeoutTicks = TIMEOUT)
    public static void aStepWithoutNewCellsIsAFailure(GameTestHelper h) {
        Rig r = bare(h);
        CompanionEntity c = r.c();
        ItemStack[] kit = supplies(32, 4);
        for (int slot = 0; slot < kit.length; slot++) c.setItem(slot, kit[slot]);
        start(h, r, 1);
        boolean[] seen = new boolean[FAILURES + 1];
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time, failures seen " + Arrays.toString(seen) + " | " + report(h, r)));
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died | " + report(h, r));
            var goal = goal(h, r);
            h.assertTrue(goal != null, "The goal is gone");
            int failures = goal.counter(goal.book().milestones().getLast().key())[1];
            if (failures >= 0 && failures < seen.length) seen[failures] = true;
            h.assertTrue(c.mineSite().opened() == 0 || goal.state() == CompanionAgenda.State.BLOCKED, "Cells opened in bedrock: " + c.mineSite().opened() + " | " + report(h, r));
            if (goal.state() != CompanionAgenda.State.BLOCKED) return;
            h.assertTrue(failures == FAILURES, "Blocked with " + failures + " failures counted, not " + FAILURES + " | " + report(h, r));
            h.assertTrue(seen[1] && seen[2], "The failures did not rise one step at a time: " + Arrays.toString(seen) + " | " + report(h, r));
            h.assertTrue(!goal.reason().isBlank(), "Blocked without a reason | " + report(h, r));
            h.succeed();
        });
    }

    /** The player asks for bread in the tunnel: he climbs, tosses it to the player's feet, goes back into the same tunnel and finishes the goal. */
    @GameTest(template = "marathon_cave", batch = "flow_bread", timeoutTicks = TIMEOUT)
    public static void handsOverBreadMidTunnelAndGoesBack(GameTestHelper h) {
        Rig r = rig(h, true, supplies(32, 4));
        CompanionEntity c = r.c();
        start(h, r, DIAMONDS);
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), r.p().getUUID(), "diamond-bread", () -> false);
        CompletableFuture<?>[] order = {null};
        int[] tunnelBefore = {0}, highest = {0};
        BlockPos[] entrance = {null};
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time, order " + (order[0] == null ? "never given" : order[0].isDone() ? "done" : "running") + " | " + report(h, r)));
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            if (order[0] == null) {
                if (rel(h, c.blockPosition()).getY() != DIAMOND_FEET || advanced(c) < 1) return;
                tunnelBefore[0] = advanced(c); entrance[0] = c.mineSite().entrance();
                order[0] = CompanionPlanRunner.run(List.of(new PlannedAction("give_items", 0, BREAD_GIVEN, "minecraft:bread")), port);
                return;
            }
            if (!order[0].isDone()) highest[0] = Math.max(highest[0], rel(h, c.blockPosition()).getY());
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
        });
        h.succeedWhen(() -> {
            h.assertTrue(order[0] != null && order[0].isDone(), "The bread order is not finished");
            h.assertTrue(highest[0] >= GROUND, "He gave the bread without climbing, highest point " + highest[0] + " | " + report(h, r));
            int tossed = 0;
            for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, r.p().getBoundingBox().inflate(8))) if (item.getItem().is(Items.BREAD)) tossed += item.getItem().getCount();
            for (int i = 0; i < r.p().getInventory().getContainerSize(); i++) if (r.p().getInventory().getItem(i).is(Items.BREAD)) tossed += r.p().getInventory().getItem(i).getCount();
            h.assertTrue(tossed == BREAD_GIVEN, "The bread that reached the player: " + tossed + " | " + report(h, r));
            h.assertTrue(state(h, r) == CompanionAgenda.State.COMPLETED && count(c, Items.DIAMOND) >= DIAMONDS, "The goal did not go on after the hand-over | " + report(h, r));
            h.assertTrue(entrance[0].equals(c.mineSite().entrance()) && c.mineSite().tunnels().size() == 1 && advanced(c) >= tunnelBefore[0], "Not the same tunnel | " + report(h, r));
        });
    }

    /** He dies in the tunnel: his things fall tagged as the owner's, another Oleg comes back and picks them up, and the goal is neither lost nor blocked. */
    @GameTest(template = "marathon_cave", batch = "flow_death", timeoutTicks = TIMEOUT)
    public static void deathInTheMineKeepsTheGoal(GameTestHelper h) {
        Rig r = rig(h, true, supplies(32, 4));
        CompanionEntity c = r.c();
        start(h, r, DIAMONDS);
        UUID[] oldId = {null};
        int[] fallen = {0};
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time, died " + (oldId[0] != null) + " | " + report(h, r)));
        h.onEachTick(() -> {
            if (oldId[0] != null || rel(h, c.blockPosition()).getY() != DIAMOND_FEET || advanced(c) < 1) return;
            oldId[0] = c.getUUID();
            c.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            fallen[0] = belongings(h, r);
        });
        h.succeedWhen(() -> {
            h.assertTrue(oldId[0] != null && !c.isAlive(), "No death in the tunnel");
            h.assertTrue(fallen[0] > 0, "His things did not fall tagged as the owner's: " + fallen[0]);
            CompanionEntity back = CompanionEvents.getOwnedCompanion(r.p());
            h.assertTrue(back != null && !back.getUUID().equals(oldId[0]), "He did not come back");
            var goal = goal(h, r);
            h.assertTrue(goal != null && goal.deaths() == 1 && goal.state().outstanding() && goal.state() != CompanionAgenda.State.BLOCKED,
                    "The goal after the death: " + (goal == null ? "gone" : goal.state() + " deaths " + goal.deaths() + " '" + goal.reason() + "'"));
            h.assertTrue(goal.counter(goal.book().milestones().getLast().key())[1] == 0, "The death was counted as a failure of the mine");
            h.assertTrue(belongings(h, r) == 0 && count(back, Items.IRON_PICKAXE) >= 1, "His things are not picked up: " + belongings(h, r) + " | bag: " + back.getStoredItemsSummary());
        });
    }

    private static int belongings(GameTestHelper h, Rig r) {
        AABB arena = new AABB(h.absolutePos(BlockPos.ZERO).getCenter(), h.absolutePos(new BlockPos(SIZE, SIZE, SIZE)).getCenter()).inflate(SIZE);
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, arena,
                item -> item.getPersistentData().hasUUID("aicompanion:belongings") && item.getPersistentData().getUUID("aicompanion:belongings").equals(r.p().getUUID())).size();
    }

    private static ItemStack worn() {
        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - WORN_USES_LEFT);
        return pickaxe;
    }

    /** South of the start, off the way north where the stairs go: three trunks, a stone outcrop with coal on its north face and iron on its west face. */
    private static void resources(GameTestHelper h) {
        for (int[] trunk : new int[][]{{18, 30}, {20, 34}, {16, 26}}) for (int y = GROUND + 1; y <= GROUND + 5; y++) h.setBlock(new BlockPos(trunk[0], y, trunk[1]), Blocks.OAK_LOG);
        for (int x = 30; x <= 35; x++) for (int z = 28; z <= 31; z++) for (int y = GROUND + 1; y <= GROUND + 4; y++) {
            boolean coal = z == 28 && x <= 33 && y <= GROUND + 3, iron = x == 30 && z >= 29 && z <= 30 && y <= GROUND + 2;
            h.setBlock(new BlockPos(x, y, z), coal ? Blocks.COAL_ORE : iron ? Blocks.IRON_ORE : Blocks.STONE);
        }
    }
}
