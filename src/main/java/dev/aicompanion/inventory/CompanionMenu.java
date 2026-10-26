package dev.aicompanion.inventory;

import com.mojang.datafixers.util.Pair;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBond;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionHappeningEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CompanionMenu extends AbstractContainerMenu {
    public static final int EQUIPMENT_COUNT = 6, BAG_START = 6, PLAYER_START = 33, PLAYER_END = 69;
    private final CompanionEntity companion;
    private final Container bag;
    private final ServerPlayer giver;
    private final Map<String, ItemStack> samples = new LinkedHashMap<>();
    private Map<String, Integer> beforeCompanion, beforePlayer;

    public static CompanionMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        var entity = inventory.player.level().getEntity(data.readVarInt());
        return new CompanionMenu(id, inventory, entity instanceof CompanionEntity owned ? owned : null);
    }

    public CompanionMenu(int id, Inventory inventory, CompanionEntity companion) {
        super(AICompanionMod.COMPANION_MENU.get(), id);
        this.companion = companion;
        this.bag = companion == null ? new SimpleContainer(27) : companion;
        Container equipment = companion == null ? new SimpleContainer(6) : new CompanionEquipmentContainer(companion);
        bag.startOpen(inventory.player);
        ResourceLocation[] icons = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, null, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};
        for (int index = 0; index < EQUIPMENT_COUNT; index++) {
            final int equipmentIndex = index;
            final EquipmentSlot type = CompanionEquipmentContainer.SLOTS[index];
            int x = index < 4 ? 12 + index * 18 : 112 + (index - 4) * 36;
            addSlot(new Slot(equipment, index, x, 36) {
                @Override public int getMaxStackSize() { return type.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? 1 : 64; }
                @Override public boolean mayPlace(ItemStack stack) {
                    return type.getType() != EquipmentSlot.Type.HUMANOID_ARMOR
                            || (companion != null ? stack.canEquip(type, companion) : stack.canEquip(type, inventory.player));
                }
                @Override public boolean mayPickup(Player player) {
                    return player.isCreative() || type.getType() != EquipmentSlot.Type.HUMANOID_ARMOR
                            || !EnchantmentHelper.has(getItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
                }
                @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return icons[equipmentIndex] == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icons[equipmentIndex]);
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(bag, column + row * 9, 12 + column * 18, 90 + row * 18));
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, 9 + column + row * 9, 12 + column * 18, 164 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 12 + column * 18, 222));
        giver = inventory.player instanceof ServerPlayer owner && companion != null && owner.getUUID().equals(companion.getOwnerId()) ? owner : null;
        if (giver != null) { beforeCompanion = tally(0, PLAYER_START); beforePlayer = tally(PLAYER_START, PLAYER_END); }
    }

    private Map<String, Integer> tally(int from, int to) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = from; index < to; index++) {
            ItemStack stack = slots.get(index).getItem();
            if (stack.isEmpty()) continue;
            String kind = CompanionBond.kindOf(stack);
            counts.merge(kind, stack.getCount(), Integer::sum);
            samples.putIfAbsent(kind, stack.copyWithCount(1));
        }
        return counts;
    }

    /**
     * What the owner left in or took from the companion's equipment and bag while this menu was open, as one net change per kind.
     * Only what also left or arrived in the owner's own slots counts: loot the companion picked up meanwhile is not a gift.
     */
    private void reportGift() {
        if (beforeCompanion == null) return;
        Map<String, Integer> afterCompanion = tally(0, PLAYER_START), afterPlayer = tally(PLAYER_START, PLAYER_END);
        List<CompanionBond.ItemChange> changes = new ArrayList<>();
        List<String> added = new ArrayList<>();
        Set<String> kinds = new LinkedHashSet<>(beforeCompanion.keySet());
        kinds.addAll(afterCompanion.keySet());
        for (String kind : kinds) {
            int companionDelta = afterCompanion.getOrDefault(kind, 0) - beforeCompanion.getOrDefault(kind, 0);
            int playerDelta = afterPlayer.getOrDefault(kind, 0) - beforePlayer.getOrDefault(kind, 0);
            int delta = companionDelta > 0 ? Math.min(companionDelta, Math.max(0, -playerDelta)) : -Math.min(-companionDelta, Math.max(0, playerDelta));
            if (delta == 0) continue;
            changes.add(CompanionBond.change(samples.get(kind), delta));
            if (delta > 0 && added.size() < 3) added.add(delta + " × " + samples.get(kind).getItem().getDescription().getString());
        }
        beforeCompanion = null;
        beforePlayer = null;
        CompanionHappeningEvents.gift(giver, changes, String.join(", ", added));
    }

    public CompanionEntity getCompanion() { return companion; }
    @Override public boolean stillValid(Player player) { return companion != null && companion.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        if (index < EQUIPMENT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else {
            int equipmentIndex = equipmentIndex(stack, player);
            boolean equipped = equipmentIndex >= 0 && !slots.get(equipmentIndex).hasItem()
                    && moveItemStackTo(stack, equipmentIndex, equipmentIndex + 1, false);
            if (!equipped) {
                boolean moved = index < PLAYER_START ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                        : moveItemStackTo(stack, BAG_START, PLAYER_START, false);
                if (!moved) return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player, stack);
        return original;
    }

    private int equipmentIndex(ItemStack stack, Player player) {
        EquipmentSlot type = companion == null ? player.getEquipmentSlotForItem(stack) : companion.getEquipmentSlotForItem(stack);
        if (type == EquipmentSlot.MAINHAND && !stack.isDamageableItem()) return -1;
        for (int index = 0; index < EQUIPMENT_COUNT; index++) if (CompanionEquipmentContainer.SLOTS[index] == type) return index;
        return -1;
    }

    @Override public void removed(Player player) { super.removed(player); bag.stopOpen(player); reportGift(); }
}
