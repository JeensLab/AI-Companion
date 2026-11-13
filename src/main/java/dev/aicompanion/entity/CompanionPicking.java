package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
 * block someone set down (it does not fade) is left; nothing is taken where the player builds (his flower bed, his cane by the house), under water, or outside
 * the overworld (the Nether and the End are later work).
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
    private int legs, waited;
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
        return CompanionObsidian.nearest(c, level, DEPTH, state -> gives(sources, state) > 0, pos -> !passed.contains(pos) && takes(level, pos, site));
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
        // Clay on the bed of a pond has no water in its own cell: the water over it counts too.
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty() || site.test(pos)) return false;
        // A rose bush is taken by its foot: the top half gives nothing, and the foot it brings down would lie there unclaimed.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
        if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
        // Leaves that do not fade were set down by someone: a hedge, a roof of leaves, wherever it stands.
        if (state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT)) return false;
        // The block above the root, within his reach from the ground: the root grows again.
        if (column(state)) return level.getBlockState(pos.below()).is(state.getBlock()) && !level.getBlockState(pos.below(2)).is(state.getBlock());
        return true;
    }

    private static boolean column(BlockState state) { return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO); }

    /**
     * The cells he takes the block from. A block with a body is worked like ore: in sight, and from where its drop is picked up.
     * A flower or a cane has no body for the eye to rest on (the game's sight goes through it): from the ground at it or beside it.
     */
    private Set<BlockPos> standsFor(ServerLevel level, BlockPos block) {
        Set<BlockPos> cells = new HashSet<>();
        if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
            for (BlockPos stand : CompanionMiningNavigation.standCells(c, block, 3.5, false, true))
                if (CompanionMiningNavigation.dropCollectable(level, block, stand)) cells.add(stand);
            return cells;
        }
        for (int dy = -2; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos cell = block.offset(dx, dy, dz);
            if (!CompanionMiningNavigation.standable(c, cell)) continue;
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
            if (c.distanceToSqr(end) < 2.25 && ++waited < WAIT) { c.getMoveControl().setWantedPosition(end.x, end.y, end.z, .7); return true; }
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
        if (shorn && w.items.countCarried(Items.SHEARS) == 0) { end(false, "Ножниц нет: срезать «" + name() + "» нечем." + (got == 0 ? "" : " Собрал " + got + " из " + wanted + ".")); return; }
        if (stands.isEmpty()) stands = standsFor(level, target);
        if (!stands.contains(c.blockPosition())) {
            c.workStatus("Иду за: " + name() + ".");
            if (!walk()) { passed.add(target); release(); }
            return;
        }
        c.getNavigation().stop();
        if (shorn) {
            if (!c.getMainHandItem().is(Items.SHEARS) && !c.equipForWork("minecraft:shears").success()) { end(false, "Не могу взять ножницы в руку: в сумке нет места."); return; }
        } else if (w.items.prepareMiningTool(state)) return;
        else if (state.is(BlockTags.MINEABLE_WITH_AXE)) CompanionTools.equip(c, ItemTags.AXES, state);
        else if (state.is(BlockTags.MINEABLE_WITH_HOE)) CompanionTools.equip(c, ItemTags.HOES, state);
        ItemStack tool = c.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) { end(false, "Нечем взять «" + name() + "»: нужен инструмент."); return; }
        if (!w.items.canStore(List.of(new ItemStack(item)))) { end(false, FULL_BAG + (got == 0 ? "" : " Собрал " + got + " из " + wanted + ".")); return; }
        c.workStatus("Собираю: " + name() + ".");
        if (!w.breaking.tick(level, target)) return;
        // A cane cut low comes down whole: what stands over the cut is his harvest too, from the top.
        int dueBefore = due;
        List<BlockPos> cut = new ArrayList<>(List.of(target));
        if (column(state)) for (BlockPos up = target.above(); level.getBlockState(up).is(state.getBlock()); up = up.above()) cut.addFirst(up);
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

    private void release() { target = null; stands = Set.of(); path = null; legs = waited = 0; w.breaking.reset(); w.resetApproach(); }

    /** A failure ends obtain_item at once with what he says (BLOCKED): planned again, the same ground was looked over twice more. */
    private void end(boolean success, String message) {
        release();
        w.finish(success ? CompanionActionResult.ok(message) : CompanionActionResult.blocked(message));
    }
}
