package dev.aicompanion.entity;

import dev.aicompanion.inventory.CompanionEquipmentContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

final class CompanionBag {
    /** On every item that fell from him at death: the owner's UUID, so the goal that outlives him can fetch its own things (CompanionAgenda.died). */
    static final String BELONGINGS = "aicompanion:belongings";
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

    boolean dropBelongings() {
        boolean any = false;
        java.util.List<ItemStack> belongings = new java.util.ArrayList<>();
        for (int slot = 0; slot < c.companionInventory.size(); slot++) { belongings.add(c.companionInventory.get(slot)); c.companionInventory.set(slot, ItemStack.EMPTY); }
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) { belongings.add(c.getItemBySlot(slot)); c.setItemSlot(slot, ItemStack.EMPTY); }
        for (ItemStack stack : belongings) if (!stack.isEmpty()) {
            var item = c.spawnAtLocation(stack.copy(), 0.5F);
            if (item != null) {
                item.setUnlimitedLifetime();
                if (c.getOwnerId() != null) item.getPersistentData().putUUID(BELONGINGS, c.getOwnerId());
                any = true;
            }
        }
        refreshInventorySummary();
        return any;
    }

    /** obtain_item: the chain of his own skills that gets the item, planned from what he carries now (CompanionObtain). */
    public CompanionObtain.Plan obtainPlan(String item, int count) {
        if (!(c.level() instanceof ServerLevel serverLevel)) return new CompanionObtain.Plan(List.of(), "Для этого нужен загруженный мир.");
        return CompanionObtain.plan(serverLevel, carriedForPlan(), c.hasCraftingTableNearby() || c.hasKnownCraftingTableWithin(16), item, count);
    }
    /** The end of obtain_item: success only by the bag, never by the plan. */
    public CompanionActionResult carriedCheck(String item, int count) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(item);
        var wanted = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (wanted == null) return CompanionActionResult.failed("Не понял, какой предмет проверить.");
        int have = 0;
        for (ItemStack stack : carriedForPlan()) if (stack.is(wanted)) have += stack.getCount();
        String name = new ItemStack(wanted).getHoverName().getString();
        return have >= count ? CompanionActionResult.ok("Готово: «" + name + "» у меня, " + have + " шт.")
                : CompanionActionResult.failed("Добыл не всё: «" + name + "» у меня " + have + " из " + count + ".");
    }
    /** How many of the item he carries, as obtain_item counts them. */
    public int carriedCount(String item) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(item);
        var wanted = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        int have = 0;
        if (wanted != null) for (ItemStack stack : carriedForPlan()) if (stack.is(wanted)) have += stack.getCount();
        return have;
    }
    List<ItemStack> carriedForPlan() {
        List<ItemStack> carried = new java.util.ArrayList<>();
        for (int slot = 0; slot < c.getContainerSize(); slot++) carried.add(c.getItem(slot));
        carried.add(c.getMainHandItem()); carried.add(c.getOffhandItem());
        return carried;
    }
}
