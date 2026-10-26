package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game log 22.10 (sessions efc17045 and b65ec97a, build 49a4b97): «для добычи камня, который перед ним, зачем-то идёт далеко
 * в другую сторону». Stone, ore and wood a few steps away are taken like a player takes them: by the real walk, not the
 * straight line, without a self-rescue where a player just walks up. The arenas are uneven on purpose: a hill, a trench,
 * a ledge, a hollow and a copy of the real hillside.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPathfindingTests {
    private static final int GROUND = 5; // top layer of the arena floor

    /**
     * Session b65ec97a, 19:20:16–19:21:34: the real hillside, copied from the world with Oleg's later edits taken back. He stood
     * in the pit of the iron he had just mined, stone all around; for the furnace he took a few, then walked 12 blocks down the
     * slope to (-10, 93, 22), twice started a self-rescue and came back. The far stones on the slope stay where they are.
     */
    @GameTest(template = "b65_hillside", timeoutTicks = 2400)
    public static void furnaceStoneComesFromThePitNotTheFarSlope(GameTestHelper h) {
        // Template = world + (26, -89, 1).
        CompanionEntity companion = spawn(h, 18, 19, 11, 18, 20, 7);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        Item[] bag = {Items.RAW_IRON, Items.ACACIA_PLANKS, Items.ACACIA_LOG, Items.WOODEN_AXE, Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.STONE_AXE, Items.DIRT};
        int[] counts = {4, 3, 2, 1, 1, 1, 1, 12};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, new ItemStack(bag[slot], counts[slot]));
        BlockPos farA = new BlockPos(16, 4, 23), farB = new BlockPos(17, 4, 24);
        Watch watch = watch(h, companion);
        h.assertTrue(companion.startWork("smelt_items", "minecraft:raw_iron", 0).success(), "Smelting order not accepted");
        h.succeedWhen(() -> {
            watch.assertCalm(h, 8);
            h.assertTrue(h.getBlockState(farA).is(Blocks.STONE) && h.getBlockState(farB).is(Blocks.STONE), "Went down the slope for the far stone");
            h.assertTrue(blocks(h, Blocks.FURNACE) >= 1, "No furnace yet: " + companion.getLastTaskResult());
        });
    }

    /**
     * A 2x3 stone core in the face of a soil hill, three blocks in front of him; a bare outcrop eleven blocks behind him. He mines
     * the core like a player's 1x2 (upper, then lower) and steps in, instead of taking only the upper row and leaving for the outcrop.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void stoneInTheHillFaceIsMinedOneByTwo(GameTestHelper h) {
        ground(h);
        for (int x = 19; x <= 26; x++) for (int z = 10; z <= 22; z++) for (int y = GROUND + 1; y <= GROUND + 4; y++)
            h.setBlock(new BlockPos(x, y, z), y == GROUND + 4 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        fill(h, new BlockPos(19, GROUND + 1, 15), new BlockPos(20, GROUND + 2, 17), Blocks.STONE);
        BlockPos farA = new BlockPos(3, GROUND + 1, 12), farB = new BlockPos(5, GROUND + 2, 20);
        fill(h, farA, farB, Blocks.STONE);
        int far = count(h, farA, farB, Blocks.STONE);
        CompanionEntity companion = spawn(h, 16, GROUND + 1, 16, 13, GROUND + 1, 13);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        Watch watch = watch(h, companion);
        companion.startWork("gather_stone", "", 8);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 8, companion.getLastTaskResult());
            h.assertTrue(count(h, farA, farB, Blocks.STONE) == far, "Took the far outcrop while the hill face still had stone");
            watch.assertCalm(h, 7);
        });
    }

    /**
     * A deep trench: stone across it is nearer by the straight line, stone on his side is nearer on foot. He takes his side.
     * The old choice went around the whole trench for the «nearest» stone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void stoneOnHisSideOfTheTrenchWinsOverTheStraightLine(GameTestHelper h) {
        trench(h);
        BlockPos acrossA = new BlockPos(21, GROUND + 1, 15), acrossB = new BlockPos(22, GROUND + 2, 16);
        fill(h, acrossA, acrossB, Blocks.STONE);
        fill(h, new BlockPos(6, GROUND + 1, 21), new BlockPos(7, GROUND + 2, 22), Blocks.STONE);
        CompanionEntity companion = spawn(h, 14, GROUND + 1, 16, 12, GROUND + 1, 14);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        Watch watch = watch(h, companion);
        companion.startWork("gather_stone", "", 4);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 4, companion.getLastTaskResult());
            h.assertTrue(count(h, acrossA, acrossB, Blocks.STONE) == 8, "Walked around the trench for the stone across it");
            h.assertTrue(watch.maxX < 17, "Crossed the trench: x=" + watch.maxX);
            watch.assertCalm(h, 12);
        });
    }

    /** The same trench with iron ore: the ore on his side first, by the walk. */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void oreOnHisSideOfTheTrenchWinsOverTheStraightLine(GameTestHelper h) {
        trench(h);
        BlockPos acrossA = new BlockPos(21, GROUND + 1, 16), acrossB = new BlockPos(21, GROUND + 1, 17);
        h.setBlock(acrossA, Blocks.IRON_ORE); h.setBlock(acrossB, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(7, GROUND + 1, 21), Blocks.IRON_ORE); h.setBlock(new BlockPos(7, GROUND + 1, 22), Blocks.IRON_ORE);
        CompanionEntity companion = spawn(h, 14, GROUND + 1, 16, 12, GROUND + 1, 14);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        Watch watch = watch(h, companion);
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 2).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.RAW_IRON) >= 2, companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(acrossA).is(Blocks.IRON_ORE) && h.getBlockState(acrossB).is(Blocks.IRON_ORE), "Walked around the trench for the ore across it");
            h.assertTrue(watch.maxX < 17, "Crossed the trench: x=" + watch.maxX);
            watch.assertCalm(h, 12);
        });
    }

    /** The same trench with two bare trunks: the tree on his side first, by the walk. */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void treeOnHisSideOfTheTrenchWinsOverTheStraightLine(GameTestHelper h) {
        trench(h);
        for (int y = GROUND + 1; y <= GROUND + 4; y++) { h.setBlock(new BlockPos(21, y, 16), Blocks.OAK_LOG); h.setBlock(new BlockPos(7, y, 21), Blocks.OAK_LOG); }
        CompanionEntity companion = spawn(h, 14, GROUND + 1, 16, 12, GROUND + 1, 14);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        Watch watch = watch(h, companion);
        companion.startGatheringLogs(4);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) >= 4, companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(new BlockPos(21, GROUND + 1, 16)).is(Blocks.OAK_LOG), "Walked around the trench for the tree across it");
            h.assertTrue(watch.maxX < 17, "Crossed the trench: x=" + watch.maxX);
        });
    }

    /**
     * Session efc17045, 17:01–17:03: the stone pickaxe was asked for on high ground; the wood and the stone were below it. After
     * the wood trip the stone was mined 13 blocks under the place of the order, and «no way back to where mining started»
     * (that place was where the order came, not where mining started) ended the work without a single stone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stonePickaxeOrderedOnALedgeIsMadeBelowIt(GameTestHelper h) {
        ground(h);
        // A three-block ledge: a way down (a three-block drop), no way back up.
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 30; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++)
            h.setBlock(new BlockPos(x, y, z), y == GROUND + 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        for (int y = GROUND + 1; y <= GROUND + 5; y++) { h.setBlock(new BlockPos(19, y, 12), Blocks.OAK_LOG); h.setBlock(new BlockPos(19, y, 20), Blocks.OAK_LOG); }
        fill(h, new BlockPos(23, GROUND + 1, 14), new BlockPos(26, GROUND + 2, 18), Blocks.STONE);
        CompanionEntity companion = spawn(h, 9, GROUND + 4, 16, 6, GROUND + 4, 14);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(player, "craft_item", "minecraft:stone_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_PICKAXE) >= 1, companion.getLastTaskResult());
        });
    }

    /**
     * A hollow two blocks deep with a stone floor, three steps in front of him; a stone outcrop on the hill behind him. A player
     * steps down and mines the floor of the hollow, without a pit and without the hill.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void stoneFloorOfTheHollowInFrontIsTaken(GameTestHelper h) {
        ground(h);
        for (int x = 18; x <= 23; x++) for (int z = 12; z <= 20; z++) {
            h.setBlock(new BlockPos(x, GROUND - 2, z), Blocks.STONE);
            // The first row is a soil step down, like the edge of a real hollow.
            h.setBlock(new BlockPos(x, GROUND - 1, z), x == 18 ? Blocks.DIRT : Blocks.AIR); h.setBlock(new BlockPos(x, GROUND, z), Blocks.AIR);
        }
        for (int x = 2; x <= 8; x++) for (int z = 10; z <= 22; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++)
            h.setBlock(new BlockPos(x, y, z), y == GROUND + 3 && x <= 5 ? Blocks.STONE : y == GROUND + 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        int hill = count(h, new BlockPos(2, GROUND + 3, 10), new BlockPos(5, GROUND + 3, 22), Blocks.STONE);
        CompanionEntity companion = spawn(h, 15, GROUND + 1, 16, 13, GROUND + 1, 14);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        Watch watch = watch(h, companion);
        companion.startWork("gather_stone", "", 6);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 6, companion.getLastTaskResult());
            h.assertTrue(count(h, new BlockPos(2, GROUND + 3, 10), new BlockPos(5, GROUND + 3, 22), Blocks.STONE) == hill, "Went up the hill behind him");
            watch.assertCalm(h, 10);
        });
    }

    /**
     * Session b65ec97a, 19:20:16–19:20:23: he walked to his bench on the step above him, found no furnace and began the long
     * search for stone (radius 96). The bench, a block above his feet, still stood as his destination, so standing still while
     * searching looked like a trap: «Выбрался, продолжаю задачу» without any trap.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void searchingForFurnaceStoneUnderHisBenchIsNoTrap(GameTestHelper h) {
        ground(h);
        for (int x = 8; x <= 14; x++) for (int z = 10; z <= 22; z++) h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.GRASS_BLOCK);
        h.setBlock(new BlockPos(14, GROUND + 2, 16), Blocks.CRAFTING_TABLE);
        fill(h, new BlockPos(22, GROUND + 1, 20), new BlockPos(24, GROUND + 3, 22), Blocks.STONE);
        CompanionEntity companion = spawn(h, 18, GROUND + 1, 16, 19, GROUND + 1, 13);
        companion.setSearchRadius(96);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 1)); companion.setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
        Watch watch = watch(h, companion);
        h.assertTrue(companion.startWork("smelt_items", "minecraft:raw_iron", 0).success(), "Smelting order not accepted");
        h.succeedWhen(() -> {
            watch.assertCalm(h, 12);
            h.assertTrue(blocks(h, Blocks.FURNACE) >= 1, "No furnace yet: " + companion.getLastTaskResult());
        });
    }

    /** Where he has been, and whether a self-rescue ever started. */
    private static final class Watch {
        final BlockPos start; double farthest; int maxX = Integer.MIN_VALUE; boolean rescued;
        final java.util.List<String> said = new java.util.ArrayList<>();
        Watch(BlockPos start) { this.start = start; }
        void assertCalm(GameTestHelper h, double radius) {
            h.assertTrue(!rescued, "Started a self-rescue where a player would just walk up: " + String.join(" | ", said.subList(0, Math.min(said.size(), 30))));
            h.assertTrue(farthest <= radius, "Went " + Math.round(farthest) + " blocks away from the start (allowed " + radius + ")");
        }
    }

    private static Watch watch(GameTestHelper h, CompanionEntity companion) {
        Watch watch = new Watch(companion.blockPosition());
        h.onEachTick(() -> {
            BlockPos here = companion.blockPosition();
            watch.farthest = Math.max(watch.farthest, Math.sqrt(Math.pow(here.getX() - watch.start.getX(), 2) + Math.pow(here.getZ() - watch.start.getZ(), 2)));
            watch.maxX = Math.max(watch.maxX, h.relativePos(here).getX());
            watch.rescued |= companion.getActivityForAi().startsWith("пытается выбраться");
            String line = here.subtract(h.absolutePos(BlockPos.ZERO)).toShortString() + " " + companion.getLastTaskResult();
            if (watch.said.isEmpty() || !watch.said.getLast().endsWith(companion.getLastTaskResult())) watch.said.add(h.getTick() + "@" + line);
        });
        return watch;
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
    }

    /** Five blocks deep, three wide, with the only crossing at the far end (z 25..30). */
    private static void trench(GameTestHelper h) {
        ground(h);
        for (int x = 17; x <= 19; x++) for (int z = 1; z <= 24; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static void fill(GameTestHelper h, BlockPos from, BlockPos to, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) h.setBlock(pos, block);
    }

    private static int count(GameTestHelper h, BlockPos from, BlockPos to, Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) if (h.getBlockState(pos).is(block)) count++;
        return count;
    }

    private static int blocks(GameTestHelper h, Block block) { return count(h, new BlockPos(0, 0, 0), new BlockPos(31, 23, 31), block); }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z, int playerX, int playerY, int playerZ) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(playerX, playerY, playerZ));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
