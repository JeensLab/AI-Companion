package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
        double distance = w.owner.distanceToSqr(block.getCenter());
        if (distance <= 3.8D * 3.8D && CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), block)
                && (!forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, w.owner.blockPosition()))) { w.owner.getNavigation().stop(); return 1; }
        w.breaking.reset();
        if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = level.getGameTime(); }
        if (level.getGameTime() - w.lastProgressTick > 160) return -1;
        if (w.path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck()) w.path = CompanionMiningNavigation.pathTo(w.owner, block, 3.5, false, true, forTheDrop);
        if (w.path == null || !w.path.canReach()) return -1;
        CompanionMiningNavigation.walk(w.owner, w.path);
        return 0;
    }

    boolean prepareMiningTool(BlockState state) {
        boolean busy = state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) ? w.shovelSense.tick(reservedStone())
                : (w.job == CompanionWork.Job.STONE || w.job == CompanionWork.Job.ORE) && state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) && w.pickaxeSense.tick(reservedStone());
        if (busy) { w.breaking.reset(); w.resetApproach(); }
        return busy;
    }
    int reservedStone() { return w.phase == CompanionWork.Phase.MINE || w.phase == CompanionWork.Phase.PICKAXE ? Math.max(0, w.owner.collectedStone() - w.stoneCollectedBefore) : 0; }

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
