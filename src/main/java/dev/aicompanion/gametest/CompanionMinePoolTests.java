package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Run descent (seed 7) of 06.11: the order found him afloat in a water-filled pit under a cliff, the bank three blocks and more over the water.
 * No entrance was found from his height, no walk reached the one found later from a step higher, the restarts in the water were refused, and
 * the order ended «Я в подземном озере, берега не нашёл» five times over. Arena as in the other mine tests, the pit cut into the middle of the
 * mountain and filled with water to three blocks.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMinePoolTests {
    /** The pit: five by five, its floor far under the surface, the water's top ten blocks below the turf. */
    private static final int PIT_HALF = 2, PIT_FLOOR = 9, WATER_TOP = 11;

    private CompanionMinePoolTests() { }

    /** Afloat in the pit, he puts a block under his feet, cuts and lays a staircase up to the surface and digs the mine from there to a diamond. */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsOutOfAWaterFilledPitAndMinesADiamond(GameTestHelper h) {
        mountain(h);
        for (int x = CENTER - PIT_HALF; x <= CENTER + PIT_HALF; x++) for (int z = CENTER - PIT_HALF; z <= CENTER + PIT_HALF; z++) for (int y = PIT_FLOOR; y <= GROUND; y++) {
            h.setBlock(new BlockPos(x, y, z), y <= WATER_TOP ? Blocks.WATER : Blocks.AIR);
        }
        CompanionEntity c = rig(h);
        BlockPos middle = h.absolutePos(new BlockPos(CENTER, WATER_TOP, CENTER));
        c.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + .5);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He said «" + result.message() + "» | " + report(c));
        });
    }

    /**
     * Run void-5 (seed 7) of 06.11: the shaft he had cut down to coal before the mine, a block wide and three deep, was taken for the entrance
     * (under the sky, a floor, room to stand), and the diamonds came back up to its floor, with no way to the player from there. The floor
     * of a pit is no entrance: the one chosen lies on the surface.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void theFloorOfAPitIsNoEntrance(GameTestHelper h) {
        mountain(h);
        int floor = GROUND - 2;
        for (int y = floor; y <= GROUND; y++) h.setBlock(new BlockPos(CENTER, y, CENTER), Blocks.AIR);
        BlockPos pit = h.absolutePos(new BlockPos(CENTER, floor, CENTER));
        h.succeedWhen(() -> {
            CompanionShaftPlan.Entrance entrance = CompanionShaftPlan.chooseEntrance(h.getLevel(), pit, 8);
            h.assertTrue(entrance != null, "No entrance found round the pit");
            h.assertTrue(entrance.feet().getY() == h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY(), "The entrance is at " + entrance.feet().subtract(h.absolutePos(BlockPos.ZERO)).toShortString() + ", the pit's floor at " + floor);
        });
    }
}
