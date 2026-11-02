package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/** Probes for the findings of the review of the mine at ded408e. Arena as in the other mine tests. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineReviewTests {
    private static final int GAP_AT = 12, WALL_AT = 5, IRON_LEFT = 60;

    /**
     * The stairs are shut at feet and head by blocks he did not put there, the record knows of none and he has only the worn iron pickaxe.
     * Whatever he does about it, the blocks stay: a chest, a player's bricks and a plug of cobblestone holding lava back are not his to break.
     */
    private static void leavesOthersBlocksOnTheStairs(GameTestHelper h, Block feet, Block head, boolean lavaBeside) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(0, ItemStack.EMPTY);
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        iron.setDamageValue(iron.getMaxDamage() - IRON_LEFT);
        c.setItem(1, iron);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos here = stairs.get(GAP_AT);
        for (BlockPos stand : stairs.subList(GAP_AT + 1, stairs.size())) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
        BlockPos low = stairs.get(WALL_AT);
        Map<BlockPos, Block> wall = Map.of(low, feet, low.above(), head);
        wall.forEach((cell, block) -> h.getLevel().setBlock(cell, block.defaultBlockState(), 2 | 16));
        // The lava sits in a pocket of the rock beside the plug and has no other way out.
        if (lavaBeside) for (BlockPos cell : wall.keySet()) h.getLevel().setBlock(cell.east(), Blocks.LAVA.defaultBlockState(), 2 | 16);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        c.mineSite().newStep(h.getLevel().getGameTime());
        for (int i = 0; i < 3; i++) {
            c.mineSite().restart(here, Direction.NORTH);
            c.mineSite().buried(true);
        }
        long[] id = {0};
        h.onEachTick(() -> {
            if (id[0] == 0) return;
            wall.forEach((cell, block) -> h.assertTrue(h.getLevel().getBlockState(cell).is(block), "He broke the " + block.getName().getString() + " on the stairs at " + cell + " | " + report(c)));
        });
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            ended(h, c, id[0]);
        });
    }

    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void leavesAChestOnTheStairsWhereItStands(GameTestHelper h) {
        leavesOthersBlocksOnTheStairs(h, Blocks.CHEST, Blocks.AIR, false);
    }

    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void leavesAPlayersBricksOnTheStairsWhereTheyStand(GameTestHelper h) {
        leavesOthersBlocksOnTheStairs(h, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, false);
    }

    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void leavesAPlugOnTheStairsThatHoldsLavaBack(GameTestHelper h) {
        leavesOthersBlocksOnTheStairs(h, Blocks.COBBLESTONE, Blocks.COBBLESTONE, true);
    }
}
