package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.VineBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The companion's own search (ticket 11): he climbs existing ladders and vines, up and down, as a player does: by the game's own
 * climbing, never by setting his position. Ground stands at y 6; a tower of stone (x 15..17) has the climbable on its west face.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionClimbTests {
    /** Ticks one trip may take: a block of ladder is five ticks up, the way is eleven blocks and a walk. */
    private static final int TRIP_TICKS = 250;
    /** Trips in one test, up and down or there and back: climbing has physics variance, so one run proves it a few times. */
    private static final int TRIPS = 4;

    private static void arena(GameTestHelper h, int towerTop) {
        box(h, 0, 0, 0, 31, 24, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 5, 31, Blocks.STONE);
        box(h, 15, 6, 12, 17, towerTop, 18, Blocks.STONE);
    }

    private static void ladder(GameTestHelper h, int top) {
        for (int y = 6; y <= top; y++) h.setBlock(new BlockPos(14, y, 15), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
    }

    /**
     * He goes between the ground (x 8) and {@code topY} on the tower (x 16), called by the player, {@link #TRIPS} times, each at most
     * {@link #TRIP_TICKS}; he never loses health (a fall).
     */
    private static void trips(GameTestHelper h, boolean startOnTop, int topY) {
        int[] ground = {8, 6, 15}, top = {16, topY, 15};
        int[] from = startOnTop ? top : ground;
        CompanionEntity c = spawn(h, from[0], from[1], from[2]);
        var player = player(c, h);
        teleport(h, player, startOnTop ? ground : top);
        float health = c.getHealth();
        int[] done = {0};
        long[] began = {h.getTick()};
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        h.onEachTick(() -> {
            h.assertTrue(c.getHealth() >= health, "Hurt: fell from the climb at " + h.relativePos(c.blockPosition()).toShortString());
            if (h.getTick() - began[0] > TRIP_TICKS) h.fail("Trip " + (done[0] + 1) + " took over " + TRIP_TICKS + " ticks: at " + h.relativePos(c.blockPosition()).toShortString() + ", " + c.getLastTaskResult());
            if (c.isWorking() || !c.didLastTaskSucceed() || c.distanceToSqr(player) > 16) return;
            if (++done[0] == TRIPS) { h.succeed(); return; }
            boolean toTop = (done[0] % 2 == 0) == !startOnTop;
            teleport(h, player, toTop ? top : ground);
            began[0] = h.getTick();
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void heClimbsALadderUpToThePlayerOnATower(GameTestHelper h) {
        arena(h, 16);
        ladder(h, 16);
        trips(h, false, 17);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void heClimbsDownALadderToThePlayerBelowWithoutFalling(GameTestHelper h) {
        arena(h, 16);
        ladder(h, 16);
        trips(h, true, 17);
    }

    /**
     * The player stands by the top of the ladder, near enough to be «рядом» from the ladder itself: he comes off it onto the tower and
     * stays there (game 28.10: «Я рядом.» on the ladder, and he slid down it, then climbed again to the follow, up and down).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void heStepsOffTheLadderOntoTheTowerAndStaysThere(GameTestHelper h) {
        arena(h, 16);
        ladder(h, 16);
        CompanionEntity c = spawn(h, 8, 6, 15);
        teleport(h, player(c, h), new int[]{15, 17, 15});
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        long[] doneAt = {-1};
        h.onEachTick(() -> {
            if (doneAt[0] < 0) { if (!c.isWorking()) doneAt[0] = h.getTick(); return; }
            if (h.getTick() - doneAt[0] < 60) return;
            h.assertTrue(c.didLastTaskSucceed(), "Come failed: " + c.getLastTaskResult());
            h.assertTrue(c.onGround() && c.getY() >= h.absolutePos(new BlockPos(0, 17, 0)).getY(),
                    "Not up on the tower three seconds after «" + c.getLastTaskResult() + "»: at " + h.relativePos(c.blockPosition()).toShortString());
            h.succeed();
        });
        h.runAtTickTime(580, () -> h.fail("Still at " + h.relativePos(c.blockPosition()).toShortString() + ", working " + c.isWorking() + ": " + c.getLastTaskResult()));
    }

    /** A wall of stone across the arena (so no way round), vines on its west face the only way up. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void heClimbsVinesUpAWallThatIsOtherwiseAClimbToNowhere(GameTestHelper h) {
        arena(h, 6);
        box(h, 15, 6, 0, 17, 12, 31, Blocks.STONE);
        for (int y = 6; y <= 12; y++) h.setBlock(new BlockPos(14, y, 15), Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
        trips(h, false, 13);
    }

    private static net.minecraft.server.level.ServerPlayer player(CompanionEntity c, GameTestHelper h) {
        return h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
    }

    private static void teleport(GameTestHelper h, net.minecraft.server.level.ServerPlayer player, int[] at) {
        BlockPos stand = h.absolutePos(new BlockPos(at[0], at[1], at[2]));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x, y, z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(32);
        return c;
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
}
