package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;

/** Playtest of 03.11: a meal instead of a climb after a wound with no foe, and water that has flowed into his own tunnel while he walks it. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineMealAndWaterTests {
    private static final int FAR_STAND = 10, CHECK_AT = 160, BREAD = 8, TUNNEL = 14, FIRST_SPRING = 4, SECOND_SPRING = 9, PAST_SPRINGS = 11;
    private static final float WOUNDED = 6;
    private static final int CHAMBER_STEP = 4, CHAMBER_REACH = 5, ENTRANCE_REACH = 6;

    private CompanionMineMealAndWaterTests() { }

    /** `/damage 14` deep down, no foe: he eats where he stands and digs on, the stairs up are not climbed for a wound a meal mends. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void woundedWithNoFoeAndFoodHeEatsAndDigsOnInsteadOfClimbing(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        c.setHealth(WOUNDED);
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            String trace = " | bread " + count(c, Items.BREAD) + ", health " + c.getHealth() + ", retreats " + c.mineSite().retreats() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.mineSite().retreats() == 0, "He climbed for a wound with food in the bag" + trace);
            if (h.getTick() - begun < CHECK_AT) return;
            h.assertTrue(count(c, Items.BREAD) < BREAD, "No bread was eaten" + trace);
            h.assertTrue(c.getHealth() > WOUNDED, "The meals did not bring him above where he began" + trace);
            h.assertTrue(c.isWorking(), "The order ended instead of going on" + trace);
            h.succeed();
        });
    }

    /** Two springs open into the tunnel he walks out along: he seals them on the way, as before a swing, and does not stand in the water until the step runs out. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void waterThatFlowedIntoHisTunnelIsSealedOnTheWalkAlongIt(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        carveMine(h, c, TUNNEL);
        BlockPos foot = c.mineSite().foot();
        BlockPos first = foot.relative(Direction.WEST, FIRST_SPRING).north(), second = foot.relative(Direction.WEST, SECOND_SPRING).south().above();
        h.getLevel().setBlock(first, Blocks.WATER.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(second, Blocks.WATER.defaultBlockState(), 2 | 16);
        place(c, foot.relative(Direction.WEST, 1));
        start(h, c, 1);
        h.onEachTick(() -> {
            String trace = " | stalls " + c.mineSite().underwaterStalls() + ", at " + c.blockPosition().toShortString() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.mineSite().underwaterStalls() == 0, "He stalled in the water and started the mine over" + trace);
            for (int k = 1; k <= TUNNEL; k++) {
                BlockPos cell = foot.relative(Direction.WEST, k);
                h.assertTrue(h.getLevel().getBlockState(cell).getCollisionShape(h.getLevel(), cell).isEmpty() && h.getLevel().getBlockState(cell.above()).getCollisionShape(h.getLevel(), cell.above()).isEmpty(),
                        "His own tunnel was walled up at cell " + k + " " + cell.toShortString() + " of " + TUNNEL + trace);
            }
        });
        h.succeedWhen(() -> {
            String trace = " | at " + c.blockPosition().toShortString() + " | " + report(c);
            h.assertTrue(!h.getLevel().getFluidState(first).is(FluidTags.WATER) && !h.getLevel().getFluidState(second).is(FluidTags.WATER), "A spring is still open" + trace);
            BlockPos from = c.blockPosition().subtract(foot);
            h.assertTrue(from.getX() * Direction.WEST.getStepX() + from.getZ() * Direction.WEST.getStepZ() >= PAST_SPRINGS, "He has not walked past the springs" + trace);
        });
    }

    /**
     * Mine-world run 233501 (come): after a resupply he stands in a chamber of his own a few blocks beside the stairs, four under the entrance and under a roof. He did not
     * go for the far foot of the stairs, a search that failed and ended in a new stair from the surface: he goes to the entrance, which is near, and down his own stairs.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void offTheStairsNearTheEntranceHeGoesDownHisOwnStairsFromTheEntrance(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos entrance = stairs.get(0), beside = stairs.get(CHAMBER_STEP);
        for (int k = 1; k <= CHAMBER_REACH; k++) {
            h.getLevel().setBlock(beside.offset(k, 0, 0), Blocks.AIR.defaultBlockState(), 2 | 16);
            h.getLevel().setBlock(beside.offset(k, 1, 0), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, beside.offset(CHAMBER_REACH, 0, 0));
        long id = start(h, c, 1);
        boolean[] atEntrance = {false};
        h.onEachTick(() -> atEntrance[0] |= c.blockPosition().distSqr(entrance) <= ENTRANCE_REACH);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(atEntrance[0], "He never went to the entrance: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 0, "He started a new stair instead of using his own: restarts " + c.mineSite().restarts() + " | " + report(c));
        });
    }
}
