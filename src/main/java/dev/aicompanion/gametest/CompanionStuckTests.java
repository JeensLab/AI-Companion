package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's request of 31.10: stuck inside blocks he gets out, as a dropped item does, instead of standing there and
 * suffocating. Ground is bedrock at y 0; he is put inside stone with the way out only where each test says.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStuckTests {
    /** Long enough for the way out and for settling: then the test checks that he stayed out. */
    private static final int OUT_TICKS = 15;
    /** The rescue is allowed twenty ticks of push first, so this waits past it. */
    private static final int RESCUE_TICKS = 40;

    /** Walled in on all four sides at his feet, air only above: a sideways nudge has nowhere to go, so he jumps. */
    @GameTest(template = "empty")
    public static void aCompanionInsideABlockGetsOutByJumping(GameTestHelper h) {
        arena(h);
        for (BlockPos pos : new BlockPos[]{new BlockPos(4, 1, 4), new BlockPos(3, 1, 4), new BlockPos(5, 1, 4),
                new BlockPos(4, 1, 3), new BlockPos(4, 1, 5)}) h.setBlock(pos, Blocks.STONE);
        CompanionEntity companion = inside(h);
        h.startSequence().thenExecuteAfter(OUT_TICKS, () -> {
            h.assertTrue(free(h, companion), "He is still inside the block at " + companion.position());
            h.assertTrue(companion.isAlive(), "He died getting out of the block");
        }).thenSucceed();
    }

    /** Inside a one-block column four tall, air on every side: the way out is a step to a free side. */
    @GameTest(template = "empty")
    public static void aCompanionInsideABlockGetsOutSideways(GameTestHelper h) {
        arena(h);
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
        CompanionEntity companion = inside(h);
        h.startSequence().thenExecuteAfter(1, () -> {
            // The step east is this feature's own probe; the dropped-item nudge would push north instead.
            h.assertTrue(companion.getDeltaMovement().x > 0.05D, "He did not step out east: " + companion.getDeltaMovement());
        }).thenExecuteAfter(OUT_TICKS, () -> {
            h.assertTrue(free(h, companion), "He is still inside the column at " + companion.position());
            h.assertTrue(companion.isAlive(), "He died getting out of the column");
        }).thenSucceed();
    }

    /** Only his head is inside a block, feet on the floor: he still has to get out. */
    @GameTest(template = "empty")
    public static void aCompanionWithHisHeadInABlockGetsOut(GameTestHelper h) {
        arena(h);
        h.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        CompanionEntity companion = inside(h);
        h.startSequence().thenExecuteAfter(OUT_TICKS, () -> {
            h.assertTrue(free(h, companion), "He is still inside the head block at " + companion.position());
            h.assertTrue(companion.isAlive(), "He died with his head in a block");
        }).thenSucceed();
    }

    /** Head in a block inside a room closed on every side but one: the push alone cannot do it, so he is put down outside. */
    @GameTest(template = "empty")
    public static void aCompanionClosedInWithABlockInHisHeadGetsOut(GameTestHelper h) {
        arena(h);
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(3, y, 4), Blocks.STONE);
            h.setBlock(new BlockPos(5, y, 4), Blocks.STONE);
            h.setBlock(new BlockPos(4, y, 3), Blocks.STONE);
            h.setBlock(new BlockPos(4, y, 5), Blocks.STONE);
        }
        h.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        h.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
        CompanionEntity companion = inside(h);
        h.startSequence().thenExecuteAfter(RESCUE_TICKS, () -> {
            h.assertTrue(free(h, companion), "He is still closed in at " + companion.position());
            h.assertTrue(companion.isAlive(), "He died closed in with a block in his head");
        }).thenSucceed();
    }

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static boolean free(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().noCollision(companion, companion.getBoundingBox().deflate(1.0E-7));
    }

    /** Spawns him with an owner, then puts him inside the stone at (4, 1, 4). */
    private static CompanionEntity inside(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var point = h.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        companion.teleportTo(point.x, point.y, point.z);
        return companion;
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}
