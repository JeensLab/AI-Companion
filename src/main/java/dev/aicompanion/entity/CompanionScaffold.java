package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Scaffolding the way a player does it: a column of dirt jumped up under his own feet, to reach the walls and roof that are out of
 * reach from the ground, and taken down from the top right after. The dirt comes back to the bag as drops. Ported in idea from the
 * scaffold of PR #6 (0r1k); only dirt, one column at a time, and the building owns the decision where and how high.
 */
final class CompanionScaffold {
    enum Outcome { WORKING, READY, FAILED }
    static final int MAX_HEIGHT = 10;
    static final List<Item> MATERIALS = List.of(Items.DIRT, Items.COARSE_DIRT);
    private static final int CLIMB_TICKS = 20 * 45;
    private final CompanionEntity owner;
    private final CompanionBlockBreaking breaking;
    /** Placed blocks, bottom first, with the state each was set down as (null after a load): a changed block is not his any more. */
    private final List<BlockPos> blocks = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();
    private BlockPos base;
    private int height;
    private BlockPos jumpCell;
    private int jumpTicks, failedJumps, cooldown, ticks, idle, walkTicks;
    private Path approach;
    private boolean descending;
    private String failure = "";
    /** The last climb ended because he was found below the column (pushed, fell, knocked off): the building goes on. */
    private boolean slipped;
    /** Dirt that lay where its block was because the bag was full on the way down, and where the last of it lies (said once, by takeNote). */
    private int leftDirt;
    private BlockPos leftAt;
    /** What the building itself still needs of a material is not stood on: reserved items count only above that amount. */
    private ToIntFunction<Item> reserved = item -> 0;

    CompanionScaffold(CompanionEntity owner) { this.owner = owner; breaking = new CompanionBlockBreaking(owner); }

    boolean climbing() { return base != null && !descending; }
    boolean descending() { return descending; }
    boolean hasBlocks() { return !blocks.isEmpty(); }
    /** True while the climb or the descent runs: it needs a tick even in the air, between two jumps. */
    boolean busy() { return base != null || descending; }
    List<BlockPos> blocks() { return List.copyOf(blocks); }
    String failure() { return failure; }
    boolean slipped() { return slipped; }
    /** What the descent left on the ground (the bag was full), in words for the player; empty when it left nothing. Said once. */
    String takeNote() {
        if (leftDirt == 0) return "";
        String note = "В сумке не было места: " + leftDirt + " блоков земли с лесов лежат на земле у " + leftAt.toShortString() + ", подбери их.";
        leftDirt = 0; leftAt = null;
        return note;
    }
    boolean owns(BlockPos pos) { return blocks.contains(pos); }

