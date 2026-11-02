package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hands of the mine expedition: breaks one cell at a time with the right pickaxe, walks between neighbouring cells and
 * puts the drops where he can use them. Stone pickaxes dig rock, the best ore pickaxe digs ore. Sealing blocks are held back
 * until the column is done and then fall as one harvest, so a pickup does not pull him off the stand mid-column.
 */
final class CompanionExpeditionDig {
    enum Status { WORKING, DONE, NO_TOOL, NO_HAND, FULL, STUCK }

    /** Sealing blocks worth keeping in the bag; every other rock and the sealing blocks beyond them are left lying, untagged, so the work never waits for them. */
    static final int SEAL_RESERVE = 64;
    private static final int CELL_PATIENCE = 400, UNSEEN_PATIENCE = 40, WALK_PATIENCE = 100, SETTLED = 40, PATH_AFTER = 20, SPARE_DURABILITY = 60;
    private static final double CENTRED = .35, RESTING = .1;
    /** A creeper this close to a block of his own is waited out before the block comes out: opened, it would be at his side before the block could be set again. */
    private static final double CREEPER_CLEAR = 3;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private BlockPos cell, walking;
    private int cellTicks, unseen, walkTicks;
    /** Why the last {@link Status#STUCK} was said, for the diagnostics. */
    private String stuckWhy = "";
    private List<BlockPos> around = List.of();
    private BlockPos reaching;
    private int aroundIndex;
    private boolean tried;
    /** Blocks broken so far: a counter the stall watchdog sees move. */
    int broken;
    /** The cell broken last, to see what it opened onto. */
    BlockPos lastBroken;
    /** Ore cells he mined himself: the hole one leaves is his, not a cave. */
    final Set<BlockPos> minedOre = new HashSet<>();
    /** Cells walked into so far: the same for walking. */
    int walked;
    /** The last NO_TOOL was for an ore (it needs a better pickaxe), not for rock (it needs a stone pickaxe). */
    boolean lackedOre;
    /** Stranded under the ground with no stone pickaxe: the rock is dug with the iron one while it has wear to spare. */
    boolean ironForRock;
    private int ironReserve = SPARE_DURABILITY;

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
        if (++cellTicks > CELL_PATIENCE) return stuck("patience");
        if (!level.getFluidState(pos).isEmpty()) return stuck("fluid");
        BlockState state = level.getBlockState(pos);
        Item tool = toolFor(state);
        lackedOre = tool == null && state.is(Tags.Blocks.ORES);
        if (tool == null) return Status.NO_TOOL;
        if (!holdTool(tool)) return Status.NO_HAND;
        Vec3 eye = owner.getEyePosition();
        owner.getLookControl().setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (eye.distanceTo(pos.getCenter()) > CompanionSeal.REACH || !CompanionMiningNavigation.visible(owner, eye, pos)) {
            w.breaking.reset();
            return ++unseen > UNSEEN_PATIENCE ? stuck(eye.distanceTo(pos.getCenter()) > CompanionSeal.REACH ? "reach" : "unseen") : Status.WORKING;
        }
        return w.breaking.tick(level, pos) ? harvest(level, pos, state) : Status.WORKING;
    }

    private Status stuck(String why) {
        stuckWhy = why;
        return Status.STUCK;
    }

    private Status harvest(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = owner.getMainHandItem();
        boolean ore = state.is(Tags.Blocks.ORES);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), owner, tool);
        if (ore && !w.items.canStore(drops)) return Status.FULL;
        if (!level.destroyBlock(pos, false, owner, 512)) return stuck("destroy_refused");
        tool.getItem().mineBlock(tool, level, state, pos, owner);
        broken++;
        lastBroken = pos.immutable();
        if (ore) minedOre.add(lastBroken);
        // Ore falls at once: a save or quit mid-column must not lose a diamond. Only sealing blocks wait, for the pickup reason above.
        if (ore) {
            owner.dropHarvest(lastBroken, drops);
            return done();
        }
        List<ItemStack> kept = new ArrayList<>(), spare = new ArrayList<>();
        int room = SEAL_RESERVE - CompanionSeal.available(owner) - w.mine.heldCount();
        for (ItemStack stack : drops) {
            if (CompanionSeal.seals(stack) && room > 0 && w.items.canStore(List.of(stack))) { kept.add(stack); room -= stack.getCount(); } else spare.add(stack);
        }
        if (!kept.isEmpty()) w.mine.hold(pos, kept);
        toss(level, pos, spare);
        return done();
    }

    /** What he can do without when the bag is full, the most useless first: rubble, what mobs leave, raw copper, wooden tools worn out by the stone ones. */
    private static final List<Set<Item>> SPARE = List.of(
            Set.of(Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.GRAVEL, Items.SAND, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.NETHERRACK),
            Set.of(Items.ROTTEN_FLESH, Items.BONE, Items.SPIDER_EYE), Set.of(Items.RAW_COPPER), Set.of(Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL));

    /** A slot for a drop that has none (a diamond): the most spare stack is thrown out as a real item in the world. False when nothing in the bag is spare. */
    boolean makeRoom(ServerLevel level) {
        int slot = -1, rank = SPARE.size();
        for (int i = 0; i < owner.getContainerSize(); i++) {
            for (int r = 0; r < rank; r++) {
                if (SPARE.get(r).contains(owner.getItem(i).getItem())) { slot = i; rank = r; break; }
            }
        }
        if (slot < 0) return false;
        ItemStack thrown = owner.getItem(slot);
        owner.setItem(slot, ItemStack.EMPTY);
        toss(level, owner.blockPosition(), List.of(thrown));
        return true;
    }

    /** The held drops fall where their blocks stood, to be picked up like any harvest. */
    void flush() {
        for (CompanionMineSite.Held drop : w.mine.takeHeld()) owner.dropHarvest(drop.pos(), drop.drops());
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

    /** Rock and soil with stone pickaxes, an iron one with wear to spare once no stone pickaxe could be made; ore with the cheapest pickaxe that gets its drop. */
    private Item toolFor(BlockState state) {
        if (!state.is(Tags.Blocks.ORES)) return rockTool();
        Item best = null;
        int least = Integer.MAX_VALUE;
        for (ItemStack stack : w.items.carriedItems()) {
            if (!stack.is(ItemTags.PICKAXES) || !stack.isCorrectToolForDrops(state) || stack.getMaxDamage() >= least) continue;
            best = stack.getItem();
            least = stack.getMaxDamage();
        }
        return best;
    }

    private Item rockTool() {
        if (w.items.countCarried(Items.STONE_PICKAXE) > 0) return Items.STONE_PICKAXE;
        return ironForRock && spareIron() ? Items.IRON_PICKAXE : null;
    }

    /** An iron pickaxe in the bag with wear to spare. */
    boolean spareIron() { return ironLeft(ironReserve); }

    /** An iron pickaxe in the bag with more than {@code reserve} wear left. */
    boolean ironLeft(int reserve) {
        for (ItemStack stack : w.items.carriedItems()) {
            if (stack.is(Items.IRON_PICKAXE) && stack.getMaxDamage() - stack.getDamageValue() > reserve) return true;
        }
        return false;
    }

    /** A diamond ore with an open face within {@code radius} of his feet: one he has seen, as the ore search sees it, not through the rock. */
    boolean diamondInSight(ServerLevel level, int radius) {
        BlockPos feet = owner.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, -radius, -radius), feet.offset(radius, radius, radius))) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BlockTags.DIAMOND_ORES)) continue;
            for (Direction side : Direction.values()) if (CompanionShaftPlan.open(level, pos.relative(side))) return true;
        }
        return false;
    }

    /** The rock is dug with the iron pickaxe down to {@code reserve} wear left, for what only it can still do (a diamond, a plug of his own on the way out). */
    void ironForRock(int reserve) {
        ironForRock = true;
        ironReserve = reserve;
    }

    /** Back to keeping {@link #SPARE_DURABILITY} wear of the iron pickaxe for ore. */
    void keepIron() {
        ironForRock = false;
        ironReserve = SPARE_DURABILITY;
    }

    /** Steps towards the cell with the move control (neighbouring cells of his own mine); true once he stands in it. */
    boolean walkTo(BlockPos to) {
        if (takingUpTemporary(to)) return false;
        boolean inside = standsIn(to);
        if (inside && (centred(to) || to.equals(walking) && walkTicks > WALK_PATIENCE / 2)) { walking = null; walkTicks = 0; walked++; return true; }
        if (!to.equals(walking)) { walking = to.immutable(); walkTicks = 0; }
        walkTicks++;
        Vec3 aim = CompanionExpeditionLedge.aim(owner, to);
        if (aim == null) return false;
        owner.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1);
        return false;
    }

    /** On the ground in the cell, or afloat in the water of it: swimming he bobs a block up and down and is never on the ground. */
    private boolean standsIn(BlockPos to) {
        BlockPos at = owner.blockPosition();
        if (owner.onGround() && at.equals(to)) return true;
        return owner.isInWater() && at.getX() == to.getX() && at.getZ() == to.getZ() && at.getY() >= to.getY() && at.getY() <= to.getY() + 1
                && !owner.level().getFluidState(to).isEmpty();
    }

    /**
     * A temporary block of his own (a creeper block) shutting the column of the cell he is to walk into comes out by hand first, up or
     * down the stairs alike. True while that goes on: with a creeper still near the block he waits, he does not open it onto the creeper.
     */
    private boolean takingUpTemporary(BlockPos to) { return takingUp(to, false); }

    /**
     * The cells of his own way (the record's stairs and tunnels) are open by construction, so a block at the feet or the head of the next one is a
     * block of his, whether the record still remembers it (it keeps sixteen temporary ones) or not. It comes out with what he has: any pickaxe,
     * the iron one however worn, a shovel, the bare hand last.
     */
    boolean takingUpOnHisWay(BlockPos to) { return takingUp(to, true); }

    private boolean takingUp(BlockPos to, boolean onHisWay) {
        CompanionMineSite mine = w.mine;
        if (mine.temporary().isEmpty() && !onHisWay || !(owner.level() instanceof ServerLevel level)) return false;
        BlockPos shut = null;
        for (BlockPos cell : CompanionShaftPlan.column(to)) {
            if (CompanionShaftPlan.open(level, cell)) continue;
            if (mine.temporary().contains(cell) || onHisWay && blocksHisWay(level, cell, to)) shut = cell;
            if (shut != null || onHisWay) break;
        }
        if (shut == null || owner.getEyePosition().distanceTo(shut.getCenter()) > CompanionSeal.REACH) return false;
        if (!mine.temporary().contains(shut) && !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), shut)) return false;
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, new net.minecraft.world.phys.AABB(shut).inflate(CREEPER_CLEAR),
                creeper -> creeper.isAlive() && !creeper.isNoAi()).isEmpty()) {
            owner.workStatus("Жду, пока крипер за моим блоком отойдёт.");
            owner.getNavigation().stop();
            return true;
        }
        switch (w.handOver.takeUp(level, shut, "Убираю свой блок с лестницы.")) {
            case WORKING -> { return true; }
            case DONE -> { mine.taken(shut); mine.unsealed(shut); return true; }
            default -> { return false; }
        }
    }

    /**
     * A solid block of the kind he plugs with at the feet or the head of the cell he is to walk into, with no liquid beside it (that is what
     * a plug may be holding back). A chest, a door or a block of any other kind is somebody else's and stays.
     */
    private static boolean blocksHisWay(ServerLevel level, BlockPos cell, BlockPos to) {
        BlockState state = level.getBlockState(cell);
        if (cell.getY() > to.getY() + 1 || !CompanionSeal.kind(state) || state.hasBlockEntity() || state.getCollisionShape(level, cell).isEmpty()) return false;
        for (Direction side : Direction.values()) if (!level.getFluidState(cell.relative(side)).isEmpty()) return false;
        return true;
    }

    /** Distance (squared) from his feet to the cell, for the walk to see how far it has been led off its way. */
    double distSqr(BlockPos cell) { return owner.blockPosition().distSqr(cell); }

    /** A block of his own that shuts his way, out with whatever he has, the bare hand last: DONE once it is open, FULL when the bag has no room for the drop, STUCK when it is out of reach or out of sight. */
    Status takeOut(ServerLevel level, BlockPos pos) {
        if (CompanionShaftPlan.open(level, pos)) return done();
        if (level.getBlockState(pos).getDestroySpeed(level, pos) < 0) return stuck("unbreakable");
        if (!pos.equals(cell)) { cell = pos.immutable(); unseen = 0; }
        owner.getLookControl().setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (owner.getEyePosition().distanceTo(pos.getCenter()) > CompanionSeal.REACH || !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), pos)) {
            w.breaking.reset();
            return ++unseen > UNSEEN_PATIENCE ? stuck("reach") : Status.WORKING;
        }
        return switch (w.handOver.takeUp(level, pos, "Убираю свой блок с дороги.")) {
            case WORKING -> Status.WORKING;
            case FULL -> Status.FULL;
            case DONE -> done();
        };
    }

    /**
     * {@link #walkTo}, and once the straight push has not brought him into the cell, the way round what blocks it (see
     * {@link CompanionExpeditionApproach}), walked cell by cell. No way found leaves the straight push to run out its patience.
     */
    boolean reach(BlockPos to) {
        if (!to.equals(reaching)) { reaching = to.immutable(); around = List.of(); aroundIndex = 0; tried = false; }
        if (aroundIndex < around.size()) {
            if (walkTo(around.get(aroundIndex)) && ++aroundIndex == around.size()) { reaching = null; return true; }
            return false;
        }
        if (walkTo(to)) { reaching = null; return true; }
        if (!tried && walkTicks >= PATH_AFTER) {
            tried = true;
            List<BlockPos> path = CompanionExpeditionApproach.path((ServerLevel) owner.level(), owner.blockPosition(), to);
            if (path != null && path.size() > 1) { around = path; aroundIndex = 0; }
        }
        return false;
    }

    boolean walkStuck() { return walkTicks > WALK_PATIENCE; }

    /** One tick of laying a floor block of his own, kept in the record of his plugs. */
    CompanionExpeditionFloor.Outcome lay(CompanionExpeditionFloor floor) {
        CompanionExpeditionFloor.Outcome laid = floor.lay(owner, (ServerLevel) owner.level());
        if (laid == CompanionExpeditionFloor.Outcome.PLACED) w.mine.sealed(floor.cell);
        return laid;
    }

    /** A firm floor under the cell; a cell in an unloaded chunk is not judged. */
    boolean floored(BlockPos cell) {
        var level = owner.level();
        BlockPos floor = cell.below();
        return !level.hasChunkAt(floor) || level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    /** The cell a {@link Status#STUCK} was said for: where it is, what is in it and which of the reasons it was. */
    void diagnoseDig(BlockPos pos) {
        if (!dev.aicompanion.CompanionDiagnostics.enabled()) return;
        var level = owner.level();
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "dig_stuck", "entity", owner.getUUID(), "cell", pos.toShortString(), "block", level.getBlockState(pos),
                "fluid", level.getFluidState(pos), "why", stuckWhy, "cellTicks", cellTicks, "unseen", unseen, "feet", owner.position(), "ground", owner.onGround(),
                "eye", owner.getEyePosition().distanceTo(pos.getCenter()));
    }

    void diagnoseStuck(BlockPos to, int index, int size) {
        if (!dev.aicompanion.CompanionDiagnostics.enabled()) return;
        var level = owner.level();
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "walk_stuck", "entity", owner.getUUID(), "feet", owner.position(), "ground", owner.onGround(),
                "to", to.toShortString(), "cell", index + "/" + size, "below", level.getBlockState(to.below()), "at", level.getBlockState(to), "above", level.getBlockState(to.above()), "above2", level.getBlockState(to.above(2)));
    }

    private boolean centred(BlockPos to) {
        Vec3 centre = Vec3.atBottomCenterOf(to);
        double dx = owner.getX() - centre.x, dz = owner.getZ() - centre.z;
        return dx * dx + dz * dz < CENTRED * CENTRED;
    }

    void reset() {
        flush();
        cell = walking = lastBroken = null;
        keepIron();
        reaching = null;
        around = List.of();
        minedOre.clear();
        cellTicks = unseen = walkTicks = 0;
        w.breaking.reset();
    }
}
