package dev.aicompanion.entity;

import dev.aicompanion.inventory.CompanionEquipmentContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

final class CompanionBag {
    final CompanionEntity c;

    CompanionBag(CompanionEntity c) { this.c = c; }

    public String getStoredItemsSummary() {
        return c.getEntityData().get(CompanionEntity.DATA_INVENTORY_SUMMARY);
    }

    public String getStoredItemsForAi() {
        return c.getEntityData().get(CompanionEntity.DATA_INVENTORY_AI);
    }

    public void mergeStoredItemsFrom(CompanionEntity other) {
        if (other == c || c.level().isClientSide) return;
        for (ItemStack stack : other.companionInventory) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = stack.copy();
            if (!storeInCompanionInventory(remaining) && c.level() instanceof ServerLevel serverLevel) {
                Block.popResource(serverLevel, c.blockPosition(), remaining);
            }
        }
        other.companionInventory.clear();
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            ItemStack equipment = other.getItemBySlot(slot);
            if (equipment.isEmpty()) continue;
            if (c.getItemBySlot(slot).isEmpty()) c.setItemSlot(slot, equipment.copy());
            else {
                ItemStack remaining = equipment.copy();
                if (!storeInCompanionInventory(remaining) && c.level() instanceof ServerLevel serverLevel) Block.popResource(serverLevel, c.blockPosition(), remaining);
            }
            other.setItemSlot(slot, ItemStack.EMPTY);
        }
        other.refreshInventorySummary();
    }

    private String buildInventorySummary() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack stack : c.companionInventory) {
            if (!stack.isEmpty()) counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        return counts.isEmpty() ? "пусто" : counts.entrySet().stream()
                .map(entry -> entry.getValue() + " " + entry.getKey()).collect(java.util.stream.Collectors.joining(", "));
    }

    boolean storeInCompanionInventory(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ItemStack stored : c.companionInventory) {
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, remaining)) continue;
            int move = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
            if (move > 0) {
                stored.grow(move);
                remaining.shrink(move);
            }
            if (remaining.isEmpty()) {
                refreshInventorySummary();
                stack.setCount(0);
                return true;
            }
        }
        for (int slot = 0; slot < c.companionInventory.size() && !remaining.isEmpty(); slot++) {
            if (c.companionInventory.get(slot).isEmpty()) {
                c.companionInventory.set(slot, remaining.copy());
                remaining.setCount(0);
            }
        }
        refreshInventorySummary();
        stack.setCount(remaining.getCount());
        return remaining.isEmpty();
    }

    void refreshInventorySummary() {
        c.getEntityData().set(CompanionEntity.DATA_INVENTORY_SUMMARY, buildInventorySummary());
        java.util.Map<String, Integer> items = new java.util.LinkedHashMap<>();
        for (ItemStack stack : c.companionInventory) {
            if (!stack.isEmpty()) items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        c.getEntityData().set(CompanionEntity.DATA_INVENTORY_AI, items.isEmpty() ? "пусто" : items.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey()).collect(java.util.stream.Collectors.joining(", ")));
    }
}
