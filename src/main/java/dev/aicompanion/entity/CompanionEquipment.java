package dev.aicompanion.entity;

import dev.aicompanion.inventory.CompanionEquipmentContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

final class CompanionEquipment {
    /** A player's shield takes no wear from a blow weaker than this. */
    private static final float SHIELD_WEARS_FROM = 3;
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
            if (!matchesEquipment(stack, requested) || c.carriedForPlayer(stack)) continue;
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

    /**
     * The hand item a tool for the block replaced, kept to be put back after the break (issue #105). Null when nothing is pending:
     * the work puts the tool away when it ends or is cancelled, so a hand kept for one break is not restored into another.
     */
    private ItemStack handBeforeTool;

    /** A kept hand the dig itself ended is no longer owed: the recovery restores its own hand instead. */
    public void forgetKeptHand() { handBeforeTool = null; }

    /** The tool that breaks this block best goes into his hand and stays there: for the paths that break many blocks in a row. */
    public void selectToolForBlock(BlockState state) { takeToolForBlock(state, false); }

    /** The same, keeping what was in his hand for {@link #putToolAway()} after the break. */
    public void takeToolForBlock(BlockState state) { takeToolForBlock(state, true); }

    /**
     * The Silk Touch tool that gives the block itself goes into his hand, keeping the hand for {@link #putToolAway()}; false when
     * he carries none of the kind. A plain tool of the same kind would give the block's drop, not the block.
     */
    public boolean takeSilkToolForBlock(BlockState state) {
        ItemStack best = CompanionTools.bestSilkToolForBlock(c, state);
        if (best.isEmpty()) return false;
        if (best == c.getMainHandItem()) return true;
        ItemStack previous = c.getMainHandItem().copy();
        if (best == c.getOffhandItem()) {
            if (!c.unequipForSupport("offhand").success()) return false;
            best = CompanionTools.bestSilkToolForBlock(c, state);
            if (best.isEmpty()) return false;
            if (best == c.getMainHandItem()) return true;
        }
        if (!equipStackForWork(best).success()) return false;
        if (handBeforeTool == null) handBeforeTool = previous;
        return true;
    }

    /** Puts back what was in his hand before the tool for the block: that same stack goes into the hand again, the tool into the bag. */
    public void putToolAway() {
        ItemStack previous = handBeforeTool;
        handBeforeTool = null;
        if (previous == null) return;
        if (previous.isEmpty()) { unequipForSupport("mainhand"); return; }
        // The stack he held, not the first of its item: a plain pickaxe comes back, not the Efficiency one he broke with.
        ItemStack same = findInBag(previous);
        if (!same.isEmpty()) { equipStackForWork(same); return; }
        equipForSupport(BuiltInRegistries.ITEM.getKey(previous.getItem()).toString());
    }

