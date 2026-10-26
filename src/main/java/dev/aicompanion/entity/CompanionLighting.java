package dev.aicompanion.entity;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;

/** Real torches from the bag, for a dark path: no replacement of blocks, no chat, no free items. */
public final class CompanionLighting {
    /** Keep the floor and walls carrying existing path lights, including lights placed by the player. */
    static boolean supportsLight(net.minecraft.world.level.Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            var state = level.getBlockState(pos.relative(side));
            if (side == Direction.UP && state.is(Blocks.TORCH)) return true;
            if (state.is(Blocks.WALL_TORCH) && state.getValue(net.minecraft.world.level.block.WallTorchBlock.FACING) == side) return true;
        }
        return false;
    }
    public static final int DARK = 7, SPACING = 6, COOLDOWN = 20 * 3;
    private final CompanionEntity owner;
    private long nextPlacement;
    private int freeTicks;

    CompanionLighting(CompanionEntity owner) { this.owner = owner; }

    void tick() {
        if (owner.hasActiveWorkOrder() || owner.isGatheringTask()) freeTicks = 0; else freeTicks++;
        if (owner.tickCount % 20 != 0 || !(owner.level() instanceof ServerLevel level)
                || !owner.canCollectItems() || !owner.onGround() || owner.isSelfRescuing() || owner.getTarget() != null
                || !(owner.isFollowing() || owner.isRoaming() || owner.hasActiveWorkOrder() || owner.autonomy().staysAtWork())
                || owner.getOwnerPlayer() == null || level.getGameTime() < nextPlacement || !dark(level)) return;
        BlockPos place = place(level);
        if (place == null) return;
        int slot = torchSlot();
        // Do not consume a just-completed order's coal before its workId result is observed.
        if (slot < 0 && freeTicks >= 100 && hasFuel() && hasStickMaterials()) {
            var result = owner.craftFromBag("minecraft:torch", 4);
            if (result.success()) slot = torchSlot();
            else nextPlacement = level.getGameTime() + COOLDOWN;
        }
        if (slot < 0) return;
        var state = Blocks.TORCH.defaultBlockState();
        if (!level.isEmptyBlock(place) || !state.canSurvive(level, place) || !level.setBlock(place, state, 3)) return;
        owner.getItem(slot).shrink(1); owner.setChanged();
        owner.animateAction(1, place, new ItemStack(Items.TORCH));
        var sound = state.getSoundType();
        level.playSound(null, place, sound.getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
        nextPlacement = level.getGameTime() + COOLDOWN;
        CompanionDiagnostics.event(owner.diagnosticTrace(), "path_torch_placed", "entity", owner.getUUID(), "position", place,
                "brightness", level.getMaxLocalRawBrightness(owner.blockPosition()));
    }

    public boolean dark(ServerLevel level) { return level.getMaxLocalRawBrightness(owner.blockPosition()) <= DARK; }
    private boolean hasFuel() {
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(ItemTags.COALS)) return true;
        return false;
    }
    private int torchSlot() {
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(Items.TORCH)) return slot;
        return -1;
    }
    private boolean hasStickMaterials() {
        int planks = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            var stack = owner.getItem(slot);
            if (stack.is(Items.STICK) || stack.is(ItemTags.LOGS)) return true;
            if (stack.is(ItemTags.PLANKS)) planks += stack.getCount();
        }
        return planks >= 2;
    }
    private BlockPos place(ServerLevel level) {
        BlockPos here = owner.blockPosition();
        java.util.List<BlockPos> torches = new java.util.ArrayList<>();
        int scan = SPACING + 2; // A candidate can be two blocks closer to an existing torch than Oleg is.
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-scan,-3,-scan), here.offset(scan,3,scan))) {
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                torches.add(pos.immutable());
                if (pos.distSqr(here) < SPACING * SPACING) return null;
            }
        }
        for (int radius = 1; radius <= 2; radius++) for (Direction side : Direction.Plane.HORIZONTAL) for (int dy : new int[]{0,-1,1}) {
            BlockPos pos = here.relative(side, radius).above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.isEmptyBlock(pos)
                    || !level.getFluidState(pos).isEmpty() || !Blocks.TORCH.defaultBlockState().canSurvive(level, pos)) continue;
            if (torches.stream().anyMatch(torch -> torch.distSqr(pos) < SPACING * SPACING)) continue;
            var hit = level.clip(new ClipContext(owner.getEyePosition(), pos.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.MISS) return pos.immutable();
        }
        return null;
    }
}
