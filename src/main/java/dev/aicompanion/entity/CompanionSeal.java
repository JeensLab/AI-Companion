package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

/** Closes a cell with a real block from the bag, like a player plugging lava or water: item consumed, gesture, sound. */
public final class CompanionSeal {
    /** Eye to the centre of the cell, as for any block the companion works on from where he stands. */
    public static final double REACH = 4.5;
    /** A point just inside a face of the cell: a line that reaches it has entered the cell through an open side. */
    private static final double FACE = .45;
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

    /**
     * The centre of the cell is hidden, but a face of it against a solid neighbour is in reach and in sight: a player sets a block onto
     * the floor seen through a hole whose middle is hidden.
     */
    private static boolean faceReachable(CompanionEntity owner, BlockPos cell) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        Vec3 eye = owner.getEyePosition();
        for (Direction side : Direction.values()) {
            if (CompanionShaftPlan.open(level, cell.relative(side))) continue;
            Vec3 face = cell.getCenter().add(side.getStepX() * FACE, side.getStepY() * FACE, side.getStepZ() * FACE);
            if (eye.distanceTo(face) > REACH) continue;
            HitResult hit = level.clip(new ClipContext(eye, face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS || hit.getType() == HitResult.Type.BLOCK && ((BlockHitResult) hit).getBlockPos().equals(cell)) return true;
        }
        return false;
    }

    /** Places one sealing block into the cell. Nothing is consumed unless the block was set. */
    public static Result place(CompanionEntity owner, BlockPos cell) { return place(owner, cell, false); }

    /** The same, also onto a face of the cell in sight when its centre is hidden: the hole he has just made is plugged at once. */
    public static Result plug(CompanionEntity owner, BlockPos cell) { return place(owner, cell, true); }

    private static Result place(CompanionEntity owner, BlockPos cell, boolean byFace) {
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(cell)) return Result.OCCUPIED;
        if (!replaceable(level, cell) || owner.getBoundingBox().intersects(new AABB(cell))) return Result.OCCUPIED;
        if (!reachable(owner, cell) && !(byFace && faceReachable(owner, cell))) return Result.OUT_OF_REACH;
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
