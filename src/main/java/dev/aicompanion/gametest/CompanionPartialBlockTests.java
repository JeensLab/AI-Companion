package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Blocks of partial shape on his way. The lava run of seed 20261102 (m8b): in a lush cave the route out of a pit led over a moss block under an azalea. The game calls the azalea's
 * cell open (it is no full block), but its leaves are a solid plate over a stem, so he jammed against it and jumped on the spot, the route
 * never advancing, until the watchdog stopped the order, five times in the same place.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPartialBlockTests {
    private static final int SIZE = 13, WALL_Z = 6, GATE_X = 6, SIDE_GAP_X = 1, FLOOR = 1, FEET = 2, TIMEOUT = 900;

    private CompanionPartialBlockTests() { }

    @AfterBatch(batch = "partial_block")
    public static void afterGate(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** A wall across the room with an azalea on a moss block in the middle and a gap at the side: the way through the azalea is shorter and shut. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "partial_block")
    public static void walksRoundAnAzaleaHeCannotStandIn(GameTestHelper h) {
        room(h, true);
        CompanionEntity c = rig(h);
        BlockPos goal = h.absolutePos(new BlockPos(GATE_X, FEET, WALL_Z + 2));
        h.onEachTick(() -> {
            if (!c.getNavigation().isDone() && !c.getNavigation().isStuck()) return;
            Path path = c.getNavigation().createPath(goal, 1);
            if (path != null) c.getNavigation().moveTo(path, 1);
        });
        h.succeedWhen(() -> h.assertTrue(c.blockPosition().distManhattan(goal) <= 1,
                "He did not get round the azalea, at " + h.relativePos(c.blockPosition()).toShortString() + ", y " + c.getY()));
    }

    /** The same wall with the gap shut: the only way on is through the azalea, and the search must not call it a way. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "partial_block")
    public static void doesNotCountAnAzaleaAsAWay(GameTestHelper h) {
        room(h, false);
        CompanionEntity c = rig(h);
        BlockPos goal = h.absolutePos(new BlockPos(GATE_X, FEET, WALL_Z + 2));
        // The search answers null while it is still going on: asked again every tick until it has an answer.
        h.succeedWhen(() -> {
            Path path = c.getNavigation().createPath(goal, 1);
            h.assertTrue(path != null, "The search has not answered yet");
            h.assertTrue(!path.canReach(), "The search found a way through the azalea: " + path.getNodeCount() + " nodes");
        });
    }

    /** Not every block of partial shape shuts a cell: a bottom slab is a sill he steps over, and the search still finds the way through it. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "partial_block")
    public static void countsABottomSlabSillAsAWay(GameTestHelper h) {
        room(h, false);
        h.setBlock(new BlockPos(GATE_X, FLOOR, WALL_Z), Blocks.STONE_SLAB);
        for (int y = FEET; y <= FEET + 3; y++) h.setBlock(new BlockPos(GATE_X, y, WALL_Z), Blocks.AIR);
        CompanionEntity c = rig(h);
        BlockPos goal = h.absolutePos(new BlockPos(GATE_X, FEET, WALL_Z + 2));
        h.succeedWhen(() -> {
            Path path = c.getNavigation().createPath(goal, 1);
            h.assertTrue(path != null, "The search has not answered yet");
            h.assertTrue(path.canReach(), "No way over the slab: " + path.getNodeCount() + " nodes");
        });
    }

    /**
     * The same wall with the gap shut, and a stair where the slab was: a stair's full-height half is a top he steps onto, as the
     * slab's half is — the way over it is found. What a sill is, then, is a shape a body steps onto; an azalea's plate (its full
     * cell, a stem under it) is not, and both files' rows say so.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "partial_block")
    public static void countsAStairTopSillAsAWay(GameTestHelper h) {
        room(h, false);
        h.setBlock(new BlockPos(GATE_X, FLOOR, WALL_Z), Blocks.OAK_STAIRS);
        for (int y = FEET; y <= FEET + 3; y++) h.setBlock(new BlockPos(GATE_X, y, WALL_Z), Blocks.AIR);
        CompanionEntity c = rig(h);
        BlockPos goal = h.absolutePos(new BlockPos(GATE_X, FEET, WALL_Z + 2));
        h.succeedWhen(() -> {
            Path path = c.getNavigation().createPath(goal, 1);
            h.assertTrue(path != null, "The search has not answered yet");
            h.assertTrue(path.canReach(), "No way over the stair's top: " + path.getNodeCount() + " nodes");
        });
    }

    private static void room(GameTestHelper h, boolean sideGap) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= FEET + 4; y++) {
            boolean shell = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1 || y == 0 || y == FEET + 4;
            boolean wall = z == WALL_Z && y >= FLOOR && !(sideGap && x == SIDE_GAP_X);
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : shell || wall || y <= FLOOR ? Blocks.STONE : Blocks.AIR);
        }
        h.setBlock(new BlockPos(GATE_X, FEET, WALL_Z), Blocks.MOSS_BLOCK);
        h.setBlock(new BlockPos(GATE_X, FEET + 1, WALL_Z), Blocks.AZALEA);
        h.setBlock(new BlockPos(GATE_X, FEET + 2, WALL_Z), Blocks.AIR);
        h.setBlock(new BlockPos(GATE_X, FEET + 3, WALL_Z), Blocks.AIR);
    }

    private static CompanionEntity rig(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), GATE_X, FEET, WALL_Z - 2);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(GATE_X, FEET, 1));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        return c;
    }
}