    /** How much dirt he can stand on: what he carries above what the building still needs. */
    static int supply(CompanionEntity owner, ToIntFunction<Item> reserved) {
        int count = 0;
        for (Item item : MATERIALS) count += Math.max(0, carried(owner, item) - reserved.applyAsInt(item));
        return count;
    }
    private static int carried(CompanionEntity owner, Item item) {
        int count = owner.getMainHandItem().is(item) ? owner.getMainHandItem().getCount() : 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) count += owner.getItem(slot).getCount();
        return count;
    }

    /** Starts a column of {@code levels} blocks whose first block stands on the ground cell {@code feet}. */
    void begin(BlockPos feet, int levels, ToIntFunction<Item> reserve) {
        reserved = reserve;
        base = feet.immutable(); height = Math.min(MAX_HEIGHT, levels);
        jumpCell = null; jumpTicks = failedJumps = cooldown = ticks = 0; approach = null; descending = false; failure = ""; slipped = false;
    }

    void startDescend() { descending = !blocks.isEmpty(); base = descending ? blocks.getFirst() : null; jumpCell = null; approach = null; idle = walkTicks = 0; breaking.reset(); }

    void reset() { breaking.reset(); jumpCell = null; approach = null; if (blocks.isEmpty()) { base = null; descending = false; } }

    Outcome climb(ServerLevel level) {
        if (++ticks > CLIMB_TICKS) return fail("Не получилось подняться на леса.");
        int surface = base.getY() + blocks.size();
        if (cooldown > 0) cooldown--;
        if (jumpCell != null) return jump(level);
        double horizontal = horizontalDistanceSqr();
        if (horizontal > .0144D || Math.abs(owner.getY() - surface) > .2D) {
            if (!blocks.isEmpty() && owner.getY() < surface - .2D) { slipped = true; return fail("Соскользнул с лесов."); }
            if (blocks.isEmpty() && (horizontal > 2.25D || Math.abs(owner.getY() - surface) > .2D)) {
                if (approach == null || owner.getNavigation().isDone() && ticks % 20 == 0) {
                    int busy = owner.work.route.busyAnswers();
                    approach = owner.work.route.toCells(Set.of(base));
                    // This tick's cells went elsewhere: asked again next tick, not a failure.
                    if (approach == null && owner.work.route.busyAnswers() != busy) return Outcome.WORKING;
                }
                if (approach == null) return fail("К месту для лесов не пройти.");
                owner.work.route.walk(approach);
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
        if (!holdMaterial()) return fail("Для лесов нужна земля в сумке.");
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
            owner.work.progressed();
            owner.rememberPlaced(jumpCell);
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

    /**
     * Takes the column down: from its top when he stands on it, else from the ground, the top-most block within reach first (dirt does not
     * fall, so the order is free). Away from it he walks back first. READY when the column is gone.
     */
    Outcome descend(ServerLevel level) {
        // What somebody broke or replaced is not his block any more, and never a free item.
        for (int index = blocks.size() - 1; index >= 0; index--) {
            BlockPos pos = blocks.get(index);
            if (!level.hasChunkAt(pos)) continue;
            BlockState actual = level.getBlockState(pos), recorded = states.get(index);
            // Loaded from the save before its chunk: the block is recognised by its material. Grass and mycelium spread onto dirt: still his.
            boolean ours = recorded == null ? MATERIALS.contains(actual.getBlock().asItem()) || spreads(actual) : recorded == actual || spreads(recorded) && spreads(actual);
            if (ours) states.set(index, actual); else { blocks.remove(index); states.remove(index); }
        }
        if (blocks.isEmpty()) { descending = false; base = null; breaking.reset(); return Outcome.READY; }
        if (!owner.onGround()) return Outcome.WORKING;
        if (cooldown > 0) { cooldown--; return Outcome.WORKING; }
        int reach = -1;
        for (int index = blocks.size() - 1; index >= 0; index--)
            if (level.hasChunkAt(blocks.get(index)) && owner.getEyePosition().distanceToSqr(blocks.get(index).getCenter()) <= 4.5D * 4.5D) { reach = index; break; }
        if (reach < 0) return walkBack(level);
        idle = 0; approach = null;
        owner.getNavigation().stop();
        BlockPos top = blocks.get(reach);
        BlockState state = states.get(reach);
        owner.getLookControl().setLookAt(top.getX() + .5D, top.getY() + .5D, top.getZ() + .5D);
        owner.selectToolForBlock(state);
        if (!breaking.tick(level, top)) return Outcome.WORKING;
        ItemStack tool = owner.getMainHandItem();
        var drops = CompanionTools.drops(state, level, top, null, owner, tool);
        // A drop that does not fit the bag would lie on the ground and go: the column stays, saved for «дострой» (as the site clearing does).
        // Not with him high up: the block under his feet is the first to break, and to stop there leaves him stranded. It comes down all the same,
        // the dirt lies where it was, and the end of the work says how much and where.
        if (!owner.canStoreDrops(drops)) {
            if (owner.getY() - blocks.getFirst().getY() <= 1.1D) {
                descending = false; base = null; approach = null; breaking.reset();
                failure = "В сумке нет места для земли, которую снимаю с лесов («" + top.toShortString() + "»). Освободи место.";
                return Outcome.FAILED;
            }
            leftDirt += drops.stream().mapToInt(ItemStack::getCount).sum(); leftAt = top;
        }
        if (!level.destroyBlock(top, false, owner, 512)) return Outcome.WORKING;
        tool.getItem().mineBlock(tool, level, state, top, owner);
        owner.swing(InteractionHand.MAIN_HAND);
        owner.dropHarvest(top, drops);
        breaking.reset();
        owner.work.progressed();
        blocks.remove(reach); states.remove(reach);
        cooldown = 4;
        return Outcome.WORKING;
    }

    /** Dirt, or what grows over it (grass, mycelium): a column's block may have turned into it. */
    private static boolean spreads(BlockState state) { return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM); }

    /** Out of reach of every block (stopped, led away, loaded elsewhere): the way to the cells beside the foot of the column. */
    private Outcome walkBack(ServerLevel level) {
        breaking.reset();
        if (owner.getNavigation().isDone() && ++idle > 40 && approach != null || ++walkTicks > CLIMB_TICKS) return giveUp();
        if (approach == null || owner.getNavigation().isDone()) {
            BlockPos foot = blocks.getFirst();
            Set<BlockPos> cells = new HashSet<>();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = foot.offset(dx, dy, dz);
                if (blocks.contains(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
                if (!level.noCollision(owner, new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2, pos.getX() + .8, pos.getY() + owner.getBbHeight(), pos.getZ() + .8))) continue;
                cells.add(pos);
            }
            int busy = owner.work.route.busyAnswers();
            approach = owner.work.route.toCells(cells);
            // This tick's cells went elsewhere: asked again next tick, not a failure.
            if (approach == null && owner.work.route.busyAnswers() != busy) return Outcome.WORKING;
            if (approach == null) return giveUp();
        }
        owner.work.route.walk(approach);
        return Outcome.WORKING;
    }

    /** What cannot be reached stays, and is said. */
    private Outcome giveUp() {
        descending = false; base = null; approach = null; idle = walkTicks = 0; breaking.reset();
        failure = "Остались мои леса из земли на " + blocks.getLast().toShortString() + ".";
        return Outcome.FAILED;
    }

    private boolean holdMaterial() {
        Item hand = owner.getMainHandItem().getItem();
        if (MATERIALS.contains(hand) && carried(owner, hand) > reserved.applyAsInt(hand)) return true;
        for (Item item : MATERIALS) if (carried(owner, item) > reserved.applyAsInt(item))
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

    /** The positions of what stands now, so that a stop and a reload can take it down. */
    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : blocks) list.add(LongTag.valueOf(pos.asLong()));
        tag.put("Scaffold", list);
    }
    void load(CompoundTag tag) {
        forget();
        for (Tag entry : tag.getList("Scaffold", Tag.TAG_LONG)) { blocks.add(BlockPos.of(((LongTag) entry).getAsLong())); states.add(null); }
    }
    void forget() { blocks.clear(); states.clear(); base = null; descending = false; jumpCell = null; breaking.reset(); }
}
