package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionSeal;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Runs r1e/void-2, r1b/void-2 and r1b/void-4: the climbs out of the mine that were not needed and the descents that were read as caves.
 * Arena as in the other mine tests: stand k is (24,22-k,24-k), its column three cells high.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK15Tests {
    private static final int SLIT_STAND = 10, SLIT_Y = 14, SLIT_Z = 14, SLIT_X0 = 20, SLIT_X1 = 23, FLOOR_Y = 12, TOP_Y = 14,
            FAR_CHAMBER_X0 = 12, FAR_CHAMBER_X1 = 19, FAR_CHAMBER_Z0 = 11, FAR_CHAMBER_Z1 = 17, CORRIDOR_X = 3, DEEP_Y = 14, HIGH_Y = GROUND - 2, PAST_THE_CAVE = 13,
            OLD_STEPS = 15, STONE_USES = 70, IRON_WEAR = 5,
            WEST_X0 = 14, WEST_X1 = 22, EAST_X0 = 26, EAST_X1 = 33, SIDE_Z0 = 12, SIDE_Z1 = 15, STRIP_FROM = 9, DEEPER_THAN_THE_CAVE = 11, TORCHES_SLOT = 3, BIG_STACK = 64;

    private CompanionMineK15Tests() { }

    private static BlockPos stand(int step) { return new BlockPos(CENTER, GROUND + 1 - step, CENTER - step); }

    private static void air(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static int feet(GameTestHelper h, CompanionEntity c) { return rel(h, c.blockPosition()).getY(); }

    /**
     * r1e/void-2: a slit one block high shows a chamber from the stairs. The only walk into it is up the stairs, over the surface and down a ramp
     * beyond the cave's reach; he looks at it from where he is and goes on down instead of leaving the mine for it.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotWalkOverTheSurfaceToACaveOnTheOtherSideOfASlit(GameTestHelper h) {
        mountain(h, false);
        air(h, SLIT_X0, SLIT_X1, SLIT_Y, SLIT_Y, SLIT_Z, SLIT_Z);
        air(h, FAR_CHAMBER_X0, FAR_CHAMBER_X1, FLOOR_Y, TOP_Y, FAR_CHAMBER_Z0, FAR_CHAMBER_Z1);
        air(h, CORRIDOR_X, FAR_CHAMBER_X0 - 1, FLOOR_Y, FLOOR_Y + 1, SLIT_Z, SLIT_Z);
        for (int rise = 1; rise <= GROUND - FLOOR_Y; rise++) air(h, CORRIDOR_X, CORRIDOR_X, FLOOR_Y + rise, Math.min(GROUND, FLOOR_Y + rise + 2), SLIT_Z + rise, SLIT_Z + rise);
        CompanionEntity c = rig(h);
        // The torches of the stairs must leave him enough for the cave to be safe: with fewer he does not go in at all.
        c.setItem(TORCHES_SLOT, new ItemStack(Items.TORCH, BIG_STACK));
        start(h, c, 1);
        boolean[] deep = {false};
        h.onEachTick(() -> {
            deep[0] |= feet(h, c) <= DEEP_Y;
            h.assertTrue(!deep[0] || feet(h, c) <= HIGH_Y, "He left the mine for the far side of the cave, at " + rel(h, c.blockPosition()).toShortString() + " in phase " + c.expeditionCavePhase() + " | " + report(c));
            if (deep[0] && c.mineSite().stands().size() >= PAST_THE_CAVE && !c.expeditionStage().isEmpty()) h.succeed();
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended by the cave: " + report(c));
        });
    }

    /**
     * r1b/void-4: after a restart at the entrance the new stairs run down the cells of the old ones, which the record forgot and a save lost
     * with it. The old air is his own: walked down at once, not looked over as a cave on every step.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotTakeHisOldStairsForACaveAfterARestart(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        BlockPos entrance = h.absolutePos(stand(0)), first = h.absolutePos(stand(1));
        Direction down = Direction.getNearest(first.getX() - entrance.getX(), 0, first.getZ() - entrance.getZ());
        List<BlockPos> stairs = new ArrayList<>();
        for (int step = 1; step <= OLD_STEPS; step++) stairs.add(h.absolutePos(stand(step)));
        for (BlockPos old : stairs) for (BlockPos cell : CompanionShaftPlan.column(old)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = c.mineSite();
        site.start(entrance, down);
        for (BlockPos old : stairs) site.reached(old);
        site.restart(entrance, down);
        site.buried(false);
        CompoundTag saved = new CompoundTag();
        site.save(saved);
        site.load(saved);
        start(h, c, 1);
        h.onEachTick(() -> {
            h.assertTrue(!"EXPLORE".equals(c.expeditionCavePhase()), "He took his old stairs for a cave at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if (site.stands().size() >= OLD_STEPS) h.succeed();
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended on the old stairs: " + report(c));
        });
    }

    /**
     * r1b/void-2: the last stone pickaxe wears out in the tunnel at the bottom, sticks for another are none. The climb out and back costs more
     * than the wear of the iron one, which is whole: it digs the rock.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void digsOnWithTheIronPickaxeWhenTheStoneOneWearsOutInATunnel(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setItem(0, ItemStack.EMPTY);
        ItemStack worn = new ItemStack(Items.STONE_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - STONE_USES);
        c.setItemSlot(EquipmentSlot.MAINHAND, worn);
        start(h, c, 1);
        h.onEachTick(() -> {
            h.assertTrue(!"CLIMB".equals(c.expeditionStage()), "He went up with a whole iron pickaxe in the bag, from " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if ("BRANCH".equals(c.expeditionStage()) && ironWear(c) >= IRON_WEAR) h.succeed();
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended before the iron pickaxe took the rock: " + report(c));
        });
    }

    /**
     * r1b/void-2: the blocks run out on the way down, a cave opens, and the rock here is too thin for a niche. A bag without blocks is no danger in
     * the cave itself: with health to spare he turns off above it instead of leaving the mine, as he does for a cave that is dangerous again.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void turnsOffAboveACaveHeHasNoBlocksToPlug(GameTestHelper h) {
        mountain(h, false);
        air(h, SLIT_X1, SLIT_X1, SLIT_Y, SLIT_Y, SLIT_Z, SLIT_Z);
        air(h, WEST_X0, WEST_X1, FLOOR_Y, TOP_Y, SIDE_Z0, SIDE_Z1);
        air(h, EAST_X0, EAST_X1, FLOOR_Y, TOP_Y, SIDE_Z0, SIDE_Z1);
        CompanionEntity c = rig(h);
        start(h, c, 1);
        h.onEachTick(() -> {
            CompanionMineSite site = c.mineSite();
            if (site.stands().size() >= STRIP_FROM && site.backtracks() == 0) stripBlocks(c);
            h.assertTrue(site.retreats() == 0 && feet(h, c) < GROUND - 3 || site.stands().size() < STRIP_FROM, "He left the mine for want of blocks, at " + rel(h, c.blockPosition()).toShortString() + ", retreats " + site.retreats() + " | " + report(c));
            if (site.backtracks() >= 1 && "DESCEND".equals(c.expeditionStage()) && feet(h, c) < DEEPER_THAN_THE_CAVE) h.succeed();
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended at the cave: " + report(c));
        });
    }

    private static void stripBlocks(CompanionEntity c) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (CompanionSeal.seals(c.getItem(slot))) c.setItem(slot, ItemStack.EMPTY);
    }

    /** The most wear on an iron pickaxe, in the bag or in hand. */
    private static int ironWear(CompanionEntity c) {
        int wear = c.getMainHandItem().is(Items.IRON_PICKAXE) ? c.getMainHandItem().getDamageValue() : 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.IRON_PICKAXE)) wear = Math.max(wear, c.getItem(slot).getDamageValue());
        return wear;
    }
}
