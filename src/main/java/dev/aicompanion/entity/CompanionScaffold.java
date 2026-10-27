package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Scaffolding the way a player does it: a column of dirt or cobblestone jumped up under his own feet, to reach the walls and roof
 * that are out of reach from the ground, and taken down from the top right after. The blocks come back to the bag as drops.
 */
final class CompanionScaffold {
    enum Outcome { WORKING, READY, FAILED }
    static final int MAX_HEIGHT = 12;
    /** In the order they are taken: dirt first, building stone last. */
    static final List<Item> MATERIALS = List.of(Items.DIRT, Items.COARSE_DIRT, Items.NETHERRACK, Items.COBBLED_DEEPSLATE, Items.COBBLESTONE,
            Items.TUFF, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.BLACKSTONE);
    private static final int CLIMB_TICKS = 20 * 45;
    private final CompanionEntity owner;
    private final CompanionBlockBreaking breaking;
    /** Placed blocks, bottom first, with the state each was set down as: a changed block is not his any more. */
    private final List<BlockPos> blocks = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();
    private BlockPos base;
    private int height;
    private BlockPos jumpCell;
    private int jumpTicks, failedJumps, cooldown, ticks;
    private Path approach;
    private boolean descending;
    private String failure = "";

    CompanionScaffold(CompanionEntity owner) { this.owner = owner; breaking = new CompanionBlockBreaking(owner); }

    boolean climbing() { return base != null && !descending; }
    boolean descending() { return descending; }
    boolean hasBlocks() { return !blocks.isEmpty(); }
    List<BlockPos> blocks() { return List.copyOf(blocks); }
    String failure() { return failure; }
    boolean owns(BlockPos pos) { return blocks.contains(pos); }

    /** What the building itself still needs of a material is not stood on: reserved items count only above that amount. */
    private ToIntFunction<Item> reserved = item -> 0;

