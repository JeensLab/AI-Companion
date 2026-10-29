package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Flint mode of the digging goal. A gravel block drops flint one time in ten, so like a player he digs a gravel block, sets one
 * gravel from the bag down on a safe cell within reach and digs it again. Everything is a real item: the block comes out of the
 * bag, and the natural gravel is only spent where the flint itself came from.
 */
final class CompanionFlint {
    static final int MAX_ORDER = 8;
    /** About 96% for one flint at one in ten per break. */
    private static final int BREAKS_PER_FLINT = 30;
    private static final int TIMEOUT_BASE_TICKS = 20 * 60;
    private static final int TIMEOUT_PER_FLINT_TICKS = 20 * 100;
    private static final int SPOT_RADIUS = 1;

    private final CompanionEntity c;
    /** Cells he put gravel on; one that was dug, or is gone, drops out of the list. */
    private final Set<BlockPos> placed = new LinkedHashSet<>();
    private int breaks;
    private int flintAtStart;

    CompanionFlint(CompanionEntity c) { this.c = c; }

    void begin() { placed.clear(); breaks = 0; flintAtStart = CompanionGround.FLINT.count(c); }

    static int timeoutTicks(int ordered) { return TIMEOUT_BASE_TICKS + TIMEOUT_PER_FLINT_TICKS * ordered; }

    void countBreak() { breaks++; }
    boolean exhausted(int ordered) { return breaks >= BREAKS_PER_FLINT * ordered; }
    int found() { return Math.max(0, CompanionGround.FLINT.count(c) - flintAtStart); }

    String report(int ordered, String why) { return "Выпало " + found() + " из " + ordered + " кремня: " + why; }
    String exhaustedReport(int ordered) { return report(ordered, "разбил " + breaks + " блоков гравия, но кремень больше не выпал. Попроси ещё раз."); }

    boolean carriesGravel() { return gravelSlot() >= 0; }

    /** The nearest gravel block he put down himself that is still standing and safe to dig. */
    BlockPos placedTarget(ServerLevel level, Set<BlockPos> unreachable) {
        placed.removeIf(pos -> !level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.GRAVEL));
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : placed) {
            double distance = c.distanceToSqr(Vec3.atCenterOf(pos));
            if (!unreachable.contains(pos) && distance < bestDistance) { best = pos; bestDistance = distance; }
        }
        return best;
    }

    /** Sets one gravel from the bag down; null when there is none or no good cell. */
    BlockPos place(ServerLevel level) {
        int slot = gravelSlot();
        BlockPos spot = slot < 0 ? null : findSpot(level);
        if (spot == null || !level.setBlock(spot, Blocks.GRAVEL.defaultBlockState(), 3)) return null;
        ItemStack stack = c.companionInventory.get(slot);
        c.animateAction(1, spot, stack);
        stack.shrink(1);
        c.refreshInventorySummary();
        level.playSound(null, spot, Blocks.GRAVEL.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
        placed.add(spot.immutable());
        return spot;
    }

    /** A Silk Touch shovel drops the block itself, never flint: the best other shovel goes into the hand, else bare hands. */
    void arm() {
        ItemStack hand = c.getMainHandItem();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        double speed = hand.is(ItemTags.SHOVELS) && !silkTouch(hand) ? hand.getDestroySpeed(gravel) : -1;
        int best = -1;
        for (int slot = 0; slot < c.companionInventory.size(); slot++) {
            ItemStack stack = c.companionInventory.get(slot);
            if (stack.is(ItemTags.SHOVELS) && !silkTouch(stack) && stack.getDestroySpeed(gravel) > speed) { best = slot; speed = stack.getDestroySpeed(gravel); }
        }
        if (best >= 0) {
            ItemStack chosen = c.companionInventory.get(best);
            c.companionInventory.set(best, hand);
            c.setItemSlot(EquipmentSlot.MAINHAND, chosen);
            c.refreshInventorySummary();
        } else if (silkTouch(hand)) c.unequipForSupport("mainhand");
    }

    private boolean silkTouch(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(c.level().registryAccess().holderOrThrow(Enchantments.SILK_TOUCH), stack) > 0;
    }

    private int gravelSlot() {
        for (int slot = 0; slot < c.companionInventory.size(); slot++) if (c.companionInventory.get(slot).is(Items.GRAVEL)) return slot;
        return -1;
    }

    /** The nearest cell where a gravel block can be dug at once after: the same reach, sight and drop checks as for natural gravel. */
    private BlockPos findSpot(ServerLevel level) {
        BlockPos origin = c.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SPOT_RADIUS, -1, -SPOT_RADIUS), origin.offset(SPOT_RADIUS, 2, SPOT_RADIUS))) {
            double distance = c.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance && fits(level, pos)) { best = pos.immutable(); bestDistance = distance; }
        }
        return best;
    }

    /** Solid ground that does not fall under it, nothing standing in it or under it, no water beside it, in reach and in sight. */
    private boolean fits(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !c.leashAllows(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;
        BlockPos below = pos.below();
        BlockState support = level.getBlockState(below);
        if (!support.isFaceSturdy(level, below, Direction.UP) || support.getBlock() instanceof FallingBlock) return false;
        if (c.distanceToSqr(Vec3.atCenterOf(pos)) > CompanionEntity.LOG_INTERACTION_RANGE_SQR) return false;
        if (!level.getEntities((Entity)null, new AABB(pos), entity -> true).isEmpty()) return false;
        var sight = level.clip(new ClipContext(c.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        return sight.getType() == HitResult.Type.MISS && CompanionMiningNavigation.dropCollectable(level, pos, c.blockPosition())
                && CompanionMiningNavigation.preservesAutonomousReturn(c, pos) && CompanionGround.safeLooseCell(level, pos, c);
    }
}
