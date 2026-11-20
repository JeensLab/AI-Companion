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

/** He goes only to ore he sees: a diamond in a cavity with rock between it and his eyes is not walked to, one in an opened passage is. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineSightTests {
    private static final int TUNNEL_START = 2, DIAMOND_AT = 7, CLOSED_AT = 6, CLOSED_ASIDE = 3, CAVITY_FROM = 4, TIMEOUT = 700;

    private CompanionMineSightTests() { }

    private static void air(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    private static void ore(GameTestHelper h, BlockPos pos) { h.getLevel().setBlock(pos, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16); }

    /** A tunnel of two cells west of the foot with him at its end, and an open passage south to a diamond seven blocks off; returns the end. */
    private static BlockPos setUp(GameTestHelper h, CompanionEntity c) {
        carveMine(h, c, TUNNEL_START);
        BlockPos end = c.mineSite().foot().relative(Direction.WEST, TUNNEL_START);
        ore(h, end.relative(Direction.SOUTH, DIAMOND_AT));
        for (int k = 1; k < DIAMOND_AT; k++) air(h, end.relative(Direction.SOUTH, k));
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        return end;
    }

    /** The order begins once he stands on the floor after the teleport: in the air no route is searched, and a walk to the ore would be given up at once. */
    private static long started(GameTestHelper h, CompanionEntity c) {
        h.assertTrue(c.onGround(), "He has not landed yet");
        return start(h, c, 1);
    }

    /**
     * A diamond nearer than the one in the open passage lies in a closed pocket three blocks aside, with rock between it and his eyes.
     * The look at ore (twelve blocks) must run and pass it over: the one in sight is taken, the one behind rock stays.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesTheDiamondInSightAndNotTheNearerOneInAClosedCavity(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        BlockPos end = setUp(h, c);
        BlockPos closed = end.relative(Direction.EAST, CLOSED_ASIDE).relative(Direction.SOUTH, CLOSED_AT);
        ore(h, closed);
        for (int k = CAVITY_FROM; k < CLOSED_AT; k++) air(h, end.relative(Direction.EAST, CLOSED_ASIDE).relative(Direction.SOUTH, k));
        long[] id = {0};
        h.succeedWhen(() -> {
            id[0] = id[0] == 0 ? started(h, c) : id[0];
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "He took no diamond | " + report(c));
            h.assertTrue(h.getLevel().getBlockState(closed).is(Blocks.DEEPSLATE_DIAMOND_ORE), "He went for the diamond he could not see | " + report(c));
        });
    }

    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void goesToADiamondInTheSameCavityOpenedAndSeen(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        setUp(h, c);
        long[] id = {0};
        h.succeedWhen(() -> {
            id[0] = id[0] == 0 ? started(h, c) : id[0];
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "He did not go to a diamond in plain sight | " + report(c));
        });
    }
}
