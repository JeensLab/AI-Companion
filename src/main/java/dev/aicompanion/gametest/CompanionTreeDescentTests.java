package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTreeDescentTests {
    private static final BlockPos TRUNK = new BlockPos(16, 12, 16);
    private static final BlockPos FOOT = new BlockPos(16, 2, 16);
    // After landing he is inside the usual follow start radius and still outside the stop radius.
    private static final BlockPos PLAYER = new BlockPos(11, 2, 16);

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void followsThePlayerDownThroughTheTrunkWithoutTools(GameTestHelper h) {
        tree(h);
        CompanionEntity c = spawn(h, TRUNK.above());
        float health = c.getHealth();
        h.succeedWhen(() -> {
            assertAtFoot(h, c, health);
            h.assertTrue(h.getBlockState(TRUNK).isAir() && h.getBlockState(FOOT).isAir(), "Did not cut the trunk from top to bottom");
            h.assertTrue(CompanionSingleRouteTests.count(c, Items.OAK_LOG) == 11, "Did not pick up all eleven dropped logs");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void reachesTheTrunkFromTheCanopy(GameTestHelper h) {
        tree(h);
        h.setBlock(TRUNK.east(2), Blocks.OAK_LEAVES);
        CompanionEntity c = spawn(h, TRUNK.east(2).above());
        float health = c.getHealth();
        h.succeedWhen(() -> {
            assertAtFoot(h, c, health);
            h.assertTrue(h.getBlockState(TRUNK).isAir(), "Did not move onto the trunk to descend");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void respondsToComeFromLeavesAboveTheTrunk(GameTestHelper h) {
        tree(h);
        h.setBlock(TRUNK.above(), Blocks.OAK_LEAVES);
        CompanionEntity c = spawn(h, TRUNK.above(2));
        float health = c.getHealth();
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order was not accepted");
        h.succeedWhen(() -> {
            assertAtFoot(h, c, health);
            h.assertTrue(c.didLastTaskSucceed() && !c.hasActiveWorkOrder(), "Did not finish the come order");
            h.assertTrue(h.getBlockState(TRUNK).isAir(), "Did not cut through the trunk underneath the leaves");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void descendsExistingStepsWithoutCuttingTheTrunk(GameTestHelper h) {
        tree(h);
        for (int step = 1; step <= 10; step++) h.setBlock(TRUNK.south(step).below(step), Blocks.STONE);
        CompanionEntity c = spawn(h, TRUNK.above());
        float health = c.getHealth();
        h.succeedWhen(() -> {
            assertAtFoot(h, c, health);
            h.assertTrue(h.getBlockState(TRUNK).is(Blocks.OAK_LOG), "Cut a tree that already had a safe route down");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 350)
    public static void leavesTheTrunkIntactAboveAnUnsafeLanding(GameTestHelper h) {
        tree(h);
        h.setBlock(FOOT.below(), Blocks.MAGMA_BLOCK);
        CompanionEntity c = spawn(h, TRUNK.above());
        h.runAfterDelay(300, () -> {
            h.assertTrue(h.getBlockState(TRUNK).is(Blocks.OAK_LOG), "Cut into a column without a safe landing");
            h.assertTrue(c.getY() >= h.absolutePos(TRUNK.above()).getY(), "Left the tree over a dangerous landing");
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 350)
    public static void leavesLogSupportsNearCraftedBlocksIntact(GameTestHelper h) {
        tree(h);
        h.setBlock(FOOT.east(), Blocks.OAK_PLANKS);
        CompanionEntity c = spawn(h, TRUNK.above());
        h.runAfterDelay(300, () -> {
            h.assertTrue(h.getBlockState(TRUNK).is(Blocks.OAK_LOG), "Cut a column that touched a wooden structure");
            h.assertTrue(c.getY() >= h.absolutePos(TRUNK.above()).getY(), "Left the protected wooden support");
            h.succeed();
        });
    }

    private static void tree(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 4, 2, 4, 27, 16, 27, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 1, 4, 27, 1, 27, Blocks.DIRT);
        for (int y = FOOT.getY(); y <= TRUNK.getY(); y++) h.setBlock(TRUNK.atY(y), Blocks.OAK_LOG);
        h.setBlock(TRUNK.east(), Blocks.OAK_LEAVES);
        h.setBlock(TRUNK.west(), Blocks.OAK_LEAVES);
        h.setBlock(TRUNK.north(), Blocks.OAK_LEAVES);
    }

    private static CompanionEntity spawn(GameTestHelper h, BlockPos start) {
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, start.getX(), start.getY(), start.getZ());
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        var ground = h.absolutePos(PLAYER).getBottomCenter();
        owner.teleportTo(ground.x, ground.y, ground.z);
        return c;
    }

    private static void assertAtFoot(GameTestHelper h, CompanionEntity c, float health) {
        h.assertTrue(c.isAlive() && c.getHealth() == health, "Took damage coming down");
        h.assertTrue(c.onGround() && h.relativePos(c.blockPosition()).getY() == FOOT.getY(),
                "Did not reach ground: " + h.relativePos(c.blockPosition()).toShortString());
        h.assertTrue(c.distanceToSqr(h.absolutePos(PLAYER).getBottomCenter()) <= 16, "Did not get back to the player");
    }
}
