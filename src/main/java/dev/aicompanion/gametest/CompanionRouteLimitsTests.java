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
 * wide search per tick, a far bench is accepted only if the walk to it is short, and a far walk is not progress without end.
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
        BlockPos stone = new BlockPos(2, 2, 6); h.setBlock(stone, Blocks.STONE);
        CompanionEntity c = CompanionSingleRouteTests.spawn(h, 2, 2, 2);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(c.startWork("gather_stone", "", 1).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && CompanionSingleRouteTests.count(c, Items.COBBLESTONE) == 1 && h.getBlockState(stone).isAir(), c.getLastTaskResult());
        });
    }

    /**
     * F6: a sealed stone room (twelve blocks tall, stone lines its inside, no way in) nearer than the one stone he can reach: its faces make more than
     * five groups of eight, each a failed search and a failed wide one. He still gets the reachable stone, and no tick of his
     * searches costs more than a couple of them.
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
        // The first searches of a cold JVM are slow: warmed up on the sealed room, then the meter starts.
        for (int i = 0; i < 12; i++) nav.createPath(h.absolutePos(new BlockPos(15, 2, 15)), 1);
        nav.resetMaxTick();
        h.assertTrue(c.startWork("gather_stone", "", 1).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && CompanionSingleRouteTests.count(c, Items.COBBLESTONE) == 1 && h.getBlockState(stone).isAir(), c.getLastTaskResult());
            double ms = nav.meter().maxTickMs();
            System.out.println("[route-limits] unreachableStoneGroups max search ms per tick: " + ms);
            h.assertTrue(ms < 8, "The searches of one tick took " + ms + " ms");
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
            boolean still; try { still = (boolean) chasing.invoke(loot); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
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
