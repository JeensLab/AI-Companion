package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.ArrayDeque;
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
 * the overworld, but for the Nether's own stone there (netherrack, basalt; never beside lava); the End is later work. Granite, tuff
 * and the other stone dug by name are taken where a face of them shows, not out of the ground. What grows on the bed of the water (kelp, seagrass, a sea pickle, a coral
 * block) he swims over and dives for, as for a foe under water (CompanionSwimming): under open water no deeper than his breath
 * takes him, never under a roof; what only lies under water (clay in a pond) is left for the dry kind. A crown above every
 * stand cell is no block out of reach: he builds up to it on his own soil (CompanionPillar) and cuts it from the top.
 * What a stand serves is cut from the stand first — the stair a player built is walked up, not built over — and a leaf no
 * stand he can walk to serves is climbed for, the soil dug first when the stair cannot be climbed (the owner, 18.11).
 */
final class CompanionPicking {
    static final String FULL_BAG = "Сумка забита — некуда класть.";
    /**
     * How far above and below him a target is looked for: as deep as the one search (CompanionWaySearch) looks, so a block it
     * promised hanging in the air or lying under him is a block this gather can find (PATH7).
     */
    private static final int DEPTH = CompanionWaySearch.DEPTH;
    /** Ticks the drops are given to be picked up before another try, and then before they are counted lost. */
    private static final int SETTLE = 40;
    /** Walks to one block before it is passed over. */
    private static final int LEGS = 8;
    /** Ticks a search for the way may not come about (he is in the air, in running water) before the block is passed over. */
    private static final int WAIT = 100;
    /** Ticks the last step into the cell is given: he walks the last cells to it himself, as a player does. */
    private static final int LAST_STEP = 20;
    /**
     * How near the cell's middle counts as «the game's own last step»: its path counts a node reached from up to two blocks off
     * (a walk to a column's foot ended two cells short and the walks ran out: the last cells are his own, within the ticks above).
     */
    private static final double LAST_STEP_REACH = 9.0D;
    /** Blocks of water over a block on the bed he dives for: half his breath down and up again, as the dive for a foe. */
    private static final int DEEP = 10;
    /** A player's reach, from the eyes to the middle of the block. */
    private static final double REACH = 4.5;
    /** How far from a leaf a cell he works it from may be: the same reach the stand cells are searched in. */
    private static final double STAND_REACH = 3.5;
    /** Ticks over the block, down and up for air, before it is passed over. */
    private static final int DIVE = 900;
    /**
     * Searches for a way to the stone dug by name that found none, before he says so. Each marks the hollow behind the face, so its
     * other faces are passed without another search: this counts the hollows (and the faces of a cave too big to mark), not faces.
     */
    private static final int STONE_TRIES = 8;
    /** Open cells a hollow is marked up to: more is no hollow but the open ground or a cave system, and only the face's cells are. */
    private static final int HOLLOW = 2048;

    private final CompanionWork w;
    /** The work is made before it knows its owner: taken at the order. */
    private CompanionEntity c;
    private Item item;
    private List<CompanionPickable.Source> sources = List.of();
    /** The order is for the stone dug by name (granite, tuff…). */
    private boolean stone;
    /** Searches for a way to a stone face that found none, and the cells no way leads to: the hollows behind those faces. */
    private int missed;
    private final Set<BlockPos> unreached = new HashSet<>();
    /** The last walk ended because the search found no way (not a wait, not walks without progress). */
    private boolean noWay;
    /** Ordered, in his bag at the order, dropped by the blocks he broke (picked up or not). */
    private int wanted, before, due;
    private long settleAt;
    private boolean retried;
    private BlockPos target;
    /** The target is a block the player set down himself: the order named it, so it is his to take (no column of soil is the way's own). */
    private boolean ownTarget;
    /** The rule for a target the player set down himself: his build keeps every block but one of the item the order named. */
    private Predicate<BlockPos> ownSite = NOTHING_KEPT;
    /** The cells he takes the target from, his way to them and the walks so far. */
    private Set<BlockPos> stands = Set.of();
    /** A leaf order is the leaves of any kind: the species it named first, the leaves of any tree or hedge about after it (any #minecraft:leaves). */
    private boolean leafOrder;
    /** The sources of the species the order named: tried before the leaves of other kinds. */
    private List<CompanionPickable.Source> speciesSources = List.of();
    /** Every leaf block about, whatever species: what a leaf order falls through to when the tree it named cannot give it. */
    private List<CompanionPickable.Source> leafSources = List.of();
    /** The gather takes the leaves with the shears: the tool stays in his hand until the order ends, also while a column comes down. */
    private boolean shearsGather;
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
    /** The target whose column decision is already in the log: one decision per block, not one per tick. */
    private BlockPos decided;

    CompanionPicking(CompanionWork w) { this.w = w; }

