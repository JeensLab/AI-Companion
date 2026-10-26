package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionWork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Work that stands still is stopped with its real reason instead of waiting for a faceless timeout. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWatchdogTests {
    private static final int SURFACE = 6; // feet level above the top layer

    /**
     * Held under the roof of a small glass box (levitation), Oleg never stands on the ground, so stone work cannot
     * start: it used to wait the whole ten-minute budget silently. Now it ends after a minute and a half as blocked,
     * says what it was doing, and nothing is lost.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void workWithoutProgressStopsWithItsReason(GameTestHelper h) {
        arena(h);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = SURFACE; y <= SURFACE + 2; y++)
            if (dx != 0 || dz != 0 || y == SURFACE + 2) h.setBlock(new BlockPos(16 + dx, y, 16 + dz), Blocks.GLASS);
        CompanionEntity companion = spawn(h);
        companion.addEffect(new MobEffectInstance(MobEffects.LEVITATION, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        h.assertTrue(companion.startWork("gather_stone", "", 4).success(), "Stone order not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && result.status() == CompanionActionResult.Status.BLOCKED, "Not a blocked result: " + result);
            h.assertTrue(result.message().contains("Полторы минуты") && result.message().contains("«"), "No real reason: " + result.message());
            h.assertTrue(h.getTick() >= CompanionWork.STALL_TICKS, "Stopped too early, tick " + h.getTick());
            h.assertTrue(count(companion, Items.COBBLESTONE) == 5 && count(companion, Items.STONE_PICKAXE) == 1, "Items lost: " + companion.getStoredItemsSummary());
        });
    }

    /**
     * In a boat vanilla switches off all walking goals: the stone order used to hang with no server timeout at all.
     * Like a player, Oleg steps out and does the work.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void orderInABoatStepsOutAndWorks(GameTestHelper h) {
        arena(h);
        h.setBlock(new BlockPos(19, SURFACE - 1, 16), Blocks.STONE);
        h.setBlock(new BlockPos(19, SURFACE - 1, 17), Blocks.STONE);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        var boat = h.spawn(EntityType.BOAT, 16, SURFACE, 16);
        h.assertTrue(companion.startRiding(boat, true), "Could not seat Oleg in the boat");
        h.assertTrue(companion.startWork("gather_stone", "", 2).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.isPassenger(), "Still in the boat");
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 2, companion.getLastTaskResult());
        });
    }

    /** Long smelting is waiting at the furnace, not standing still: ten raw iron take a hundred seconds and are all smelted. */
    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void longSmeltingIsNotMistakenForAStall(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        bind(h, companion);
        h.setBlock(new BlockPos(20, 1, 16), Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 10)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        // One furnace only: the batch must cook longer than the watchdog limit in a single place.
        h.assertTrue(companion.startWork("smelt_items", "minecraft:raw_iron", 10).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 10, "Wrong product: " + companion.getStoredItemsSummary());
        });
    }

    /**
     * Stone exists only far below, behind sealed caves he can see into from nowhere, and in the ground under the grass.
     * The caves are given up quickly and the own stairs bring the stone: the order succeeds well within the step limit.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void unreachableCavesGiveWayToStairs(GameTestHelper h) {
        arena(h);
        for (int x = 2; x < 30; x += 3) for (int z = 2; z < 30; z += 3) h.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("gather_stone", "", 4).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 4, companion.getLastTaskResult());
        });
    }

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y <= 2 ? Blocks.STONE : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
    }
    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, SURFACE, 16);
        bind(h, companion);
        return companion;
    }
    private static void bind(GameTestHelper h, CompanionEntity companion) {
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
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
