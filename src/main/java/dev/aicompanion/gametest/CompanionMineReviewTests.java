package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
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
    private static final int TRENCH_LENGTH = 8, TRENCH_CREEPER = 3, FUSE_SETTLED = 100, FIRE_TICKS = 200, FIRE_REACTION = 60;

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

    /**
     * A dead end of the open ground, no mine anywhere, a creeper with a lit fuse three blocks off: a wall of blocks there would stay in the
     * world for good, nobody takes it out. He has the sword to strike with; the cobblestone stays in his bag.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void setsNoWallOnTheOpenGroundWhenAFuseIsLit(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = CompanionMineFightTests.armed(h);
        for (int x = 0; x <= TRENCH_LENGTH; x++) for (int y = GROUND - 2; y <= GROUND; y++) h.setBlock(new BlockPos(CENTER + x, y, CENTER), Blocks.AIR);
        BlockPos stand = h.absolutePos(new BlockPos(CENTER, GROUND - 2, CENTER));
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        h.assertTrue(h.getLevel().canSeeSky(stand), "The trench is roofed");
        Creeper creeper = h.spawn(EntityType.CREEPER, new BlockPos(CENTER + TRENCH_CREEPER, GROUND - 2, CENTER));
        creeper.setPersistenceRequired();
        creeper.ignite();
        h.onEachTick(() -> {
            for (int x = 0; x <= TRENCH_LENGTH; x++) for (int y = GROUND - 2; y <= GROUND; y++) {
                h.assertTrue(!h.getBlockState(new BlockPos(CENTER + x, y, CENTER)).is(Blocks.COBBLESTONE), "A wall of his stands on the open ground at " + x + "," + y + " | " + report(c));
            }
        });
        h.runAfterDelay(FUSE_SETTLED, h::succeed);
    }

    /**
     * On fire on the stairs, with no cave anywhere near (the cave sense is the only part that reads a fire): the step turns back at once,
     * as it did when the goal stopped the step for a fire. The fire is short, so the health never gets low enough to be the reason.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void turnsBackWhenHeBurnsOnTheStairsOutsideACave(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        carveMine(h, c, 0);
        long id = start(h, c, 1);
        long[] ignited = {-1};
        h.onEachTick(() -> {
            String stage = c.expeditionStage();
            if (ignited[0] < 0) {
                if (stage.equals("WALK_DOWN") || stage.equals("DESCEND")) { c.setRemainingFireTicks(FIRE_TICKS); ignited[0] = h.getTick(); }
                return;
            }
            if (stage.equals("RETREAT") || stage.equals("CLIMB") || c.workResult(id) != null) { h.succeed(); return; }
            h.assertTrue(h.getTick() - ignited[0] < FIRE_REACTION, "He burns and goes on in stage " + stage + ", health " + c.getHealth() + " | " + report(c));
        });
    }
}
