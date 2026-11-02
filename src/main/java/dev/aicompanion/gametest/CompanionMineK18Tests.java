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
    private static final int CORRIDOR_Y = 12, CORRIDOR_Z = 14, ENTRANCE_X = 5, PREVIOUS_X = 6, START_X = 18, CHASM_FROM = 9, CHASM_TO = 11, CHASM_DEPTH = 3;

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
}
