package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The way out of lava (CompanionLavaExit). The tests live beside the package-private class they call. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLavaExitTests {
    private static final int CENTER = 24, GROUND = 21, TIMEOUT = 400, IN_LAVA_TICKS = 60, STAY_TICKS = 40;

    private CompanionLavaExitTests() { }

    /** Put into a 3 by 3 pool of lava on open ground, a step from the bank: a second on, he is alive and out of it, and stays out. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void getsOutOfAPoolOfLava(GameTestHelper h) {
        int y = GROUND + 8;
        // Open air over a block of ground with a pool in it; the pool is flush with the ground.
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int up = -4; up <= 6; up++) h.setBlock(new BlockPos(x, y + up, z), up <= 0 ? Blocks.STONE : Blocks.AIR);
        for (int x = 5; x <= 7; x++) for (int z = 5; z <= 7; z++) for (int up = -1; up <= 0; up++) h.setBlock(new BlockPos(x, y + up, z), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 5, y, 6);
        long[] start = {-1};
        h.onEachTick(() -> {
            if (start[0] < 0 && c.isInLava()) start[0] = h.getTick();
            if (!c.isAlive()) h.fail("Burned to death " + (h.getTick() - start[0]) + " ticks after he was put into the lava");
            if (start[0] >= 0 && h.getTick() - start[0] == IN_LAVA_TICKS) h.assertTrue(!c.isInLava(), "Still in the lava " + IN_LAVA_TICKS + " ticks on, hp " + c.getHealth());
            if (start[0] < 0 || h.getTick() - start[0] < IN_LAVA_TICKS + STAY_TICKS) return;
            h.assertTrue(!c.isInLava(), "In the lava again " + (IN_LAVA_TICKS + STAY_TICKS) + " ticks on, hp " + c.getHealth());
            h.succeed();
        });
    }

    /**
     * Run s7-d1/retreat (seed 7, a lava fall spreading over a ledge by a lake): out of the lava he went for the nearest bank, across the sheet,
     * and burned to death on the way. In lava, a bank three blocks east past two more cells of lava and one four blocks west past none, over slabs: west.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void theWayOutOfLavaDoesNotCrossMoreLava(GameTestHelper h) {
        int y = GROUND + 8;
        // Air all round the row, so that the only banks are on it.
        for (int x = 0; x <= 12; x++) for (int up = -3; up <= 3; up++) for (int dz = -7; dz <= 7; dz++) h.setBlock(new BlockPos(x, y + up, CENTER + dz), Blocks.AIR);
        for (int x = 0; x <= 12; x++) h.setBlock(new BlockPos(x, y, CENTER), Blocks.STONE);
        // Slabs between: a way he can walk, and no cell of it a bank, so the near bank is the one across the lava.
        for (int x = 2; x <= 5; x++) h.setBlock(new BlockPos(x, y, CENTER), Blocks.STONE_SLAB);
        for (int x = 6; x <= 8; x++) h.setBlock(new BlockPos(x, y + 1, CENTER), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 6, y + 1, CENTER);
        c.setNoAi(true);
        BlockPos out = new CompanionLavaExit(c).nearestOut();
        h.assertTrue(out != null && out.getX() < c.getBlockX(), "The way out of the lava goes to " + rel(h, out) + ", across the lava east");
        h.succeed();
    }

    /**
     * Runs void and restart of seed 7 (series h, 21.11): afloat in a lake under a lava sheet, the only floor in reach lay across the sheet, and he waded
     * into it to that bank. In lava, deep water two blocks west (no floor under it in reach; a slab between, so that the two do not meet) and a bank
     * three blocks east past two more cells of lava: the water.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void theWayOutOfLavaTakesWaterBeforeABankAcrossIt(GameTestHelper h) {
        int y = GROUND + 8;
        for (int x = 0; x <= 12; x++) for (int up = -3; up <= 3; up++) for (int dz = -7; dz <= 7; dz++) h.setBlock(new BlockPos(x, y + up, CENTER + dz), Blocks.AIR);
        for (int x = 6; x <= 12; x++) h.setBlock(new BlockPos(x, y, CENTER), Blocks.STONE);
        h.setBlock(new BlockPos(5, y, CENTER), Blocks.STONE_SLAB);
        for (int x = 3; x <= 4; x++) for (int up = -2; up <= 1; up++) h.setBlock(new BlockPos(x, y + up, CENTER), Blocks.WATER);
        for (int x = 6; x <= 8; x++) h.setBlock(new BlockPos(x, y + 1, CENTER), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 6, y + 1, CENTER);
        c.setNoAi(true);
        BlockPos out = new CompanionLavaExit(c).nearestOut();
        h.assertTrue(out != null && out.getX() < c.getBlockX(), "The way out of the lava goes to " + rel(h, out) + ", across the lava east");
        h.succeed();
    }

    /**
     * With the AI on: the bank west, three blocks off, lies across a hole of two blocks, and the one east is beside the lava he is in (the edge costs, so
     * the west one scores better). A straight line to the west
     * bank is clear of blocks but he would walk into the hole; he must step east and stand on that bank, alive.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void getsOutOfLavaByTheBankHeCanWalkTo(GameTestHelper h) {
        int y = GROUND + 8;
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int up = -6; up <= 6; up++) h.setBlock(new BlockPos(x, y + up, z), up <= 0 ? Blocks.STONE : Blocks.AIR);
        // A lane along z = 6: lava at x 6 (his cell), the hole at x 4-5; walls beside it, so that no bank is to the side.
        for (int x = 3; x <= 7; x++) for (int up = 1; up <= 2; up++) for (int dz = -1; dz <= 1; dz += 2) h.setBlock(new BlockPos(x, y + up, 6 + dz), Blocks.STONE);
        for (int x = 4; x <= 5; x++) for (int up = -5; up <= 0; up++) h.setBlock(new BlockPos(x, y + up, 6), Blocks.AIR);
        for (int up = -1; up <= 0; up++) h.setBlock(new BlockPos(6, y + up, 6), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 6, y, 6);
        BlockPos east = h.absolutePos(new BlockPos(7, y + 1, 6));
        h.onEachTick(() -> {
            if (!c.isAlive()) h.fail("Died on the way out of the lava, at tick " + h.getTick());
            if (h.getTick() < IN_LAVA_TICKS) return;
            h.assertTrue(!c.isInLava() && c.distanceToSqr(east.getX() + .5, east.getY(), east.getZ() + .5) < 4,
                    "Not on the east bank: " + c.position() + ", bank " + east.toShortString() + ", hp " + c.getHealth());
            h.succeed();
        });
    }

    private static String rel(GameTestHelper h, BlockPos world) { return world == null ? "none" : h.relativePos(world).toShortString(); }
}
