package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class CompanionWorkItems {
    final CompanionWork w;

    CompanionWorkItems(CompanionWork w) { this.w = w; }


    /**
     * 1: in reach and in sight; 0: walking there; -1: no way or no progress for eight seconds. forTheDrop: the ore itself is
     * mined only from where its drop can then be picked up (a tunnel cell is not).
     */
    int reachBlock(ServerLevel level, BlockPos block, boolean forTheDrop) {
        w.owner.movementIntent(block);
        w.owner.getLookControl().setLookAt(block.getX() + .5D, block.getY() + .5D, block.getZ() + .5D);
        return w.route.approach(level, block, forTheDrop);
    }

    boolean prepareMiningTool(BlockState state) {
        boolean busy = state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) ? keepingOrderStone(() -> w.shovelSense.tick(reservedStone()))
                : (w.job == CompanionWork.Job.STONE || w.job == CompanionWork.Job.ORE) && state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) && keepingOrderStone(() -> w.pickaxeSense.tick(reservedStone()));
        if (busy) { w.breaking.reset(); w.resetApproach(); }
        return busy;
    }
    int reservedStone() { return w.phase == CompanionWork.Phase.MINE || w.phase == CompanionWork.Phase.PICKAXE ? Math.max(0, w.owner.collectedStone() - w.stoneCollectedBefore) : 0; }

    /**
     * A stone order keeps all its stone: a tool made from the stone he carried is mined back on top of the order, the way wood
     * counts the bag (marathon of PR #6: 11 carried, 7 ordered, a stone pickaxe took 3 and the build got 15 of 18).
     */
    boolean keepingOrderStone(java.util.function.BooleanSupplier toolStep) {
        int before = CompanionTools.count(w.owner, ItemTags.STONE_TOOL_MATERIALS);
        boolean result = toolStep.getAsBoolean();
        if (w.job == CompanionWork.Job.STONE) w.wantedStone += Math.max(0, before - CompanionTools.count(w.owner, ItemTags.STONE_TOOL_MATERIALS));
        return result;
    }

    static String blocks(int count) {
        int last = count % 10, tens = count % 100;
        return count + (last == 1 && tens != 11 ? " блок" : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? " блока" : " блоков");
    }
    int countCarried(net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : carriedItems()) if (stack.is(item)) count += stack.getCount();
        return count;
    }

    boolean usablePickaxe(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES) || !stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())
                || !(w.owner.level() instanceof ServerLevel level)) return false;
        // Silk Touch stone is not a material for stone tools. Use a normal pickaxe for this job.
        return Block.getDrops(Blocks.STONE.defaultBlockState(), level, w.owner.blockPosition(), null, w.owner, stack)
                .stream().anyMatch(drop -> drop.is(ItemTags.STONE_TOOL_MATERIALS));
    }
    int countStoneMaterials() {
        int result = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (w.owner.getItem(slot).is(ItemTags.STONE_TOOL_MATERIALS)) result += w.owner.getItem(slot).getCount();
        return result;
    }
    List<ItemStack> carriedItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(w.owner.getMainHandItem()); items.add(w.owner.getOffhandItem());
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) items.add(w.owner.getItem(slot));
        return items;
    }

    boolean canStore(List<ItemStack> drops) {
        return w.owner.canStoreDrops(drops);
    }

    boolean visibleFrom(ServerLevel level, Vec3 eye, BlockPos block) {
        return CompanionEntity.seesBlock(w.owner, eye, block);
    }

    boolean moveHandMaterialsToBag() {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = w.owner.getItemBySlot(slot);
            if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(Items.STICK)) {
                CompanionActionResult result = w.owner.unequipForSupport(slot.getName());
                if (!result.success()) { w.finish(false, result.message()); return false; }
            }
        }
        return true;
    }

    /**
     * The hands a stack is held in while the bag has no cell for a craft; empty when nothing is held. Both hands are
     * tracked: one stack in one hand may not be enough when the bag fills up again before the next step.
     */
    private final Set<EquipmentSlot> heldHands = EnumSet.noneOf(EquipmentSlot.class);
    /** The saved held hands: a save without the key holds nothing. */
    private static final String HELD_HANDS = "WorkHeldHands";

    /**
     * A full bag leaves no cell for what a craft is made of: one stack is taken in a free hand, the way a player holds
     * something to free a cell. Nothing is taken when the bag already has a cell or when both hands are busy. False when
     * there is nothing to take — the craft may still fit into a cell it frees itself.
     */
    boolean holdOneStack(Set<Item> keep) {
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (w.owner.getItem(slot).isEmpty()) return true;
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND}) {
            // A hand already holding a stack is skipped, so the next step takes the other free hand instead of nothing.
            if (!w.owner.getItemBySlot(hand).isEmpty()) continue;
            int pick = pickForTheHand(keep);
            if (pick < 0) return false;
            ItemStack held = w.owner.getItem(pick).copy();
            w.owner.setItem(pick, ItemStack.EMPTY);
            w.owner.setItemSlot(hand, held);
            w.owner.setChanged();
            heldHands.add(hand);
            return true;
        }
        return false;
    }

    /**
     * The stack a hand may take: the least worth one, but never what a hand gives back at once (moveHandMaterialsToBag)
     * and never what the chain itself is made of (keep) — the plan reads the bag, so a material in a hand is a material
     * the plan cannot see («не хватает «Уголь»» while the coal sits in the hand).
     */
    private int pickForTheHand(Set<Item> keep) {
        int pick = -1;
        double worst = Double.MAX_VALUE;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) {
            ItemStack stack = w.owner.getItem(slot);
            if (stack.isEmpty() || keep.contains(stack.getItem()) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)
                    || stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(Items.STICK)) continue;
            double worth = CompanionBelongings.worth(stack);
            if (worth < worst) { worst = worth; pick = slot; }
        }
        return pick;
    }

    /**
     * The craft as the work does it: the plan first, and only when it says the bag has no cell is a stack taken in a free
     * hand and the plan tried again. A hand takes a stack out of the bag while the plan reads the bag, so holding before
     * the plan hides a material of the chain from it; a retry that still fails puts the stack back and keeps the plan's
     * own answer about the cell.
     */
    CompanionActionResult craftFromBagMakingRoom(String itemId, int count) {
        CompanionActionResult crafted = w.owner.craftFromBag(itemId, count);
        if (!spaceRefusal(crafted)) return crafted;
        if (!holdOneStack(chainMaterials(itemId))) return crafted;
        CompanionActionResult again = w.owner.craftFromBag(itemId, count);
        if (again.success()) return again;
        putHeldStackBack();
        return crafted;
    }

    /** The plan's own words for a bag with no cell: the one refusal a free hand can lift. */
    private static boolean spaceRefusal(CompanionActionResult result) {
        return !result.success() && (result.message().contains("места") || result.message().contains("безопасное место"));
    }

    /** Everything the chain for an item is made of, the item itself too: none of it may leave the bag for a hand. */
    private Set<Item> chainMaterials(String itemId) {
        if (!(w.owner.level() instanceof ServerLevel level)) return Set.of();
        var id = ResourceLocation.tryParse(itemId);
        Item target = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return target == null ? Set.of() : CompanionCrafting.chainMaterials(level, target);
    }

    /** The stack taken in a hand goes back once the made block has left the bag and its cell is free; never dropped. */
    void putHeldStackBack() {
        for (EquipmentSlot hand : heldHands.toArray(new EquipmentSlot[0])) {
            ItemStack held = w.owner.getItemBySlot(hand);
            if (held.isEmpty()) { heldHands.remove(hand); continue; }
            w.owner.setItemSlot(hand, ItemStack.EMPTY);
            w.owner.storeLoot(held);
            // No cell for it yet: it stays in the hand and goes back on the next end of the work.
            if (!held.isEmpty()) w.owner.setItemSlot(hand, held);
            else heldHands.remove(hand);
        }
    }

    /** The hands holding a stack, in a fixed order, for the GameTest observer. */
    List<String> heldHands() {
        List<String> names = new ArrayList<>();
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (heldHands.contains(hand)) names.add(hand.getName());
        return names;
    }

    /** A stack held in a hand is the work's, not equipment: the save carries it, so the reload returns it to the bag. */
    void save(CompoundTag tag) {
        if (heldHands.isEmpty()) { tag.remove(HELD_HANDS); return; }
        tag.putString(HELD_HANDS, String.join(",", heldHands()));
    }

    /** The hands of the save: a hand that is empty now holds nothing, whatever the save said. */
    void load(CompoundTag tag) {
        heldHands.clear();
        for (String name : tag.getString(HELD_HANDS).split(",")) {
            EquipmentSlot hand = "mainhand".equals(name) ? EquipmentSlot.MAINHAND : "offhand".equals(name) ? EquipmentSlot.OFFHAND : null;
            if (hand != null && !w.owner.getItemBySlot(hand).isEmpty()) heldHands.add(hand);
        }
    }

    int takeFromSlot(Container container, int slot, int max) {
        ItemStack stack = container.getItem(slot);
        if (max <= 0 || stack.isEmpty()) return 0;
        ItemStack offered = stack.copyWithCount(Math.min(max, stack.getCount()));
        int before = offered.getCount();
        w.owner.storeLoot(offered);
        int moved = before - offered.getCount();
        if (moved > 0) { stack.shrink(moved); container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack); }
        return moved;
    }

    /** Takes up to count of one kind (identical stacks only) from the bag, then the hands. */
    ItemStack takeCarried(net.minecraft.world.item.Item item, int count) {
        ItemStack taken = ItemStack.EMPTY;
        for (int index = 0; index < w.owner.getContainerSize() + 2 && taken.getCount() < count; index++) {
            EquipmentSlot hand = index == w.owner.getContainerSize() ? EquipmentSlot.MAINHAND : index == w.owner.getContainerSize() + 1 ? EquipmentSlot.OFFHAND : null;
            ItemStack stack = hand == null ? w.owner.getItem(index) : w.owner.getItemBySlot(hand);
            if (!stack.is(item) || !taken.isEmpty() && !ItemStack.isSameItemSameComponents(stack, taken)) continue;
            int amount = Math.min(count - taken.getCount(), stack.getCount());
            if (taken.isEmpty()) taken = stack.copyWithCount(amount); else taken.grow(amount);
            stack.shrink(amount);
            if (hand == null) w.owner.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack);
            else if (stack.isEmpty()) w.owner.setItemSlot(hand, ItemStack.EMPTY);
        }
        w.owner.setChanged();
        return taken;
    }
}
