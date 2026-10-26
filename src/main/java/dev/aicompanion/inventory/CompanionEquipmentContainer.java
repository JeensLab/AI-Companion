package dev.aicompanion.inventory;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Views the mob's real equipment, so the menu, renderer, combat and save data share the same stacks. */
public final class CompanionEquipmentContainer implements Container {
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private final CompanionEntity companion;

    public CompanionEquipmentContainer(CompanionEntity companion) { this.companion = companion; }
    @Override public int getContainerSize() { return SLOTS.length; }
    @Override public boolean isEmpty() {
        for (EquipmentSlot slot : SLOTS) if (!companion.getItemBySlot(slot).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return companion.getItemBySlot(SLOTS[slot]); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack remaining = getItem(slot).copy();
        ItemStack removed = remaining.split(count);
        setItem(slot, remaining);
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) { companion.setEquipmentByPlayer(SLOTS[slot], stack); }
    @Override public void setChanged() { }
    @Override public boolean stillValid(Player player) { return companion.stillValid(player); }
    @Override public void clearContent() { for (EquipmentSlot slot : SLOTS) companion.setItemSlot(slot, ItemStack.EMPTY); }
}
