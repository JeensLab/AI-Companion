package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWorkflowTests {
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void harvestedLogExistsOnGroundBeforeWalkingPickup(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, 1); BlockPos log = new BlockPos(19, 1, 16); helper.setBlock(log, Blocks.OAK_LOG);
        boolean[] dropped = {false}, animated = {false}; double x = companion.getX();
        helper.onEachTick(() -> {
            animated[0] |= companion.isMiningAnimation();
            if (!dropped[0] && helper.getBlockState(log).isAir()) {
                dropped[0] = true;
                helper.assertTrue(count(companion, Items.OAK_LOG) == 0 && !items(helper, companion).isEmpty(), "Block was transferred directly to inventory");
            }
        });
        companion.startGatheringLogs(1);
        helper.succeedWhen(() -> {
            done(helper, companion); helper.assertTrue(dropped[0] && animated[0] && count(companion, Items.OAK_LOG) == 1, companion.getLastTaskResult());
            helper.assertTrue(companion.getX() > x + .8 && !companion.isMiningAnimation(), "No walking pickup or mining animation stuck");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2200)
    public static void woodJobBootstrapsRealAxeAndKeepsRequestedLogs(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, 1);
        forest(helper, 1);
        companion.startGatheringLogs(10);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) >= 10, diagnostic(companion));
            helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_AXE) && companion.getMainHandItem().getDamageValue() > 0, "No real crafted axe was used");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2600)
    public static void woodJobFindsSoilForItsOwnClimbingSupports(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) { helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK); helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT); }
        CompanionEntity companion = spawn(helper, 14, 2, 16); companion.setItem(0, new ItemStack(Items.WOODEN_AXE));
        for (int y = 2; y <= 10; y++) helper.setBlock(new BlockPos(16, y, 16), Blocks.OAK_LOG);
        for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(23, y, 18), Blocks.OAK_LOG);
        boolean[] dug = {false}, climbed = {false};
        helper.onEachTick(() -> { dug[0] |= count(companion, Items.DIRT) > 0; climbed[0] |= companion.getTemporarySupportCount() > 0; });
        companion.startGatheringLogs(9);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) >= 9 && dug[0] && climbed[0], diagnostic(companion));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1100)
    public static void escapesDeepPitByJumpingOnInventoryBlocks(GameTestHelper helper) {
        CompanionEntity companion = pit(helper, false); companion.setItem(0, new ItemStack(Items.DIRT, 5));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        double[] previous = {companion.getY()}; boolean[] placed = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(companion.getY() - previous[0] < 1, "Recovery teleported upward"); previous[0] = companion.getY();
            placed[0] |= count(companion, Items.DIRT) < 5;
        });
        helper.succeedWhen(() -> helper.assertTrue(placed[0] && companion.getY() >= helper.absolutePos(new BlockPos(16, 5, 16)).getY()
                && companion.distanceToSqr(helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId())) < 64
                && companion.getMainHandItem().is(Items.WOODEN_PICKAXE) && companion.getMainHandItem().getDamageValue() == 0,
                diagnostic(companion) + " Hand=" + companion.getMainHandItem() + " d2=" + companion.distanceToSqr(helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()))));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1300)
    public static void escapesSoilPitWithoutFreeStartingBlocks(GameTestHelper helper) {
        CompanionEntity companion = pit(helper, true); boolean[] animated = {false}, drop = {false};
        helper.onEachTick(() -> { animated[0] |= companion.isMiningAnimation(); drop[0] |= !items(helper, companion).isEmpty(); });
        helper.succeedWhen(() -> helper.assertTrue(animated[0] && drop[0] && companion.getY() >= helper.absolutePos(new BlockPos(16, 5, 16)).getY(), diagnostic(companion)));
    }

    /** The escape itself walks him out: the steps lead within reach of the player, where only a random stroll would move him on. */
    @GameTest(template = "tall_tree", timeoutTicks = 1300)
    public static void escapesSoilPitWithoutRandomStroll(GameTestHelper helper) {
        CompanionEntity companion = pit(helper, true);
        companion.removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
        helper.succeedWhen(() -> helper.assertTrue(companion.getY() >= helper.absolutePos(new BlockPos(16, 5, 16)).getY(), diagnostic(companion)));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void escapesTwoBlockPitEvenInsideNormalFollowDistance(GameTestHelper helper) {
        CompanionEntity companion = pit(helper, false, 2); companion.setItem(0, new ItemStack(Items.DIRT, 2));
        helper.succeedWhen(() -> helper.assertTrue(companion.getY() >= helper.absolutePos(new BlockPos(16, 3, 16)).getY()
                && count(companion, Items.DIRT) < 2, diagnostic(companion)));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1100)
    public static void stopCancelsEmergencyPillarAndMining(GameTestHelper helper) {
        CompanionEntity companion = pit(helper, false); companion.setItem(0, new ItemStack(Items.DIRT, 5));
        long[] stop = {-1}; int[] remaining = {-1};
        helper.onEachTick(() -> {
            if (stop[0] < 0 && count(companion, Items.DIRT) < 5) {
                CompanionEvents.executeAction(helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()), "wait", "", 1);
                stop[0] = helper.getTick(); remaining[0] = count(companion, Items.DIRT);
            }
            if (stop[0] >= 0) helper.assertTrue(count(companion, Items.DIRT) == remaining[0] && !companion.isMiningAnimation(), "Recovery continued after stay");
        });
        helper.succeedWhen(() -> helper.assertTrue(stop[0] >= 0 && helper.getTick() - stop[0] > 120, "Wait for cancellation"));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void dirtRequestMakesShovelAfterGatheringItsWood(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, 1); forest(helper, 1);
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        helper.assertTrue(CompanionEvents.executeAction(player, "gather_dirt", "", 3).success(), "Dirt chain rejected");
        helper.succeedWhen(() -> {
            done(helper, companion); helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.DIRT) >= 3, diagnostic(companion));
            helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_SHOVEL) && companion.getMainHandItem().getDamageValue() >= 3, "Shovel was not prepared or used");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void stolenDropDoesNotCountAsCompanionStoneStock(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, 1); for (int z = 15; z <= 18; z++) helper.setBlock(new BlockPos(20, 1, z), Blocks.STONE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE)); boolean[] stolen = {false};
        helper.onEachTick(() -> {
            if (!stolen[0]) for (ItemEntity item : items(helper, companion)) if (item.getItem().is(Items.COBBLESTONE)) {
                item.discard(); stolen[0] = true; break;
            }
        });
        companion.startWork("gather_stone", "", 2);
        helper.succeedWhen(() -> {
            done(helper, companion); helper.assertTrue(stolen[0] && companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) == 2
                    && companion.getMainHandItem().getDamageValue() == 3, diagnostic(companion));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void inventoryPausePreservesGroundDropAndResumesPickup(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, 1); BlockPos log = new BlockPos(19, 1, 16); helper.setBlock(log, Blocks.OAK_LOG);
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        long[] opened = {-1}; boolean[] closed = {false};
        companion.startGatheringLogs(1);
        helper.onEachTick(() -> {
            if (opened[0] < 0 && helper.getBlockState(log).isAir()) { companion.startOpen(player); opened[0] = helper.getTick(); }
            if (opened[0] >= 0 && !closed[0]) {
                helper.assertTrue(count(companion, Items.OAK_LOG) == 0 && !items(helper, companion).isEmpty(), "Pickup ignored inventory pause");
                if (helper.getTick() - opened[0] >= 60) { closed[0] = true; companion.stopOpen(player); }
            }
        });
        helper.succeedWhen(() -> { helper.assertTrue(closed[0], "Wait for inventory close"); done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 1, diagnostic(companion)); });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 350)
    public static void stayCancelsPickupWithoutDeletingOrTeleportingTheItem(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, 1); BlockPos log = new BlockPos(19, 1, 16); helper.setBlock(log, Blocks.OAK_LOG);
        long[] stopped = {-1}; companion.startGatheringLogs(1);
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && helper.getBlockState(log).isAir()) { companion.setFollowing(false); stopped[0] = helper.getTick(); }
            if (stopped[0] >= 0) helper.assertTrue(count(companion, Items.OAK_LOG) == 0 && !items(helper, companion).isEmpty()
                    && !companion.isMiningAnimation(), "Drop was picked up, removed or work continued after stay");
        });
        helper.succeedWhen(() -> { helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] >= 100, "Wait after stay"); done(helper, companion); });
    }

    private static CompanionEntity pit(GameTestHelper helper, boolean soil) {
        return pit(helper, soil, 4);
    }
    private static CompanionEntity pit(GameTestHelper helper, boolean soil, int depth) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= depth; y++) helper.setBlock(new BlockPos(x, y, z), soil && y > 0 ? Blocks.DIRT : Blocks.BEDROCK);
        for (int y = 1; y <= depth; y++) helper.setBlock(new BlockPos(16, y, 16), Blocks.AIR);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = helper.makeMockServerPlayerInLevel(); BlockPos at = helper.absolutePos(new BlockPos(21, depth + 1, 16)); player.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); return companion;
    }
    private static CompanionEntity setup(GameTestHelper helper, int y) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        return spawn(helper, 16, y, 16);
    }
    private static CompanionEntity spawn(GameTestHelper helper, int x, int y, int z) {
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), x, y, z); var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16); return companion;
    }
    private static void forest(GameTestHelper helper, int base) { for (int x : new int[]{20, 24, 28}) for (int z : new int[]{9, 14, 19}) for (int y = base; y < base + 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG); }
    private static java.util.List<ItemEntity> items(GameTestHelper helper, CompanionEntity companion) { return helper.getLevel().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(16)); }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0; for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount(); return count;
    }
    private static String diagnostic(CompanionEntity companion) { return companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary() + " At=" + companion.position(); }
    private static void done(GameTestHelper helper, CompanionEntity companion) { helper.assertTrue(!companion.isGatheringTask(), diagnostic(companion)); }
}