    /** gather_block: {@code count} more of the item in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, int count) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        ServerLevel level = c.level() instanceof ServerLevel here ? here : null;
        speciesSources = asked == null || level == null ? List.of() : allowed(level, CompanionPickable.sources(level, asked));
        // A block that is the item itself (a planks block, a lapis block, a cobblestone the player hung) is taken as it lies, as
        // much as the blocks that drop it: the game's own block-to-item map, never a list of names.
        if (asked != null && level != null) speciesSources = merge(speciesSources, allowed(level, CompanionPickable.identity(level, asked)));
        // A leaf order is the leaves of any tree or hedge about, whatever species it named: the named one is tried first, and
        // another kind stands in for the part it cannot give (any #minecraft:leaves) before the order is refused.
        leafOrder = asked != null && level != null && new ItemStack(asked).is(ItemTags.LEAVES);
        leafSources = leafOrder ? allowed(level, CompanionPickable.leaves(level)) : List.of();
        sources = leafOrder ? leafSources : speciesSources;
        shearsGather = leafOrder || asked != null && level != null && CompanionPickable.shorn(level, asked);
        ownSite = asked == null || level == null ? NOTHING_KEPT : keptUnlessOwn(c, level, asked);
        if (sources.isEmpty()) return CompanionActionResult.failed("Такое я с земли не собираю: " + id + ".");
        item = asked; wanted = count; before = leafOrder ? CompanionLeaves.carried(c) : w.items.countCarried(item); due = 0; settleAt = 0; retried = false;
        stone = sources.stream().allMatch(source -> CompanionPickable.stone(source.block()));
        missed = 0;
        release();
        passed.clear(); lavaKept.clear(); unreached.clear(); soilAboutKnown = false;
        return null;
    }

    /** What the broken blocks dropped so far: progress for the watchdog while the drops are walked to. */
    int due() { return due; }

    /** What he broke is not in his bag yet: descending his own column or walking to the drop is not standing still. */
    boolean waitingForHisDrop() { return due > got(); }

    String jobName() { return "сбор: «" + name() + "» (" + got() + " из " + wanted + ")"; }

    private String name() { return leafOrder ? "Листва" : new ItemStack(item).getHoverName().getString(); }

    /** «5 блоков вверх и 6 вбок»: the nearest block he saw and no way came at — the real reason of a refusal, never a name. */
    private String seenAt() {
        return passed.stream().min(java.util.Comparator.comparingDouble(pos -> c.distanceToSqr(pos.getCenter())))
                .map(pos -> CompanionReach.distance(c, pos)).orElse("");
    }

    /** What the order has brought: for a leaf order every kind of leaf in the bag counts, as the order itself counts them. */
    private int got() { return Math.max(0, (leafOrder ? CompanionLeaves.carried(c) : w.items.countCarried(item)) - before); }

    /** What the order has brought so far: said when it is cut short (the errand's watch in the Nether). */
    String progress() { return "в сумке прибавилось " + got() + " «" + CompanionWork.itemName(item) + "»."; }

    /** Whether one of these blocks stands within his search radius where he may take it. */
    static boolean near(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> sources) {
        List<CompanionPickable.Source> here = allowed(level, sources);
        if (here.isEmpty()) return false;
        Predicate<BlockPos> site = kept(c, level);
        if (find(c, level, here, site, Set.of(), new HashSet<>(), false, false, false) != null) return true;
        // A crown above every stand cell is still his when the soil for the column can be dug about him (the owner, 18.11).
        return find(c, level, here, site, Set.of(), new HashSet<>(), true, false, false) != null && soilAbout(c, level);
    }

    /**
     * Whether a block about him is his to take at all: not the player's building, not the stone the player set down, ripe,
     * dry, not beside lava, off solid ground — the gather's own rule, for the one search (CompanionWaySearch) to ask.
     */
    static Predicate<BlockPos> mine(CompanionEntity c, ServerLevel level) {
        Predicate<BlockPos> site = kept(c, level);
        return pos -> takes(level, pos, site) && grounded(c, level, pos) && !lavaBeside(level, pos);
    }

    /** No cell is the player's own: the rule of an order that named the block itself (see {@link #requested}). */
    private static final Predicate<BlockPos> NOTHING_KEPT = pos -> false;

    /**
     * Whether the block is the item's own: it IS the item (a pumpkin, a lapis block, the cobblestone he hung) or a block a recipe
     * of one makes it of (a lapis block for nine lapis, an iron block for ingots). Such a block is the order's target wherever it
     * stands, the player's own build or not. A block that merely gives the item when broken — the poppy for red dye, the clay for
     * clay balls — is a source, and the flowers by the player's chest stay his (the owner, 18.11).
     */
    static boolean ownBlock(ServerLevel level, Item item, BlockState state) {
        for (CompanionPickable.Source source : CompanionPickable.identity(level, item)) if (state.is(source.block())) return true;
        for (CompanionPickable.Source source : CompanionPickable.storage(level, item)) if (state.is(source.block())) return true;
        return false;
    }

