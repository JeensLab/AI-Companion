package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
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
 * block someone set down (it does not fade) is left; nothing is taken where the player builds (his flower bed, his cane by the house),
 * but for a pumpkin or a melon at its stem there, his harvest (the stem stays to bear again), nor outside
 * the overworld, but for the Nether's own stone there (netherrack, basalt; never beside lava); the End is later work. What grows on the bed of the water (kelp, seagrass, a sea pickle, a coral
 * block) he swims over and dives for, as for a foe under water (CompanionSwimming): under open water no deeper than his breath
 * takes him, never under a roof; what only lies under water (clay in a pond) is left for the dry kind. A crown above every
 * stand cell is no block out of reach: he builds up to it on his own soil (CompanionPillar) and cuts it from the top.
 * What a stand serves is cut from the stand first — the stair a player built is walked up, not built over — and a leaf no
 * stand he can walk to serves is climbed for, the soil dug first when the stair cannot be climbed (the owner, 18.11).
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
    /** How far from a leaf a cell he works it from may be: the same reach the stand cells are searched in. */
    private static final double STAND_REACH = 3.5;
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
    /** No way to the stands of this target: the climb is what is left, and the walk is not searched again for it. */
    private boolean walkFailed;
    private Path path;
    private int legs, waited, dived;
    /** Blocks he found no way to. */
    private final Set<BlockPos> passed = new HashSet<>();
    /** Whether a block that gives dirt is about him, looked for once: the soil a crown's column is dug from. */
    private boolean soilAbout, soilAboutKnown;
    /** Blocks left alone this order, for the report: lava stands beside them (source or running, any of the six sides). */
    private final Set<BlockPos> lavaKept = new HashSet<>();

    CompanionPicking(CompanionWork w) { this.w = w; }

    /** gather_block: {@code count} more of the item in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, int count) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        sources = asked == null || !(c.level() instanceof ServerLevel level) ? List.of() : allowed(level, CompanionPickable.sources(level, asked));
        if (sources.isEmpty()) return CompanionActionResult.failed("Такое я с земли не собираю: " + id + ".");
        item = asked; wanted = count; before = w.items.countCarried(item); due = 0; settleAt = 0; retried = false;
        release();
        passed.clear(); lavaKept.clear(); soilAboutKnown = false;
        return null;
    }

    /** What the broken blocks dropped so far: progress for the watchdog while the drops are walked to. */
    int due() { return due; }

    String jobName() { return "сбор: «" + name() + "» (" + got() + " из " + wanted + ")"; }

    private String name() { return new ItemStack(item).getHoverName().getString(); }

    private int got() { return Math.max(0, w.items.countCarried(item) - before); }

    /** What the order has brought so far: said when it is cut short (the errand's watch in the Nether). */
    String progress() { return "в сумке прибавилось " + got() + " «" + CompanionWork.itemName(item) + "»."; }

    /** Whether one of these blocks stands within his search radius where he may take it. */
    static boolean near(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> sources) {
        List<CompanionPickable.Source> here = allowed(level, sources);
        if (here.isEmpty()) return false;
        Predicate<BlockPos> site = sites(c, level);
        if (find(c, level, here, site, Set.of(), new HashSet<>(), false, false, false) != null) return true;
        // A crown above every stand cell is still his when the soil for the column can be dug about him (the owner, 18.11).
        return find(c, level, here, site, Set.of(), new HashSet<>(), true, false, false) != null && soilAbout(c, level);
    }

    /**
     * The leaf block the picking can really work from: the nearest leaf of any tree in his search that he can stand by
     * (the gather's own rule — a stand cell under the crown, never merely the nearest block in the air), or a crown he can
     * build up to on the soil he carries or digs. A tree's own leaves come first, and the ground reach before a column; the
     * leaves a player set down are leaves too, cut when no tree's own can meet the order (the owner, 18.11). Null when no
     * leaf is within reach at all — the order is then refused short instead of naming a species he cannot come at.
     */
    static BlockPos nearestLeaf(CompanionEntity c, ServerLevel level) {
        return nearestLeaf(c, level, allowed(level, CompanionPickable.leaves(level)));
    }

    /** The nearest of these leaf blocks he can come at: a tree's own first, the ground reach before a column, the player's own last. */
    private static BlockPos nearestLeaf(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> all) {
        if (all.isEmpty()) return null;
        Predicate<BlockPos> site = sites(c, level);
        boolean dug = soilAbout(c, level);
        for (boolean natural : new boolean[]{true, false})
            for (boolean soil : new boolean[]{false, true}) {
                if (soil && !dug) continue;
                BlockPos found = find(c, level, all, site, Set.of(), new HashSet<>(), soil, natural, false);
                if (found != null) return found;
            }
        return null;
    }

    /** Whether a block that gives dirt stands within his search where he may take it: the soil for a crown he digs himself. */
    static boolean soilAbout(CompanionEntity c, ServerLevel level) {
        List<CompanionPickable.Source> dirt = allowed(level, CompanionPickable.sources(level, Items.DIRT));
        return !dirt.isEmpty() && find(c, level, dirt, sites(c, level), Set.of(), new HashSet<>(), false, false, false) != null;
    }

    /**
     * The soil he must dig for the crown of this leaf block the order will come at: the steps of its longest column less what
     * he carries. 0 when a stand he can walk to serves it or the bag already has the soil; the crown is not counted when no
     * column reaches it (the order is refused for its height then).
     */
    static int soilToDig(CompanionEntity c, ServerLevel level, String item) {
        var id = ResourceLocation.tryParse(item == null ? "" : item);
        var leaf = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (leaf == null) return 0;
        List<CompanionPickable.Source> all = allowed(level, CompanionPickable.sources(level, leaf)).stream()
                .filter(source -> source.block().defaultBlockState().is(BlockTags.LEAVES)).toList();
        // The leaf the gather comes at first: one a stand serves, as the gather takes them first, else the nearest one a
        // column reaches (the owner, 18.11: the stair's crown is cut from the stair, and no soil is dug for it).
        Predicate<BlockPos> site = sites(c, level);
        BlockPos found = find(c, level, all, site, Set.of(), new HashSet<>(), false, true, true);
        if (found == null) found = find(c, level, all, site, Set.of(), new HashSet<>(), false, false, true);
        if (found == null) found = nearestLeaf(c, level, all);
        if (found == null) return 0;
        // The leaf the order comes at first is served by a stand only when there is a way to that stand on foot: a stair he
        // cannot climb (a step of two, a wall across it) leaves the crown above every stand cell all the same, and the soil for
        // the column is dug before the gather instead of being given up over (the owner, 18.11: «Вижу, где взять …, но подойти
        // не смог»). No dirt about to dig: the leaf is passed over for the one beside it, as it was (a stone shelf).
        Set<BlockPos> stands = standsFor(c, level, found);
        if (!stands.isEmpty() && CompanionMiningNavigation.canWalkTo(c, c.blockPosition(), stands)) return 0;
        // No soil he can really dig (the dig's own reach, not the search radius of the order): the leaf is passed over for the
        // one beside it, as it was — a leaf on a stone shelf with the dirt twenty blocks off (the owner, 18.11).
        if (!soilNear(c, level)) return 0;
        return Math.max(0, c.temporaryPillar.columnSteps(found) - c.temporaryPillar.soilCarried());
    }

    /** Whether a block that gives dirt stands within the reach the dig works in, where he may take it. */
    private static boolean soilNear(CompanionEntity c, ServerLevel level) {
        List<CompanionPickable.Source> dirt = allowed(level, CompanionPickable.sources(level, Items.DIRT));
        BlockPos found = dirt.isEmpty() ? null : find(c, level, dirt, sites(c, level), Set.of(), new HashSet<>(), false, false, false);
        return found != null && found.distSqr(c.blockPosition()) <= 12 * 12;
    }

    /**
     * Whether a crown about him is out of his ground reach only for want of soil: a leaf of a tree he could come at up a short
     * column of his own, if the bag held the soil for it. Said as the soil the refusal names, not as no leaves at all — and
     * never when that soil lies about him to dig (the owner, 18.11).
     */
    static boolean soilForACrown(CompanionEntity c, ServerLevel level) {
        List<CompanionPickable.Source> all = allowed(level, CompanionPickable.leaves(level));
        if (all.isEmpty() || soilAbout(c, level)) return false;
        Predicate<BlockPos> site = sites(c, level);
        return CompanionObsidian.nearest(c, level, DEPTH, state -> gives(all, state) > 0, pos ->
                c.errand.within(pos) && takes(level, pos, site) && grounded(c, level, pos) && !lavaBeside(level, pos)
                && crownOverSoil(c, level, pos)) != null;
    }

    /** A leaf the ground reach does not give, but a short column of his would — the soil being there. */
    private static boolean crownOverSoil(CompanionEntity c, ServerLevel level, BlockPos pos) {
        return !(low(c, level, pos) && !standsFor(c, level, pos).isEmpty()) && !c.temporaryPillar.buildCellsWithAnySoil(pos).isEmpty();
    }

    /**
     * Whether a crown about him is above even the longest column of his own: a leaf of a tree he could come at that neither the
     * ground reach nor six steps of soil bring within a swing. Said as its height, not as no leaves at all (the owner, 17.11).
     */
    static boolean aboveAnyColumn(CompanionEntity c, ServerLevel level) {
        List<CompanionPickable.Source> all = allowed(level, CompanionPickable.leaves(level));
        if (all.isEmpty()) return false;
        Predicate<BlockPos> site = sites(c, level);
        return CompanionObsidian.nearest(c, level, DEPTH, state -> gives(all, state) > 0, pos ->
                c.errand.within(pos) && takes(level, pos, site) && grounded(c, level, pos) && !lavaBeside(level, pos)
                && !(low(c, level, pos) && !standsFor(c, level, pos).isEmpty())
                && c.temporaryPillar.buildCellsWithAnySoil(pos).isEmpty()) != null;
    }

    /** What is taken in this dimension: the overworld's own, not the Nether's stone (put there by the player), in the Nether only that stone, nothing elsewhere. */
    private static List<CompanionPickable.Source> allowed(ServerLevel level, List<CompanionPickable.Source> all) {
        if (level.dimension() == Level.OVERWORLD) return all.stream().filter(source -> !CompanionPickable.nether(source.block())).toList();
        return level.dimension() == Level.NETHER ? all.stream().filter(source -> CompanionPickable.nether(source.block())).toList() : List.of();
    }

    /** The Nether's stone is taken only off solid ground, never the block he stands on: a floor over a cave or a lava lake stays shut. */
    private static boolean grounded(CompanionEntity c, ServerLevel level, BlockPos pos) {
        if (!CompanionPickable.nether(level.getBlockState(pos).getBlock())) return true;
        return !pos.equals(c.blockPosition().below()) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean lavaBeside(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) if (CompanionLava.at(level, pos.relative(side))) return true;
        return false;
    }

    static Predicate<BlockPos> sites(CompanionEntity c, ServerLevel level) {
        return c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
    }

    private static BlockPos find(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> sources, Predicate<BlockPos> site,
                                 Set<BlockPos> passed, Set<BlockPos> lava, boolean dugSoil, boolean naturalOnly, boolean standOnly) {
        return CompanionObsidian.nearest(c, level, DEPTH, state -> gives(sources, state) > 0, pos -> {
            if (passed.contains(pos) || !c.errand.within(pos) || !takes(level, pos, site) || !atHand(c, level, pos, dugSoil, standOnly)
                    || naturalOnly && setDown(level.getBlockState(pos)) || !grounded(c, level, pos)) return false;
            if (!lavaBeside(level, pos)) return true;
            lava.add(pos.immutable());
            return false;
        });
    }

    private static int gives(List<CompanionPickable.Source> sources, BlockState state) {
        for (CompanionPickable.Source source : sources) if (state.is(source.block())) return source.yield();
        return 0;
    }

    private static boolean shorn(List<CompanionPickable.Source> sources, BlockState state) {
        for (CompanionPickable.Source source : sources) if (state.is(source.block())) return source.shears();
        return false;
    }

    /** Whether this block gives the item only to Silk Touch: the block itself, not its drop. */
    private static boolean silked(List<CompanionPickable.Source> sources, BlockState state) {
        for (CompanionPickable.Source source : sources) if (state.is(source.block())) return source.silk();
        return false;
    }

    /** A block of the kind he may take now: dry, not on a site of the player's (but the fruit of a stem, and a leaf the player set down himself), ripe, and not the root of a growing column. */
    private static boolean takes(ServerLevel level, BlockPos pos, Predicate<BlockPos> site) {
        BlockState state = level.getBlockState(pos);
        // A leaf that does not fade was set down by the player as leaves (a hedge, a roof, a bed of them on the ground): it is
        // still his to cut on a site of his, as the last leaves of a leaf order (the owner, 18.11: the leaves he placed lay
        // beside him and were not taken at all).
        if (site.test(pos) && !CompanionCrops.onStem(level, pos) && !(state.is(BlockTags.LEAVES) && setDown(state))) return false;
        // Clay on the bed of a pond has no water in its own cell: the water over it counts too. Only what grows there is dived for.
        // A coral block under another one, or a sea pickle sat on it, is not under open water: none of it is dived for.
        if (wet(level, pos) ? !grows(state) || depth(level, pos) < 0 : state.is(BlockTags.CORAL_BLOCKS)) return false;
        // A rose bush is taken by its foot: the top half gives nothing, and the foot it brings down would lie there unclaimed.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
        if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
        // A leaf that does not fade was set down by someone: a hedge, a roof of leaves. It is still leaves, and its own item
        // comes to shears the same (the owner, 18.11: the leaves beside him were his own); a tree's own are cut first.
        // A hanging vine from its lower end: the ones under a cut fall with nothing to pick up.
        if (state.is(Blocks.VINE) && level.getBlockState(pos.below()).is(Blocks.VINE)) return false;
        // The block above the root, within his reach from the ground: the root grows again.
        if (column(state)) return kin(state, level.getBlockState(pos.below())) && !kin(state, level.getBlockState(pos.below(2)));
        return true;
    }

    private static boolean wet(ServerLevel level, BlockPos pos) { return !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty(); }

    /**
     * What grows on the bed of the sea: kelp, seagrass, a sea pickle, a coral block, a coral plant or its fan (waterlogged, as it
     * stands there). Not a dry kind that happens to stand in water (mangrove roots in a swamp, glow lichen, dripleaf): those are
     * not dived for.
     */
    private static boolean grows(BlockState state) {
        return kelp(state) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.SEA_PICKLE)
                || state.is(BlockTags.CORAL_BLOCKS) || state.is(BlockTags.CORALS) || state.is(BlockTags.WALL_CORALS);
    }

    /** Still water over the block he swims through, up to the sky: -1 under a roof (a cave's too), in running water or deeper than {@link #DEEP}. */
    private static int depth(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cell = pos.above().mutable();
        for (int blocks = 0; blocks <= DEEP; blocks++, cell.move(0, 1, 0)) {
            var fluid = level.getFluidState(cell);
            boolean open = level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
            if (fluid.isEmpty()) return open && level.canSeeSky(cell) ? blocks : -1;
            if (!fluid.is(FluidTags.WATER) || !fluid.isSource() || !open) return -1;
        }
        return -1;
    }

    /**
     * Leaves he reaches from a stand: a crown high over the ground is no source (a spruce, a tall oak) unless he builds up to
     * it, but any cell he can stand in within the reach of the leaf is — the ground, a stair or ledge the player built, a
     * pillar left standing. A stand on top of a leaf or of the trunk alone is no such reach: nothing walks up there without a
     * column — the rule {@link #standsFor} follows for a leaf too, so the stand the walk is sent to is one it can come down from.
     */
    private static boolean low(CompanionEntity c, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.LEAVES)) return true;
        for (int dy = -4; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos cell = pos.offset(dx, dy, dz);
            BlockState floor = level.getBlockState(cell.below());
            if (floor.is(BlockTags.LEAVES) || floor.is(BlockTags.LOGS)) continue;
            if (Vec3.atBottomCenterOf(cell).distanceToSqr(pos.getCenter()) > STAND_REACH * STAND_REACH) continue;
            if (CompanionMiningNavigation.standable(c, cell)) return true;
        }
        return false;
    }

    /**
     * Whether this block is his to take: from a stand under it (the ground reach of before), or — for a crown above every
     * stand cell (a tree grown with bone meal) — up a short column of his own soil (CompanionPillar). The soil for that column
     * is his to dig when a block that gives dirt stands about him, so a crown is not out of reach for want of soil he can get
     * (the owner, 18.11). Nothing else is a source of his. {@code standOnly} asks for the stand alone: what the gather takes
     * first, before it raises anything — a stair the player built is walked up, not built over (the owner, 18.11).
     */
    private static boolean atHand(CompanionEntity c, ServerLevel level, BlockPos pos, boolean dugSoil, boolean standOnly) {
        boolean stand = low(c, level, pos) && !standsFor(c, level, pos).isEmpty();
        if (standOnly) return stand;
        if (stand) return true;
        if (!c.temporaryPillar.buildCells(pos).isEmpty()) return true;
        return dugSoil && !c.temporaryPillar.buildCellsWithAnySoil(pos).isEmpty();
    }

    /** A leaf that does not fade: set down by a player, his own hedge or roof, and still a leaf a shears gives. */
    private static boolean setDown(BlockState state) {
        return state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT);
    }

    private static boolean column(BlockState state) { return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO) || kelp(state); }

    private static boolean kelp(BlockState state) { return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT); }

    /** One column: the same block, or kelp whose top is another block than its stalk. */
    private static boolean kin(BlockState column, BlockState other) { return other.is(column.getBlock()) || kelp(column) && kelp(other); }

    /**
     * The cells he takes the block from. A block with a body is worked like ore: in sight, and from where its drop is picked up.
     * A flower or a cane has no body for the eye to rest on (the game's sight goes through it): from the ground at it or beside it.
     */
    private static Set<BlockPos> standsFor(CompanionEntity c, ServerLevel level, BlockPos block) {
        Set<BlockPos> cells = new HashSet<>();
        // On the bed of the water: the open water straight over it (depth), the surface the way comes to and the dive down from there.
        // Not the columns beside it: from the floor by a drop-off he could not sink to it (in game 13.11, 45 s on the spot).
        if (wet(level, block)) {
            for (int dy = 1; dy <= DEEP + 1; dy++) {
                BlockPos cell = block.above(dy);
                if (level.getFluidState(cell).is(FluidTags.WATER) && CompanionMiningNavigation.pickupStand(c, cell)) cells.add(cell);
            }
            return cells;
        }
        if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
            boolean leaf = level.getBlockState(block).is(BlockTags.LEAVES);
            for (BlockPos stand : CompanionMiningNavigation.standCells(c, block, STAND_REACH, false, true)) {
                // A leaf's drop falls off a crown or a stair to the ground below: the stand is still his, and the pickup walks
                // down for what fell. Any standable cell within reach counts — a stair the player built, a ledge, a pillar.
                // A cell on top of a leaf or of the trunk is no stand for a leaf: nothing walks up there without a column of
                // his own (the same rule the ground reach follows), so a crown whose only cells are leaf tops is climbed, and
                // the walk is not sent up to a leaf top it cannot come down from (the owner, 18.11).
                if (leaf && (level.getBlockState(stand.below()).is(BlockTags.LEAVES) || level.getBlockState(stand.below()).is(BlockTags.LOGS))) continue;
                if (leaf || CompanionMiningNavigation.dropCollectable(level, block, stand)) cells.add(stand);
            }
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

    /**
     * A crown that stands above every stand cell: he walks to the cells beside the trunk, builds up on his own soil until the
     * block is within a swing, and cuts it from the top of the column — the same supports the wood path raises (CompanionPillar),
     * never a teleport. False when he cannot build up to it at all: no soil, a ceiling, or a crown too high for six steps.
     */
    private boolean climb() {
        // Already within a swing from the top of his own column: no more steps up, the crown is cut from here.
        if (c.temporaryPillar.isActive() && c.temporaryPillar.inReach(target)) { stands = Set.of(c.blockPosition()); return true; }
        Set<BlockPos> feet = c.temporaryPillar.buildCells(target);
        if (feet.isEmpty()) return false;
        // The pillar plans its column beside him: from farther off he walks to the trunk's foot first, as the wood path walks
        // to a tree. While it already stands he lets it plan from where he is.
        if (!c.temporaryPillar.isActive() && !feet.contains(c.blockPosition())) return approach(feet);
        CompanionPillar.Outcome outcome = c.temporaryPillar.tickToward(target, false);
        if (outcome == CompanionPillar.Outcome.FAILED) return false;
        if (outcome == CompanionPillar.Outcome.READY) { stands = Set.of(c.blockPosition()); return true; }
        c.workStatus("Поднимаюсь на опоре: " + name() + ".");
        return true;
    }

    /** Walking to the cells beside the trunk, as {@link #walk()} walks to a stand: false when there is no way there. */
    private boolean approach(Set<BlockPos> feet) {
        // The leg already being walked is the way there: searched again only when it is over, as a walk to a stand is. Asked
        // afresh every tick it barely moved him — eight ticks of a walk are not eight blocks (the stair he cannot climb, 18.11).
        if (path != null && !w.route.legOver()) return true;
        if (++legs > LEGS) return false;
        CompanionRoute.Reach reach = w.route.cells(feet, 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return ++waited < WAIT; }
        if (!reach.found()) return false;
        waited = 0;
        w.route.walk(reach.path());
        c.workStatus("Иду к стволу: " + name() + ".");
        return true;
    }

    /** His own column is taken back before the drops on the ground are walked to: the pickup waits while it stands. */
    private boolean down() {
        if (!c.temporaryPillar.isActive()) return false;
        boolean stood = c.temporaryPillar.hasSupports();
        c.temporaryPillar.requestCleanup();
        return stood;
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

    /** Whether a block that gives dirt is about him now: looked for once, the soil a crown's column is dug from. */
    private boolean soilAbout(ServerLevel level) {
        if (!soilAboutKnown) { soilAbout = soilAbout(c, level); soilAboutKnown = true; }
        return soilAbout;
    }

    void tick(ServerLevel level) {
        int got = got();
        if (got >= wanted) { end(true, "Собрал: «" + name() + "», " + got + " шт."); return; }
        if (due >= wanted) { down(); collect(level, got); return; }
        Predicate<BlockPos> site = sites(c, level);
        if (target != null && (gives(sources, level.getBlockState(target)) == 0 || !takes(level, target, site) || lavaBeside(level, target))) release();
        if (target == null) {
            // A leaf a stand serves is cut from it first: the stair the player built is walked up and what is in reach of it
            // is taken, and no column is raised beside it. A tree's own leaves come before the ones he set down (the owner, 18.11).
            target = find(c, level, sources, site, passed, lavaKept, false, true, true);
            if (target == null) target = find(c, level, sources, site, passed, lavaKept, false, false, true);
            // No leaf a stand serves: a crown above every stand cell is climbed on the soil he carries or digs.
            if (target == null) target = find(c, level, sources, site, passed, lavaKept, soilAbout(level), true, false);
            if (target == null) target = find(c, level, sources, site, passed, lavaKept, soilAbout(level), false, false);
        }
        if (target == null) {
            // On his own column nothing else is in reach: the soil comes back before the drops on the ground are walked to.
            if (down()) return;
            // What he broke and has not picked up yet is picked up before he says how far he got.
            if (due > got) { collect(level, got); return; }
            String rest = passed.isEmpty() ? "больше рядом нет" : "до остального не добрался";
            String lava = lavaKept.isEmpty() ? "" : " Рядом с лавой " + CompanionWorkItems.blocks(lavaKept.size()) + " — не трогал.";
            // Some gathered: obtain_item plans the rest again (red dye goes on with the tulips once the poppies are out). None: it ends here.
            if (got > 0) { release(); w.finish(CompanionActionResult.failed("Собрал «" + name() + "»: " + got + " из " + wanted + ", " + rest + "." + lava)); return; }
            String why = (passed.isEmpty() ? "Рядом не нашёл, где взять «" + name() + "»." : "Вижу, где взять «" + name() + "», но подойти не смог.") + lava;
            // Leaves he saw but could not come at, with the soil for a column lying about him: the chain is planned again and
            // digs that soil first (the plan made from where he stood could not see the way to the stair). A blocked end would
            // give the order up on the spot (the stair he cannot climb, 18.11).
            if (!passed.isEmpty() && soilAbout(level)) { release(); w.finish(CompanionActionResult.failed(why)); return; }
            end(false, why);
            return;
        }
        BlockState state = level.getBlockState(target);
        boolean shorn = shorn(sources, state);
        boolean silked = silked(sources, state);
        if (shorn && w.items.countCarried(Items.SHEARS) == 0) {
            if (due > got) { collect(level, got); return; }
            // His shears wore out on the way: obtain_item plans the rest again, new shears first.
            if (got > 0) { release(); w.finish(CompanionActionResult.failed("Собрал «" + name() + "»: " + got + " из " + wanted + ", ножницы сломались.")); return; }
            end(false, "Ножниц нет: срезать «" + name() + "» нечем.");
            return;
        }
        if (stands.isEmpty()) stands = standsFor(c, level, target);
        if (stands.isEmpty()) {
            // A crown above every stand cell: he builds up to it on the wood path's own soil supports, cuts it from the top
            // of the column, and the soil comes back when he is down (requestCleanup, the descent of RecoverSupportsGoal).
            if (!climb()) { passed.add(target); release(); }
            return;
        }
        if (!stands.contains(c.blockPosition())) {
            // He is on his own column and this block needs the ground: down first, then the walk, not off the edge. A column
            // merely planned is no reason to take it down: the climb to it is what is wanted, and a cleanup would refuse it
            // this tick (the stair he cannot climb, 18.11).
            if (!c.temporaryPillar.climbingTo(target) && c.temporaryPillar.hasSupports() && down()) return;
            if (!walkFailed) {
                c.workStatus("Иду за: " + name() + ".");
                if (walk()) return;
                // A stand he saw but no way to it (a stair behind a wall, a ledge across a gap): up a column of his own if the
                // soil for one is there, else the leaf is passed over and the next one about is tried (the owner, 18.11).
                // The way to the column's foot is another walk: the legs the failed walk spent are no reason to give the leaf
                // up before that walk is tried, and the walk to the same stands is not searched again while the climb goes on
                // (a stair he cannot climb, the owner 18.11).
                walkFailed = true;
                legs = waited = 0;
            }
            if (climb()) return;
            passed.add(target);
            release();
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
            c.takeToolForBlock(state);
            if (!c.getMainHandItem().is(Items.SHEARS) && !CompanionTools.equip(c, net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR, state)) { c.putToolAway(); end(false, "Не могу взять ножницы в руку: в сумке нет места."); return; }
        } else if (silked) {
            // The block itself, not its drop: only Silk Touch gives it, and a plain tool of the kind would give the drop.
            if (!c.takeSilkToolForBlock(state)) { c.putToolAway(); end(false, "Для «" + name() + "» нужен инструмент с «Шёлковым касанием»: в сумке такого нет."); return; }
        } else if (w.items.prepareMiningTool(state)) return;
        else c.takeToolForBlock(state);
        ItemStack tool = c.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) { c.putToolAway(); end(false, "Нечем взять «" + name() + "»: нужен инструмент."); return; }
        if (!w.items.canStore(List.of(new ItemStack(item)))) { c.putToolAway(); end(false, FULL_BAG + (got == 0 ? "" : " Собрал " + got + " из " + wanted + ".")); return; }
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
        // The shears stay in his hand for the whole crown: the player sees the tool that cuts, and the hand he had before comes
        // back once, when the work ends (CompanionWork.cancel) — not after every leaf (the owner, 18.11).
        if (!shorn) c.putToolAway();
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

    private void release() { target = null; stands = Set.of(); path = null; walkFailed = false; legs = waited = dived = 0; w.breaking.reset(); w.resetApproach(); }

    /** A failure ends obtain_item at once with what he says (BLOCKED): planned again, the same ground was looked over twice more. */
    private void end(boolean success, String message) {
        release();
        w.finish(success ? CompanionActionResult.ok(message) : CompanionActionResult.blocked(message));
    }
}