    private ItemStack findInBag(ItemStack like) {
        for (ItemStack stack : c.companionInventory)
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, like)) return stack;
        return ItemStack.EMPTY;
    }

    /**
     * The chosen stack itself goes into his hand, not a stack of the same item: the one the work picked (the fastest, the enchanted
     * one) is the one that wears. The hand item it replaces goes into the bag first; with no room for it the two trade places, as a
     * player swaps them: the hand item takes the cell the tool leaves and no cell is needed. Only a tool of the other hand (no cell
     * of its own) is refused then.
     */
    CompanionActionResult equipStackForWork(ItemStack chosen) {
        if (c.level().isClientSide) return CompanionActionResult.failed("Экипировка меняется на сервере мира.");
        if (chosen.isEmpty()) return CompanionActionResult.failed("В руке нечего взять.");
        NonNullList<ItemStack> before = copyBag();
        ItemStack previous = c.getMainHandItem();
        int slot = bagSlotOf(chosen);
        ItemStack toStore = previous.copy();
        ItemStack incoming;
        if (!toStore.isEmpty() && !c.storeInCompanionInventory(toStore)) {
            // The bag has no cell for what his hand held (a full bag, a stack that only half fits): the tool leaves its own cell
            // for it. One tool at a time: a stack of several stays in the bag, its cell still taken.
            if (slot < 0 || before.get(slot).getCount() != 1) {
                restoreBag(before);
                return CompanionActionResult.failed("В сумке нет места для прежнего предмета. Освободи ячейку.");
            }
            incoming = before.get(slot);
            restoreBag(before);
            c.companionInventory.set(slot, previous);
            c.setItemSlot(EquipmentSlot.MAINHAND, incoming);
        } else {
            incoming = chosen.split(1);
            c.setItemSlot(EquipmentSlot.MAINHAND, incoming);
        }
        String id = BuiltInRegistries.ITEM.getKey(incoming.getItem()).toString();
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "equipment_changed", "entity", c.getUUID(), "slot", "mainhand",
                "before", previous.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(previous.getItem()),
                "after", id, "requested", id);
        c.refreshInventorySummary();
        return CompanionActionResult.ok("Взял в правую руку: " + incoming.getHoverName().getString() + ".");
    }

    /** The bag cell this very stack lies in, or -1 for a stack of a hand. */
    private int bagSlotOf(ItemStack stack) {
        for (int slot = 0; slot < c.companionInventory.size(); slot++)
            if (c.companionInventory.get(slot) == stack) return slot;
        return -1;
    }

    private void takeToolForBlock(BlockState state, boolean keepPrevious) {
        // The switch off: he breaks with whatever he happens to hold, as a player who does not bother changing tools.
        if (!c.rightToolForBlock()) return;
        // This path leaves the tool in his hand: a hand kept for a restore belongs to another break.
        if (!keepPrevious) handBeforeTool = null;
        ItemStack best = CompanionTools.bestToolForBlock(c, state);
        if (best.isEmpty()) {
            // Nothing of his speeds the block up: a tool of another class is no faster than a bare hand, and a player puts it away
            // rather than digs with it in the way (the shovel branch emptied the hand like this).
            if (!CompanionTools.wrongToolForBlock(c, c.getMainHandItem(), state)) return;
            ItemStack previous = c.getMainHandItem().copy();
            if (!c.unequipForSupport("mainhand").success()) return;
            if (keepPrevious && handBeforeTool == null) handBeforeTool = previous;
            return;
        }
        if (best == c.getMainHandItem()) return;
        ItemStack previous = c.getMainHandItem().copy();
        if (best == c.getOffhandItem()) {
            // The whole offhand stack goes into the bag first, as unequipping it does; the fastest stack is then taken from there.
            if (!c.unequipForSupport("offhand").success()) return;
            best = CompanionTools.bestToolForBlock(c, state);
            if (best.isEmpty() || best == c.getMainHandItem()) return;
        }
        if (!equipStackForWork(best).success()) return;
        if (keepPrevious && handBeforeTool == null) handBeforeTool = previous;
    }

    public void selectWeaponForCombat() {
        // The crystals of the End are shot with no target set: the sword the player chose must not come back mid-draw.
        if (c.archery.wants(c.getTarget()) || c.endCrystals.shooting()) {
            if (!(c.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem)) equipItemInternal("minecraft:bow", "mainhand");
            return;
        }
        if (!c.preferredWeapon.isEmpty()) {
            if (matchesEquipment(c.getMainHandItem(), c.preferredWeapon)) return;
            if (equipItemInternal(c.preferredWeapon, "mainhand").success()) return;
        }
        ItemStack choice = bestWeapon(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES));
        // No sword or axe in the bag or the hand: the hardest-hitting pickaxe, as a player would; a stone pickaxe against a zombie in his
        // tunnel is an even trade he can lose by one blow while an iron one lies in the bag. The hand is not in the bag: a held sword stays.
        ItemStack hand = c.getMainHandItem();
        if (choice.isEmpty() && !hand.is(ItemTags.SWORDS) && !hand.is(ItemTags.AXES)) choice = bestWeapon(stack -> stack.is(ItemTags.PICKAXES));
        if (!choice.isEmpty() && meleeScore(choice) > meleeScore(c.getMainHandItem()))
            equipItemInternal(BuiltInRegistries.ITEM.getKey(choice.getItem()).toString(), "mainhand");
    }

    /** Damage over time, not per blow: a wooden axe hits harder than a stone sword but twice as rarely, and loses a fight the sword wins. */
    private double meleeScore(ItemStack stack) { return equipmentScore(stack, EquipmentSlot.MAINHAND) * CompanionCombat.attackSpeed(stack); }

    static boolean isMelee(ItemStack stack) { return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES); }

    /** A sword or an axe in the hand or the bag (a pickaxe is a last resort, not a weapon he goes at a creeper with). */
    boolean hasMeleeWeapon() { return isMelee(c.getMainHandItem()) || !bestWeapon(CompanionEquipment::isMelee).isEmpty(); }

    /** The stone pickaxe is in the hand or the bag: the stone he carries is then not what it is still to be made of. */
    boolean hasStonePickaxe() {
        return c.getMainHandItem().is(net.minecraft.world.item.Items.STONE_PICKAXE) || c.companionInventory.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.STONE_PICKAXE));
    }

    private ItemStack bestWeapon(java.util.function.Predicate<ItemStack> kind) {
        ItemStack choice = ItemStack.EMPTY;
        for (ItemStack stack : c.companionInventory)
            if (kind.test(stack) && !c.carriedForPlayer(stack) && (choice.isEmpty() || meleeScore(stack) > meleeScore(choice))) choice = stack;
        return choice;
    }

    /** The blow the raised shield took, as a player's shield takes it: 1 + the whole damage from 3 up; a worn-out shield is gone, and the journal keeps it. */
    void wearShield(float damage) {
        ItemStack shield = c.getUseItem();
        if (damage < SHIELD_WEARS_FROM || !shield.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK) || !(c.level() instanceof ServerLevel level)) return;
        InteractionHand hand = c.getUsedItemHand();
        int wear = 1 + Mth.floor(damage);
        shield.hurtAndBreak(wear, level, c, item -> {
            c.onEquippedItemBroken(item, LivingEntity.getSlotForHand(hand));
            c.stopUsingItem();
        });
        boolean broken = shield.isEmpty();
        if (broken) {
            c.setItemSlot(LivingEntity.getSlotForHand(hand), ItemStack.EMPTY);
            c.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + c.getRandom().nextFloat() * 0.4F);
        }
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), broken ? "shield_broken" : "shield_wear", "entity", c.getUUID(), "damage", damage, "wear", wear,
                "left", broken ? 0 : shield.getMaxDamage() - shield.getDamageValue());
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
