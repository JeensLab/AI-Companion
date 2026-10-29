package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Review of the single route (pass B): a far walk that first leads away is still a far walk, a failed choice costs at most one
 * share of cells per tick, a far bench is accepted only if the walk to it is short, and a far walk is not progress without end.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionRouteLimitsTests {
    private static void sealedArena(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
    }

    /**
     * F2: the stone is four blocks off behind bedrock; the only way to it runs 27 blocks away along one wall, 14 along the next and back
     * (about 80 blocks): for well over eight seconds it leads away from the ore, and it is still walked.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stoneWhoseOnlyWayFirstLeadsAwayIsStillWalked(GameTestHelper h) {
        sealedArena(h);
        CompanionSingleRouteTests.box(h, 2, 2, 2, 29, 3, 2, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 29, 2, 3, 29, 3, 16, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 2, 2, 16, 28, 3, 16, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 2, 2, 7, 2, 3, 15, Blocks.AIR);
        // Eight faces nearest by the straight line make the first group; the ninth, at the end of a dead end a little longer, the second:
        // its wide search is the last one made, and it is not the way he walks.
        CompanionSingleRouteTests.box(h, 3, 2, 7, 7, 3, 7, Blocks.AIR);
        for (BlockPos pos : new BlockPos[] {new BlockPos(2, 2, 6), new BlockPos(2, 3, 6), new BlockPos(1, 2, 7), new BlockPos(1, 3, 7),
                new BlockPos(1, 2, 8), new BlockPos(1, 3, 8), new BlockPos(3, 2, 8), new BlockPos(3, 3, 8), new BlockPos(8, 2, 7)}) h.setBlock(pos, Blocks.STONE);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 2, 2, 2);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(c.startWork("gather_stone", "", 1).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && CompanionSingleRouteTests.count(c, Items.COBBLESTONE) == 1, c.getLastTaskResult());
            h.assertTrue(CompanionSingleRouteTests.routeEvents(c).stream().noneMatch(line -> line.contains("no progress")),
                    "The first walk was told as given up for no progress: " + CompanionSingleRouteTests.routeEvents(c));
        });
    }

    /**
     * F6: a sealed stone room (twelve blocks tall, stone lines its inside, no way in) nearer than the one stone he can reach: its faces make more than
     * five groups of eight, each a failed search and a failed wide one. He still gets the reachable stone, and no tick of his
     * searches holds more cells than the tick's share.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void unreachableStoneGroupsDoNotCostATickEach(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 31, 13, 0, Blocks.BEDROCK); CompanionSingleRouteTests.box(h, 0, 2, 31, 31, 13, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 0, 13, 31, Blocks.BEDROCK); CompanionSingleRouteTests.box(h, 31, 2, 0, 31, 13, 31, Blocks.BEDROCK);
        // Only the quarter of the arena around the room is open: fewer cells make a search cheap enough to be counted in milliseconds.
        CompanionSingleRouteTests.box(h, 6, 2, 7, 6, 13, 31, Blocks.BEDROCK); CompanionSingleRouteTests.box(h, 30, 2, 7, 30, 13, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 6, 2, 7, 30, 13, 7, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 10, 2, 10, 21, 14, 21, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 11, 2, 11, 20, 13, 20, Blocks.STONE);
        CompanionSingleRouteTests.box(h, 12, 2, 12, 19, 13, 19, Blocks.AIR);
        BlockPos stone = new BlockPos(27, 2, 21); h.setBlock(stone, Blocks.STONE);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 15, 2, 29);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        var nav = (CompanionNavigation) c.getNavigation();
        nav.resetMaxTick();
        h.assertTrue(c.startWork("gather_stone", "", 1).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && CompanionSingleRouteTests.count(c, Items.COBBLESTONE) == 1 && h.getBlockState(stone).isAir(), c.getLastTaskResult());
            h.assertTrue(nav.maxTickCells() <= CompanionNavigation.CHOICE_CELLS, nav.maxTickCells() + " cells searched in one tick");
        });
    }

    /**
     * Review 2, N1: a vein of eighteen ores along the last corridor of a winding way (longer than one search). The nearest eight make the
     * group that is walked to by the wide search; the ninth and the rest are asked after it in the same tick: that must not cancel the
     * walk to the group, nor may the group take the tick's cells over and over.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aVeinBehindADetourIsMinedWhateverItsSize(GameTestHelper h) {
        sealedArena(h);
        CompanionSingleRouteTests.box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 5, 2, 13, 13, 3, 13, Blocks.IRON_ORE);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 4, 2, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && CompanionSingleRouteTests.count(c, Items.RAW_IRON) == 1, c.getLastTaskResult());
        });
    }

    /**
     * Review 2, N2: the cells of a sealed pocket in a big room next to him (the room is more cells than a search has) and the cells at the
     * end of a winding way. The search to both runs out of its budget in the room, not of its range: that proves nothing about the far
     * cells, which a search of their own reaches. The budget is set between what the two searches take; the route is asked directly.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aNearSealedGoalDoesNotHideAFarReachableOne(GameTestHelper h) {
        sealedArena(h);
        CompanionSingleRouteTests.box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        // The way to the room, the room, and the sealed pocket in it.
        CompanionSingleRouteTests.box(h, 2, 2, 4, 3, 3, 4, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 2, 2, 5, 2, 3, 16, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 2, 2, 17, 28, 3, 30, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 12, 2, 18, 20, 3, 26, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 15, 2, 21, 17, 3, 23, Blocks.AIR);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 4, 2, 4);
        c.setSearchRadius(24);
        java.util.Set<BlockPos> far = java.util.Set.of(h.absolutePos(new BlockPos(13, 2, 12)));
        java.util.Set<BlockPos> both = java.util.Set.of(h.absolutePos(new BlockPos(13, 2, 12)), h.absolutePos(new BlockPos(16, 2, 21)));
        // A search is made only by one that stands on the ground: a few ticks first.
        h.runAfterDelay(10, () -> {
            // The budget is measured from where he stands: kept still on the ground from here on, or a few blocks walked in between (seen in a
            // full run) change what a search costs.
            c.setNoAi(true);
            var nav = (CompanionNavigation) c.getNavigation();
            long before = nav.meter().nodes();
            nav.wide(far, 0);
            long alone = nav.meter().nodes() - before;
            before = nav.meter().nodes();
            nav.wide(both, 0);
            long together = nav.meter().nodes() - before;
            h.assertTrue(together > alone + 40, "The room does not cost the search to both more than the far cells alone: " + together + " cells against " + alone);
            // The finished searches are kept for two seconds: let them go, so the route searches anew within the lower budget.
            h.runAfterDelay(60, () -> {
            nav.limitSearch((int)(alone + together) / 2);
            Object route; java.lang.reflect.Method cells, found;
            try {
                route = CompanionSingleRouteTests.field(CompanionSingleRouteTests.field(c, "work"), "route");
                cells = route.getClass().getDeclaredMethod("cells", java.util.Set.class, int.class); cells.setAccessible(true);
                found = cells.getReturnType().getDeclaredMethod("found"); found.setAccessible(true);
                h.assertTrue(!(boolean) found.invoke(cells.invoke(route, both, 0)), "The search to both found the way: the budget is not short of it");
                h.runAfterDelay(2, () -> {
                    try { h.assertTrue((boolean) found.invoke(cells.invoke(route, far, 0)), "The far cells are written off with the room's"); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                    h.succeed();
                });
            } catch (Exception e) { throw new IllegalStateException(e); }
            });
        });
    }

    /** Review 2, F6: following a player sealed in bedrock, the leg search and the "no way" question share the cells of a tick. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void followingASealedPlayerSearchesOnlyATicksShare(GameTestHelper h) {
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); CompanionSingleRouteTests.box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); CompanionSingleRouteTests.box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 21, 2, 21, 27, 7, 27, Blocks.BEDROCK);
        CompanionSingleRouteTests.box(h, 23, 2, 23, 25, 5, 25, Blocks.AIR);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 6, 2, 6);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        CompanionSingleRouteTests.teleport(h, player, 24, 2, 24);
        var nav = (CompanionNavigation) c.getNavigation();
        nav.resetMaxTick();
        h.runAtTickTime(1400, () -> {
            h.assertTrue(nav.maxTickCells() <= CompanionNavigation.CHOICE_CELLS, nav.maxTickCells() + " cells searched in one tick");
            h.succeed();
        });
    }

    /**
     * F9: a far drop whose way is blocked after he set out (a plug in the corridor, and the way round is longer than the wide search
     * sees): the pickup is given up within the time the walk was worth, he does not stand chasing it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void farDropWhoseWayIsBlockedMidWayIsGivenUp(GameTestHelper h) throws Exception {
        sealedArena(h);
        CompanionSingleRouteTests.box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 4, 2, 4);
        Object loot = CompanionSingleRouteTests.field(c, "loot");
        var drop = loot.getClass().getDeclaredMethod("drop", BlockPos.class, java.util.List.class); drop.setAccessible(true);
        drop.invoke(loot, h.absolutePos(new BlockPos(13, 2, 12)), java.util.List.of(new ItemStack(Items.COBBLESTONE)));
        var chasing = loot.getClass().getDeclaredMethod("canChase"); chasing.setAccessible(true);
        h.runAtTickTime(100, () -> CompanionSingleRouteTests.box(h, 24, 2, 6, 24, 3, 6, Blocks.BEDROCK));
        h.runAtTickTime(1100, () -> {
            boolean still; try { still = (boolean) chasing.invoke(loot); } catch (Exception e) { throw new IllegalStateException(e); }
            h.assertTrue(!still, "Still chasing the drop at " + h.relativePos(c.blockPosition()).toShortString());
            h.succeed();
        });
    }

    /** F8: a remembered bench ten blocks off behind a way of sixty is not walked to: a new bench is set down here instead. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void benchBehindALongDetourIsNotWalkedTo(GameTestHelper h) {
        sealedArena(h);
        CompanionSingleRouteTests.box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        CompanionSingleRouteTests.box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        BlockPos bench = new BlockPos(14, 2, 12); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 4, 2, 4);
        c.rememberPlaced(h.absolutePos(bench));
        c.setItem(0, new ItemStack(Items.STICK, 7));
        c.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(player, "craft_item", "minecraft:ladder", 1).success(), "Craft order not accepted");
        double[] closest = {Double.MAX_VALUE};
        h.succeedWhen(() -> {
            closest[0] = Math.min(closest[0], Math.sqrt(c.distanceToSqr(h.absolutePos(bench).getCenter())));
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && CompanionSingleRouteTests.count(c, Items.LADDER) == 3, c.getLastTaskResult());
            // Where he came closest to the far bench tells whether he went the long way to it.
            h.assertTrue(closest[0] > 8, "He walked to the far bench (came within " + closest[0] + " blocks of it) instead of setting one down");
        });
    }
}
