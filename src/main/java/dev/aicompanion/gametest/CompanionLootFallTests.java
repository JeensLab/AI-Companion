package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A drop that lies at the bottom of a pit ten blocks deep (marathon m15: a fall of 23 blocks under the pickup, four falls of two in a row
 * down a slope of ledges, all while going for things lying below): he never walks into a fall of more than three blocks for it, and
 * drops one after another count as one fall. With steps down he takes the drop by them; without any the drop is left and the pickup
 * stands down.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLootFallTests {
    /** A stone plateau (he stands at y 14) with a pit ten blocks deep (bottom stand y 4) and a drop at its bottom. */
    private static BlockPos pit(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 31, 13, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 18, 4, 20, 27, 13, 23, Blocks.AIR);
        return new BlockPos(22, 4, 22);
    }

    /** The steps down the pit's wall along its last row: one block lower at every column. */
    private static void steps(GameTestHelper h) {
        for (int i = 1; i <= 9; i++) CompanionSingleRouteTests.box(h, 17 + i, 3, 23, 17 + i, 13 - i, 23, Blocks.STONE);
    }

    /**
     * Ledges down the pit's west end over rows {@code firstRow..lastRow}, each three blocks lower than the one before and one column wide:
     * every drop is allowed, together they are a fall of ten. Only ledges one row long are no way down: on longer ones a way lands on a
     * ledge and walks a column along it before the next drop, which starts a new fall (CompanionMoves.fall) and is allowed.
     */
    private static void ledges(GameTestHelper h, int firstRow, int lastRow) {
        CompanionSingleRouteTests.box(h, 18, 3, firstRow, 18, 10, lastRow, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 19, 3, firstRow, 19, 7, lastRow, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 20, 3, firstRow, 20, 4, lastRow, Blocks.STONE);
    }

    private static CompanionEntity companion(GameTestHelper h, BlockPos bottom) throws Exception {
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 8, 14, 21);
        c.setHealth(c.getMaxHealth());
        Object loot = CompanionSingleRouteTests.field(c, "loot");
        var drop = loot.getClass().getDeclaredMethod("drop", BlockPos.class, List.class); drop.setAccessible(true);
        drop.invoke(loot, h.absolutePos(bottom), List.of(new ItemStack(Items.COBBLESTONE)));
        return c;
    }

    private static boolean chasing(CompanionEntity c) throws Exception {
        Object loot = CompanionSingleRouteTests.field(c, "loot");
        var canChase = loot.getClass().getDeclaredMethod("canChase"); canChase.setAccessible(true);
        return (boolean) canChase.invoke(loot);
    }

    /** Ledges of three blocks one after another are a fall of ten in all (he is in the air over each when he turns to the next): left as well. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void dropBelowLedgesOfThreeBlocksIsLeftAndNotFallenFor(GameTestHelper h) throws Exception {
        BlockPos bottom = pit(h);
        ledges(h, 22, 22);
        left(h, companion(h, bottom));
    }

    /** Steps down the wall: he goes by them, takes the drop and is unhurt. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void dropAtThePitBottomIsFetchedByTheSteps(GameTestHelper h) throws Exception {
        BlockPos bottom = pit(h);
        steps(h);
        fetched(h, companion(h, bottom));
    }

    /** The ledges are the shorter way down, the steps along the pit's last row the safe one: he takes the steps. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void dropBelowLedgesIsFetchedByTheStepsRoundThem(GameTestHelper h) throws Exception {
        BlockPos bottom = pit(h);
        ledges(h, 20, 22);
        steps(h);
        fetched(h, companion(h, bottom));
    }

    /** The route search itself: from nowhere on the plateau is a way down the ledges found, and the way down by the steps has no run of drops past three blocks. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void routeDownNeverAddsUpDropsPastWhatHeMayFall(GameTestHelper h) {
        BlockPos bottom = pit(h);
        ledges(h, 22, 22);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 8, 14, 21);
        var nav = (CompanionNavigation) c.getNavigation();
        // A search begins once he stands on the ground.
        h.runAfterDelay(5, () -> {
            // Wherever a stroll has taken him on the plateau when he tries the drop again: the answer may not depend on the start.
            for (int x = 0; x <= 31; x++) for (int z = 0; z <= 31; z++) {
                if (x >= 18 && x <= 27 && z >= 20 && z <= 23) continue;
                CompanionEntity from = h.spawn(AICompanionMod.COMPANION.get(), x, 14, z);
                from.setNoAi(true); from.setOnGround(true);
                var way = ((CompanionNavigation) from.getNavigation()).searchNow(java.util.Set.of(h.absolutePos(bottom)), 0);
                from.discard();
                h.assertTrue(way != null && !way.canReach(), "A way down the ledges was found from " + x + ", 14, " + z + ": " + way);
            }
            steps(h);
            var path = nav.searchNow(java.util.Set.of(h.absolutePos(bottom)), 1);
            h.assertTrue(path != null && path.canReach(), "No way down by the steps");
            int fall = 0;
            for (int i = 1; i < path.getNodeCount(); i++) {
                int drop = path.getNode(i - 1).y - path.getNode(i).y;
                // A drop of one block lands within a column's time: a stair, not a fall that goes on.
                if (drop <= 1) { fall = 0; continue; }
                fall += drop;
                h.assertTrue(fall <= 3, "A fall of " + fall + " blocks in a row on the way, at node " + i);
            }
            h.succeed();
        });
    }

    /**
     * A plateau, a ledge one cell wide two blocks below it, ground two blocks below the ledge, and nothing else between: the plateau drops
     * onto the ledge (quicker), and a stair of single blocks leads down to it as well. The drop onto the ledge and on to the ground is a fall
     * of four, the stair and the drop a fall of two: the search reaches the ledge by the drop first, and must still find the way by the stair.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void aLedgeReachedByADropAndByAStairKeepsTheDropBeyondItForTheStair(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 10, 7, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 11, 2, 0, 11, 12, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 12, 2, 0, 31, 3, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 12, 4, 9, 12, 12, 9, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 11, 6, 10, 11, 12, 10, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 11, 7, 9, 11, 12, 9, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 0, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 0, 31, 31, 23, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 0, 23, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 31, 0, 0, 31, 23, 31, Blocks.BEDROCK);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 8, 8, 10);
        var nav = (CompanionNavigation) c.getNavigation();
        h.runAfterDelay(5, () -> {
            var path = nav.searchNow(java.util.Set.of(h.absolutePos(new BlockPos(16, 4, 10))), 1);
            StringBuilder dbg = new StringBuilder(); if (path != null) for (int i = 0; i < path.getNodeCount(); i++) dbg.append(" ").append(h.relativePos(path.getNode(i).asBlockPos()).toShortString());
            h.assertTrue(path != null && path.canReach(), "No way to the ground by the stair: " + dbg);
            int fall = 0;
            for (int i = 1; i < path.getNodeCount(); i++) {
                int drop = path.getNode(i - 1).y - path.getNode(i).y;
                if (drop <= 1) { fall = 0; continue; }
                fall += drop;
                h.assertTrue(fall <= 3, "A fall of " + fall + " blocks in a row on the way, at node " + i);
            }
            h.succeed();
        });
    }

    /** The plateau, the wall, the ledge and the ground of the test before, with {@code stair} cut in the wall as its way up from the ledge. */
    private static void ledgeTerrain(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 10, 7, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 11, 2, 0, 11, 12, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 12, 2, 0, 31, 3, 31, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 12, 4, 9, 12, 12, 9, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 11, 6, 10, 11, 12, 10, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 0, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 0, 31, 31, 23, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 0, 23, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 31, 0, 0, 31, 23, 31, Blocks.BEDROCK);
    }

    private static String route(GameTestHelper h, net.minecraft.world.level.pathfinder.Path path) {
        StringBuilder text = new StringBuilder();
        if (path != null) for (int i = 0; i < path.getNodeCount(); i++) text.append(" ").append(h.relativePos(path.getNode(i).asBlockPos()).toShortString());
        return text.toString();
    }

    /**
     * The stair up to the ledge is cut in the wall behind a long walled corridor: the plateau reaches its top only by that way round. The search
     * drops onto the ledge first (the ledge is a fall of two, and a second drop from it a fall of four), then climbs the stair from the ledge,
     * and the step down from the stair to the ledge is a new standing there, which may drop to the ground: drop, up, down, drop.
     * The way is found whatever cell the search reached first, and no node is on it twice.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void aStairReachedOnlyFromTheLedgeStillLetsTheLedgeDropOn(GameTestHelper h) {
        ledgeTerrain(h);
        CompanionSingleRouteTests.box(h, 11, 7, 1, 11, 12, 9, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 12, 4, 1, 12, 12, 9, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 10, 8, 2, 10, 12, 9, Blocks.STONE);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 8, 8, 10);
        var nav = (CompanionNavigation) c.getNavigation();
        h.runAfterDelay(5, () -> {
            var path = nav.searchNow(java.util.Set.of(h.absolutePos(new BlockPos(16, 4, 10))), 1);
            h.assertTrue(path != null && path.canReach(), "No way to the ground by the stair after the ledge:" + route(h, path));
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            for (int i = 0; i < path.getNodeCount(); i++) h.assertTrue(seen.add(path.getNode(i)), "A node is on the way twice, at " + i + ":" + route(h, path));
            int fall = 0;
            for (int i = 1; i < path.getNodeCount(); i++) {
                int drop = path.getNode(i - 1).y - path.getNode(i).y;
                if (drop <= 1) { fall = 0; continue; }
                fall += drop;
                h.assertTrue(fall <= 3, "A fall of " + fall + " blocks in a row on the way, at node " + i + ":" + route(h, path));
            }
            h.succeed();
        });
    }

    /** Water ends a fall: from the ledge, a fall of two blocks into a pool is no second fall past three, and the way on to the pool is found. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void aDropIntoWaterBelowALedgeIsNoSecondFall(GameTestHelper h) {
        ledgeTerrain(h);
        CompanionSingleRouteTests.box(h, 12, 4, 6, 20, 4, 14, Blocks.WATER);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 8, 8, 10);
        var nav = (CompanionNavigation) c.getNavigation();
        h.runAfterDelay(5, () -> {
            var path = nav.searchNow(java.util.Set.of(h.absolutePos(new BlockPos(15, 4, 10))), 1);
            h.assertTrue(path != null && path.canReach(), "No way from the ledge into the pool:" + route(h, path));
            h.succeed();
        });
    }

    /** He stays on the plateau unhurt, and after a while is no longer going for the drop. */
    private static void left(GameTestHelper h, CompanionEntity c) {
        h.onEachTick(() -> h.assertTrue(c.getY() > h.absolutePos(new BlockPos(0, 12, 0)).getY() && c.getHealth() >= c.getMaxHealth(),
                "He went down the pit: y " + c.getY() + ", health " + c.getHealth() + " at " + h.relativePos(c.blockPosition()).toShortString()));
        // A stroll of four blocks earns the drop another look, a few ticks long (CompanionLoot.giveUpFromHere): only going for it on and on is wrong.
        h.runAtTickTime(900, () -> h.succeedWhen(() -> {
            try { h.assertTrue(!chasing(c), "Still going for the drop at " + h.relativePos(c.blockPosition()).toShortString()); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }));
    }

    /** He is never hurt on the way and the drop is in his bag at the end. */
    private static void fetched(GameTestHelper h, CompanionEntity c) {
        h.onEachTick(() -> h.assertTrue(c.getHealth() >= c.getMaxHealth(), "He fell: health " + c.getHealth() + " at " + h.relativePos(c.blockPosition()).toShortString()));
        h.succeedWhen(() -> h.assertTrue(CompanionSingleRouteTests.count(c, Items.COBBLESTONE) == 1,
                "Drop not picked up, he is at " + h.relativePos(c.blockPosition()).toShortString()));
    }
}
