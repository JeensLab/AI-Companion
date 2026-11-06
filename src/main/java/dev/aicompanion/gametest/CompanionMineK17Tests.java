package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;

/**
 * Runs u1d/void-4 and u1a/void-4: the creeper he bobbed into sight of and out of it for 50000 ticks, its fuse going and coming with his
 * every bob, and the climb that gave up the rock with an iron pickaxe in the bag because its wear was kept for ore.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK17Tests {
    private static final int IRON_WORN = 191;

    private CompanionMineK17Tests() { }

    /** u1a/void-4: the only pickaxe left is the iron one, its wear below what is kept for ore: the way out is worth more than the ore, and rock is cut with it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsHisWayOutWithTheWornIronPickaxeWhenItIsTheLastHeHas(GameTestHelper h) {
        CompanionEntity c = CompanionMineClimbOutTests.roofedPit(h, false);
        c.getItem(1).setDamageValue(IRON_WORN);
        BlockPos stand = c.mineSite().stands().get(0);
        int surface = h.absolutePos(new BlockPos(0, GROUND - 1, 0)).getY();
        CompanionMineClimbOutTests.climb(h, c, stand, result -> h.assertTrue(result.success() && c.getBlockY() >= surface,
                "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c)));
    }
}
