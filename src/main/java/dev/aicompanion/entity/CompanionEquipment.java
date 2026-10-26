package dev.aicompanion.entity;

import dev.aicompanion.inventory.CompanionEquipmentContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

final class CompanionEquipment {
    final CompanionEntity c;

    CompanionEquipment(CompanionEntity c) { this.c = c; }

    public String getEquipmentForAi() {
        List<String> equipment = new java.util.ArrayList<>();
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            ItemStack stack = c.getItemBySlot(slot);
            equipment.add(slot.getName() + "=" + (stack.isEmpty() ? "пусто" : BuiltInRegistries.ITEM.getKey(stack.getItem())
                    + " x" + stack.getCount() + (stack.isDamageableItem() ? " (прочность " + (stack.getMaxDamage() - stack.getDamageValue()) + ")" : "")));
        }
        return String.join(", ", equipment);
    }

    public CompanionActionResult equipItem(String requested, String slotName) {
        var result = equipItemInternal(requested, slotName);
        if (result.success() && ("mainhand".equals(slotName) || "auto".equals(slotName) && matchesEquipment(c.getMainHandItem(), requested)))
            preferCurrentWeapon();
        if (result.success()) c.animateAction(4, c.blockPosition(), c.getMainHandItem());
        return c.recordActionResult(result);
    }

    CompanionActionResult equipForSupport(String itemId) {
        return equipItemInternal(itemId, "mainhand");
    }

    CompanionActionResult equipForWork(String itemId) { return equipItemInternal(itemId, "mainhand"); }

    private CompanionActionResult equipItemInternal(String requested, String slotName) {
        if (c.level().isClientSide) return CompanionActionResult.failed("Экипировка меняется на сервере мира.");
        EquipmentSlot explicit = "auto".equals(slotName) ? null : equipmentSlot(slotName);
        if (explicit == null && !"auto".equals(slotName)) return CompanionActionResult.failed("Неизвестный слот экипировки.");
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            if ((explicit == null || explicit == slot) && matchesEquipment(c.getItemBySlot(slot), requested)) {
                return CompanionActionResult.ok("Уже экипирован: " + c.getItemBySlot(slot).getHoverName().getString() + ".");
            }
        }
        int source = -1;
        double best = -Double.MAX_VALUE;
        for (int index = 0; index < c.companionInventory.size(); index++) {
            ItemStack stack = c.companionInventory.get(index);
            if (!matchesEquipment(stack, requested)) continue;
            EquipmentSlot destination = explicit == null ? c.getEquipmentSlotForItem(stack) : explicit;
            if (!java.util.Arrays.asList(CompanionEquipmentContainer.SLOTS).contains(destination)) continue;
            if (destination.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !stack.canEquip(destination, c)) continue;
            double score = equipmentScore(stack, destination);
            if (score > best) { source = index; best = score; }
        }
        if (source < 0) return CompanionActionResult.failed("В моей сумке нет подходящего предмета «" + requested + "» для этого слота.");
        ItemStack selected = c.companionInventory.get(source);
        EquipmentSlot destination = explicit == null ? c.getEquipmentSlotForItem(selected) : explicit;
        ItemStack previous = c.getItemBySlot(destination);
        if (destination.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && net.minecraft.world.item.enchantment.EnchantmentHelper.has(previous,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return CompanionActionResult.failed("Не могу снять предмет с проклятием несъёмности.");
        }
        NonNullList<ItemStack> before = copyBag();
        ItemStack incoming = selected.split(1);
        ItemStack toStore = previous.copy();
        if (!toStore.isEmpty() && !c.storeInCompanionInventory(toStore)) {
            restoreBag(before);
            return CompanionActionResult.failed("В сумке нет места для прежнего предмета. Освободи ячейку.");
        }
        c.setItemSlot(destination, incoming);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "equipment_changed", "entity", c.getUUID(), "slot", destination.getName(),
                "before", previous.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(previous.getItem()),
                "after", BuiltInRegistries.ITEM.getKey(incoming.getItem()), "requested", requested);
        c.refreshInventorySummary();
        return CompanionActionResult.ok((destination.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? "Надел: " : "Взял в "
                + (destination == EquipmentSlot.OFFHAND ? "левую" : "правую") + " руку: ") + incoming.getHoverName().getString() + ".");
    }

    public CompanionActionResult unequipItem(String slotName) {
        var result = unequipForSupport(slotName);
        if (result.success() && (slotName.equals("mainhand") || slotName.equals("auto"))) c.preferredWeapon = "";
        if (result.success()) c.animateAction(4, c.blockPosition(), ItemStack.EMPTY);
        return c.recordActionResult(result);
    }

    CompanionActionResult unequipForSupport(String slotName) {
        EquipmentSlot slot = equipmentSlot(slotName);
        if (slot == null) return CompanionActionResult.failed("Укажи слот: mainhand, offhand, head, chest, legs или feet.");
        ItemStack equipped = c.getItemBySlot(slot);
        if (equipped.isEmpty()) return CompanionActionResult.ok("Эта ячейка уже пуста.");
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && net.minecraft.world.item.enchantment.EnchantmentHelper.has(equipped,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return CompanionActionResult.failed("Не могу снять предмет с проклятием несъёмности.");
        }
        NonNullList<ItemStack> before = copyBag();
        ItemStack offered = equipped.copy();
        if (!c.storeInCompanionInventory(offered)) {
            restoreBag(before);
            return CompanionActionResult.failed("В сумке нет места для снятого предмета.");
        }
        c.setItemSlot(slot, ItemStack.EMPTY);
        return CompanionActionResult.ok("Убрал в сумку: " + equipped.getHoverName().getString() + ".");
    }

    NonNullList<ItemStack> copyBag() {
        NonNullList<ItemStack> copy = NonNullList.withSize(c.companionInventory.size(), ItemStack.EMPTY);
        for (int index = 0; index < copy.size(); index++) copy.set(index, c.companionInventory.get(index).copy());
        return copy;
    }

    private void restoreBag(List<ItemStack> bag) {
        for (int index = 0; index < bag.size(); index++) c.companionInventory.set(index, bag.get(index).copy());
        c.refreshInventorySummary();
    }

    public static EquipmentSlot equipmentSlot(String name) {
        return switch (name) {
            case "mainhand", "auto" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private boolean matchesEquipment(ItemStack stack, String requested) {
        if (stack.isEmpty()) return false;
        return switch (requested) {
            case "sword" -> stack.is(ItemTags.SWORDS);
            case "axe" -> stack.is(ItemTags.AXES);
            case "pickaxe" -> stack.is(ItemTags.PICKAXES);
            case "shovel" -> stack.is(ItemTags.SHOVELS);
            case "hoe" -> stack.is(ItemTags.HOES);
            case "shield" -> stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK);
            case "helmet" -> c.getEquipmentSlotForItem(stack) == EquipmentSlot.HEAD;
            case "chestplate" -> c.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
            case "leggings" -> c.getEquipmentSlotForItem(stack) == EquipmentSlot.LEGS;
            case "boots" -> c.getEquipmentSlotForItem(stack) == EquipmentSlot.FEET;
            default -> requested.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        };
    }

    private double equipmentScore(ItemStack stack, EquipmentSlot slot) {
        boolean armor = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        var relevant = stack.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(armor ? Attributes.ARMOR : Attributes.ATTACK_DAMAGE)).toList();
        double score = new net.minecraft.world.item.component.ItemAttributeModifiers(relevant, true).compute(armor ? 0 : 1, slot);
        return score * 10000 + (stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0);
    }

    public void selectToolForBlock(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
            if (!CompanionTools.equip(c, ItemTags.SHOVELS, state) && !c.getMainHandItem().isEmpty()) unequipForSupport("mainhand");
            return;
        }
        double best = c.getMainHandItem().getDestroySpeed(state);
        ItemStack choice = ItemStack.EMPTY;
        for (ItemStack stack : c.companionInventory) if (!stack.isEmpty() && stack.getDestroySpeed(state) > best) {
            best = stack.getDestroySpeed(state);
            choice = stack;
        }
        if (!choice.isEmpty()) equipItemInternal(BuiltInRegistries.ITEM.getKey(choice.getItem()).toString(), "mainhand");
    }

    public void selectWeaponForCombat() {
        if (!c.preferredWeapon.isEmpty()) {
            if (matchesEquipment(c.getMainHandItem(), c.preferredWeapon)) return;
            if (equipItemInternal(c.preferredWeapon, "mainhand").success()) return;
        }
        double best = equipmentScore(c.getMainHandItem(), EquipmentSlot.MAINHAND);
        ItemStack choice = ItemStack.EMPTY;
        for (ItemStack stack : c.companionInventory) if ((stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES))
                && equipmentScore(stack, EquipmentSlot.MAINHAND) > best) {
            best = equipmentScore(stack, EquipmentSlot.MAINHAND);
            choice = stack;
        }
        if (!choice.isEmpty()) equipItemInternal(BuiltInRegistries.ITEM.getKey(choice.getItem()).toString(), "mainhand");
    }

    private void preferCurrentWeapon() {
        ItemStack hand = c.getMainHandItem();
        c.preferredWeapon = hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES) ? BuiltInRegistries.ITEM.getKey(hand.getItem()).toString() : "";
    }
    public void setEquipmentByPlayer(EquipmentSlot slot, ItemStack stack) {
        c.setItemSlot(slot, stack);
        if (!c.level().isClientSide && slot == EquipmentSlot.MAINHAND) preferCurrentWeapon();
    }
}
