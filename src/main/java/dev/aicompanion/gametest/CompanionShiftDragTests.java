package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A shift-drag is a run of quick moves, one per slot the pointer passes: the screen sends each of them, so what the
 * server has to do for a whole drag is checked here.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShiftDragTests {
    /** Every stack of a run of quick moves leaves the bag, and none of them is lost. */
    @GameTest(template = "empty")
    public static void aRunOfQuickMovesEmptiesThoseSlots(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        // The bag's own indices start at zero; the menu's first bag slot is BAG_START.
        companion.setItem(0, new ItemStack(Items.DIRT, 12));
        companion.setItem(1, new ItemStack(Items.BREAD, 5));
        companion.setItem(2, new ItemStack(Items.COBBLESTONE, 16));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(menu.getSlot(CompanionMenu.BAG_START).getItem().is(Items.DIRT),
                    "The menu does not see the dirt: " + menu.getSlot(CompanionMenu.BAG_START).getItem());
            for (int slot = CompanionMenu.BAG_START; slot < CompanionMenu.BAG_START + 3; slot++) {
                ItemStack moved = menu.quickMoveStack(owner, slot);
                h.assertTrue(!moved.isEmpty(), "The quick move of slot " + slot + " moved nothing");
                h.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Slot " + slot + " still holds " + menu.getSlot(slot).getItem());
            }
            int dirt = count(owner, Items.DIRT), bread = count(owner, Items.BREAD), stone = count(owner, Items.COBBLESTONE);
            h.assertTrue(dirt == 12, "The dirt went missing: " + dirt);
            h.assertTrue(bread == 5, "The bread went missing: " + bread);
            h.assertTrue(stone == 16, "The cobblestone went missing: " + stone);
        }).thenSucceed();
    }

    /** The same run the other way: a drag over the player's slots puts those stacks into the bag. */
    @GameTest(template = "empty")
    public static void aRunFromThePlayersSlotsFillsTheBag(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        // The menu shows the inventory's rows from index 9 on; its hotbar is not in it.
        owner.getInventory().setItem(9, new ItemStack(Items.DIRT, 7));
        owner.getInventory().setItem(10, new ItemStack(Items.BREAD, 3));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        h.startSequence().thenExecute(() -> {
            for (int slot = CompanionMenu.PLAYER_START; slot < CompanionMenu.PLAYER_START + 2; slot++) {
                ItemStack moved = menu.quickMoveStack(owner, slot);
                h.assertTrue(!moved.isEmpty(), "The quick move of slot " + slot + " moved nothing");
            }
            h.assertTrue(countInBag(companion, Items.DIRT) == 7, "The dirt did not reach the bag: " + countInBag(companion, Items.DIRT));
            h.assertTrue(countInBag(companion, Items.BREAD) == 3, "The bread did not reach the bag: " + countInBag(companion, Items.BREAD));
            h.assertTrue(count(owner, Items.DIRT) == 0, "The dirt stayed with the player: " + count(owner, Items.DIRT));
        }).thenSucceed();
    }

    /** A slot the drag already emptied is not moved a second time when the pointer stays on it. */
    @GameTest(template = "empty")
    public static void anEmptiedSlotIsNoSecondMove(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        companion.setItem(0, new ItemStack(Items.DIRT, 4));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!menu.quickMoveStack(owner, CompanionMenu.BAG_START).isEmpty(), "The first move did nothing");
            h.assertTrue(menu.quickMoveStack(owner, CompanionMenu.BAG_START).isEmpty(), "An emptied slot moved again");
            h.assertTrue(count(owner, Items.DIRT) == 4, "The dirt was moved twice: " + count(owner, Items.DIRT));
        }).thenSucceed();
    }

    private static int count(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static int countInBag(CompanionEntity companion, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(owner != null, "The companion has no owner in the level");
        return owner;
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}
