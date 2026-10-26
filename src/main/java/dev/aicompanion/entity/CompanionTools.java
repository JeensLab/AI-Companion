package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class CompanionTools {
    private CompanionTools() { }
    static int count(CompanionEntity owner, TagKey<Item> tag) {
        int total = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(tag)) total += owner.getItem(slot).getCount();
        if (owner.getMainHandItem().is(tag)) total += owner.getMainHandItem().getCount();
        if (owner.getOffhandItem().is(tag)) total += owner.getOffhandItem().getCount();
        return total;
    }
    static int soil(CompanionEntity owner) {
        int count = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (soilItem(owner.getItem(slot))) count += owner.getItem(slot).getCount();
        if (soilItem(owner.getMainHandItem())) count += owner.getMainHandItem().getCount();
        if (soilItem(owner.getOffhandItem())) count += owner.getOffhandItem().getCount();
        return count;
    }
    static boolean soilItem(ItemStack stack) { return stack.is(net.minecraft.world.item.Items.DIRT) || stack.is(net.minecraft.world.item.Items.COARSE_DIRT) || stack.is(net.minecraft.world.item.Items.ROOTED_DIRT); }
    static int inventoryHash(CompanionEntity owner) {
        int hash = 1;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            hash = hash * 31 + BuiltInRegistries.ITEM.getId(stack.getItem()) * 67 + stack.getCount();
        }
        return hash;
    }
    static boolean equip(CompanionEntity owner, TagKey<Item> tag, BlockState state) {
        ItemStack best = owner.getMainHandItem().is(tag) ? owner.getMainHandItem() : ItemStack.EMPTY;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack candidate = owner.getItem(slot);
            if (candidate.is(tag) && (best.isEmpty() || candidate.getDestroySpeed(state) > best.getDestroySpeed(state))) best = candidate;
        }
        if (owner.getOffhandItem().is(tag) && (best.isEmpty() || owner.getOffhandItem().getDestroySpeed(state) > best.getDestroySpeed(state))) {
            String id = BuiltInRegistries.ITEM.getKey(owner.getOffhandItem().getItem()).toString();
            if (!owner.unequipForSupport("offhand").success()) return false;
            return owner.equipForWork(id).success();
        }
        if (best.isEmpty()) return false;
        return owner.equipForWork(BuiltInRegistries.ITEM.getKey(best.getItem()).toString()).success();
    }
    static boolean craft(CompanionEntity owner, String kind, BlockState state, TagKey<Item> tag) {
        if (count(owner, ItemTags.LOGS) == 0 && count(owner, ItemTags.PLANKS) == 0) return false;
        for (String material : new String[]{"stone", "wooden"}) {
            if (material.equals("stone") && count(owner, ItemTags.STONE_TOOL_MATERIALS) < (kind.equals("shovel") ? 1 : 3)) continue;
            String id = "minecraft:" + material + "_" + kind;
            if (owner.craftFromBag(id, 1).success()) return equip(owner, tag, state);
        }
        return false;
    }
}
