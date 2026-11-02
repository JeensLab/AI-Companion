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
 * The arena: his platform, a wall no walk crosses, and the soil he wants on the far side.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineGatherStallTests {
    private static final int TIMEOUT = 3300;
    /** The guard's ninety seconds and a little more; the goals' own limits run to three thousand six hundred ticks and over. */
    private static final int SETTLED_BY = 2400;
    private static final int STAND_X = 4, STAND_Y = 2, STAND_Z = 16, WALL_FROM = 7, WALL_TO = 14, ISLAND_X = 15;

    /** His platform at the west edge, a wall eight cells thick across the whole arena, soil from the far bank on. */
    private static void island(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, WALL_FROM, 0, 0, WALL_TO, 23, 31, Blocks.BEDROCK);
        // Walled in on his side too: a walk round the arena's edge would be a way to the soil.
        box(h, 0, 0, 0, 0, 23, 31, Blocks.BEDROCK); box(h, 0, 0, 0, WALL_FROM, 23, 0, Blocks.BEDROCK); box(h, 0, 0, 31, WALL_FROM, 23, 31, Blocks.BEDROCK);
        box(h, ISLAND_X, 1, 0, 31, 1, 31, Blocks.DIRT);
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
