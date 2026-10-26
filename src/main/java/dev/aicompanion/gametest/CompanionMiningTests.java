package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMiningTests {
    @GameTest(template = "tall_tree", timeoutTicks = 250)
    public static void emptyHandWoodNeedsContinuousSurvivalSwings(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        BlockPos log = new BlockPos(18, 1, 16); helper.setBlock(log, Blocks.OAK_LOG);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_PLANKS, 4));
        companion.startGatheringLogs(1);
        boolean[] swung = {false};
        helper.onEachTick(() -> {
            swung[0] |= companion.swinging;
            if (helper.getTick() <= 55) helper.assertTrue(helper.getBlockState(log).is(Blocks.OAK_LOG) && count(companion, Items.OAK_LOG) == 0, "Wood broke instantly or too early");
        });
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(helper.getTick() >= 60 && helper.getTick() < 180 && swung[0], "No real hand swings/time");
            helper.assertTrue(count(companion, Items.OAK_LOG) == 1 && companion.getMainHandItem().isEmpty(), "No actual empty-hand harvest");
            helper.assertTrue(count(companion, Items.OAK_PLANKS) == 4, "Held supplies lost when hand was emptied");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 250)
    public static void realAxeIsFasterAndConsumesDurability(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        helper.setBlock(new BlockPos(18, 1, 16), Blocks.OAK_LOG);
        companion.setItem(0, new ItemStack(Items.DIAMOND_AXE)); companion.startGatheringLogs(1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(helper.getTick() < 60 && companion.getMainHandItem().is(Items.DIAMOND_AXE)
                    && companion.getMainHandItem().getDamageValue() == 1 && count(companion, Items.OAK_LOG) == 1, "Axe did not affect time/durability");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void brokenAxeFallsBackToFullHandMining(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        helper.setBlock(new BlockPos(18, 1, 16), Blocks.OAK_LOG); helper.setBlock(new BlockPos(18, 2, 16), Blocks.OAK_LOG);
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE); axe.setDamageValue(axe.getMaxDamage() - 1); companion.setItem(0, axe);
        long[] first = {-1};
        // Timed from the first broken log, not its pickup: Oleg now finishes the next swing before picking up.
        helper.onEachTick(() -> { if (first[0] < 0 && (helper.getBlockState(new BlockPos(18, 1, 16)).isAir()
                || helper.getBlockState(new BlockPos(18, 2, 16)).isAir())) first[0] = helper.getTick(); });
        companion.startGatheringLogs(2);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(first[0] >= 0 && helper.getTick() - first[0] >= 60 && count(companion, Items.OAK_LOG) == 2
                    && count(companion, Items.DIAMOND_AXE) == 0 && companion.getMainHandItem().isEmpty(), "Broken axe kept fast mining or duplicated");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 350)
    public static void stopAndRestartDoNotKeepPartialWoodProgress(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); BlockPos log = new BlockPos(18, 1, 16);
        helper.setBlock(log, Blocks.OAK_LOG); companion.startGatheringLogs(1);
        helper.runAtTickTime(30, companion::stopGathering);
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(helper.getBlockState(log).is(Blocks.OAK_LOG), "Wood broke after stop");
            companion.startGatheringLogs(1);
        });
        helper.runAtTickTime(110, () -> helper.assertTrue(helper.getBlockState(log).is(Blocks.OAK_LOG), "Cancelled progress was reused"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 130, "Wait for a fresh full swing sequence"); done(helper, companion);
            helper.assertTrue(count(companion, Items.OAK_LOG) == 1 && companion.didLastTaskSucceed(), companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void logsBehindSolidWallsAreNotMinedThroughThem(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        for (int x = 20; x <= 22; x++) for (int y = 1; y <= 4; y++) for (int z = 15; z <= 17; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        BlockPos sealed = new BlockPos(21, 2, 16); helper.setBlock(sealed, Blocks.OAK_LOG);
        companion.startGatheringLogs(1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && helper.getBlockState(sealed).is(Blocks.OAK_LOG) && count(companion, Items.OAK_LOG) == 0, "Log mined through an enclosing wall");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 350)
    public static void fullBagDoesNotDestroyWoodAndLoseItsDrops(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        BlockPos log = new BlockPos(18, 1, 16); helper.setBlock(log, Blocks.OAK_LOG); companion.startGatheringLogs(1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && helper.getBlockState(log).is(Blocks.OAK_LOG), "Full bag destroyed a log");
            helper.assertTrue(count(companion, Items.DIAMOND) == 27 * 64, "Inventory overwritten while harvesting");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 1800)
    public static void findsAndWalksToStoneOverFiftyBlocksAway(GameTestHelper helper) {
        for (int x = 60; x < 124; x++) for (int z = 60; z < 69; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        // Isolate the walking corridor from stone in neighboring GameTest structures.
        for (int x = 60; x < 124; x++) for (int z = 60; z < 69; z++) {
            helper.setBlock(new BlockPos(x, 4, z), Blocks.BEDROCK);
            if (x == 60 || x == 123 || z == 60 || z == 68) for (int y = 1; y < 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64); owner(helper, companion);
        companion.setSearchRadius(64); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos stone = new BlockPos(118, 1, 64); helper.setBlock(stone, Blocks.STONE);
        // A closer exposed target floats too high to approach. It must not monopolize the job.
        helper.setBlock(new BlockPos(69, 12, 64), Blocks.STONE);
        companion.startWork("gather_stone", "", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && helper.getBlockState(stone).isAir() && count(companion, Items.COBBLESTONE) == 1, companion.getLastTaskResult());
            helper.assertTrue(companion.distanceToSqr(helper.absolutePos(new BlockPos(64, 1, 64)).getCenter()) > 40 * 40, "Stone appeared without walking to it");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void findsStoneHighOnReachableSteppedSlope(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        for (int x = 4; x <= 28; x++) for (int z = 12; z <= 20; z++) for (int y = 0; y <= (x - 4) / 2; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 16)); companion.moveTo(start.getX() + .5, start.getY(), start.getZ() + .5, 0, 0);
        helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()).teleportTo(companion.getX(), companion.getY(), companion.getZ() + 2);
        BlockPos stone = new BlockPos(28, 13, 16); helper.setBlock(stone, Blocks.STONE);
        companion.setSearchRadius(32); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE)); companion.startWork("gather_stone", "", 1);
        helper.succeedWhen(() -> { done(helper, companion); helper.assertTrue(companion.didLastTaskSucceed() && helper.getBlockState(stone).isAir(), companion.getLastTaskResult()); });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2200)
    public static void shallowStoneUnderGrassIsReachedByRealStairDigging(GameTestHelper helper) {
        CompanionEntity companion = covered(helper);
        int initialStone = stoneBlocks(helper);
        boolean[] descended = {false};
        helper.onEachTick(() -> descended[0] |= companion.getY() < helper.absolutePos(new BlockPos(16, 5, 16)).getY());
        companion.startWork("gather_stone", "", 3);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) == 3 && descended[0], companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.DIRT) > 0, "Covering soil was not mined into real drops");
            helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_PICKAXE)
                    && companion.getMainHandItem().getDamageValue() == initialStone - stoneBlocks(helper),
                    "Pickaxe wear must match broken stone; hand=" + companion.getMainHandItem() + " damage=" + companion.getMainHandItem().getDamageValue()
                            + " broken=" + (initialStone - stoneBlocks(helper)) + " at=" + companion.position());
            helper.assertTrue(companion.getY() >= helper.absolutePos(new BlockPos(16, 6, 16)).getY()
                    && companion.distanceToSqr(helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId())) < 6 * 6, "Cannot return out of the staircase");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void stopDuringSoilAccessCancelsAllFurtherExcavation(GameTestHelper helper) {
        CompanionEntity companion = covered(helper);
        int[] dirtAtStop = {-1}; long[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && companion.getLastTaskResult().startsWith("Убираю землю")) {
                companion.stopGathering(); dirtAtStop[0] = count(companion, Items.DIRT); stopped[0] = helper.getTick();
            }
            if (stopped[0] >= 0) helper.assertTrue(count(companion, Items.DIRT) == dirtAtStop[0] && count(companion, Items.COBBLESTONE) == 0, "Excavation continued after stop");
        });
        companion.startWork("gather_stone", "", 3);
        helper.succeedWhen(() -> { helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 50, "Wait for soil stop"); done(helper, companion); });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void openingBagPausesMiningAndResetsCracksProgress(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); BlockPos stone = new BlockPos(18, 1, 16); helper.setBlock(stone, Blocks.STONE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        companion.startWork("gather_stone", "", 1);
        helper.runAtTickTime(12, () -> companion.startOpen(player));
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(helper.getBlockState(stone).is(Blocks.STONE) && count(companion, Items.COBBLESTONE) == 0, "Mining continued with the bag open");
            companion.stopOpen(player);
        });
        helper.runAtTickTime(80, () -> helper.assertTrue(helper.getBlockState(stone).is(Blocks.STONE), "Paused progress completed instantly"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 92, "Wait for mining to resume"); done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) == 1 && companion.getMainHandItem().getDamageValue() == 1, companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void waterAdjacentStoneAndExistingChestsArePreserved(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); BlockPos stone = new BlockPos(20, 1, 16);
        helper.setBlock(stone, Blocks.STONE); helper.setBlock(stone.east(), Blocks.WATER);
        helper.setBlock(new BlockPos(21, 1, 21), Blocks.CHEST);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE)); companion.startWork("gather_stone", "", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && helper.getBlockState(stone).is(Blocks.STONE) && helper.getBlockState(new BlockPos(21, 1, 21)).is(Blocks.CHEST), "Unsafe block mined");
        });
    }

    @GameTest(template = "empty")
    public static void directStoneSearchRequestsKeepCountsAndNegations(GameTestHelper helper) {
        helper.assertTrue(dev.aicompanion.client.CompanionWorkCommands.parse("Олег, поищи камень поблизости", "Олег").type().equals("gather_stone"), "Search request missed");
        helper.assertTrue(dev.aicompanion.client.CompanionWorkCommands.parse("найди 10 камня", "Олег").count() == 10, "Search count lost");
        helper.assertTrue(dev.aicompanion.client.CompanionWorkCommands.parse("не ищи камень", "Олег") == null, "Negative search executed");
        helper.succeed();
    }

    private static CompanionEntity covered(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 5; y++) {
            helper.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK : y <= 2 ? Blocks.STONE : y == 5 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        }
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 6, 16); owner(helper, companion);
        companion.setSearchRadius(16); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE)); return companion;
    }
    private static int stoneBlocks(GameTestHelper helper) {
        int count = 0;
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) for (int y = 1; y <= 2; y++)
            if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) count++;
        return count;
    }
    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16); owner(helper, companion); companion.setSearchRadius(16); return companion;
    }
    private static void owner(GameTestHelper helper, CompanionEntity companion) {
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player);
    }
    private static void done(GameTestHelper helper, CompanionEntity companion) { helper.assertTrue(!companion.isGatheringTask(), companion.getLastTaskResult()); }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0; for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount(); return count;
    }
}
