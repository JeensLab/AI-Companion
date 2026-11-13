package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * gather_block: an item a block gives when broken (CompanionPickable), gathered like a player does it. The nearest such block
 * within his search radius is walked to, broken with the tool it wants and its drop picked up, until the bag holds as many as
 * ordered. Sugar cane, cactus and bamboo are cut at the block above the root: all that stands over the cut comes down with it and
 * the root is left to grow; cocoa is taken ripe; what only shears take (leaves, a vine, a cobweb) is cut with his shears, and a leaf
 * block someone set down (it does not fade) is left; nothing is taken where the player builds (his flower bed, his cane by the house) or outside
 * the overworld (the Nether and the End are later work). What grows on the bed of the water (kelp, seagrass, a sea pickle, a coral
 * block) he swims over and dives for, as for a foe under water (CompanionSwimming): under open water no deeper than his breath
 * takes him, never under a roof; what only lies under water (clay in a pond) is left for the dry kind.
 */
final class CompanionPicking {
    static final String FULL_BAG = "Сумка забита — некуда класть.";
    /** Blocks above and below his feet that are looked at: the hill he stands on, not the cave under it. */
    private static final int DEPTH = 12;
    /** Ticks the drops are given to be picked up before another try, and then before they are counted lost. */
    private static final int SETTLE = 40;
    /** Walks to one block before it is passed over. */
    private static final int LEGS = 8;
    /** Ticks a search for the way may not come about (he is in the air, in running water) before the block is passed over. */
    private static final int WAIT = 100;
    /** Ticks the last step into the cell is given: he is less than a block from its middle. */
    private static final int LAST_STEP = 20;
    /** Blocks of water over a block on the bed he dives for: half his breath down and up again, as the dive for a foe. */
    private static final int DEEP = 10;
    /** A player's reach, from the eyes to the middle of the block. */
    private static final double REACH = 4.5;
    /** Ticks over the block, down and up for air, before it is passed over. */
    private static final int DIVE = 900;

    private final CompanionWork w;
    /** The work is made before it knows its owner: taken at the order. */
    private CompanionEntity c;
    private Item item;
    private List<CompanionPickable.Source> sources = List.of();
    /** Ordered, in his bag at the order, dropped by the blocks he broke (picked up or not). */
    private int wanted, before, due;
    private long settleAt;
    private boolean retried;
    private BlockPos target;
    /** The cells he takes the target from, his way to them and the walks so far. */
    private Set<BlockPos> stands = Set.of();
    private Path path;
    private int legs, waited, dived;
    /** Blocks he found no way to. */
    private final Set<BlockPos> passed = new HashSet<>();

    CompanionPicking(CompanionWork w) { this.w = w; }

    /** gather_block: {@code count} more of the item in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, int count) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        sources = asked == null || !(c.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD ? List.of() : CompanionPickable.sources(level, asked);
        if (sources.isEmpty()) return CompanionActionResult.failed("Такое я с земли не собираю: " + id + ".");
        item = asked; wanted = count; before = w.items.countCarried(item); due = 0; settleAt = 0; retried = false;
        release();
        passed.clear();
        return null;
    }

    /** What the broken blocks dropped so far: progress for the watchdog while the drops are walked to. */
    int due() { return due; }

    String jobName() { return "сбор: «" + name() + "» (" + got() + " из " + wanted + ")"; }

    private String name() { return new ItemStack(item).getHoverName().getString(); }

    private int got() { return Math.max(0, w.items.countCarried(item) - before); }

    /** Whether one of these blocks stands within his search radius where he may take it. */
    static boolean near(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> sources) {
        return !sources.isEmpty() && level.dimension() == Level.OVERWORLD && find(c, level, sources, sites(c, level), Set.of()) != null;
    }

    static Predicate<BlockPos> sites(CompanionEntity c, ServerLevel level) {
        return c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
    }

    private static BlockPos find(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> sources, Predicate<BlockPos> site, Set<BlockPos> passed) {
        return CompanionObsidian.nearest(c, level, DEPTH, state -> gives(sources, state) > 0, pos -> !passed.contains(pos) && takes(level, pos, site) && low(c, level, pos));
    }

