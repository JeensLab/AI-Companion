package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Afloat in a pond, an order for a furnace or a chest on the bank goes on instead of waiting for firm ground. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAfloatTests {
    private static final int DEPTH = 4;
    private static final BlockPos BANK = new BlockPos(21, DEPTH + 1, 26);

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void afloatHeSmeltsInTheFurnaceOnTheBank(GameTestHelper h) {
        CompanionEntity companion = pond(h);
        h.setBlock(BANK, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 1));
        companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.runAfterDelay(40, () -> h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:raw_iron", 1).success(), "Smelt order refused"));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(count(companion, Items.IRON_INGOT) == 1, companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void afloatHeTakesFromTheChestOnTheBank(GameTestHelper h) {
        CompanionEntity companion = pond(h);
        h.setBlock(BANK, Blocks.CHEST);
        ((Container) h.getBlockEntity(BANK)).setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        h.runAfterDelay(40, () -> h.assertTrue(companion.startWork("take_items", "all", 64, "minecraft:chest").success(), "Take order refused"));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(count(companion, Items.COBBLESTONE) == 3, companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void afloatHeSetsDownTheChestAsked(GameTestHelper h) {
        CompanionEntity companion = pond(h);
        companion.setItem(0, new ItemStack(Items.CHEST, 1));
        h.runAfterDelay(40, () -> h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "place_block", "minecraft:chest", 1).success(), "Chest order refused"));
        h.succeedWhen(() -> {
            done(h, companion);
            boolean set = false;
            for (BlockPos pos : BlockPos.betweenClosed(0, DEPTH, 0, 31, DEPTH + 3, 31)) set |= h.getBlockState(pos).is(Blocks.CHEST);
            h.assertTrue(count(companion, Items.CHEST) == 0 && set, companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void afloatHeStillBuildsTheHouse(GameTestHelper h) {
        CompanionEntity companion = pond(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        h.runAfterDelay(40, () -> h.assertTrue(companion.startWork("build_house", "", 1).success(), "House order refused"));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.getLastTaskResult().startsWith("Дом готов"), companion.getLastTaskResult());
        });
    }

    /** Stone to {@link #DEPTH} with a 5×5 pond in it; he floats in the middle, the owner stands on the bank. */
    private static CompanionEntity pond(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 1; y <= DEPTH; y++)
            h.setBlock(new BlockPos(x, y, z), x >= 24 && x <= 28 && z >= 24 && z <= 28 ? Blocks.WATER : Blocks.STONE);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, DEPTH + 1, 16);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        BlockPos water = h.absolutePos(new BlockPos(26, DEPTH, 26)), bank = h.absolutePos(new BlockPos(26, DEPTH + 1, 21));
        companion.teleportTo(water.getX() + .5, water.getY(), water.getZ() + .5);
        player.teleportTo(bank.getX() + .5, bank.getY(), bank.getZ() + .5);
        return companion;
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(h.getTick() > 40 && !companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
        h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
