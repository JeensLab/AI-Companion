package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionSingleRouteTests.box;
import static dev.aicompanion.gametest.CompanionSingleRouteTests.count;
import static dev.aicompanion.gametest.CompanionSingleRouteTests.spawn;

/**
 * Run p1d/void-2 (seed 20261102): the gathering goals stood on one spot for six thousand ticks, every target of theirs «no route», and the
 * work guard that ends an order after ninety seconds without progress never looked, as the work's own tick does not run while a goal gathers.
 * The arena: his closed room, and the soil he wants all round it.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineGatherStallTests {
    private static final int TIMEOUT = 3300;
    /** The guard's ninety seconds and a little more; the goals' own limits run to three thousand six hundred ticks and over. */
    private static final int SETTLED_BY = 2400;
    private static final int STAND_X = 3, STAND_Y = 2, STAND_Z = 15, TOP_Y = 23, LAST = 31;

    /**
     * His room is a closed box of bedrock, two cells by two and three high, so that his drifting inside it never reaches the two blocks
     * the guard counts as a move. All round it a hill of soil with uneven tops reaches into his search: the search finds many blocks, each a
     * target of its own to be refused, and none has a way.
     */
    private static void island(GameTestHelper h) {
        box(h, 0, 0, 0, LAST, TOP_Y, LAST, Blocks.AIR);
        box(h, 0, 0, 0, LAST, 1, LAST, Blocks.BEDROCK);
        for (int x = 0; x <= LAST; x++) for (int z = 0; z <= LAST; z++) box(h, x, 1, z, x, 1 + (x * 3 + z * 7) % 6, z, Blocks.DIRT);
        box(h, STAND_X - 1, 0, STAND_Z - 1, STAND_X + 2, 5, STAND_Z + 2, Blocks.BEDROCK);
        box(h, STAND_X, STAND_Y, STAND_Z, STAND_X + 1, 4, STAND_Z + 1, Blocks.AIR);
    }

    private static void ends(GameTestHelper h, CompanionEntity c, long[] id, BlockPos stand) {
        h.onEachTick(() -> {
            if (h.getTick() == SETTLED_BY) {
                CompanionActionResult result = c.workResult(id[0]);
                h.assertTrue(result != null && !c.isWorking() && !c.isGatheringTask(),
                        "Still gathering at tick " + SETTLED_BY + ": " + c.getLastTaskResult() + " | " + c.watchDetail() + " at " + h.relativePos(c.blockPosition()).toShortString());
                h.assertTrue(!result.success(), "Reported a success with no soil in reach: " + result.message());
                h.assertTrue(c.blockPosition().distSqr(stand) <= 9, "Left his platform to a place he could not have reached: " + h.relativePos(c.blockPosition()).toShortString());
                h.assertTrue(count(c, Items.DIRT) == 0, "Soil fell into the bag from nowhere");
                h.succeed();
            }
        });
    }

    /** gather_dirt with all the soil behind the wall: each block is refused one by one, and the order has to come to an end, not wander for an hour. */
    @GameTest(template = "tall_tree", timeoutTicks = TIMEOUT)
    public static void soilOrderWithNoWayToSoilEnds(GameTestHelper h) {
        island(h);
        CompanionEntity c = spawn(h, STAND_X, STAND_Y, STAND_Z);
        c.setFollowing(false);
        BlockPos stand = h.absolutePos(new BlockPos(STAND_X, STAND_Y, STAND_Z));
        var started = c.startWork("gather_dirt", "", 8);
        h.assertTrue(started.success(), "Order not accepted: " + started.message());
        ends(h, c, new long[]{c.activeWorkId()}, stand);
    }

    private static final int LEDGE_X = 15, LEDGE_Z = 15, LEDGE_Y = 8, SEEN_BY = 1200;

    /** A bedrock ledge in a closed ring, six blocks over a floor with a grove of real trees round it: all in his search, none with a way. */
    private static void ledgeAboveTrees(GameTestHelper h) {
        box(h, 0, 0, 0, LAST, TOP_Y, LAST, Blocks.AIR);
        box(h, 0, 0, 0, LAST, 1, LAST, Blocks.DIRT);
        box(h, LEDGE_X - 1, 0, LEDGE_Z - 1, LEDGE_X + 2, LEDGE_Y - 1, LEDGE_Z + 2, Blocks.BEDROCK);
        box(h, LEDGE_X - 1, LEDGE_Y, LEDGE_Z - 1, LEDGE_X + 2, LEDGE_Y + 2, LEDGE_Z + 2, Blocks.BEDROCK);
        box(h, LEDGE_X, LEDGE_Y, LEDGE_Z, LEDGE_X + 1, LEDGE_Y + 2, LEDGE_Z + 1, Blocks.AIR);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int fx = 2; fx <= 29; fx += 3) for (int fz = 2; fz <= 29; fz += 3) {
            if (Math.hypot(fx - LEDGE_X, fz - LEDGE_Z) < 7) continue;
            box(h, fx, 2, fz, fx, 6, fz, Blocks.OAK_LOG);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 5; y <= 7; y++) {
                if (x == 0 && z == 0 && y <= 6) continue;
                h.setBlock(new BlockPos(fx + x, y, fz + z), leaves);
            }
        }
    }

    /**
     * Probe mine-world-f lava, 4050-4160: a gather_wood order on a ledge over a cliff, every tree below with no way to it. Each approach
     * failed with «безопасно не добраться, ищу другую» and he stood there until the guard ended it ninety seconds later, only for the plan
     * to order the same wood again. With no route to any tree the order ends in seconds, with the answer of «no tree».
     */
    @GameTest(template = "tall_tree", timeoutTicks = TIMEOUT)
    public static void aTreeWithNoRouteEndsTheGatherInsteadOfStanding(GameTestHelper h) {
        ledgeAboveTrees(h);
        CompanionEntity c = spawn(h, LEDGE_X, LEDGE_Y, LEDGE_Z);
        c.setFollowing(false);
        BlockPos stand = h.absolutePos(new BlockPos(LEDGE_X, LEDGE_Y, LEDGE_Z));
        var started = c.startWork("gather_wood", "", 4);
        h.assertTrue(started.success(), "Order not accepted: " + started.message());
        long id = c.activeWorkId();
        h.onEachTick(() -> {
            if (h.getTick() == SEEN_BY) {
                CompanionActionResult result = c.workResult(id);
                h.assertTrue(result != null && !c.isWorking() && !c.isGatheringTask(),
                        "Still gathering at tick " + SEEN_BY + ": " + c.getLastTaskResult() + " | " + c.watchDetail() + " at " + h.relativePos(c.blockPosition()).toShortString());
                h.assertTrue(!result.success() && result.message().contains("Не нашёл доступного дерева"), "Not the answer of «no tree»: " + result.message());
                h.assertTrue(c.blockPosition().distSqr(stand) <= 9, "Left his ledge to a place he could not have reached: " + h.relativePos(c.blockPosition()).toShortString());
                h.assertTrue(count(c, Items.OAK_LOG) == 0, "Logs fell into the bag from nowhere");
                h.succeed();
            }
        });
    }
}
