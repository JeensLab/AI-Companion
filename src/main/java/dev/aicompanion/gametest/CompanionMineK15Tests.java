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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.rel;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;
import static dev.aicompanion.gametest.CompanionMineArena.start;

/**
 * Runs r1e/void-2 and r1b/void-4: the climb out of the mine that was not needed and the descent that was read as a cave.
 * Arena as in the other mine tests: stand k is (24,22-k,24-k), its column three cells high.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK15Tests {
    private static final int SLIT_Y = 14, SLIT_Z = 14, SLIT_X0 = 20, SLIT_X1 = 23, FLOOR_Y = 12, TOP_Y = 14,
            FAR_CHAMBER_X0 = 12, FAR_CHAMBER_X1 = 19, FAR_CHAMBER_Z0 = 11, FAR_CHAMBER_Z1 = 17, CORRIDOR_X = 3, DEEP_Y = 14, HIGH_Y = GROUND - 2, PAST_THE_CAVE = 13,
            OLD_STEPS = 15, TORCHES_SLOT = 3, BIG_STACK = 64, STRIP_FROM = 8, NICHE_BEHIND = 3,
            WEST_X0 = 14, WEST_X1 = 22, EAST_X0 = 26, EAST_X1 = 33, SIDE_Z0 = 12, SIDE_Z1 = 15, STRIP_ABOVE_CAVE = 9, DEEPER_THAN_THE_CAVE = 11;

    private CompanionMineK15Tests() { }

    private static BlockPos stand(int step) { return new BlockPos(CENTER, GROUND + 1 - step, CENTER - step); }

    private static void air(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static int feet(GameTestHelper h, CompanionEntity c) { return rel(h, c.blockPosition()).getY(); }

    /**
     * The bag runs out of blocks on the way down and he cuts a niche in the wall of his stairs for stone, in a mountain with no cave in it:
     * the niche is his own air, not a cave to tell of and plug.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotTakeTheNicheHeCutForStoneForACave(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        start(h, c, 1);
        boolean[] seen = {false, false}; // the bag was emptied of blocks; a niche stands open beside a stand
        h.onEachTick(() -> {
            int steps = c.mineSite().stands().size();
            if (!seen[0] && steps >= STRIP_FROM) {
                seen[0] = true;
                for (int slot = 0; slot < c.getContainerSize(); slot++) if (CompanionSeal.seals(c.getItem(slot))) c.setItem(slot, ItemStack.EMPTY);
            }
            for (int step = STRIP_FROM; !seen[1] && step <= steps; step++) seen[1] = h.getBlockState(stand(step).east()).isAir() || h.getBlockState(stand(step).west()).isAir();
            String phase = c.expeditionCavePhase();
            h.assertTrue(phase.isEmpty() || "IDLE".equals(phase), "He took " + (seen[1] ? "his niche" : "solid rock") + " for a cave (" + phase + ") at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if (seen[1] && steps >= STRIP_FROM + NICHE_BEHIND) h.succeed();
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended, a niche was " + (seen[1] ? "" : "not ") + "cut: " + report(c));
        });
    }

    /**
     * r1e/void-2: a slit one block high shows a chamber from the stairs. The only walk into it is up the stairs, over the surface and down a ramp
     * beyond the cave's reach; he looks at it from where he is and goes on down instead of leaving the mine for it.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
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
        boolean[] deep = {false}, looked = {false};
        h.onEachTick(() -> {
            deep[0] |= feet(h, c) <= DEEP_Y;
            looked[0] |= "EXPLORE".equals(c.expeditionCavePhase());
            h.assertTrue(!deep[0] || feet(h, c) <= HIGH_Y, "He left the mine for the far side of the cave, at " + rel(h, c.blockPosition()).toShortString() + " in phase " + c.expeditionCavePhase() + " | " + report(c));
            if (deep[0] && c.mineSite().stands().size() >= PAST_THE_CAVE && !c.expeditionStage().isEmpty()) {
                h.assertTrue(looked[0], "SETUP: the cave was never looked over, the test proves nothing | " + report(c));
                h.succeed();
            }
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended by the cave: " + report(c));
        });
    }

    /**
     * r1b/void-4: after a restart at the entrance the new stairs run down the cells of the old ones, which the record forgot and a save lost
     * with it. The old air is his own: walked down at once, not looked over as a cave on every step.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
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
     * r1b/void-2: the blocks run out on the way down, a cave opens, and the rock here is too thin for a niche. A bag without blocks is no danger in
     * the cave itself: with health to spare he turns off above it instead of leaving the mine, as he does for a cave that is dangerous again.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void turnsOffAboveACaveHeHasNoBlocksToPlug(GameTestHelper h) {
        mountain(h, false);
        air(h, SLIT_X1, SLIT_X1, SLIT_Y, SLIT_Y, SLIT_Z, SLIT_Z);
        air(h, WEST_X0, WEST_X1, FLOOR_Y, TOP_Y, SIDE_Z0, SIDE_Z1);
        air(h, EAST_X0, EAST_X1, FLOOR_Y, TOP_Y, SIDE_Z0, SIDE_Z1);
        CompanionEntity c = rig(h);
        start(h, c, 1);
        h.onEachTick(() -> {
            CompanionMineSite site = c.mineSite();
            if (site.stands().size() >= STRIP_ABOVE_CAVE && site.backtracks() == 0) stripBlocks(c);
            h.assertTrue(site.retreats() == 0 && feet(h, c) < GROUND - 3 || site.stands().size() < STRIP_ABOVE_CAVE, "He left the mine for want of blocks, at " + rel(h, c.blockPosition()).toShortString() + ", retreats " + site.retreats() + " | " + report(c));
            if (site.backtracks() >= 1 && "DESCEND".equals(c.expeditionStage()) && feet(h, c) < DEEPER_THAN_THE_CAVE) h.succeed();
            h.assertTrue(c.isWorking() || c.isGatheringTask(), "The order ended at the cave: " + report(c));
        });
    }

    private static void stripBlocks(CompanionEntity c) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (CompanionSeal.seals(c.getItem(slot))) c.setItem(slot, ItemStack.EMPTY);
    }
}
