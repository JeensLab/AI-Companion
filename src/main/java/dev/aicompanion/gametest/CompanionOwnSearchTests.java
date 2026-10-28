package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The companion's own route search (ticket 08): how far down he jumps. Four blocks onto land is not taken (he goes round by the
 * steps); four blocks and more into water is.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOwnSearchTests {
    /** A plateau four blocks over the meadow with steps down on its east side; the player waits on its west side below. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aDropOfFourOntoLandIsNotTakenHeGoesRoundByTheSteps(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 12, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 2, 31, Blocks.GRASS_BLOCK);
        box(h, 12, 3, 12, 15, 6, 19, Blocks.STONE);
        for (int step = 0; step < 4; step++) box(h, 16 + step, 3, 12, 16 + step, 5 - step, 19, Blocks.STONE);
        CompanionEntity c = spawn(h, 13, 7, 15);
        var player = player(c, h);
        teleport(h, player, 8, 3, 15);
        double[] fall = {0};
        h.onEachTick(() -> fall[0] = Math.max(fall[0], c.fallDistance));
        h.runAfterDelay(5, () -> {
            Path way = search(c, player);
            h.assertTrue(way != null && way.canReach(), "No way to the player");
            for (int i = 1; i < way.getNodeCount(); i++) h.assertTrue(way.getNode(i - 1).y - way.getNode(i).y <= 3, "The way drops " + (way.getNode(i - 1).y - way.getNode(i).y) + " blocks at once");
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
        h.succeedWhen(() -> {
            h.assertTrue(c.isWorking() == false && c.didLastTaskSucceed(), "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.distanceToSqr(player) <= 16, c.getLastTaskResult());
            h.assertTrue(fall[0] < 3.5, "Jumped down: fell " + fall[0] + " blocks");
        });
    }

    /** A pillar nine blocks over a lake: the only way off it is down into the water (a fall into water does no harm). */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aDropOfMoreThanThreeIntoWaterIsTaken(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 14, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 5, 31, Blocks.STONE);
        box(h, 8, 2, 8, 20, 5, 22, Blocks.WATER);
        box(h, 14, 2, 15, 14, 9, 15, Blocks.STONE);
        CompanionEntity c = spawn(h, 14, 10, 15);
        var player = player(c, h);
        teleport(h, player, 26, 6, 15);
        float health = c.getHealth();
        h.runAfterDelay(5, () -> {
            Path way = search(c, player);
            h.assertTrue(way != null && way.canReach(), "No way off the pillar: the drop into the water is not taken");
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
        h.succeedWhen(() -> {
            h.assertTrue(c.isWorking() == false && c.didLastTaskSucceed(), "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.distanceToSqr(player) <= 16, c.getLastTaskResult());
            h.assertTrue(c.getHealth() >= health, "Hurt by the fall");
        });
    }

    /** The whole way to the player by the companion's own search. */
    private static Path search(CompanionEntity c, net.minecraft.server.level.ServerPlayer player) {
        return ((CompanionNavigation)c.getNavigation()).search(java.util.Set.of(player.blockPosition()), 1);
    }

    private static net.minecraft.server.level.ServerPlayer player(CompanionEntity c, GameTestHelper h) {
        return h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
    }

    private static void teleport(GameTestHelper h, net.minecraft.server.level.ServerPlayer player, int x, int y, int z) {
        BlockPos stand = h.absolutePos(new BlockPos(x, y, z));
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
        c.setSearchRadius(16);
        return c;
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
}
