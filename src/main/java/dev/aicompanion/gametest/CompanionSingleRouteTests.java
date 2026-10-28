package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;

/**
 * The single route (ticket 04): ore mining walks to its ore and out of the mine by it. An ore ten blocks off by the straight
 * line whose only way is a detour longer than one search is not «no safe way»: it is far, and he goes there in legs.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSingleRouteTests {
    private static final int SURFACE = 19;

    /**
     * Bedrock all through with a one-wide corridor winding back and forth (nothing to dig, no short cut): about 60 cells to an
     * iron ore in the end wall that is thirteen blocks from the start by the straight line, inside the scan of the order.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void oreBeyondOneSearchIsWalkedToInLegs(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        BlockPos ore = new BlockPos(14, 2, 12); h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1 && h.getBlockState(ore).isAir(), c.getLastTaskResult());
            h.assertTrue(routeEvents(c).stream().anyMatch(line -> line.contains("\"activity\":\"ORE\"")), "No route_outcome of the ore walk in the journal");
        });
    }

    /**
     * A mine of 59 cells, 17 blocks deep, winding over four rows (the way out is longer than one search). Iron in the wall at its
     * bottom; the entrance is his exit. After the ore he walks up his own corridor to the entrance, in legs, and says nothing of
     * being below; no stone is dug on the way.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void climbsOutOfALongMineToItsEntranceInLegs(GameTestHelper h) throws Exception {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, SURFACE - 1, 31, Blocks.STONE);
        box(h, 0, SURFACE, 0, 31, SURFACE, 31, Blocks.GRASS_BLOCK);
        List<int[]> cells = new java.util.ArrayList<>();
        for (int row = 0; row < 4; row++) {
            int z = 6 + 2 * row;
            for (int k = 0; k < 14; k++) cells.add(new int[]{row % 2 == 0 ? 4 + k : 17 - k, z});
            if (row < 3) cells.add(new int[]{row % 2 == 0 ? 17 : 4, z + 1});
        }
        for (int i = 0; i < cells.size(); i++) {
            int floor = Math.max(2, SURFACE - i / 3), x = cells.get(i)[0], z = cells.get(i)[1];
            box(h, x, floor, z, x, floor, z, floor == SURFACE ? Blocks.GRASS_BLOCK : Blocks.STONE);
            box(h, x, floor + 1, z, x, floor + 3, z, Blocks.AIR);
        }
        int[] bottom = cells.get(cells.size() - 1);
        BlockPos ore = new BlockPos(bottom[0] - 1, 3, bottom[1]); h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = spawn(h, bottom[0], 3, bottom[1]);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        BlockPos entrance = h.absolutePos(new BlockPos(cells.get(0)[0], SURFACE + 1, cells.get(0)[1]));
        Object work = field(c, "work");
        var exit = work.getClass().getDeclaredField("oreExit"); exit.setAccessible(true); exit.set(work, entrance);
        int[] solid = {solidCount(h)};
        // After the work he follows the player, who stands at the bottom: what counts is how high he got.
        double[] highest = {c.getY()};
        h.onEachTick(() -> highest[0] = Math.max(highest[0], c.getY()));
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, c.getLastTaskResult());
            h.assertTrue(highest[0] >= entrance.getY() - .5 && !c.getLastTaskResult().contains("Наверх пока не выбрался"), "Still below: " + c.position() + " vs " + entrance + " " + c.getLastTaskResult());
            h.assertTrue(solidCount(h) >= solid[0] - 1, "Dug on the way up: " + (solid[0] - solidCount(h)) + " blocks");
            h.assertTrue(routeEvents(c).stream().anyMatch(line -> line.contains("\"activity\":\"ORE\"") && line.contains("\"outcome\":\"NEAR\"")), "No route_outcome of the climb");
        });
    }

    /**
     * Ticket 05: stone, a trunk, dirt and a drop that lie thirteen blocks off by the straight line, inside the scan, behind a
     * corridor of about 60 cells (bedrock all round, nothing to dig): the way is longer than one search, so they are far, not lost.
     */
    private static BlockPos labyrinth(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        return new BlockPos(14, 2, 12);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stoneBeyondOneSearchIsWalkedTo(GameTestHelper h) {
        BlockPos stone = labyrinth(h); h.setBlock(stone, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(c.startWork("gather_stone", "", 1).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) == 1 && h.getBlockState(stone).isAir(), c.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void trunkBeyondOneSearchIsWalkedTo(GameTestHelper h) {
        BlockPos trunk = labyrinth(h); h.setBlock(trunk, Blocks.OAK_LOG);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.startGatheringLogs(1);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Still gathering: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.OAK_LOG) == 1 && h.getBlockState(trunk).isAir(), c.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void dirtBeyondOneSearchIsWalkedTo(GameTestHelper h) throws Exception {
        BlockPos dirt = labyrinth(h); h.setBlock(dirt, Blocks.DIRT);
        box(h, 14, 3, 12, 14, 7, 12, Blocks.AIR); // soil is dug only with a free cover above it (CompanionQuarry.safeCell)
        CompanionEntity c = spawn(h, 4, 2, 4);
        var gather = CompanionEntity.class.getDeclaredMethod("startGatheringDirtForWork", int.class); gather.setAccessible(true); gather.invoke(c, 1);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask(), "Still gathering: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.DIRT) == 1 && h.getBlockState(dirt).isAir(), c.getLastTaskResult());
        });
    }

    /** A drop of his harvest that came to rest at the far end of the corridor is picked up: the walk to it is in legs. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void dropBeyondOneSearchIsPickedUp(GameTestHelper h) throws Exception {
        BlockPos spot = labyrinth(h).west();
        CompanionEntity c = spawn(h, 4, 2, 4);
        Object loot = field(c, "loot");
        var drop = loot.getClass().getDeclaredMethod("drop", BlockPos.class, List.class); drop.setAccessible(true);
        drop.invoke(loot, h.absolutePos(spot), List.of(new ItemStack(Items.COBBLESTONE)));
        h.succeedWhen(() -> h.assertTrue(count(c, Items.COBBLESTONE) == 1, "Drop not picked up: " + c.getLastTaskResult()));
    }

    /**
     * Ticket 06a: «иди ко мне» when the player is twelve blocks off by the straight line behind a wall that is walked round
     * only at its far end: a way of about sixty blocks, longer than one search; the walk goes in legs and he arrives.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void comeBeyondOneSearchArrives(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 3, 2, 16, 31, 12, 16, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 28, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 28, 2, 22);
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.didLastTaskSucceed() && c.distanceToSqr(player) <= 16, c.getLastTaskResult());
        });
    }

    /**
     * Following a player who stands sealed in bedrock: he walks up to the wall, says once that there is no way, and waits there
     * (no teleport, no second message over a minute and more); when the player steps out to a reachable place he goes to him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void followingAnUnreachablePlayerSaysOnceAndWaits(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        // Walled in all round: where the neighbouring tests stand is open ground, and he would walk round the world to find a way.
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        box(h, 21, 2, 21, 27, 7, 27, Blocks.BEDROCK);
        box(h, 23, 2, 23, 25, 5, 25, Blocks.AIR);
        CompanionEntity c = spawn(h, 6, 2, 6);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 24, 2, 24);
        int[] said = {0};
        h.onEachTick(() -> {
            if (h.getTick() == 1100) said[0] = c.autonomyLineSerial();
            if (h.getTick() > 1100 && h.getTick() < 2300) h.assertTrue(c.autonomyLineSerial() == said[0], "Spoke again while waiting");
            if (h.getTick() == 2300) {
                h.assertTrue(said[0] == 1, "Told " + said[0] + " times, not once: " + c.autonomyLine() + " | " + c.getLastTaskResult() + " | " + state(c) + " at " + h.relativePos(c.blockPosition()).toShortString());
                h.assertTrue(c.distanceToSqr(player) > 9, "Reached the sealed player?");
                teleport(h, player, 8, 2, 28);
            }
        });
        // He may stroll up to eight blocks from the player once he has come (the idle stroll's own bound): reaching him counts, not staying.
        h.succeedWhen(() -> h.assertTrue(h.getTick() > 2300 && c.distanceToSqr(player) <= 36, "Did not go to the player who moved: " + h.relativePos(c.blockPosition()).toShortString()));
    }

    /** A cow at the far end of the corridor, a detour of sixty cells: hunted in legs, not given up as unreachable. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void animalBeyondOneSearchIsHunted(GameTestHelper h) {
        BlockPos end = labyrinth(h).west();
        h.spawn(net.minecraft.world.entity.EntityType.COW, end.getX(), end.getY(), end.getZ());
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        h.assertTrue(c.startWork("hunt_animals", "minecraft:cow", 1).success(), "Hunt order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still hunting: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.BEEF) >= 1, c.getLastTaskResult());
        });
    }

    /** A chest at the far end of the corridor (a detour of sixty cells, inside his search radius): reached in legs, not given up. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void chestBeyondOneSearchIsReached(GameTestHelper h) {
        BlockPos end = labyrinth(h); h.setBlock(end, Blocks.CHEST);
        h.setBlock(end.above(), Blocks.AIR); // a chest under a solid block cannot be opened
        h.assertTrue(h.getBlockEntity(end) instanceof net.minecraft.world.Container, "No chest block entity");
        ((net.minecraft.world.Container) h.getBlockEntity(end)).setItem(0, new ItemStack(Items.OAK_LOG, 3));
        CompanionEntity c = spawn(h, 4, 2, 4);
        h.assertTrue(c.startWork("take_items", "logs", 3).success(), "Take order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.OAK_LOG) == 3, c.getLastTaskResult());
        });
    }

    /** A remembered bench at the far end of the corridor (a way of about 38, beyond one search but within twice the bench trip): walked to in legs, none set down instead. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void benchBeyondOneSearchIsWalkedTo(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        box(h, 7, 2, 8, 24, 3, 8, Blocks.AIR);
        BlockPos bench = new BlockPos(7, 2, 8); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.rememberPlaced(h.absolutePos(bench));
        c.setItem(0, new ItemStack(Items.STICK, 7));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(player, "craft_item", "minecraft:ladder", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.LADDER) == 3, c.getLastTaskResult());
            int benches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(31, 3, 31))) if (h.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) benches++;
            h.assertTrue(benches == 1, "Another bench was set down: " + benches);
        });
    }

    /**
     * Ticket 06b: following keeps up with a player walking at walking pace (4.3 blocks a second): he walks as fast as he used to
     * near the player (1.15), not slower. A strip of flat ground, the player twelve blocks off walking away along it.
     */
    @GameTest(template = "marathon_far", timeoutTicks = 700)
    public static void followingKeepsUpWithAWalkingPlayer(GameTestHelper h) {
        box(h, 40, 2, 0, 71, 12, 171, Blocks.AIR);
        box(h, 40, 1, 0, 71, 1, 171, Blocks.STONE);
        box(h, 39, 1, 0, 39, 14, 171, Blocks.BARRIER); box(h, 72, 1, 0, 72, 14, 171, Blocks.BARRIER);
        CompanionEntity c = spawn(h, 56, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 56, 2, 22);
        double[] z = {22};
        h.onEachTick(() -> {
            if (h.getTick() > 450) return;
            z[0] += 4.3 / 20;
            BlockPos at = h.absolutePos(new BlockPos(56, 2, 0));
            player.teleportTo(at.getX() + .5, at.getY(), at.getZ() + z[0]);
        });
        h.runAtTickTime(452, () -> {
            h.assertTrue(c.distanceToSqr(player) <= 14 * 14, "Lagged behind: " + Math.round(Math.sqrt(c.distanceToSqr(player))) + " blocks");
            h.succeed();
        });
    }

    /** Within three blocks of the player but a wall between (no sight): one NEAR event of the journal, not one a tick. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void arrivedNearButNotSeenTellsOnce(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 11, 2, 0, 11, 12, 31, Blocks.BEDROCK);
        // Closed all round (and the wall across the whole of it): no way round for the wide search through the neighbours' arenas.
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 10, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 12, 2, 10);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 300 && !c.isWorking(), "Still walking: " + c.getLastTaskResult());
            long near = routeEvents(c).stream().filter(line -> line.contains("\"NEAR\"")).count();
            h.assertTrue(near == 1, near + " NEAR events for one walk: " + c.getLastTaskResult());
            h.assertTrue(c.getLastTaskResult().equals("Не могу к тебе пройти, вещи остались у меня."), "The hand-over ended otherwise: " + c.getLastTaskResult());
        });
    }

    /** «Иди ко мне» to a player sealed in bedrock: it ends at once with the honest short answer, not with «Застрял». */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void comeToASealedPlayerSaysThereIsNoWay(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        box(h, 21, 2, 21, 27, 7, 27, Blocks.BEDROCK);
        box(h, 23, 2, 23, 25, 5, 25, Blocks.AIR);
        CompanionEntity c = spawn(h, 6, 2, 6);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 24, 2, 24);
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.getLastTaskResult().equals("Не могу к тебе пройти."), c.getLastTaskResult());
        });
    }

    /** Review fix F1: a player standing off the centre of his block: the hand-over still comes close enough to toss the gift. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void handOverToAnOffCentrePlayerSucceeds(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 11, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        BlockPos stand = h.absolutePos(new BlockPos(14, 2, 10));
        player.teleportTo(stand.getX() + .99, stand.getY(), stand.getZ() + .5);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
        });
    }

    /** Review fix F1: three blocks from the player (near) but a pillar in the line of sight: he steps round it and hands over. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void handOverBehindAPillarCornerSucceeds(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 11, 2, 10, 11, 5, 10, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 9, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 12, 2, 10);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
        });
    }

    /**
     * Review fix F3: the player on top of an eight-high pillar four blocks aside, following on: he does not set off on side steps of
     * twenty blocks (he stays by the pillar's foot) and the self-rescue is left its turn.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void followingAPlayerOnAPillarStaysByItsFoot(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 14, 2, 16, 14, 9, 16, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 10, 2, 16);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 14, 10, 16);
        BlockPos foot = h.absolutePos(new BlockPos(14, 2, 16));
        double[] far = {0};
        h.onEachTick(() -> {
            if (h.getTick() > 100) far[0] = Math.max(far[0], Math.hypot(c.getX() - foot.getX() - .5, c.getZ() - foot.getZ() - .5));
            if (h.getTick() == 1500) {
                h.assertTrue(far[0] <= 6, "Wandered " + far[0] + " blocks from the pillar: " + h.relativePos(c.blockPosition()).toShortString());
                h.succeed();
            }
        });
    }

    /**
     * Review fix F4: a player sealed in bedrock and higher than him: the self-rescue interrupts the following over and over, yet the
     * phrase is said once over three minutes.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void aHigherSealedPlayerIsToldOnce(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        box(h, 21, 2, 21, 27, 12, 27, Blocks.BEDROCK);
        box(h, 23, 6, 23, 25, 9, 25, Blocks.AIR);
        CompanionEntity c = spawn(h, 6, 2, 6);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 24, 6, 24);
        // Anything that interrupts the following for a moment (here an open inventory) starts it anew; the phrase is said once all the same.
        h.onEachTick(() -> {
            if (h.getTick() % 1000 >= 900 && h.getTick() % 1000 <= 903) setInventoryViewers(c, h.getTick() % 1000 < 903 ? 1 : 0);
        });
        h.runAtTickTime(3600, () -> {
            h.assertTrue(c.autonomyLineSerial() == 1, "Told " + c.autonomyLineSerial() + " times, not once: " + c.autonomyLine() + " | " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /** Review fix F11: the follow phrase does not replace the result of the order that ended before it. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void theFollowPhraseKeepsTheOrderResult(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        box(h, 1, 2, 16, 30, 12, 16, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 15, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 15, 2, 26);
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        h.runAtTickTime(2400, () -> {
            h.assertTrue(c.autonomyLineSerial() >= 1, "The follow phrase was not said");
            h.assertTrue(c.getLastTaskResult().equals("Не могу к тебе пройти."), "The result of the order was replaced: " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /** Review fix F5: «иди ко мне» to a player beyond the reach of the wide search, with a wall between: stuck, not «no way». */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void comeFromBeyondTheWideSearchSaysStuck(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        box(h, 1, 2, 16, 30, 12, 16, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 15, 2, 3);
        c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 15, 2, 29);
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.getLastTaskResult().contains("Застрял"), c.getLastTaskResult());
        });
    }

    /** Round 2, F1a: the player stands on a carpet, the cells round his feet are carpets too (they have a collision shape). */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void handOverToAPlayerOnACarpetSucceeds(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 11, 2, 10, 11, 5, 10, Blocks.BEDROCK);
        box(h, 12, 2, 9, 13, 2, 11, Blocks.WHITE_CARPET);
        CompanionEntity c = spawn(h, 9, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 12, 2, 10);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
        });
    }

    /** Round 2, F1b: the player is in a small house two blocks behind its wall, the door is eleven blocks aside: the way in is longer than a short step. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void handOverToAPlayerInAHouseBehindItsWallSucceeds(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 6, 2, 14, 26, 6, 18, Blocks.BEDROCK);
        box(h, 7, 2, 15, 25, 5, 17, Blocks.AIR);
        box(h, 25, 2, 14, 25, 3, 14, Blocks.AIR);
        CompanionEntity c = spawn(h, 14, 2, 13);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 14, 2, 15);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
        });
    }

    /** Round 2, F7: legs that went and then stood still, towards a player sealed in bedrock: the honest «no way», not «застрял». */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void handOverToASealedPlayerAfterWalkingSaysThereIsNoWay(GameTestHelper h) {
        sealedArena(h);
        CompanionEntity c = spawn(h, 10, 2, 10);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 24, 2, 24);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(c.getLastTaskResult().equals("Не могу к тебе пройти, вещи остались у меня."), c.getLastTaskResult());
        });
    }

    /** Round 2, F9: the walk's time bound is not renewed when the followed player shifts a block. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void followingShiftsDoNotRenewTheWalkBound(GameTestHelper h) throws Exception {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 4, 2, 4);
        Object travel = field(c, "travel");
        var follow = travel.getClass().getDeclaredMethod("follow", BlockPos.class, double.class, double.class);
        follow.setAccessible(true);
        BlockPos goal = h.absolutePos(new BlockPos(24, 2, 24));
        follow.invoke(travel, goal, 3D, 1D);
        long first = (Long) field(travel, "walkUntil");
        h.runAtTickTime(40, () -> {
            try {
                follow.invoke(travel, goal.east(), 3D, 1D);
                h.assertTrue((Long) field(travel, "walkUntil") == first, "The bound was renewed by a shift of one block");
            } catch (Exception e) { throw new IllegalStateException(e); }
            h.succeed();
        });
    }

    /** Round 2, Н5: told once, he is then with the player (the following ends near him); the player leaves for the same sealed place: told again. */
    @GameTest(template = "tall_tree", timeoutTicks = 4200)
    public static void followPhraseIsSaidAgainAfterTheFollowingEndedNearThePlayer(GameTestHelper h) {
        sealedArena(h);
        // Thicker walls: he waits farther than four blocks from the player, where the following does not end by itself.
        box(h, 19, 2, 19, 29, 7, 29, Blocks.BEDROCK); box(h, 23, 2, 23, 25, 5, 25, Blocks.AIR);
        CompanionEntity c = spawn(h, 6, 2, 6);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 24, 2, 24);
        h.runAtTickTime(1100, () -> {
            h.assertTrue(c.autonomyLineSerial() == 1, "Told " + c.autonomyLineSerial() + " times by now: " + c.autonomyLine());
            BlockPos home = h.absolutePos(new BlockPos(6, 2, 6));
            c.setPos(home.getX() + .5, home.getY(), home.getZ() + .5);
            player.teleportTo(home.getX() + 2.5, home.getY(), home.getZ() + .5);
        });
        h.runAtTickTime(1200, () -> teleport(h, player, 24, 2, 24));
        h.runAtTickTime(3900, () -> {
            h.assertTrue(c.autonomyLineSerial() == 2, "Told " + c.autonomyLineSerial() + " times, not twice: " + c.autonomyLine() + " | " + state(c) + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.succeed();
        });
    }

    /** Round 2, Н6: in the air nothing is searched: «no way» is not said. On the ground the same sealed player is told. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void noWayIsNotToldWhileInTheAir(GameTestHelper h) throws Exception {
        sealedArena(h);
        CompanionEntity c = spawn(h, 15, 12, 15);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        teleport(h, player, 24, 2, 24);
        BlockPos target = h.absolutePos(new BlockPos(24, 2, 24));
        h.assertTrue(!c.onGround(), "Not in the air at the start");
        h.assertTrue(!noWayTo(c, target), "«No way» told in the air");
        // Polled: one wide search is allowed per tick, and the companion's own following may have taken this tick's.
        h.succeedWhen(() -> h.assertTrue(h.getTick() > 100 && c.onGround() && noWayTo(c, target), "On the ground «no way» is expected"));
    }

    /** Round 2, Н3: the tick's wide search is spent for a while (the route answers «busy»): he waits for a trunk beyond one search, he does not start climbing it. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void trunkAskedWhileBusyIsWaitedFor(GameTestHelper h) {
        BlockPos trunk = labyrinth(h); h.setBlock(trunk, Blocks.OAK_LOG);
        CompanionEntity c = spawn(h, 6, 2, 4);
        forceBusy(h, c, 80);
        c.startGatheringLogs(1);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Still gathering: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.OAK_LOG) == 1 && h.getBlockState(trunk).isAir(), c.getLastTaskResult());
        });
    }

    /** Round 2, Н4: the same for the remembered bench: «busy» is not «no way», no «cannot go there now». */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void benchAskedWhileBusyIsWaitedFor(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        box(h, 7, 2, 8, 24, 3, 8, Blocks.AIR);
        BlockPos bench = new BlockPos(7, 2, 8); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.rememberPlaced(h.absolutePos(bench));
        c.setItem(0, new ItemStack(Items.STICK, 7));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        forceBusy(h, c, 60);
        h.assertTrue(CompanionEvents.executeAction(player, "craft_item", "minecraft:ladder", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.LADDER) == 3, c.getLastTaskResult());
        });
    }

    /** Until this tick of the test the companion's wide search is taken before it can ask: every search that needs it is told «busy». */
    private static void forceBusy(GameTestHelper h, CompanionEntity c, int untilTick) {
        h.onEachTick(() -> {
            if (h.getTick() > untilTick) return;
            try {
                var nav = c.getNavigation();
                var wideTick = nav.getClass().getDeclaredField("wideTick"); wideTick.setAccessible(true);
                wideTick.setLong(nav, c.level().getGameTime() + 1);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    private static boolean noWayTo(CompanionEntity c, BlockPos target) {
        try {
            Object route = field(field(c, "work"), "route");
            var method = route.getClass().getDeclaredMethod("noWayTo", BlockPos.class); method.setAccessible(true);
            return (boolean) method.invoke(route, target);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** A bedrock arena with a sealed cell (23..25) in its corner: the player in it cannot be reached. */
    private static void sealedArena(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 1, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 31, 12, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 31, 12, 31, Blocks.BEDROCK);
        box(h, 0, 2, 0, 0, 12, 31, Blocks.BEDROCK); box(h, 31, 2, 0, 31, 12, 31, Blocks.BEDROCK);
        box(h, 21, 2, 21, 27, 7, 27, Blocks.BEDROCK);
        box(h, 23, 2, 23, 25, 5, 25, Blocks.AIR);
    }

    private static void setInventoryViewers(CompanionEntity c, int viewers) {
        try { var f = CompanionEntity.class.getDeclaredField("inventoryViewers"); f.setAccessible(true); f.setInt(c, viewers); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static String state(CompanionEntity c) {
        try { var m = CompanionEntity.class.getDeclaredMethod("diagnosticState"); m.setAccessible(true); return String.valueOf(m.invoke(c)); }
        catch (ReflectiveOperationException e) { return e.toString(); }
    }

    static void teleport(GameTestHelper h, net.minecraft.server.level.ServerPlayer player, int x, int y, int z) {
        BlockPos stand = h.absolutePos(new BlockPos(x, y, z));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    private static int solidCount(GameTestHelper h) {
        int n = 0;
        for (int x = 0; x < 32; x++) for (int y = 1; y < SURFACE; y++) for (int z = 0; z < 32; z++) if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) n++;
        return n;
    }

    static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
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

    /** The journal lines of route_outcome written by this companion. */
    static List<String> routeEvents(CompanionEntity c) {
        CompanionDiagnostics.flush();
        try {
            return Files.readAllLines(FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion").resolve("companion-latest.jsonl")).stream()
                    .filter(line -> line.contains("\"route_outcome\"") && line.contains(c.getUUID().toString())).toList();
        } catch (java.io.IOException error) { return List.of(); }
    }

    static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }

    static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        return count;
    }
}
