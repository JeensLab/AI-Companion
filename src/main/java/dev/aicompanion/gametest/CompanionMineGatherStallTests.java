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
}