    /** What the player's own build keeps: his site and his stone — but never a block of the item itself. */
    private static Predicate<BlockPos> keptUnlessOwn(CompanionEntity c, ServerLevel level, Item item) {
        Predicate<BlockPos> kept = kept(c, level);
        return pos -> kept.test(pos) && !ownBlock(level, item, level.getBlockState(pos));
    }

    /**
     * The gather's rule for an explicit order, named for the search's line: a block of the item itself is no reason to pass it
     * over because the player set it down — «do not break the player's build» governs the soil a way lays under his feet and the
     * flowers by his chest, never the target the order named (the owner: he hung a pumpkin above his own base and «добудь тыкву»
     * found nothing). What still keeps a block is lava beside it, a drop that would burn, and the rest of the gather's own rules.
     */
    static CompanionWaySearch.Mine requested(CompanionEntity c, ServerLevel level, Item item) {
        return requested(c, level, item, true);
    }

    /**
     * The same rule for an item the chain only passes through ({@code takeOwn} false): the order named another thing, so the
     * player's own build keeps its blocks — the flowers by his chest stay his for a dye made of the wild bush (the owner, 18.11).
     */
    static CompanionWaySearch.Mine requested(CompanionEntity c, ServerLevel level, Item item, boolean takeOwn) {
        Predicate<BlockPos> own = kept(c, level);
        Predicate<BlockPos> keptRule = takeOwn ? keptUnlessOwn(c, level, item) : own;
        return new CompanionWaySearch.Mine() {
            @Override public String why(BlockPos pos) {
                if (lavaBeside(level, pos)) return LAVA;
                if (!grounded(c, level, pos) || !takes(level, pos, keptRule)) return OTHER;
                return null;
            }
            @Override public boolean own(BlockPos pos) { return own.test(pos); }
        };
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
        return nearestLeaf(c, level, all, false);
    }

    /** The same, {@code naturalOnly} leaving out the leaves a player set down: only the crowns a tree grew. */
    private static BlockPos nearestLeaf(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> all, boolean naturalOnly) {
        if (all.isEmpty()) return null;
        Predicate<BlockPos> site = sites(c, level);
        boolean dug = soilAbout(c, level);
        for (boolean natural : naturalOnly ? new boolean[]{true} : new boolean[]{true, false})
            for (boolean soil : new boolean[]{false, true}) {
                if (soil && !dug) continue;
                BlockPos found = find(c, level, all, site, Set.of(), new HashSet<>(), soil, natural, false);
                if (found != null) return found;
            }
        return null;
    }

    /**
     * The leaf the gather comes at first, in the gather's own order: the named species' own crown, another tree's or hedge's
     * leaves about, the named one's leaves a player set down, then any set-down leaf — so the plan names the species it really
     * cuts (the soil for a crown is sized for it, CompanionLeaves.leafTarget). A kind whose reachable leaves cannot give the
     * order is passed over for one that can: a small crown beside a hedge that meets the count is no reason to come short
     * (the owner, 19.11). When none of them meets it, the first found stands as before.
     */
    static BlockPos firstLeaf(CompanionEntity c, ServerLevel level, Item requested, int count) {
        List<CompanionPickable.Source> named = requested == null ? List.of()
                : allowed(level, CompanionPickable.sources(level, requested)).stream()
                        .filter(source -> source.block().defaultBlockState().is(BlockTags.LEAVES)).toList();
        List<CompanionPickable.Source> all = allowed(level, CompanionPickable.leaves(level));
        BlockPos first = null;
        for (boolean natural : new boolean[]{true, false})
            for (List<CompanionPickable.Source> from : List.of(named, all)) {
                BlockPos found = nearestLeaf(c, level, from, natural);
                if (found == null) continue;
                if (first == null) first = found;
                if (reachable(c, level, from, natural) >= count) return found;
            }
        return first;
    }

    /**
     * How many leaves of these sources the gather can come at now: from a stand under them or up a column on the soil about
     * him, as {@link #atHand} takes them. The count an order asks for is what a kind must be able to give before it is named
     * over another kind that can (the owner, 19.11).
     */
    private static int reachable(CompanionEntity c, ServerLevel level, List<CompanionPickable.Source> from, boolean natural) {
        Predicate<BlockPos> site = sites(c, level);
        boolean dug = soilAbout(c, level);
        int found = 0;
        for (BlockPos pos : CompanionLogSearch.find(level, c.blockPosition(), c.searchRadius, BlockTags.LEAVES)) {
            BlockState state = level.getBlockState(pos);
            if (gives(from, state) == 0 || natural && setDown(state) || !c.errand.within(pos) || !takes(level, pos, site)
                    || !grounded(c, level, pos) || lavaBeside(level, pos) || !atHand(c, level, pos, dug, false)) continue;
            found++;
        }
        return found;
    }

