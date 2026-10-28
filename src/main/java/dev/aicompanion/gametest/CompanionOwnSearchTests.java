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

    /** A far goal is searched a share of cells a tick (ticket 09): he walks within ten ticks of the order, and no tick searches more than its share. */
    private static final int TICK_CELLS = 600;

    /**
     * A goal 150 blocks away over an obstacle (a wall with a gap at its far side), past what one search sees: from the order to the
     * first step towards it are at most ten ticks, no tick of the walk expands more cells than one tick's share, and he arrives.
     */
    @GameTest(template = "marathon_far", timeoutTicks = 3000)
    public static void aFarGoalIsWalkedTowardsWithinTenTicksAndSearchedBySharesOfCells(GameTestHelper h) {
        box(h, 40, 0, 0, 71, 14, 171, Blocks.AIR);
        box(h, 39, 1, 0, 39, 14, 171, Blocks.BARRIER);
        box(h, 72, 1, 0, 72, 14, 171, Blocks.BARRIER);
        box(h, 40, 1, 0, 71, 1, 171, Blocks.STONE);
        box(h, 40, 2, 0, 71, 2, 171, Blocks.GRASS_BLOCK);
        box(h, 40, 3, 80, 69, 6, 81, Blocks.STONE);
        CompanionEntity c = spawn(h, 56, 3, 10);
        var player = player(c, h);
        teleport(h, player, 56, 3, 160);
        var nav = (CompanionNavigation)c.getNavigation();
        double start = c.distanceToSqr(player);
        long[] first = {-1}, most = {0}, cells = {nav.meter().nodes()};
        var given = c.startWork("come", "", 1);
        h.assertTrue(given.success(), "Come order not accepted: " + given);
        long order = h.getTick();
        h.onEachTick(() -> {
            long nodes = nav.meter().nodes();
            most[0] = Math.max(most[0], nodes - cells[0]); cells[0] = nodes;
            if (first[0] < 0 && c.distanceToSqr(player) < start - 3) first[0] = h.getTick() - order;
        });
        h.succeedWhen(() -> {
            h.assertTrue(first[0] >= 0 && first[0] <= 10, "The first step towards the goal after " + first[0] + " ticks");
            h.assertTrue(most[0] <= TICK_CELLS, "One tick expanded " + most[0] + " cells");
            h.assertTrue(c.distanceToSqr(player) <= 16, "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
        });
    }

    /** The world is left and loaded again in the middle of a long walk: the way is searched anew, he arrives with his things, and he is the only one. */
    @GameTest(template = "marathon_far", timeoutTicks = 3000)
    public static void aReloadInTheMiddleOfALongWalkLeavesHimArrivingWithHisThings(GameTestHelper h) {
        box(h, 40, 0, 0, 71, 14, 171, Blocks.AIR);
        box(h, 39, 1, 0, 39, 14, 171, Blocks.BARRIER);
        box(h, 72, 1, 0, 72, 14, 171, Blocks.BARRIER);
        box(h, 40, 1, 0, 71, 1, 171, Blocks.STONE);
        box(h, 40, 2, 0, 71, 2, 171, Blocks.GRASS_BLOCK);
        box(h, 40, 3, 80, 69, 6, 81, Blocks.STONE);
        CompanionEntity first = spawn(h, 56, 3, 10);
        var player = player(first, h);
        teleport(h, player, 56, 3, 160);
        first.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        h.assertTrue(first.startWork("come", "", 1).success(), "Come order not accepted");
        CompanionEntity[] now = {first};
        BlockPos startAt = first.blockPosition();
        boolean[] reloaded = {false};
        h.onEachTick(() -> {
            if (reloaded[0] || now[0].distanceToSqr(startAt.getX() + .5, startAt.getY(), startAt.getZ() + .5) < 50 * 50) return;
            reloaded[0] = true;
            var saved = new net.minecraft.nbt.CompoundTag();
            now[0].saveWithoutId(saved);
            now[0].discard();
            CompanionEntity replacement = AICompanionMod.COMPANION.get().create(h.getLevel());
            replacement.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(replacement), "Reloaded companion rejected");
            now[0] = replacement;
        });
        h.succeedWhen(() -> {
            h.assertTrue(reloaded[0], "Not at the middle of the walk yet: " + h.relativePos(now[0].blockPosition()).toShortString() + " tick " + h.getTick() + " " + now[0].getLastTaskResult());
            CompanionEntity c = now[0];
            h.assertTrue(c.distanceToSqr(player) <= 36, "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && c.getItem(0).getCount() == 3, "Items after the reload: " + c.getItem(0));
            long companions = h.getLevel().getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(300)).stream().filter(e -> e.isAlive()).count();
            h.assertTrue(companions == 1, "Companions after the reload: " + companions);
        });
    }

    /** The whole route to the player by the companion's own search. */
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
