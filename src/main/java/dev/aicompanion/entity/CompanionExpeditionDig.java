package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * The hands of the mine expedition: breaks one cell at a time with the right pickaxe, walks between neighbouring cells and
 * puts the drops where he can use them. Stone pickaxes dig rock, the best ore pickaxe digs ore. Drops of a cell are held back
 * until its column is done and then fall as one harvest, so a pickup does not pull him off the stand mid-column.
 */
final class CompanionExpeditionDig {
    enum Status { WORKING, DONE, NO_TOOL, NO_HAND, FULL, STUCK }

    /** Sealing blocks worth keeping in the bag; every other rock and the sealing blocks beyond them are left lying, untagged, so the work never waits for them. */
    static final int SEAL_RESERVE = 64;
    private static final int CELL_PATIENCE = 400, UNSEEN_PATIENCE = 40, WALK_PATIENCE = 100, SETTLED = 40;
    private static final double CENTRED = .35, RESTING = .1;

    private record Held(BlockPos pos, List<ItemStack> drops) { }

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final List<Held> held = new ArrayList<>();
    private BlockPos cell, walking;
    private int cellTicks, unseen, walkTicks;
    /** Blocks broken so far: a counter the stall watchdog sees move. */
    int broken;
    /** Cells walked into so far: the same for walking. */
    int walked;
    /** The last NO_TOOL was for an ore (it needs a better pickaxe), not for rock (it needs a stone pickaxe). */
    boolean lackedOre;

    CompanionExpeditionDig(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    boolean hasTool(BlockState state) { return toolFor(state) != null; }

    /** Forgets the cell being worked on, so a second try at it starts with fresh patience. */
    void forget() { cell = null; cellTicks = unseen = 0; }

    private Status done() {
        forget();
        return Status.DONE;
    }

    /** One tick of breaking the cell. DONE once it is open (broken now or before). */
    Status dig(ServerLevel level, BlockPos pos) {
        if (CompanionShaftPlan.open(level, pos)) return done();
        if (!pos.equals(cell)) { cell = pos.immutable(); cellTicks = unseen = 0; }
        if (++cellTicks > CELL_PATIENCE || !level.getFluidState(pos).isEmpty()) return Status.STUCK;
        BlockState state = level.getBlockState(pos);
        Item tool = toolFor(state);
        lackedOre = tool == null && state.is(Tags.Blocks.ORES);
        if (tool == null) return Status.NO_TOOL;
        if (!holdTool(tool)) return Status.NO_HAND;
        Vec3 eye = owner.getEyePosition();
        owner.getLookControl().setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (eye.distanceTo(pos.getCenter()) > CompanionSeal.REACH || !CompanionMiningNavigation.visible(owner, eye, pos)) {
            w.breaking.reset();
            return ++unseen > UNSEEN_PATIENCE ? Status.STUCK : Status.WORKING;
        }
        return w.breaking.tick(level, pos) ? harvest(level, pos, state) : Status.WORKING;
    }

    private Status harvest(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = owner.getMainHandItem();
        boolean ore = state.is(Tags.Blocks.ORES);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), owner, tool);
        if (ore && !w.items.canStore(drops)) return Status.FULL;
        if (!level.destroyBlock(pos, false, owner, 512)) return Status.STUCK;
        tool.getItem().mineBlock(tool, level, state, pos, owner);
        broken++;
        List<ItemStack> kept = new ArrayList<>(), spare = new ArrayList<>();
        int room = SEAL_RESERVE - CompanionSeal.available(owner) - heldCount();
        for (ItemStack stack : drops) {
            boolean keep = ore || CompanionSeal.seals(stack) && room > 0 && w.items.canStore(List.of(stack));
            if (keep) { kept.add(stack); if (!ore) room -= stack.getCount(); } else spare.add(stack);
        }
        if (!kept.isEmpty()) held.add(new Held(pos.immutable(), kept));
        toss(level, pos, spare);
        return done();
    }

    /** The held drops fall where their blocks stood, to be picked up like any harvest. */
    void flush() {
        for (Held drop : held) owner.dropHarvest(drop.pos(), drop.drops());
        held.clear();
    }

    private int heldCount() {
        int count = 0;
        for (Held drop : held) for (ItemStack stack : drop.drops()) count += stack.getCount();
        return count;
    }

    /** Spare rock: a real item in the world, thrown by him, so his pickup does not take it back and nothing waits for it. */
    private void toss(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemEntity item = new ItemEntity(level, pos.getX() + .5, pos.getY() + .35, pos.getZ() + .5, stack.copy());
            item.setThrower(owner);
            item.setDeltaMovement(0, RESTING, 0);
            item.setPickUpDelay(SETTLED);
            level.addFreshEntity(item);
        }
    }

    private boolean holdTool(Item tool) {
        return owner.getMainHandItem().is(tool) || owner.equipForWork(BuiltInRegistries.ITEM.getKey(tool).toString()).success();
    }

    /** Rock and soil with stone pickaxes only; ore with the cheapest pickaxe that gets its drop. */
    private Item toolFor(BlockState state) {
        if (!state.is(Tags.Blocks.ORES)) return w.items.countCarried(Items.STONE_PICKAXE) > 0 ? Items.STONE_PICKAXE : null;
        Item best = null;
        int least = Integer.MAX_VALUE;
        for (ItemStack stack : w.items.carriedItems()) {
            if (!stack.is(ItemTags.PICKAXES) || !stack.isCorrectToolForDrops(state) || stack.getMaxDamage() >= least) continue;
            best = stack.getItem();
            least = stack.getMaxDamage();
        }
        return best;
    }

    /** Steps towards the cell with the move control (neighbouring cells of his own mine); true once he stands in it. */
    boolean walkTo(BlockPos to) {
        boolean inside = owner.onGround() && owner.blockPosition().equals(to);
        if (inside && (centred(to) || to.equals(walking) && walkTicks > WALK_PATIENCE / 2)) { walking = null; walkTicks = 0; walked++; return true; }
        if (!to.equals(walking)) { walking = to.immutable(); walkTicks = 0; }
        walkTicks++;
        Vec3 centre = Vec3.atBottomCenterOf(to);
        owner.getMoveControl().setWantedPosition(centre.x, centre.y, centre.z, 1);
        return false;
    }

    boolean walkStuck() { return walkTicks > WALK_PATIENCE; }

    private boolean centred(BlockPos to) {
        Vec3 centre = Vec3.atBottomCenterOf(to);
        double dx = owner.getX() - centre.x, dz = owner.getZ() - centre.z;
        return dx * dx + dz * dz < CENTRED * CENTRED;
    }

    void reset() {
        flush();
        cell = walking = null;
        cellTicks = unseen = walkTicks = 0;
        w.breaking.reset();
    }
}
