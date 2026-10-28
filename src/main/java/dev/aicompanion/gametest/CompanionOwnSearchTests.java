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

    /** A strip of meadow 32 wide and 172 long (x 40..71): stone floor, grass, barriers at the sides. */
    private static void strip(GameTestHelper h) {
        box(h, 40, 0, 0, 71, 14, 171, Blocks.AIR);
        box(h, 39, 1, 0, 39, 14, 171, Blocks.BARRIER);
        box(h, 72, 1, 0, 72, 14, 171, Blocks.BARRIER);
        box(h, 40, 1, 0, 71, 1, 171, Blocks.STONE);
        box(h, 40, 2, 0, 71, 2, 171, Blocks.GRASS_BLOCK);
    }

    /**
     * Walled in for six seconds while a path is set (the game's own stuck check fires), then freed: the stuck mark is of that walk
     * only. {@code after} runs once he is free again and the mark is known to have been set.
     */
    private static void stuckOnce(GameTestHelper h, CompanionEntity c, Runnable after) {
        var nav = (CompanionNavigation)c.getNavigation();
        // His goals are put aside meanwhile (they stop a walk they did not give): the walk is the one the test sets, as a stroll sets one and leaves it.
        var selector = goals(c);
        var aside = new java.util.ArrayList<>(selector.getAvailableGoals());
        c.removeAllGoals(goal -> true);
        h.runAtTickTime(2, () -> {
            Path way = nav.searchNow(java.util.Set.of(h.absolutePos(new BlockPos(56, 3, 40))), 1);
            h.assertTrue(way != null && way.canReach(), "No way to the far cell");
            nav.moveTo(way, 1D);
            // Without the block update (flag 16): the path he was given is not rebuilt round the wall, as if it came from a goal.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 3; y <= 5; y++)
                if (dx != 0 || dz != 0 || y == 5) h.getLevel().setBlock(h.absolutePos(new BlockPos(56 + dx, y, 10 + dz)), Blocks.STONE.defaultBlockState(), 16);
        });
        h.runAtTickTime(160, () -> {
            h.assertTrue(nav.isStuck(), "The walled-in walk was not told stuck (path " + nav.getPath() + ", at " + h.relativePos(c.blockPosition()).toShortString() + ")");
            for (int x = 55; x <= 57; x++) for (int z = 9; z <= 11; z++) for (int y = 3; y <= 5; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 16);
            for (var wrapped : aside) selector.addGoal(wrapped.getPriority(), wrapped.getGoal());
            after.run();
        });
    }

    /** Stuck once, he still walks a normal leg to a far target: a new path is not stuck by what stopped the last one. */
    @GameTest(template = "marathon_far", timeoutTicks = 1500)
    public static void aCompanionThatWasStuckOnceStillWalksALegToAFarTarget(GameTestHelper h) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 3, 10);
        var player = player(c, h);
        var nav = (CompanionNavigation)c.getNavigation();
        boolean[] stuckWalking = {false};
        h.onEachTick(() -> { if (h.getTick() > 170 && !nav.isDone() && nav.isStuck()) stuckWalking[0] = true; });
        stuckOnce(h, c, () -> {
            teleport(h, player, 56, 3, 60);
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 200 && c.distanceToSqr(player) <= 16, "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(!stuckWalking[0], "A walk of his was told stuck from the walk before it");
        });
    }

    /** Stuck once, he still fells a tree on foot: the tree is not sent to the climb at once. */
    @GameTest(template = "marathon_far", timeoutTicks = 1500)
    public static void aCompanionThatWasStuckOnceStillFellsATreeWithoutClimbing(GameTestHelper h) {
        strip(h);
        for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(56, y, 24), Blocks.OAK_LOG);
        CompanionEntity c = spawn(h, 56, 3, 10);
        double top = c.getY();
        double[] highest = {top};
        stuckOnce(h, c, () -> c.startGatheringLogs(3));
        h.onEachTick(() -> highest[0] = Math.max(highest[0], c.getY()));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 200 && !c.isGatheringTask() && !c.isWorking() && c.didLastTaskSucceed(), "Not felled: " + c.getLastTaskResult());
            h.assertTrue(highest[0] <= top + .6, "He climbed: up to " + (highest[0] - top) + " blocks");
        });
    }

    /** A wall put across the route in the middle of the walk (a way round it, to one side): the path is rebuilt, not walked into. */
    @GameTest(template = "marathon_far", timeoutTicks = 2000)
    public static void aWallPutAcrossTheRouteMidWalkIsGoneRoundWithoutWalkingIntoIt(GameTestHelper h) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 3, 10);
        var nav = (CompanionNavigation)c.getNavigation();
        // The walk is given once, as a goal of the game gives it (nothing of his asks for another): only a rebuilt path goes round.
        c.removeAllGoals(goal -> true);
        BlockPos goal = h.absolutePos(new BlockPos(56, 3, 40));
        h.runAtTickTime(2, () -> nav.moveTo(nav.searchNow(java.util.Set.of(goal), 1), 1D));
        boolean[] through = {false};
        h.runAtTickTime(40, () -> box(h, 40, 3, 26, 60, 6, 26, Blocks.STONE));
        h.onEachTick(() -> {
            // A while after the wall (the recompute may wait twenty ticks): no node ahead of him is inside it.
            if (h.getTick() < 70 || h.getTick() > 120) return;
            Path way = nav.getPath();
            if (way == null) return;
            for (int i = way.getNextNodeIndex(); i < way.getNodeCount(); i++) {
                var node = way.getNode(i);
                if (h.getLevel().getBlockState(new BlockPos(node.x, node.y, node.z)).is(Blocks.STONE)) through[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 130 && c.distanceToSqr(goal.getX() + .5, goal.getY(), goal.getZ() + .5) <= 4, "Still walking at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(!through[0], "The path still led through the wall put across it");
        });
    }

    /**
     * The show of 28.10: a platform 21 wide and 120 long with a plank wall of 110 along its middle, open at one end only; he stands
     * at the wall, the target is a few blocks behind it, and the way round is some 180 blocks (the game's search ranges: radius 64).
     */
    private static CompanionEntity longWall(GameTestHelper h) {
        box(h, 39, 0, 0, 72, 14, 171, Blocks.AIR);
        box(h, 39, 1, 0, 39, 14, 122, Blocks.BARRIER);
        box(h, 61, 1, 0, 61, 14, 122, Blocks.BARRIER);
        box(h, 40, 1, 0, 60, 14, 0, Blocks.BARRIER);
        box(h, 40, 1, 121, 60, 14, 121, Blocks.BARRIER);
        box(h, 40, 1, 1, 60, 1, 120, Blocks.STONE);
        box(h, 40, 2, 1, 60, 2, 120, Blocks.GRASS_BLOCK);
        box(h, 50, 3, 1, 50, 6, 110, Blocks.OAK_PLANKS);
        CompanionEntity c = spawn(h, 51, 3, 25);
        c.setSearchRadius(64);
        return c;
    }

    private static int planks(GameTestHelper h) {
        int n = 0;
        for (int z = 1; z <= 110; z++) for (int y = 3; y <= 6; y++) if (h.getBlockState(new BlockPos(50, y, z)).is(Blocks.OAK_PLANKS)) n++;
        return n;
    }

    /**
     * «Иди ко мне» to the player six blocks off behind the long wall while leaves come and go above the platform, as a felled tree's
     * crown decays (show 28.10: he walked to and fro along the wall for three minutes). The route round it is walked in one go: a
     * block change rebuilds it as wide as it was found, not as a part of one search's way that leads back to the wall, and the game's
     * time-out of a path does not cut it off half-way.
     */
    @GameTest(template = "marathon_far", batch = "longwall", timeoutTicks = 3000)
    public static void theWayRoundALongWallIsWalkedInOneGoWhileBlocksChangeBesideIt(GameTestHelper h) {
        CompanionEntity c = longWall(h);
        var player = player(c, h);
        teleport(h, player, 45, 3, 22);
        var nav = (CompanionNavigation)c.getNavigation();
        int wall = planks(h);
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        boolean[] walking = {false};
        h.onEachTick(() -> {
            if (h.getTick() % 10 == 0 && h.getTick() < 900) h.setBlock(new BlockPos(53, 8, 30), h.getTick() % 20 == 0 ? Blocks.OAK_LEAVES : Blocks.AIR);
            if (!c.isWorking()) return;
            // Once he walks the whole way round, it is neither dropped half-way nor swapped for a part that does not get there.
            Path way = nav.getPath();
            String at = " at " + h.relativePos(c.blockPosition()).toShortString() + ", tick " + h.getTick();
            if (way != null && !way.isDone() && way.canReach()) walking[0] = true;
            else if (walking[0] && c.distanceToSqr(player) > 16) h.fail(way == null || way.isDone() ? "The walk round was cut off half-way" + at : "The walk round was swapped for a part of a short search" + at);
        });
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking() && c.didLastTaskSucceed() && c.distanceToSqr(player) <= 16, "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(planks(h) == wall, "Broke the wall: " + (wall - planks(h)) + " planks");
        });
    }

    /** A tree eight blocks off behind the long wall, within the twelve of a near tree: walked round to and felled from the ground, no climb. */
    @GameTest(template = "marathon_far", batch = "longwall", timeoutTicks = 3000)
    public static void aNearTreeBehindALongWallIsWalkedRoundToAndFelled(GameTestHelper h) {
        CompanionEntity c = longWall(h);
        box(h, 45, 3, 20, 45, 5, 20, Blocks.OAK_LOG);
        int wall = planks(h);
        double top = c.getY();
        double[] highest = {top};
        h.onEachTick(() -> highest[0] = Math.max(highest[0], c.getY()));
        c.startGatheringLogs(3);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking() && c.didLastTaskSucceed(), "Not felled: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(highest[0] <= top + .6, "He climbed: up to " + (highest[0] - top) + " blocks");
            h.assertTrue(planks(h) == wall, "Broke the wall: " + (wall - planks(h)) + " planks");
        });
    }

    private static net.minecraft.world.entity.ai.goal.GoalSelector goals(CompanionEntity c) {
        try {
            var field = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return (net.minecraft.world.entity.ai.goal.GoalSelector)field.get(c);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    /** The whole route to the player by the companion's own search. */
    private static Path search(CompanionEntity c, net.minecraft.server.level.ServerPlayer player) {
        return ((CompanionNavigation)c.getNavigation()).searchNow(java.util.Set.of(player.blockPosition()), 1);
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