    /**
     * The block to take from these sources: a tree's own leaves from a stand he can walk to first (the stair the player built
     * is walked up, not built over), then a tree's own up a column of his soil; {@code naturalOnly} false adds the leaves a
     * player set down the same two ways, and they are asked for only after every natural leaf about (the owner, 18.11, 19.11).
     */
    private BlockPos pick(ServerLevel level, Predicate<BlockPos> site, List<CompanionPickable.Source> from, boolean dug, boolean naturalOnly) {
        BlockPos found = find(c, level, from, site, passed, lavaKept, false, naturalOnly, true);
        if (found == null) found = find(c, level, from, site, passed, lavaKept, dug, naturalOnly, false);
        return found;
    }

    /** Whether a block of soil stands within the reach the dig works in, where he may take it: the one reach answer's soil (CompanionReach). */
    static boolean soilAbout(CompanionEntity c, ServerLevel level) { return CompanionReach.soilAbout(c, level); }

    /**
     * The soil he must dig for the crown of these leaves the order will come at: the steps of the longest column among the
     * leaves no stand he can walk to serves, less what he carries. 0 when the stands serve the whole order or the bag already
     * has the soil; a leaf no column reaches is not counted (the order is refused for its height then). A stair that serves only
     * part of a crown is no reason to pass the far leaves over: the dig is sized for the leaves that remain reachable, not only
     * for the first (the owner, 19.11).
     */
    static int soilToDig(CompanionEntity c, ServerLevel level, String item, int count) {
        var id = ResourceLocation.tryParse(item == null ? "" : item);
        var leaf = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (leaf == null) return 0;
        List<CompanionPickable.Source> all = allowed(level, CompanionPickable.sources(level, leaf)).stream()
                .filter(source -> source.block().defaultBlockState().is(BlockTags.LEAVES)).toList();
        Predicate<BlockPos> site = sites(c, level);
        List<BlockPos> candidates = CompanionLogSearch.find(level, c.blockPosition(), c.searchRadius, BlockTags.LEAVES).stream()
                .filter(pos -> gives(all, level.getBlockState(pos)) > 0 && c.errand.within(pos) && takes(level, pos, site)
                        && grounded(c, level, pos) && !lavaBeside(level, pos))
                .toList();
        // The leaves the order comes at: those a stand serves need no soil, and the leaf the gather comes at first is walked to,
        // so a stair he cannot climb (a step of two, a wall across it) leaves it to a column. The leaves above every stand cell
        // are climbed for: the dig is sized for the longest of those columns, so a stair that serves only part of a crown is no
        // reason to pass the far leaves over (the owner, 19.11).
        int byStand = 0;
        for (BlockPos found : candidates) if (low(c, level, found)) byStand++;
        int steps = 0;
        BlockPos first = find(c, level, all, site, Set.of(), new HashSet<>(), false, true, true);
        if (first == null) first = find(c, level, all, site, Set.of(), new HashSet<>(), false, false, true);
        if (first == null) first = nearestLeaf(c, level, all);
        if (byStand >= count && first != null) {
            Set<BlockPos> stands = standsFor(c, level, first);
            // The stands serve the whole order and the first of them is walked to: no column, no soil.
            if (!stands.isEmpty() && CompanionMiningNavigation.canWalkTo(c, c.blockPosition(), stands)) return 0;
            steps = c.temporaryPillar.columnSteps(first);
        }
        for (BlockPos found : candidates) {
            if (low(c, level, found) || c.temporaryPillar.buildCellsWithAnySoil(found).isEmpty()) continue;
            steps = Math.max(steps, c.temporaryPillar.columnSteps(found));
        }
        if (steps == 0) return 0;
        // No soil he can really dig (the dig's own reach, not the search radius of the order): the leaf is passed over for the
        // one beside it, as it was — a leaf on a stone shelf with the dirt twenty blocks off (the owner, 18.11).
        if (!soilAbout(c, level)) return 0;
        return Math.max(0, steps - c.temporaryPillar.soilCarried());
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
    static List<CompanionPickable.Source> allowed(ServerLevel level, List<CompanionPickable.Source> all) {
        if (level.dimension() == Level.OVERWORLD) return all.stream().filter(source -> !CompanionPickable.nether(source.block())).toList();
        return level.dimension() == Level.NETHER ? all.stream().filter(source -> CompanionPickable.nether(source.block())).toList() : List.of();
    }

    /** The Nether's stone is taken only off solid ground, never the block he stands on: a floor over a cave or a lava lake stays shut. */
    static boolean grounded(CompanionEntity c, ServerLevel level, BlockPos pos) {
        if (!CompanionPickable.nether(level.getBlockState(pos).getBlock())) return true;
        return !pos.equals(c.blockPosition().below()) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    static boolean lavaBeside(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) if (CompanionLava.at(level, pos.relative(side))) return true;
        return false;
    }

    /** What he leaves where it stands: the player's buildings, and the stone the player set down anywhere (a path, a wall of andesite). */
    /** The cells that are the player's own build: never dug for a way (the wall in the way is walked round, not broken). */
    static Predicate<BlockPos> kept(CompanionEntity c, ServerLevel level) {
        if (c.getOwnerId() == null) return pos -> false;
        Predicate<BlockPos> site = sites(c, level), stone = CompanionPlayerBuilds.get(level.getServer()).placedStone(c.getOwnerId(), level);
        return pos -> site.test(pos) || stone.test(pos);
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

    /** The blocks of the second list added to the first, one source per block: a block is taken once, whatever names it. */
    private static List<CompanionPickable.Source> merge(List<CompanionPickable.Source> first, List<CompanionPickable.Source> second) {
        if (second.isEmpty()) return first;
        List<CompanionPickable.Source> all = new ArrayList<>(first);
        for (CompanionPickable.Source source : second) {
            boolean known = false;
            for (CompanionPickable.Source has : all) if (has.block() == source.block()) { known = true; break; }
            if (!known) all.add(source);
        }
        return all;
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

    /** A block of the kind he may take now: dry, not on a site of the player's (but the fruit of a stem, and leaves of any kind), ripe, and not the root of a growing column. */
    static boolean takes(ServerLevel level, BlockPos pos, Predicate<BlockPos> site) {
        BlockState state = level.getBlockState(pos);
        // Stone dug by name where a face of it shows, as a player sees it: one hidden in the ground is not dug for (the player 17.11);
        // nor one that holds a torch or a pond back, as gather_stone leaves them.
        if (CompanionPickable.stone(state.getBlock()) && (!shows(level, pos) || CompanionLighting.supportsLight(level, pos) || CompanionLava.floods(level, pos, List.of())))
            return false;
        // A leaf is no building of the player's: a tree's own crown and the leaves he set down alike stand on a site of his (a
        // tree beside the house is not his wall). The gather takes a tree's own leaves first and a leaf the player set down
        // only when no natural leaf is reachable for the order, so a hedge beside a working tree stands (the owner, 18.11, 19.11).
        if (site.test(pos) && !CompanionCrops.onStem(level, pos) && !state.is(BlockTags.LEAVES)) return false;
        // Clay on the bed of a pond has no water in its own cell: the water over it counts too. Only what grows there is dived for.
        // A coral block under another one, or a sea pickle sat on it, is not under open water: none of it is dived for.
        if (wet(level, pos) ? !grows(state) || depth(level, pos) < 0 : state.is(BlockTags.CORAL_BLOCKS)) return false;
        // A rose bush is taken by its foot: the top half gives nothing, and the foot it brings down would lie there unclaimed.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
        if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
        // A leaf that does not fade was set down by someone: a hedge, a roof of leaves. It is still leaves, and its own item
        // comes to shears the same (the owner, 18.11: the leaves beside him were his own); it is the last the gather comes at,
        // after every tree's own leaf about.
        // A hanging vine from its lower end: the ones under a cut fall with nothing to pick up.
        if (state.is(Blocks.VINE) && level.getBlockState(pos.below()).is(Blocks.VINE)) return false;
        // The block above the root, within his reach from the ground: the root grows again.
        if (column(state)) return kin(state, level.getBlockState(pos.below())) && !kin(state, level.getBlockState(pos.below(2)));
        return true;
    }

    /** A face of the block is open: nothing solid on one of its sides. */
    private static boolean shows(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) if (level.getBlockState(pos.relative(side)).getCollisionShape(level, pos.relative(side)).isEmpty()) return true;
        return false;
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
        if (dugSoil && !c.temporaryPillar.buildCellsWithAnySoil(pos).isEmpty()) return true;
        // A block no stand cell and no short column reaches: the one reach answer's way up, which may take sixteen steps
        // (PATH7: a pumpkin hanging ten blocks above was never taken). A leaf crown keeps its short column (CompanionLeaves).
        return !level.getBlockState(pos).is(BlockTags.LEAVES)
                && CompanionReach.how(c, level, pos, STAND_REACH, true, dugSoil, CompanionPillar.WAY_SUPPORTS).up();
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
        // The game counts the last node reached from a block or two off: at a trunk he stood a step short of the cell, and the walks
        // ran out in eight ticks (the vine on the trunk in the test). Stuck on the lip of the last cell counts as the same end: that
        // last step is his own, walked straight to the cell's middle.
        if (path != null && route.legOver() && path.getEndNode() != null && stands.contains(path.getEndNode().asBlockPos())) {
            Vec3 end = Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());
            if (c.distanceToSqr(end) < LAST_STEP_REACH && ++waited < LAST_STEP) { c.getMoveControl().setWantedPosition(end.x, end.y, end.z, .7); return true; }
        }
        if (stands.isEmpty() || ++legs > LEGS) return false;
        CompanionRoute.Reach reach = route.cells(stands, 0);
        // He is in the air or the search goes on: asked again at the next tick.
        // Not for ever: under a ledge in running water no search came about and he stood there until the watchdog (in game 12.11).
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return ++waited < WAIT; }
        if (!reach.found()) { noWay = true; return false; }
        waited = 0;
        path = reach.path();
        route.walk(path);
        return true;
    }

