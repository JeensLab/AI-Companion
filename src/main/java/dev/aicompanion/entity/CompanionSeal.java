package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

/** Closes a cell with a real block from the bag, like a player plugging lava or water: item consumed, gesture, sound. */
public final class CompanionSeal {
    /** Eye to the centre of the cell, as for any block the companion works on from where he stands. */
    public static final double REACH = 4.5;
    /** Cheapest first: what mining makes anyway, then soil, then the plain stone. */
    private static final List<Item> PREFERENCE = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.STONE);

    private CompanionSeal() { }

    public enum Result {
        PLACED, NO_BLOCK, OUT_OF_REACH, OCCUPIED;

        public boolean placed() { return this == PLACED; }
    }

    /** Sealing blocks in the bag. */
    public static int available(CompanionEntity owner) {
        int count = 0;
        for (ItemStack stack : owner.companionInventory) if (seals(stack)) count += stack.getCount();
        return count;
    }

    /** A kind of block he plugs liquid with. */
    public static boolean seals(ItemStack stack) { return PREFERENCE.contains(stack.getItem()); }

    /** The cell takes a block: air, a plant or other replaceable block, or a liquid. */
    public static boolean replaceable(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    /** In reach and in sight from where the companion stands now. */
    public static boolean reachable(CompanionEntity owner, BlockPos cell) {
        return owner.level() instanceof ServerLevel level && CompanionShaftPlan.clearTo(level, owner.getEyePosition(), cell);
    }

    /** Places one sealing block into the cell. Nothing is consumed unless the block was set. */
    public static Result place(CompanionEntity owner, BlockPos cell) {
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(cell)) return Result.OCCUPIED;
        if (!replaceable(level, cell) || owner.getBoundingBox().intersects(new AABB(cell))) return Result.OCCUPIED;
        if (!reachable(owner, cell)) return Result.OUT_OF_REACH;
        int slot = slot(owner);
        if (slot < 0) return Result.NO_BLOCK;
        ItemStack stack = owner.getItem(slot);
        if (!(stack.getItem() instanceof BlockItem item)) return Result.NO_BLOCK;
        BlockState state = item.getBlock().defaultBlockState();
        if (!state.canSurvive(level, cell) || !level.isUnobstructed(state, cell, CollisionContext.empty())) return Result.OCCUPIED;
        owner.animateAction(1, cell, new ItemStack(item));
        if (!level.setBlock(cell, state, 3)) return Result.OCCUPIED;
        stack.shrink(1);
        level.playSound(null, cell, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
        owner.refreshInventorySummary();
        owner.setChanged();
        return Result.PLACED;
    }

    private static int slot(CompanionEntity owner) {
        for (Item wanted : PREFERENCE) {
            for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(wanted)) return slot;
        }
        return -1;
    }
}
