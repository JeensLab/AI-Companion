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

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.DIAMOND_FEET;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * A safe lit cave opened by the first tunnel advance, as in {@link CompanionCaveTests}: the chamber is at x 26..30, y 2..4, z 3..7 and the
 * tunnel's breach is the two cells at x 25, z 3, so the cave air next to his mine is the cells at x 26, z 3.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineCaveSideTests {
    private static final int CHAMBER_X0 = 26, CHAMBER_X1 = 30, CHAMBER_Z0 = 3, CHAMBER_Z1 = 7, STRIP_Z = 1, STRIP_X0 = 22, STRIP_X1 = 25, BREACH_X = 25;
    private static final BlockPos POST = new BlockPos(CHAMBER_X0, DIAMOND_FEET, CHAMBER_Z0 + 1), PLUG = new BlockPos(CHAMBER_X0, DIAMOND_FEET, CHAMBER_Z0);
    /**
     * Where he stands in the post's cell, as offsets into it along the arena's x and z: z on the side of his stairs, so that the post (0.25 wide at the middle)
     * is beside him and not on him, and x against the rock, so that he is within the 2.5 blocks of his stairs at which the way back counts as done.
     * He is held there for a few ticks of the way back.
     */
    private static final double BESIDE_THE_POST = 0.07, AGAINST_THE_WALL = 0.31;
    private static final int HOLD_TICKS = 10;

    /**
     * He comes back from the cave and ends up in a cell that holds a fence post (of a mine shaft the cave opened into), the post beside him,
     * a block from his stairs. The cell is no cell of the cave air and no open cell of his mine, yet it is on the cave side: the plugging
     * starts only once he is back on his side of the breach, and he digs on to the diamond in the tunnel.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void walksOnFromAFencePostBeforeThePlugGoesIn(GameTestHelper h) {
        mountain(h, false);
        for (int x = STRIP_X0; x <= STRIP_X1; x++) h.setBlock(new BlockPos(x, DIAMOND_FEET - 1, STRIP_Z), Blocks.DEEPSLATE_DIAMOND_ORE);
        for (int x = CHAMBER_X0; x <= CHAMBER_X1; x++) for (int z = CHAMBER_Z0; z <= CHAMBER_Z1; z++) for (int y = DIAMOND_FEET; y <= DIAMOND_FEET + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        h.setBlock(new BlockPos(CHAMBER_X0 + 1, DIAMOND_FEET, CHAMBER_Z1), Blocks.TORCH);
        h.setBlock(new BlockPos(CHAMBER_X0 + 3, DIAMOND_FEET, CHAMBER_Z1), Blocks.TORCH);
        h.setBlock(new BlockPos(CHAMBER_X0 + 2, DIAMOND_FEET + 3, 5), Blocks.GLOWSTONE);
        h.setBlock(POST, Blocks.OAK_FENCE);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        boolean[] back = {false};
        int[] held = {0};
        h.onEachTick(() -> {
            if (held[0] < HOLD_TICKS && "BACK".equals(c.expeditionCavePhase())) {
                back[0] = true;
                held[0]++;
                BlockPos post = h.absolutePos(POST), origin = h.absolutePos(BlockPos.ZERO);
                BlockPos east = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), south = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
                c.teleportTo(post.getX() + .5 + (AGAINST_THE_WALL - .5) * east.getX() + (BESIDE_THE_POST - .5) * south.getX(), post.getY(),
                        post.getZ() + .5 + (AGAINST_THE_WALL - .5) * east.getZ() + (BESIDE_THE_POST - .5) * south.getZ());
            }
            boolean onCaveSide = rel(h, c.blockPosition()).getX() > BREACH_X;
            h.assertTrue(!(back[0] && onCaveSide && "SEAL".equals(c.expeditionCavePhase())), "He began to plug the breach from the cave side at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
        });
        h.succeedWhen(() -> {
            h.assertTrue(back[0], "SETUP: he never came back from the cave | " + report(c) + " | phase " + c.expeditionCavePhase());
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not dig on from the post: " + result.message() + " | " + report(c));
            h.assertTrue(!h.getBlockState(PLUG).isAir(), "The breach is not plugged | " + report(c));
        });
    }
}
