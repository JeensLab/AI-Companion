package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * The stairs he dug stay whole: no landing or tunnel opens the floor of one of his steps, the ore under a step is left in place, and a cut
 * in the stairs above him does not cost him the part below.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineIntegrityTests {
    private static final int TURN_AT = 6, FLOOR_CUT_A = 4, FLOOR_CUT_B = 7, STANDS_ABOVE_FOOT = 3, ORE_WAIT_TICKS = 300, LOCATE_TICKS = 200, CHAMBER_STAND = 6, CHAMBER_LENGTH = 3,
            COAL_BESIDE = -1, COAL_BELOW = -4, COAL_BEHIND = 1, FIRST_STEPS = 4;

    private CompanionMineIntegrityTests() { }

    /** A step of the stairs stands one level above a cell beside him: that cell is the floor of the step, and a landing there would take it out from under. */
    @GameTest(template = "marathon_cave")
    public static void aLandingNeverOpensTheFloorOfOneOfHisSteps(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, TURN_AT + 2);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        BlockPos at = stairs.get(TURN_AT), floor = CompanionShaftPlan.landing(at, Direction.WEST), step = floor.above();
        for (BlockPos cell : CompanionShaftPlan.column(step)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        site.reached(step);

        int west = CompanionShaftPlan.turnCost(level, at, Direction.WEST, site.seals(), site.avoid());
        int east = CompanionShaftPlan.turnCost(level, at, Direction.EAST, site.seals(), site.avoid());
        h.assertTrue(west == CompanionShaftPlan.NO_TURN, "A landing at " + rel(h, floor).toShortString() + ", the floor of the step above it, is allowed at the cost " + west);
        h.assertTrue(east != CompanionShaftPlan.NO_TURN, "The free side is refused too");
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, at, Direction.NORTH, site.seals(), site.avoid()) == Direction.EAST, "The turn did not go to the free side");
        h.assertTrue(site.avoid().forbidden().contains(floor), "avoid() does not show the floor of the step");
        h.succeed();
    }

    /** Coal is the floor of the step next to the foot and gravel lies under it: he looks at it from the foot with the torches running low, and leaves it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void coalUnderTheNextStepIsLeftWhereItLies(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos coal = stairs.get(stairs.size() - 2).below();
        h.getLevel().setBlock(coal, Blocks.COAL_ORE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(coal.below(), Blocks.GRAVEL.defaultBlockState(), 2 | 16);
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.TORCH)) c.setItem(slot, new ItemStack(Items.TORCH, 2));
        start(h, c, 1);
        long[] atFoot = {-1};
        h.onEachTick(() -> {
            String stage = c.expeditionStage();
            if (atFoot[0] < 0 && (stage.equals("BRANCH") || stage.equals("DESCEND"))) atFoot[0] = h.getTick();
            if (atFoot[0] < 0 || h.getTick() - atFoot[0] < ORE_WAIT_TICKS) return;
            h.assertTrue(h.getLevel().getBlockState(coal).is(Blocks.COAL_ORE), "The coal under the step at " + rel(h, coal).toShortString() + " was taken: "
                    + h.getLevel().getBlockState(coal).getBlock() + " | " + report(c));
            h.assertTrue(c.mineSite().verify(h.getLevel()) == c.mineSite().stands().size() && c.mineSite().stands().size() >= stairs.size() - 1, "The stairs were cut to " + c.mineSite().stands().size());
            h.succeed();
        });
    }

    /** Two floors above him are gone: the stairs under his feet are the way he is going, and the check of the record leaves them. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void twoCutFloorsAboveHimDoNotCostTheStairsBelow(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int stands = stairs.size() - 1;
        for (int cut : new int[]{FLOOR_CUT_A, FLOOR_CUT_B}) h.getLevel().setBlock(stairs.get(cut).below(), Blocks.AIR.defaultBlockState(), 2 | 16);
        BlockPos stand = stairs.get(stairs.size() - 1 - STANDS_ABOVE_FOOT);
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            h.assertTrue(c.mineSite().stands().size() >= stands, "The stairs below the cut were dropped: " + c.mineSite().stands().size() + " of " + stands + " stands, stage " + c.expeditionStage());
            if (h.getTick() - begun >= LOCATE_TICKS) h.succeed();
        });
    }

    /**
     * A stone-gathering step cut a side chamber one level above the floor of his stairs and left him standing in it: the next stand is on the
     * other side of the wall, a straight push goes nowhere. He walks round it (or back to a stand of the stairs) and the mine goes on to the ore.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void walksOutOfASideChamberBackOntoTheStairs(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos stand = stairs.get(CHAMBER_STAND);
        for (int dz = 0; dz < CHAMBER_LENGTH; dz++) for (int dx = 1; dx <= 2; dx++) for (int dy = 1; dy <= 3; dy++) {
            h.getLevel().setBlock(stand.offset(-dx, dy, -dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos inside = stand.offset(-1, 1, -1);
        c.teleportTo(inside.getX() + .5, inside.getY(), inside.getZ() + .5);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not get out of the chamber at " + rel(h, inside).toShortString() + ": " + report(c));
        });
    }

    /** Coal fetched for torches beside the top of his stairs is tunnelled to from a step: the tunnel never goes through the floor of another step. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCoalTunnelAtTheEntranceLeavesTheFloorsOfHisFirstSteps(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        h.getLevel().setBlock(entrance.offset(COAL_BESIDE, COAL_BELOW, COAL_BEHIND), Blocks.COAL_ORE.defaultBlockState(), 2 | 16);
        h.assertTrue(c.startWork("mine_ore", "minecraft:coal_ore", 1).success(), "Coal order not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.onEachTick(() -> {
            for (BlockPos stand : stairs.subList(0, FIRST_STEPS)) {
                h.assertTrue(!h.getLevel().getBlockState(stand.below()).isAir(), "The floor of the step at " + rel(h, stand).toShortString() + " was dug out | " + report(c));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking() && c.workResult(id) != null, "Work still going: " + report(c));
        });
    }
}