    static int supply(CompanionEntity owner, ToIntFunction<Item> reserved) {
        int count = 0;
        for (Item item : MATERIALS) if (usable(owner, item)) count += Math.max(0, carried(owner, item) - reserved.applyAsInt(item));
        return count;
    }
    /**
     * Stone is stood on only with a pickaxe in the bag: by hand each block of it takes ten seconds to take down again (review of
     * PR #6: a cobblestone scaffold under a 7×7 roof took five minutes). Dirt goes by hand in a moment.
     */
    private static boolean usable(CompanionEntity owner, Item item) {
        if (item == Items.DIRT || item == Items.COARSE_DIRT) return true;
        if (owner.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(net.minecraft.tags.ItemTags.PICKAXES)) return true;
        return false;
    }
    private static int carried(CompanionEntity owner, Item item) {
        int count = owner.getMainHandItem().is(item) ? owner.getMainHandItem().getCount() : 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) count += owner.getItem(slot).getCount();
        return count;
    }

    void begin(BlockPos feet, int levels, ToIntFunction<Item> reserve) {
        reserved = reserve;
        base = feet.immutable(); height = Math.min(MAX_HEIGHT, levels);
        jumpCell = null; jumpTicks = failedJumps = cooldown = ticks = 0; approach = null; descending = false; failure = "";
    }

    void startDescend() { descending = !blocks.isEmpty(); base = descending ? blocks.getFirst() : null; jumpCell = null; breaking.reset(); }

    void reset() { breaking.reset(); jumpCell = null; approach = null; if (blocks.isEmpty()) { base = null; descending = false; } }

    Outcome climb(ServerLevel level) {
        if (++ticks > CLIMB_TICKS) return fail("Не получилось подняться на леса.");
        int surface = base.getY() + blocks.size();
        if (cooldown > 0) cooldown--;
        if (jumpCell != null) return jump(level);
        double horizontal = horizontalDistanceSqr();
        if (horizontal > .0144D || Math.abs(owner.getY() - surface) > .2D) {
            if (!blocks.isEmpty() && owner.getY() < surface - .2D) return fail("Соскользнул с лесов.");
            if (blocks.isEmpty() && (horizontal > 2.25D || Math.abs(owner.getY() - surface) > .2D)) {
                if (approach == null || owner.getNavigation().isDone() && ticks % 20 == 0) approach = owner.getNavigation().createPath(base, 0);
                if (approach == null || !approach.canReach()) return fail("К месту для лесов не пройти.");
                CompanionMiningNavigation.walk(owner, approach);
            } else {
                owner.getNavigation().stop();
                owner.getMoveControl().setWantedPosition(base.getX() + .5D, surface, base.getZ() + .5D, .6D);
            }
            return Outcome.WORKING;
        }
        owner.getNavigation().stop();
        if (!owner.onGround()) return Outcome.WORKING;
        if (blocks.size() >= height) return Outcome.READY;
        if (cooldown > 0) return Outcome.WORKING;
        BlockPos cell = base.above(blocks.size());
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos above = cell.above(dy);
            if (!level.hasChunkAt(above) || !level.getFluidState(above).isEmpty() || !level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                    || dy == 0 && !level.getBlockState(above).canBeReplaced()) return fail("Над лесами нет места.");
        }
        if (!holdMaterial()) return fail("Для лесов нужна земля или булыжник в сумке.");
        Vec3 motion = owner.getDeltaMovement();
        owner.setDeltaMovement(motion.x * .15D, motion.y, motion.z * .15D);
        jumpCell = cell.immutable(); jumpTicks = 0;
        owner.getJumpControl().jump();
        return Outcome.WORKING;
    }

    private Outcome jump(ServerLevel level) {
        owner.getNavigation().stop();
        owner.getMoveControl().setWantedPosition(base.getX() + .5D, owner.getY(), base.getZ() + .5D, .35D);
        jumpTicks++;
        AABB cell = new AABB(jumpCell);
        if (owner.getBoundingBox().minY >= jumpCell.getY() + 1.001D && !owner.getBoundingBox().intersects(cell)) {
            if (!holdMaterial() || !level.getBlockState(jumpCell).canBeReplaced() || !level.getFluidState(jumpCell).isEmpty()
                    || !level.getEntities(owner, cell, entity -> entity.isAlive() && entity.blocksBuilding).isEmpty()) return fail("Место для лесов занято.");
            ItemStack hand = owner.getMainHandItem();
            BlockState state = ((BlockItem) hand.getItem()).getBlock().defaultBlockState();
            if (!level.setBlockAndUpdate(jumpCell, state)) return fail("Не удалось поставить леса.");
            blocks.add(jumpCell.immutable()); states.add(state);
            owner.animateAction(1, jumpCell, hand.copy());
            hand.shrink(1);
            if (hand.isEmpty()) owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            owner.setChanged();
            owner.swing(InteractionHand.MAIN_HAND);
            var sound = state.getSoundType(level, jumpCell, owner);
            level.playSound(null, jumpCell, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) * .5F, sound.getPitch() * .8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, jumpCell, GameEvent.Context.of(owner, state));
            jumpCell = null; cooldown = 6; failedJumps = 0;
            return Outcome.WORKING;
        }
        if (jumpTicks > 28 && owner.onGround()) {
            jumpCell = null; cooldown = 6;
            if (++failedJumps >= 3) return fail("Здесь не получается подпрыгнуть.");
        }
        return Outcome.WORKING;
    }

    /** From the top down, standing on each block while breaking it. READY when the column is gone. */
    Outcome descend(ServerLevel level) {
        while (!blocks.isEmpty()) {
            BlockPos top = blocks.getLast();
            if (!level.hasChunkAt(top)) return Outcome.WORKING;
            BlockState actual = level.getBlockState(top);
            // Loaded from the save before its chunk: the block is recognised by its material.
            if (states.getLast() == null && MATERIALS.contains(actual.getBlock().asItem())) states.set(states.size() - 1, actual);
            // Somebody broke or replaced it: not his block any more, and never a free item.
            if (actual != states.getLast()) { blocks.removeLast(); states.removeLast(); continue; }
            break;
        }
        if (blocks.isEmpty()) { descending = false; base = null; breaking.reset(); return Outcome.READY; }
        BlockPos top = blocks.getLast();
        BlockState state = states.getLast();
        if (!owner.onGround()) return Outcome.WORKING;
        if (cooldown > 0) { cooldown--; return Outcome.WORKING; }
        owner.getNavigation().stop();
        boolean standing = Math.abs(owner.getBoundingBox().minY - (top.getY() + 1D)) < .15D && overlapsColumn(owner.getBoundingBox(), top);
        if (!standing && owner.getEyePosition().distanceToSqr(top.getCenter()) > 4.5D * 4.5D) {
            // Pushed off or reloaded somewhere else: what cannot be reached from here stays, and is said.
            descending = false; base = null; breaking.reset();
            failure = "Остались мои леса на " + top.toShortString() + ".";
            return Outcome.FAILED;
        }
        owner.getLookControl().setLookAt(top.getX() + .5D, top.getY() + .5D, top.getZ() + .5D);
        owner.selectToolForBlock(state);
        if (!breaking.tick(level, top)) return Outcome.WORKING;
        ItemStack tool = owner.getMainHandItem();
        var drops = Block.getDrops(state, level, top, null, owner, tool);
        if (!level.destroyBlock(top, false, owner, 512)) return Outcome.WORKING;
        tool.getItem().mineBlock(tool, level, state, top, owner);
        owner.swing(InteractionHand.MAIN_HAND);
        owner.dropHarvest(top, drops);
        breaking.reset();
        blocks.removeLast(); states.removeLast();
        cooldown = 4;
        return Outcome.WORKING;
    }

    private boolean holdMaterial() {
        Item hand = owner.getMainHandItem().getItem();
        if (MATERIALS.contains(hand) && usable(owner, hand) && carried(owner, hand) > reserved.applyAsInt(hand)) return true;
        for (Item item : MATERIALS) if (usable(owner, item) && carried(owner, item) > reserved.applyAsInt(item))
            return owner.equipForWork(BuiltInRegistries.ITEM.getKey(item).toString()).success() && owner.getMainHandItem().getItem() instanceof BlockItem;
        return false;
    }

    private Outcome fail(String reason) {
        failure = reason; jumpCell = null; approach = null;
        owner.getNavigation().stop();
        if (blocks.isEmpty()) base = null;
        else startDescend();
        return Outcome.FAILED;
    }

    private double horizontalDistanceSqr() {
        double dx = owner.getX() - (base.getX() + .5D), dz = owner.getZ() - (base.getZ() + .5D);
        return dx * dx + dz * dz;
    }
    private static boolean overlapsColumn(AABB box, BlockPos pos) {
        return box.maxX > pos.getX() && box.minX < pos.getX() + 1 && box.maxZ > pos.getZ() && box.minZ < pos.getZ() + 1;
    }

    void save(CompoundTag tag) {
        var list = new net.minecraft.nbt.ListTag();
        for (BlockPos pos : blocks) list.add(LongTag.valueOf(pos.asLong()));
        tag.put("Scaffold", list);
    }
    void load(CompoundTag tag) {
        blocks.clear(); states.clear(); base = null; descending = false; breaking.reset();
        for (Tag entry : tag.getList("Scaffold", Tag.TAG_LONG)) { blocks.add(BlockPos.of(((LongTag) entry).getAsLong())); states.add(null); }
    }
}
