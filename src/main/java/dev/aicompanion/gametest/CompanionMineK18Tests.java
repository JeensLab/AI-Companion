package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;

/**
 * Runs w1b/void-3: the way up has no floor in it, he has no block to lay one with, and the rock beside him is a pickaxe's reach away. He stood
 * on «в пути наверх нет пола, а блоков, чтобы его настелить, нет» with an iron pickaxe in the bag.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK18Tests {
    private static final int CORRIDOR_Y = 12, CORRIDOR_Z = 14, ENTRANCE_X = 5, PREVIOUS_X = 6, START_X = 18, CHASM_FROM = 9, CHASM_TO = 11, CHASM_DEPTH = 3, EDGE_X = 12, PIT_DEPTH = 6;
    /** How far from the middle of his cell his centre stands when the hitbox, 0.6 wide, reaches 0.04 into the neighbour. */
    private static final double BODY_OVER_EDGE = 0.34;

    private CompanionMineK18Tests() { }

    /**
     * A corridor of his own with a chasm in its floor, three cells long and three deep, between where he stands and the stairs he came down: no walk crosses it, and the
     * bag has a pickaxe and no block. A player cuts stone from the wall of the corridor and lays the floor with it.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsStoneFromTheWallToLayTheFloorOfTheWayUp(GameTestHelper h) {
        mountain(h, false);
        for (int x = ENTRANCE_X; x <= START_X; x++) for (int y = CORRIDOR_Y; y <= CORRIDOR_Y + 2; y++) h.setBlock(new BlockPos(x, y, CORRIDOR_Z), Blocks.AIR);
        for (int x = CHASM_FROM; x <= CHASM_TO; x++) for (int y = CORRIDOR_Y - CHASM_DEPTH; y < CORRIDOR_Y; y++) h.setBlock(new BlockPos(x, y, CORRIDOR_Z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, ItemStack.EMPTY);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        CompanionMineSite site = c.mineSite();
        site.start(h.absolutePos(new BlockPos(ENTRANCE_X, CORRIDOR_Y, CORRIDOR_Z)), Direction.WEST);
        site.reached(h.absolutePos(new BlockPos(PREVIOUS_X, CORRIDOR_Y, CORRIDOR_Z)));
        BlockPos start = h.absolutePos(new BlockPos(START_X, CORRIDOR_Y, CORRIDOR_Z));
        site.reached(start);
        c.teleportTo(start.getX() + .5, start.getY(), start.getZ() + .5);
        CompanionMineClimbOutTests.climb(h, c, start, result -> {
            int x = rel(h, c.blockPosition()).getX();
            h.assertTrue(result.success() && x <= PREVIOUS_X + 1, "He said «" + result.message() + "» at x " + x + ", short of the stairs at x " + PREVIOUS_X + " | " + report(c));
        });
    }

    /**
     * Runs w1a/descent: he stood at (45.16, 14, -6.17), on the corner of a block that his body only overlapped, with air under the cell of his centre. The floor of
     * that cell is air, so no side was a side to begin stairs to and the goal stopped. A player on the edge of a ledge is on the ledge.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void beginsTheStairsFromTheLedgeHisBodyStandsOn(GameTestHelper h) {
        mountain(h, false);
        for (int x = ENTRANCE_X; x <= START_X; x++) for (int y = CORRIDOR_Y; y <= CORRIDOR_Y + 2; y++) h.setBlock(new BlockPos(x, y, CORRIDOR_Z), Blocks.AIR);
        for (int x = EDGE_X + 1; x <= START_X; x++) for (int y = CORRIDOR_Y - PIT_DEPTH; y < CORRIDOR_Y; y++) h.setBlock(new BlockPos(x, y, CORRIDOR_Z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos ledge = h.absolutePos(new BlockPos(EDGE_X, CORRIDOR_Y, CORRIDOR_Z)), over = h.absolutePos(new BlockPos(EDGE_X + 1, CORRIDOR_Y, CORRIDOR_Z));
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        c.teleportTo(over.getX() + .5 + (ledge.getX() - over.getX()) * BODY_OVER_EDGE, over.getY(), over.getZ() + .5 + (ledge.getZ() - over.getZ()) * BODY_OVER_EDGE);
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (!started[0]) {
                // The sky light of a freshly built mountain lags behind it.
                if (!c.onGround() || h.getLevel().canSeeSky(over.above())) return;
                h.assertTrue(c.blockPosition().equals(over), "He did not stand with his centre over the pit: " + c.blockPosition() + " | " + report(c));
                started[0] = true;
                h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
                return;
            }
            if (c.mineSite().restarts() == 0) return;
            h.assertTrue(c.mineSite().entrance().distManhattan(ledge) <= 1, "He began the stairs at " + rel(h, c.mineSite().entrance()) + ", not from his ledge at " + rel(h, ledge) + " | " + report(c));
            h.succeed();
        });
    }
}
