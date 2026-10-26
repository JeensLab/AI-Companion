package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
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

/** Requests repeated while the model was slow must not turn into duplicate work. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionRequestTests {
    /** Game log 22.10: "сделай себе инструменты" said three times became three stone pickaxes. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void repeatedToolRequestDoesNotMakeASecondTool(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 9)); companion.setItem(1, new ItemStack(Items.STICK, 6));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "craft_item", "minecraft:stone_pickaxe", 1).success(), "First craft not accepted");
        CompanionActionResult[] repeat = {null};
        boolean[] storedAway = {false};
        h.onEachTick(() -> {
            if (companion.isWorking() || count(companion, Items.STONE_PICKAXE) == 0) return;
            if (repeat[0] == null) {
                repeat[0] = CompanionEvents.executeAction(owner, "craft_item", "minecraft:stone_pickaxe", 1);
                return;
            }
            if (!storedAway[0]) {
                // Given away (here: removed from the bag), a new request is a real one again.
                companion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(Items.STONE_PICKAXE)) companion.setItem(slot, ItemStack.EMPTY);
                storedAway[0] = true;
                CompanionEvents.executeAction(owner, "craft_item", "minecraft:stone_pickaxe", 1);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(repeat[0] != null && repeat[0].success() && repeat[0].message().contains("только что сделал"), "Repeat not recognised: " + repeat[0]);
            h.assertTrue(storedAway[0] && !companion.isWorking(), "Wait for the genuine second order");
            h.assertTrue(count(companion, Items.STONE_PICKAXE) == 1 && count(companion, Items.COBBLESTONE) == 3,
                    "Expected one duplicate skipped and one genuine craft: " + companion.getStoredItemsSummary());
        });
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
