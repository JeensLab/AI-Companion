package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.DEEPSLATE_TOP;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Runs p1b/lava and p1b/void-4 (seed 20261102): the goal stopped five times in a row at the same spot, «Не смог дойти до входа в шахту».
 * Arena as in the other mine tests; the pits are small copies of what the world held where he stood.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK6Tests {
    private static final int PIT = 11, FAR_ENTRANCE = 200, SPENT_RESTARTS = 3;
    /** How far into the cell, along z, he stands from a fence post in it: the post is 0.25 wide at the middle, he is 0.6 wide. */
    private static final double BESIDE_THE_POST = 0.93;

    /** A closed pit of three by three cells under the deepslate top, three high; the cell in its middle is returned. */
    private static BlockPos pit(GameTestHelper h) {
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        return h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
    }

    /**
     * Run p1b/lava: the cell his body is in holds a fence post of a mine shaft he broke into (the post stands beside him, not on him), so the
     * cell is no place to stand and no side was found from it: the entrance was far, the walk to it failed, and the order ended five times.
     * He starts the mine again from the open cell beside the post.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainBesideAFencePostHeStandsIn(GameTestHelper h) {
        mountain(h, true);
        BlockPos middle = pit(h);
        h.getLevel().setBlock(middle, Blocks.OAK_FENCE.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + BESIDE_THE_POST);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(middle.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again beside the post: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * Run p1b/void-4: three restarts spent and carried, he stands in a pit that was refused on every side, and the entrance is out of reach.
     * Neither a free side nor a plug of his own tells him he may dig, yet the one restart past the cap is his: he breaks the floor until a
     * side is free and the stairs go on.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void digsOutOfAPitRefusedOnEverySide(GameTestHelper h) {
        mountain(h, true);
        BlockPos middle = pit(h);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + .5);
        BlockPos far = h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER));
        c.mineSite().start(far, Direction.NORTH);
        c.mineSite().newStep(h.getLevel().getGameTime());
        for (int i = 0; i < SPENT_RESTARTS; i++) c.mineSite().restart(far, Direction.NORTH);
        for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(middle, d);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(middle.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood in the refused pit: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == SPENT_RESTARTS + 1, "Restarts " + c.mineSite().restarts());
        });
    }
}
