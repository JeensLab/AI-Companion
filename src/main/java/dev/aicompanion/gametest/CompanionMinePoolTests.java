package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.count;
import static dev.aicompanion.gametest.CompanionMineArena.ended;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;
import static dev.aicompanion.gametest.CompanionMineArena.start;

/**
 * Run void-5 (seed 7): the mine began in a pit full of water under a cliff, and no walk leads from the water to the surface. He gets out as a
 * player does, on a block under his feet and a staircase of his own, and the entrance is chosen on the surface, never on the floor of a pit.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMinePoolTests {
    /** The pit: five by five, its floor far under the surface, the water's top ten blocks below the turf. */
    private static final int PIT_HALF = 2, PIT_FLOOR = 9, WATER_TOP = 11;

    private CompanionMinePoolTests() { }

    /** Afloat in the pit, he puts a block under his feet, cuts and lays a staircase up to the surface and digs the mine from there to a diamond. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
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
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = 100)
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