    /**
     * A crown that stands above every stand cell: he walks to the cells beside the trunk, builds up on his own soil until the
     * block is within a swing, and cuts it from the top of the column — the same supports the wood path raises (CompanionPillar),
     * never a teleport. A leaf crown keeps its short column; any other block a hand gives (a pumpkin, a cane) may take the way
     * up's sixteen steps (PATH7). False when he cannot build up to it at all: no soil, a ceiling, or a block too high.
     */
    private boolean climb(ServerLevel level) {
        // Every climb is a decision of the way, and the player reads it in the game log: the block, how far up and across,
        // and the answer — a column, a floor or none with its reason — once for this target, never one line per tick.
        if (!target.equals(decided)) {
            decided = target.immutable();
            CompanionReach.decision(c, target, CompanionReach.how(c, level, target, STAND_REACH, true, false,
                    level.getBlockState(target).is(BlockTags.LEAVES) ? CompanionPillar.LEAF_SUPPORTS : CompanionPillar.WAY_SUPPORTS));
        }
        // A floor across the gap already being laid is ticked before anything else: its plan is kept while it stands, and the
        // material of its cells is already in the world (no plan is made anew for a floor that is laid).
        if (c.temporaryBridge.isActive() || c.temporaryBridge.isRecovering()) return bridge(level);
        // Already within a swing from the top of his own column: no more steps up, the crown is cut from here.
        if (c.temporaryPillar.isActive() && c.temporaryPillar.inReach(target)) { stands = Set.of(c.blockPosition()); return true; }
        int limit = level.getBlockState(target).is(BlockTags.LEAVES) ? CompanionPillar.LEAF_SUPPORTS : CompanionPillar.WAY_SUPPORTS;
        Set<BlockPos> feet = c.temporaryPillar.buildCells(target, limit, c.temporaryPillar.soilCarried());
        if (feet.isEmpty()) {
            // No column reaches it: a floor laid across the gap between him and it, the same geometry answer (PATH7: «мост»).
            // A stand cell that reaches it but no walk does (a gap, a wall) is the same case.
            CompanionReach.How how = CompanionReach.how(c, level, target, STAND_REACH, true, false, CompanionPillar.WAY_SUPPORTS);
            CompanionReach.decision(c, target, how);
            CompanionReach.BridgePlan plan = (how.way() == CompanionReach.Way.WALK || how.way() == CompanionReach.Way.ACROSS)
                    ? CompanionReach.bridge(c, level, target, STAND_REACH, true, CompanionPillar.WAY_SUPPORTS, false) : null;
            if (how.bridge() || plan != null && plan.length() > 0) return bridge(level);
            dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "climb_refused", "entity", c.getUUID(), "target", target,
                    "reason", how.why(), "limit", limit, "soil", c.temporaryPillar.soilCarried());
            return false;
        }
        // The pillar plans its column beside him: from farther off he walks to the trunk's foot first, as the wood path walks
        // to a tree. While it already stands he lets it plan from where he is.
        if (!c.temporaryPillar.isActive() && !feet.contains(c.blockPosition())) {
            boolean walked = approach(feet);
            if (!walked) dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "climb_refused", "entity", c.getUUID(),
                    "target", target, "reason", "no way to the feet", "feet", feet.size(), "legs", legs, "waited", waited);
            return walked;
        }
        CompanionPillar.Outcome outcome = c.temporaryPillar.tickToward(target, false, limit);
        if (outcome == CompanionPillar.Outcome.FAILED) {
            dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "climb_refused", "entity", c.getUUID(), "target", target,
                    "reason", c.temporaryPillar.failureReason(), "soil", c.temporaryPillar.soilCarried(), "limit", limit);
            return false;
        }
        if (outcome == CompanionPillar.Outcome.READY) { stands = Set.of(c.blockPosition()); return true; }
        c.workStatus("Поднимаюсь на опоре: " + name() + ".");
        return true;
    }

    /** The floor across the gap to a block no column reaches: laid, crossed, and taken back when he is home. */
    private boolean bridge(ServerLevel level) {
        CompanionBridge.Outcome outcome = c.temporaryBridge.tickToward(target);
        if (outcome == CompanionBridge.Outcome.FAILED) {
            dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "bridge_refused", "entity", c.getUUID(), "target", target,
                    "reason", c.temporaryBridge.failureReason());
            return false;
        }
        if (outcome == CompanionBridge.Outcome.READY) { stands = Set.of(c.blockPosition()); return true; }
        c.workStatus("Мостю провал: " + name() + ".");
        return true;
    }

    /** Walking to the cells beside the trunk, as {@link #walk()} walks to a stand: false when there is no way there. */
    private boolean approach(Set<BlockPos> feet) {
        // The leg already being walked is the way there: searched again only when it is over, as a walk to a stand is. Asked
        // afresh every tick it barely moved him — eight ticks of a walk are not eight blocks (the stair he cannot climb, 18.11).
        if (path != null && !w.route.legOver()) return true;
        // The last step into the cell, as walk() takes it: the game stops a node a block or two off — or stuck on its lip — and the
        // column's foot is his own.
        if (path != null && w.route.legOver() && path.getEndNode() != null && feet.contains(path.getEndNode().asBlockPos())) {
            Vec3 end = Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());
            if (c.distanceToSqr(end) < LAST_STEP_REACH && ++waited < LAST_STEP) { c.getMoveControl().setWantedPosition(end.x, end.y, end.z, .7); return true; }
        }
        if (++legs > LEGS) return false;
        CompanionRoute.Reach reach = w.route.cells(feet, 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return ++waited < WAIT; }
        if (!reach.found()) return false;
        waited = 0;
        // The way there is kept, as walk() keeps it: the last step into the cell is walked from it when the leg is over.
        path = reach.path();
        w.route.walk(path);
        c.workStatus("Иду к стволу: " + name() + ".");
        return true;
    }

    /** His own column is taken back before the drops on the ground are walked to: the pickup waits while it stands. */
    private boolean down() {
        if (c.temporaryBridge.isActive() || c.temporaryBridge.isRecovering()) {
            // A floor across a gap is the walk to the drops it was laid for: it comes back only once they are in his bag.
            if (!c.temporaryBridge.isRecovering() && waitingForHisDrop()) return false;
            c.temporaryBridge.requestCleanup();
            return c.temporaryBridge.hasSupports();
        }
        if (!c.temporaryPillar.isActive()) return false;
        boolean stood = c.temporaryPillar.hasSupports();
        // The gather's own tool stays in his hand while the column comes down: the shears cut the next leaf, not the hand the
        // column replaced (the owner, 19.11).
        c.temporaryPillar.requestCleanup(shearsGather);
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
        if (got >= wanted) {
            // The floor across the gap is taken back once the drops are in his bag: it was his walk to them (PATH7, #105).
            if (down()) return;
            end(true, "Собрал: «" + name() + "», " + got + " шт.");
            return;
        }
        if (due >= wanted) { down(); collect(level, got); return; }
        Predicate<BlockPos> site = kept(c, level);
        if (target != null && (gives(sources, level.getBlockState(target)) == 0 || !takes(level, target, ownTarget ? ownSite : site) || lavaBeside(level, target))) { release(); ownTarget = false; }
        if (target == null) {
            boolean dug = soilAbout(level);
            if (leafOrder) {
                // A tree's own leaves of every species come before the leaves a player set down: the species the order named
                // from a stand or a column first, then the leaves of any other tree or hedge about (any #minecraft:leaves), and
                // only when no natural leaf is reachable the leaves he set down — so a hedge beside a working tree stands
                // (the owner, 18.11, 19.11).
                target = pick(level, site, speciesSources, dug, true);
                if (target == null) target = pick(level, site, leafSources, dug, true);
                if (target == null) target = pick(level, site, speciesSources, dug, false);
                if (target == null) target = pick(level, site, leafSources, dug, false);
            } else {
                // A block of the kind he may take: from a stand he can walk to first, then up a column of his own soil.
                // Stone dug by name: from the ground only, and not after the hollows it showed in had no way to them (#197).
                if (!(stone && missed >= STONE_TRIES)) target = pick(level, site, speciesSources, dug && !stone, false);
                // A block of the item itself that the player set down is the order's too: taken when none of the kind is his by
                // the way's own rule — the pumpkin he hung above his base, the lapis block in the air (the owner, PATH7). The
                // flowers by his chest are sources, not the item, and stay his.
                if (target == null) { target = pick(level, ownSite, speciesSources, dug && !stone, false); ownTarget = target != null; }
            }
        }
        if (target == null) {
            // On his own column nothing else is in reach: the soil comes back before the drops on the ground are walked to.
            if (down()) return;
            // What he broke and has not picked up yet is picked up before he says how far he got.
            if (due > got) { collect(level, got); return; }
            String rest = passed.isEmpty() ? "больше рядом нет" : "до остального (" + seenAt() + ") не добрался";
            String lava = lavaKept.isEmpty() ? "" : " Рядом с лавой " + CompanionWorkItems.blocks(lavaKept.size()) + " — не трогал.";
            // Some gathered: obtain_item plans the rest again (red dye goes on with the tulips once the poppies are out). None: it ends here.
            if (got > 0) { release(); w.finish(CompanionActionResult.failed("Собрал «" + name() + "»: " + got + " из " + wanted + ", " + rest + "." + lava)); return; }
            // The stone's ask in one line short enough to be spoken whole with the longest name (CompanionDialogue).
            // The refusal names the distance of the block he saw, never only that he saw one: «5 блоков вверх и 6 вбок».
            if (stone) end(false, (!passed.isEmpty() ? "«" + name() + "» вижу — " + seenAt() + ", но подойти не смог" : !lavaKept.isEmpty() ? "«" + name() + "» вижу только у лавы, не трогал"
                    : "Рядом не нашёл, где взять «" + name() + "»") + ": отведи меня к склону или в пещеру.");
            else {
                String why = (passed.isEmpty() ? "Рядом не нашёл, где взять «" + name() + "»." : "Вижу, где взять «" + name() + "» — " + seenAt() + ", но подойти не смог.") + lava;
                // Leaves he saw but could not come at, with the soil for a column lying about him: the chain is planned again and
                // digs that soil first (the plan made from where he stood could not see the way to the stair). A blocked end would
                // give the order up on the spot (the stair he cannot climb, 18.11).
                if (!passed.isEmpty() && soilAbout(level)) { release(); w.finish(CompanionActionResult.failed(why)); return; }
                end(false, why);
            }
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
        if (stands.isEmpty()) {
            stands = standsFor(c, level, target);
            // A face of a hollow no way led to already: passed at once, without a search.
            if (stone && stands.removeAll(unreached) && stands.isEmpty()) { passed.add(target); release(); return; }
        }
        if (stands.isEmpty()) {
            // A crown above every stand cell, or any block no stand cell reaches: he builds up to it on the wood path's own soil
            // supports, cuts it from the top of the column, and the soil comes back when he is down (requestCleanup, the descent
            // of RecoverSupportsGoal). Stone dug by name is taken where a face shows, and a column to it is the way when no
            // ground reaches it (PATH7: a granite hanging in the air). A hollow with no way to the column's foot is still passed.
            if (!climb(level)) { passed.add(target); release(); }
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
                if (stone && noWay) { missed++; unreached.addAll(hollow(level, stands, c.blockPosition())); }
                // A stand he saw but no way to it (a stair behind a wall, a ledge across a gap): up a column of his own if the
                // soil for one is there, else the leaf is passed over and the next one about is tried (the owner, 18.11).
                // The way to the column's foot is another walk: the legs the failed walk spent are no reason to give the leaf
                // up before that walk is tried, and the walk to the same stands is not searched again while the climb goes on
                // (a stair he cannot climb, the owner 18.11).
                walkFailed = true;
                legs = waited = 0;
            }
            if (!stone && climb(level)) return;
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
        if (!w.items.canStore(List.of(leafOrder ? new ItemStack(state.getBlock().asItem()) : new ItemStack(item)))) { c.putToolAway(); end(false, FULL_BAG + (got == 0 ? "" : " Собрал " + got + " из " + wanted + ".")); return; }
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
            for (ItemStack drop : drops) if (leafOrder ? drop.is(ItemTags.LEAVES) : drop.is(item)) due += drop.getCount();
        }
        CompanionTools.wearOnBreak(tool, level, state, target, c);
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
        // His own column is taken back first (down), and the pickup walks to nothing while it stands or comes down: the drops
        // that fell below wait for the ground, so their settle clock starts when he is down, not on top of it (PATH7, 19.11).
        // A floor across a gap is different: it is a walk, so the drops on the far bank are taken over it before it comes back.
        if (c.temporaryPillar.isActive() || c.temporaryPillar.isRecovering() || c.temporaryBridge.isRecovering()) { settleAt = 0; return; }
        if (settleAt == 0) { settleAt = now + SETTLE; return; }
        if (now < settleAt) return;
        if (!retried) { retried = true; settleAt = now + SETTLE; c.retryHarvestPickup(); return; }
        due = got; settleAt = 0; retried = false;
    }

    private void release() { target = null; stands = Set.of(); path = null; walkFailed = false; legs = waited = dived = 0; noWay = false; w.breaking.reset(); w.resetApproach(); }

    /**
     * The open cells joined to these, if they make a hollow smaller than {@link #HOLLOW} and he does not stand in it (a mine whose
     * way out is shut: a ledge in it is no way, the rest is); else only these cells.
     */
    private static Set<BlockPos> hollow(ServerLevel level, Set<BlockPos> cells, BlockPos here) {
        Set<BlockPos> seen = new HashSet<>(cells);
        ArrayDeque<BlockPos> open = new ArrayDeque<>(cells);
        while (!open.isEmpty() && seen.size() < HOLLOW) {
            BlockPos at = open.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!seen.contains(next) && level.isLoaded(next) && level.getBlockState(next).getCollisionShape(level, next).isEmpty()) { seen.add(next); open.add(next); }
            }
        }
        return seen.size() < HOLLOW && !seen.contains(here) ? seen : cells;
    }

    /** A failure ends obtain_item at once with what he says (BLOCKED): planned again, the same ground was looked over twice more. */
    private void end(boolean success, String message) {
        release();
        w.finish(success ? CompanionActionResult.ok(message) : CompanionActionResult.blocked(message));
    }
}
