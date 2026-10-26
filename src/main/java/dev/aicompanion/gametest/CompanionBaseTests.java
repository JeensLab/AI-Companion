package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The base and the field: one bench at home, a travelling bench carried along, no duplicates next to known ones. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBaseTests {
    /** Far from the base bench Oleg sets one down from his wood, crafts, then picks it up and carries it. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void benchSetDownInTheFieldIsCarriedAlong(GameTestHelper h) {
        CompanionEntity companion = setup(h, 18);
        BlockPos base = new BlockPos(2, 1, 2); h.setBlock(base, Blocks.CRAFTING_TABLE); // known, 22 blocks away
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_axe", 1).success(), "Craft order not accepted");
        boolean[] stood = {false};
        h.onEachTick(() -> stood[0] |= blocks(h, Blocks.CRAFTING_TABLE) == 2);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_AXE) == 1, companion.getLastTaskResult());
            h.assertTrue(stood[0], "No bench was set down in the field");
            h.assertTrue(blocks(h, Blocks.CRAFTING_TABLE) == 1 && h.getBlockState(base).is(Blocks.CRAFTING_TABLE) && count(companion, Items.CRAFTING_TABLE) == 1,
                    "Field bench not picked up and carried: " + companion.getStoredItemsSummary());
        });
    }

    /** A carried bench is set down again next time: no wood is spent on another one. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void carriedBenchIsUsedInsteadOfNewWood(GameTestHelper h) {
        CompanionEntity companion = setup(h, 18);
        h.setBlock(new BlockPos(2, 1, 2), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.STICK, 2));
        companion.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_PICKAXE) == 1, companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.CRAFTING_TABLE) == 1 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Carried bench lost or duplicated: " + companion.getStoredItemsSummary());
        });
    }

    /** Game log 22.10: a bench two blocks away was "unreachable" from a pit and a new one was set down beside it. */
    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void benchAFewStepsAwayIsNeverDuplicated(GameTestHelper h) {
        CompanionEntity companion = setup(h, 16);
        BlockPos walled = new BlockPos(19, 1, 16);
        for (int x = 18; x <= 20; x++) for (int y = 1; y <= 3; y++) for (int z = 15; z <= 17; z++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        h.setBlock(walled, Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.OAK_LOG, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Duplicated the bench a few steps away: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.COBBLESTONE) == 3 && count(companion, Items.OAK_LOG) == 4, "Materials spent on a failed order");
        });
    }

    /** Game log 22.10: "put the stone in the chest" filled a new chest instead of the one Oleg had been using. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void ownChestIsFilledBeforeAnotherNearbyOne(GameTestHelper h) {
        CompanionEntity companion = setup(h, 16);
        BlockPos own = new BlockPos(23, 1, 16); h.setBlock(own, Blocks.CHEST);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        var owner = owner(h, companion);
        h.assertTrue(CompanionEvents.executeAction(owner, "store_items", "stone", 1).success(), "First store not accepted");
        boolean[] second = {false};
        BlockPos[] nearer = {null};
        h.onEachTick(() -> {
            if (second[0] || companion.isWorking() || ((Container)h.getBlockEntity(own)).isEmpty()) return;
            // Oleg walks off; another chest right beside him is now much nearer than his own (13 blocks).
            var away = h.absoluteVec(new net.minecraft.world.phys.Vec3(10.5, 1, 16.5));
            companion.teleportTo(away.x, away.y, away.z);
            nearer[0] = new BlockPos(8, 1, 16);
            h.setBlock(nearer[0], Blocks.CHEST);
            companion.setItem(0, new ItemStack(Items.COBBLESTONE, 7));
            second[0] = CompanionEvents.executeAction(owner, "store_items", "stone", 1).success();
        });
        h.succeedWhen(() -> {
            h.assertTrue(second[0], "Second store not started");
            done(h, companion);
            h.assertTrue(count((Container)h.getBlockEntity(own), Items.COBBLESTONE) == 12 && ((Container)h.getBlockEntity(nearer[0])).isEmpty(),
                    "Stone went into another chest: " + companion.getLastTaskResult());
        });
    }

    private static CompanionEntity setup(GameTestHelper h, int at) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), at, 1, at);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int blocks(GameTestHelper h, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static int count(Container bag, Item item) {
        int count = 0;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount();
        if (bag instanceof CompanionEntity companion) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
