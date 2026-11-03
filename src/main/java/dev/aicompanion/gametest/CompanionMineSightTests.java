package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/** He goes only to ore he sees: a diamond in a cavity with rock between it and his eyes is not walked to, the same cavity opened is. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineSightTests {
    private static final int TUNNEL_START = 2, DIAMOND_AT = 8, CAVITY_FROM = 5, WATCH_TICKS = 300, NEAR_BLOCKS = 5, OPEN_TIMEOUT = 700;

    private CompanionMineSightTests() { }

    /** A tunnel of two cells west of the foot with him at its end, and a diamond eight blocks south with an air pocket on its near side. */
    private static BlockPos setUp(GameTestHelper h, CompanionEntity c, boolean open) {
        carveMine(h, c, TUNNEL_START);
        BlockPos end = c.mineSite().foot().relative(Direction.WEST, TUNNEL_START);
        BlockPos diamond = end.relative(Direction.SOUTH, DIAMOND_AT);
        h.getLevel().setBlock(diamond, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        for (int k = open ? 1 : CAVITY_FROM; k < DIAMOND_AT; k++) {
            h.getLevel().setBlock(end.relative(Direction.SOUTH, k), Blocks.AIR.defaultBlockState(), 2 | 16);
            h.getLevel().setBlock(end.relative(Direction.SOUTH, k).above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        return diamond;
    }

    /** Rock lies between his eyes and the cavity, so its diamond is no reason to dig or walk off. */
    @GameTest(template = "marathon_cave", timeoutTicks = WATCH_TICKS + 100)
    public static void doesNotGoToADiamondInAClosedCavityHeCannotSee(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        BlockPos diamond = setUp(h, c, false);
        double[] nearest = {Double.MAX_VALUE};
        h.onEachTick(() -> nearest[0] = Math.min(nearest[0], c.blockPosition().distSqr(diamond)));
        start(h, c, 1);
        h.runAfterDelay(WATCH_TICKS, () -> {
            h.assertTrue(h.getLevel().getBlockState(diamond).is(Blocks.DEEPSLATE_DIAMOND_ORE) && count(c, Items.DIAMOND) == 0, "He took a diamond he could not see | " + report(c));
            h.assertTrue(nearest[0] > NEAR_BLOCKS * NEAR_BLOCKS, "He went to a diamond he could not see, as near as " + Math.sqrt(nearest[0]) + " | " + report(c));
            h.succeed();
        });
    }

    @GameTest(template = "marathon_cave", timeoutTicks = OPEN_TIMEOUT)
    public static void goesToADiamondInTheSameCavityOpenedAndSeen(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        setUp(h, c, true);
        start(h, c, 1);
        h.succeedWhen(() -> h.assertTrue(count(c, Items.DIAMOND) >= 1, "He did not go to a diamond in plain sight | " + report(c)));
    }
}