    private static int gives(List<CompanionPickable.Source> sources, BlockState state) {
        for (CompanionPickable.Source source : sources) if (state.is(source.block())) return source.yield();
        return 0;
    }

    private static boolean shorn(List<CompanionPickable.Source> sources, BlockState state) {
        for (CompanionPickable.Source source : sources) if (state.is(source.block())) return source.shears();
        return false;
    }

    /** A block of the kind he may take now: dry, not on a site of the player's, ripe, and not the root of a growing column. */
    private static boolean takes(ServerLevel level, BlockPos pos, Predicate<BlockPos> site) {
        BlockState state = level.getBlockState(pos);
        if (site.test(pos)) return false;
        // Clay on the bed of a pond has no water in its own cell: the water over it counts too. Only what grows there is dived for.
        if (wet(level, pos) && (!grows(state) || depth(level, pos) < 0)) return false;
        // A rose bush is taken by its foot: the top half gives nothing, and the foot it brings down would lie there unclaimed.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
        if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
        // Leaves that do not fade were set down by someone: a hedge, a roof of leaves, wherever it stands.
        if (state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT)) return false;
        // A hanging vine from its lower end: the ones under a cut fall with nothing to pick up.
        if (state.is(Blocks.VINE) && level.getBlockState(pos.below()).is(Blocks.VINE)) return false;
        // The block above the root, within his reach from the ground: the root grows again.
        if (column(state)) return kin(state, level.getBlockState(pos.below())) && !kin(state, level.getBlockState(pos.below(2)));
        return true;
    }

    private static boolean wet(ServerLevel level, BlockPos pos) { return !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty(); }

    /** What grows in the water: holds it (kelp, seagrass, a sea pickle), or a coral block. */
    private static boolean grows(BlockState state) { return state.getFluidState().is(FluidTags.WATER) || state.is(BlockTags.CORAL_BLOCKS); }

    /** Still water over the block he swims through, up to the air: -1 under a roof, in running water or deeper than {@link #DEEP}. */
    private static int depth(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cell = pos.above().mutable();
        for (int blocks = 0; blocks <= DEEP; blocks++, cell.move(0, 1, 0)) {
            var fluid = level.getFluidState(cell);
            boolean open = level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
            if (fluid.isEmpty()) return open ? blocks : -1;
            if (!fluid.is(FluidTags.WATER) || !fluid.isSource() || !open) return -1;
        }
        return -1;
    }

    /**
     * Leaves he reaches from the ground: a crown high over it is no source (a spruce, a tall oak), or iron would be mined for
     * shears he then stands under the tree with. He builds no stair to a leaf and does not walk the crowns.
     */
    private static boolean low(CompanionEntity c, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.LEAVES)) return true;
        for (int dy = -3; dy <= 0; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos cell = pos.offset(dx, dy, dz);
            if (!level.getBlockState(cell.below()).is(BlockTags.LEAVES) && CompanionMiningNavigation.standable(c, cell)) return true;
        }
        return false;
    }

    private static boolean column(BlockState state) { return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO) || kelp(state); }

    private static boolean kelp(BlockState state) { return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT); }

    /** One column: the same block, or kelp whose top is another block than its stalk. */
    private static boolean kin(BlockState column, BlockState other) { return other.is(column.getBlock()) || kelp(column) && kelp(other); }

    /**
     * The cells he takes the block from. A block with a body is worked like ore: in sight, and from where its drop is picked up.
     * A flower or a cane has no body for the eye to rest on (the game's sight goes through it): from the ground at it or beside it.
     */
    private Set<BlockPos> standsFor(ServerLevel level, BlockPos block) {
        Set<BlockPos> cells = new HashSet<>();
        // On the bed of the water: from the water over it and beside it, the surface the way comes to and the dive down from there.
        if (wet(level, block)) {
            for (int dy = 0; dy <= DEEP + 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos cell = block.offset(dx, dy, dz);
                if (!cell.equals(block) && level.getFluidState(cell).is(FluidTags.WATER) && CompanionMiningNavigation.pickupStand(c, cell)) cells.add(cell);
            }
            return cells;
        }
        if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
            for (BlockPos stand : CompanionMiningNavigation.standCells(c, block, 3.5, false, true))
                if (CompanionMiningNavigation.dropCollectable(level, block, stand)) cells.add(stand);
            return cells;
        }
        for (int dy = -2; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos cell = block.offset(dx, dy, dz);
            // Not from inside a cobweb: it holds him.
            if (!CompanionMiningNavigation.standable(c, cell) || level.getBlockState(cell).is(Blocks.COBWEB)) continue;
            // Nothing between his eyes and it: not through a fence, nor from the cave under the flower.
            Vec3 eye = Vec3.atBottomCenterOf(cell).add(0, c.getEyeHeight(), 0);
            if (level.clip(new ClipContext(eye, block.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c)).getType() == HitResult.Type.MISS) cells.add(cell);
        }
        return cells;
    }

    /** One tick of the walk to the stand cells: false when there is no way (or no progress after {@link #LEGS} walks). */
    private boolean walk() {
        CompanionRoute route = w.route;
        if (path != null && !route.legOver()) return true;
        // The game counts the last node reached from half a block off: at a trunk he stood a step short of the cell, and the walks
        // ran out in eight ticks (the vine on the trunk in the test). That last step is his own.
        if (path != null && route.done() && path.getEndNode() != null && stands.contains(path.getEndNode().asBlockPos())) {
            Vec3 end = Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());
            if (c.distanceToSqr(end) < 1 && ++waited < LAST_STEP) { c.getMoveControl().setWantedPosition(end.x, end.y, end.z, .7); return true; }
        }
        if (stands.isEmpty() || ++legs > LEGS) return false;
        CompanionRoute.Reach reach = route.cells(stands, 0);
        // He is in the air or the search goes on: asked again at the next tick.
        // Not for ever: under a ledge in running water no search came about and he stood there until the watchdog (in game 12.11).
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return ++waited < WAIT; }
        if (!reach.found()) return false;
        waited = 0;
        path = reach.path();
        route.walk(path);
        return true;
    }

    /** The block on the bed he is over now: the swimmer sinks to it while his breath allows (CompanionSwimming). */
    BlockPos dive() { return target != null && stands.contains(c.blockPosition()) && c.level() instanceof ServerLevel level && wet(level, target) ? target : null; }

    /** Within a player's reach of it, nothing solid between: a block under water is broken from there, the water does not stop the eye. */
    private boolean reaches(ServerLevel level) {
        Vec3 eye = c.getEyePosition();
        if (eye.distanceTo(target.getCenter()) > REACH) return false;
        var hit = level.clip(new ClipContext(eye, target.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    void tick(ServerLevel level) {
        int got = got();
        if (got >= wanted) { end(true, "Собрал: «" + name() + "», " + got + " шт."); return; }
        if (due >= wanted) { collect(level, got); return; }
        Predicate<BlockPos> site = sites(c, level);
        if (target != null && (gives(sources, level.getBlockState(target)) == 0 || !takes(level, target, site))) release();
        if (target == null) target = find(c, level, sources, site, passed);
        if (target == null) {
            // What he broke and has not picked up yet is picked up before he says how far he got.
            if (due > got) { collect(level, got); return; }
            String rest = passed.isEmpty() ? "больше рядом нет" : "до остального не добрался";
            // Some gathered: obtain_item plans the rest again (red dye goes on with the tulips once the poppies are out). None: it ends here.
            if (got > 0) { release(); w.finish(CompanionActionResult.failed("Собрал «" + name() + "»: " + got + " из " + wanted + ", " + rest + ".")); return; }
            end(false, passed.isEmpty() ? "Рядом не нашёл, где взять «" + name() + "»." : "Вижу, где взять «" + name() + "», но подойти не смог.");
            return;
        }
        BlockState state = level.getBlockState(target);
        boolean shorn = shorn(sources, state);
        if (shorn && w.items.countCarried(Items.SHEARS) == 0) {
            if (due > got) { collect(level, got); return; }
            // His shears wore out on the way: obtain_item plans the rest again, new shears first.
            if (got > 0) { release(); w.finish(CompanionActionResult.failed("Собрал «" + name() + "»: " + got + " из " + wanted + ", ножницы сломались.")); return; }
            end(false, "Ножниц нет: срезать «" + name() + "» нечем.");
            return;
        }
        if (stands.isEmpty()) stands = standsFor(level, target);
        if (!stands.contains(c.blockPosition())) {
            c.workStatus("Иду за: " + name() + ".");
            if (!walk()) { passed.add(target); release(); }
            return;
        }
        c.getNavigation().stop();
        if (wet(level, target) && !reaches(level)) {
            c.workStatus("Ныряю за: " + name() + ".");
            // Breath never enough to come at it (a current, a long way down): passed over, as a block with no way to it.
            if (++dived > DIVE) { passed.add(target); release(); }
            return;
        }
        if (shorn) {
            // From the bag or from the other hand.
            if (!c.getMainHandItem().is(Items.SHEARS) && !CompanionTools.equip(c, net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR, state)) { end(false, "Не могу взять ножницы в руку: в сумке нет места."); return; }
        } else if (w.items.prepareMiningTool(state)) return;
        else if (state.is(BlockTags.MINEABLE_WITH_AXE)) CompanionTools.equip(c, ItemTags.AXES, state);
        else if (state.is(BlockTags.MINEABLE_WITH_HOE)) CompanionTools.equip(c, ItemTags.HOES, state);
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) CompanionTools.equip(c, ItemTags.PICKAXES, state);
        ItemStack tool = c.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) { end(false, "Нечем взять «" + name() + "»: нужен инструмент."); return; }
        if (!w.items.canStore(List.of(new ItemStack(item)))) { end(false, FULL_BAG + (got == 0 ? "" : " Собрал " + got + " из " + wanted + ".")); return; }
        c.workStatus("Собираю: " + name() + ".");
        if (!w.breaking.tick(level, target)) return;
        // A cane cut low comes down whole: what stands over the cut is his harvest too, from the top.
        int dueBefore = due;
        List<BlockPos> cut = new ArrayList<>(List.of(target));
        if (column(state)) for (BlockPos up = target.above(); kin(state, level.getBlockState(up)); up = up.above()) cut.addFirst(up);
        for (BlockPos pos : cut) {
            BlockState broken = level.getBlockState(pos);
            var drops = CompanionTools.drops(broken, level, pos, null, c, tool);
            if (!level.destroyBlock(pos, false, c, 512)) { passed.add(pos); continue; }
            c.dropHarvest(pos, drops);
            for (ItemStack drop : drops) if (drop.is(item)) due += drop.getCount();
        }
        tool.getItem().mineBlock(tool, level, state, target, c);
        // A tool with Silk Touch brings the block itself (clay, not its balls): one is enough to see it, not every block about.
        if (due == dueBefore) { end(false, "С этим инструментом «" + name() + "» не выходит: в руке " + tool.getHoverName().getString() + "."); return; }
        release();
    }

    /** Enough is broken: the drops are picked up (the work waits while the pickup runs); what is not in his bag after that is lost, and more is gathered. */
    private void collect(ServerLevel level, int got) {
        long now = level.getGameTime();
        if (settleAt == 0) { settleAt = now + SETTLE; return; }
        if (now < settleAt) return;
        if (!retried) { retried = true; settleAt = now + SETTLE; c.retryHarvestPickup(); return; }
        due = got; settleAt = 0; retried = false;
    }

    private void release() { target = null; stands = Set.of(); path = null; legs = waited = dived = 0; w.breaking.reset(); w.resetApproach(); }

    /** A failure ends obtain_item at once with what he says (BLOCKED): planned again, the same ground was looked over twice more. */
    private void end(boolean success, String message) {
        release();
        w.finish(success ? CompanionActionResult.ok(message) : CompanionActionResult.blocked(message));
    }
}
