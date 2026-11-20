package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * Run m15b/descent (seed 20261102): he stands on the grass of a dense jungle in a pocket he cut in the leaves, and the leaves shut all four
 * sides of the first step. Leaves were no block to dig, so no side took stairs: «Дальше лестницу не прокопать: некуда свернуть» twenty-five
 * times and the goal stood twice. The leaves are cut as part of the digging, with the pickaxe, like any other block in the way.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineCanopyTests {
    private static final int SHELL = 2, SHELL_HEIGHT = 3, CAGE_AT = 8;

    private CompanionMineCanopyTests() { }

    /** No record of a mine yet: the entrance is chosen in the pocket, where every side of the first step is leaves. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsTheLeavesOfTheFirstStepWhenEverySideIsRoofed(GameTestHelper h) {
        CompanionEntity c = pocket(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The leaves stopped the order: " + result.message() + " | " + report(c));
        });
    }

    /** The recorded entrance cannot be walked to (the same pocket, a record of a mine at a walled-in cell): the mine begins again in the leaves. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsTheLeavesWhenTheRecordedEntranceCannotBeWalkedTo(GameTestHelper h) {
        CompanionEntity c = pocket(h);
        BlockPos entrance = new BlockPos(CENTER + CAGE_AT, GROUND + 1, CENTER);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 3; dy++) {
            if (dx != 0 || dz != 0 || dy == 3) h.setBlock(entrance.offset(dx, dy, dz), Blocks.BEDROCK);
        }
        c.mineSite().start(h.absolutePos(entrance), Direction.NORTH);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The leaves stopped the order: " + result.message() + " | " + report(c));
        });
    }

    /** He stands on the grass in a column three high, open to the sky; leaves two thick close it on every side. They do not fall off with the time. */
    private static CompanionEntity pocket(GameTestHelper h) {
        mountain(h, true);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -SHELL; dx <= SHELL; dx++) for (int dz = -SHELL; dz <= SHELL; dz++) for (int dy = 1; dy <= SHELL_HEIGHT; dy++) {
            if (dx != 0 || dz != 0) h.setBlock(new BlockPos(CENTER + dx, GROUND + dy, CENTER + dz), leaves);
        }
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        return c;
    }
}
