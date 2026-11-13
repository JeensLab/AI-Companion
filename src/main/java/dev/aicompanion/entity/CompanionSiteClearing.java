package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The site of a template building the way a player prepares it: the floor is sunk into the ground (the door then stands at ground
 * level), and what grew on the site (grass, bushes, trees) or rises one block above it is broken, block by block, with a real tool;
 * the harvest drops and is picked up. What is not natural terrain (a chest, anything a player put there), what cannot be broken and
 * what his tools cannot break is never touched: he stops and says what is in the way and where. Holds no saved state: the cells to
 * clear are read from the world again after a stop and a reload. Ported in idea from the clearing of PR #6 (0r1k).
 */
final class CompanionSiteClearing {
    /** HIGH: the cell is out of reach from the ground, a column of dirt is wanted (see {@link #high}). DOWN: nothing more is in reach from the column he stands on. */
    enum Outcome { WORKING, READY, BLOCKED, HIGH, DOWN }
    /**
     * How many layers above the roof are kept free: the column that reaches the top row has him standing at most one cell above it
     * (feet at the top row's level plus one) and his body takes that cell and the next. The ring round the box is one block wide: the
     * cells beside the walls where a column of dirt and he on it, or he on the ground, stand.
     */
    private static final int HEADROOM = 2;
    private record Door(BlockPos rel, Direction out) { }
    private static final int GIVE_UP_TICKS = 160;
    private final CompanionEntity owner;
    private final CompanionHouse house;
    private final CompanionBlockBreaking breaking;
    private int sizeX, sizeY, sizeZ;
    /** The building's own cells (relative to its corner), second halves included: everything else of the box is air to be freed. */
    private Set<BlockPos> own = Set.of();
    private List<Door> doors = List.of();
    private List<BlockPos> queue;
    /** The cells of the queue that are no part of the box: what grows above the roof and in the ring beside the walls. They are cleared when they can be, and left when not. */
    private final Set<BlockPos> extra = new HashSet<>();
    private BlockPos target, cell, needsColumn;
    private int cleared;
    private Path path;
    private long progress;
    private double best;
    private String message = "";
    /** Why the last site assessed was refused, for the player. */
    private String refusal = "";
    private static final String FREE_IT = " Освободи место и скажи «дострой».";

    CompanionSiteClearing(CompanionEntity owner, CompanionHouse house) { this.owner = owner; this.house = house; breaking = new CompanionBlockBreaking(owner); }

    String message() { return message; }
    String refusal() { return refusal; }
    BlockPos high() { return needsColumn; }
    /** How many blocks were cleared so far: a column that did not help is tried again once one was (the same rule as for the building's own blocks). */
    int cleared() { return cleared; }
    /** The cells still to free this pass (a copy, empty before the pass reads the world), for the journal; whether a cell is one round the box. */
    List<BlockPos> queued() { return queue == null ? List.of() : List.copyOf(queue); }
    boolean extra(BlockPos pos) { return extra.contains(pos); }
    private int no(String reason) { refusal = reason; return -1; }

    void use(CompanionTemplates.Loaded loaded) {
        logVerdicts.clear();
        sizeX = loaded.sizeX(); sizeY = loaded.sizeY(); sizeZ = loaded.sizeZ();
        Set<BlockPos> cells = new HashSet<>();
        List<Door> found = new ArrayList<>();
        for (var one : loaded.cells()) {
            BlockPos rel = one.rel();
            cells.add(rel);
            if (one.state().getBlock() instanceof DoorBlock) {
                cells.add(rel.above());
                for (Direction side : Direction.Plane.HORIZONTAL) if (outside(rel.relative(side))) found.add(new Door(rel, side));
            } else if (one.state().getBlock() instanceof BedBlock) cells.add(rel.relative(one.state().getValue(BedBlock.FACING)));
        }
        own = Set.copyOf(cells); doors = List.copyOf(found);
    }
    private boolean outside(BlockPos rel) { return rel.getX() < 0 || rel.getX() >= sizeX || rel.getZ() < 0 || rel.getZ() >= sizeZ; }

    /** The start of every pass: the cells are read from the world again, a drop left by an interruption is gone for again. */
    void reset() { breaking.reset(); queue = null; target = cell = needsColumn = null; path = null; }

    // ---- what may be broken

    Predicate<BlockPos> playerSites(ServerLevel level) {
        return owner.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(owner.getOwnerId(), level);
    }

    /** Terrain and what grows on it. A block of anything else was put there by somebody. */
    private static boolean natural(BlockState state) {
        // Leaves a player set down (persistent) are his: the crown of a tree never is.
        if (state.getBlock() instanceof LeavesBlock && state.getValue(LeavesBlock.PERSISTENT)) return false;
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM) || state.canBeReplaced();
    }
    /** Ground rather than growth: a hill of it above the floor level is the height difference, no more than one block of it is dug. */
    private static boolean terrain(BlockState state) { return !state.canBeReplaced() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.FLOWERS) && !state.is(Blocks.SWEET_BERRY_BUSH); }

    private boolean hasTool(BlockState state) {
        if (owner.getMainHandItem().isCorrectToolForDrops(state) || owner.getOffhandItem().isCorrectToolForDrops(state)) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).isCorrectToolForDrops(state)) return true;
        return false;
    }

    /** Why the block at {@code pos} is not his to break, in words for the player; null for air and for what he may dig out. */
    String obstruction(ServerLevel level, BlockPos pos) { return obstruction(level, pos, playerSites(level)); }
    private String obstruction(ServerLevel level, BlockPos pos, Predicate<BlockPos> players) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos))
            return "Место стройки " + pos.toShortString() + " за пределами мира или не загружено.";
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        String why = !level.getFluidState(pos).isEmpty() ? "это жидкость, в воде и лаве строить не буду."
                : level.getBlockEntity(pos) != null ? "это не моё, ломать не буду."
                : state.getDestroySpeed(level, pos) < 0 ? "его не сломать."
                : !natural(state) || state.is(BlockTags.LOGS) && (players.test(pos) || builtLog(level, pos)) ? "это не выросло само, похоже на постройку игрока; ломать не буду."
                : state.requiresCorrectToolForDrops() && !hasTool(state) ? "мне нечем его сломать, нужен подходящий инструмент."
                : null;
        return why == null ? null : "Мешает «" + state.getBlock().getName().getString() + "» на " + pos.toShortString() + ": " + why + FREE_IT;
    }

    /** The verdict on each log met this tick: a search tries many corners, and every one of them meets the same trees. */
    private final java.util.Map<BlockPos, Boolean> logVerdicts = new java.util.HashMap<>();
    private long verdictTick = -1;
    /** The log is part of a wall or a frame (no living crown, crafted blocks against it), not of a tree: the same judgement as for the woodcutter, once per connected group. */
    private boolean builtLog(ServerLevel level, BlockPos pos) {
        if (verdictTick != level.getGameTime()) { logVerdicts.clear(); verdictTick = level.getGameTime(); }
        Boolean known = logVerdicts.get(pos);
        if (known != null) return known;
        var group = CompanionLogSearch.connected(level, pos, BlockTags.LOGS);
        boolean built = CompanionBuildings.builtLogs(level, group);
        for (BlockPos member : group) logVerdicts.put(member, built);
        logVerdicts.put(pos.immutable(), built);
        return built;
    }

    // ---- the search for a site

    /**
     * How many blocks of the site are to be cleared (not counting what breaks at a touch, grass and flowers, nor the ground the floor is
     * sunk into), or -1 when it cannot be built on: the corner's layer is the ground itself, below it stands the support, the box holds
     * no fluid, nothing that is not his to break, no more than a block of hill; and the way in at the door is free. A site he picked
     * himself ({@code strict}, no player placed it) is refused for stone and snow blocks too: only plants, trees and soil are broken for it.
     */
    int assess(ServerLevel level, BlockPos corner, boolean digging, Predicate<BlockPos> players, boolean strict) {
        int cost = 0;
        for (int x = -1; x <= sizeX; x++) for (int z = -1; z <= sizeZ; z++) {
            BlockPos below = corner.offset(x, -1, z);
            if (!level.hasChunkAt(below)) return no("это место не загружено.");
            if (outside(new BlockPos(x, 0, z))) {
                // Around the site only the ground to walk on is looked at: no pit, no fluid.
                for (int y = 0; y <= 2; y++) if (!level.hasChunkAt(corner.offset(x, y, z)) || !level.getFluidState(corner.offset(x, y, z)).isEmpty()) return no("рядом с площадкой вода или лава, или место не загружено.");
                if (!sturdy(level, corner.offset(x, 0, z)) && !sturdy(level, below)) return no("рядом с площадкой яма: нет твёрдой земли на " + below.toShortString() + ".");
                continue;
            }
            if (!sturdy(level, below) || !level.getFluidState(below).isEmpty()) return no("под зданием на " + below.toShortString() + " нет твёрдой опоры или там жидкость.");
            for (int y = 0; y < sizeY; y++) {
                BlockPos pos = corner.offset(x, y, z);
                if (!level.hasChunkAt(pos)) return no("это место не загружено.");
                BlockState state = level.getBlockState(pos);
                if (!level.getFluidState(pos).isEmpty()) return no("на площадке вода или лава (" + pos.toShortString() + ").");
                boolean building = own.contains(new BlockPos(x, y, z));
                // The floor is sunk: ground (or the trunk standing in it) is where the floor goes. Air or grass there means the floor would lie on the ground.
                if (y == 0 && !building) continue;
                if (y == 0 && (state.isAir() || state.canBeReplaced())) return no("пол встал бы на траву или в воздухе (" + pos.toShortString() + "): его нужно опустить в землю, на склон или в яму так нельзя.");
                if (state.isAir()) continue;
                String blocked = obstruction(level, pos, players);
                if (blocked != null) return no(blocked.replace(FREE_IT, ""));
                if (y >= 2 && terrain(state)) return no("холм выше блока (" + pos.toShortString() + ").");
                if (strict && terrain(state) && !state.is(BlockTags.DIRT) && !state.is(BlockTags.SAND) && !state.is(Blocks.GRAVEL))
                    return no("на площадке «" + state.getBlock().getName().getString() + "» (" + pos.toShortString() + "): сам выбранное место ради него не расчищаю.");
                if (y >= 1 && state.getDestroySpeed(level, pos) > 0) cost++;
            }
        }
        for (Door door : doors) {
            BlockPos before = corner.offset(door.rel()).relative(door.out());
            if (!passable(level, before) || !passable(level, before.above()) || !sturdy(level, before.below()) && !sturdy(level, before.below(2))) return no("перед дверью на " + before.toShortString() + " нет свободного прохода или опоры.");
        }
        return digging || cost == 0 ? cost : no("площадку пришлось бы расчищать.");
    }
    private static boolean sturdy(ServerLevel level, BlockPos pos) { return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP) && level.getFluidState(pos).isEmpty(); }
    private static boolean passable(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    // ---- the work

    /**
     * Frees the cells of the box above the floor (the building's and its air) that are not yet what the building put there, and what grows
     * above the roof and in the ring beside the walls ({@link #HEADROOM}); {@code built} is what already stands as planned. READY when
     * nothing is left. {@code onScaffold}: he stands on a column, and only what is in sight from it is cleared.
     */
    Outcome tick(ServerLevel level, BlockPos base, Set<BlockPos> built, boolean onScaffold) {
        if (queue == null) {
            queue = new ArrayList<>(); extra.clear();
            var players = playerSites(level);
            for (int x = -1; x <= sizeX; x++) for (int z = -1; z <= sizeZ; z++) for (int y = 1; y < sizeY + HEADROOM; y++) {
                BlockPos pos = base.offset(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (!outside(new BlockPos(x, 0, z)) && y < sizeY) { if (!built.contains(pos)) queue.add(pos); continue; }
                // Round the box only what grew and may be broken: not the ground, not what a player put there.
                if (!terrain(state) && obstruction(level, pos, players) == null) { queue.add(pos); extra.add(pos); }
            }
            // What a stop left lying (the drops are not saved) is gone for again.
            owner.retryHarvestPickup();
        }
        while (!queue.isEmpty()) {
            if (target == null || onScaffold && !seen(level, target)) target = nearest(level, onScaffold);
            if (target == null) { cell = null; breaking.reset(); return Outcome.DOWN; }
            // Put there since by somebody (a block round the box is his to break only while it grows): left alone.
            if (extra.contains(target) && obstruction(level, target) != null) { queue.remove(target); target = null; continue; }
            Outcome outcome = clear(level, base, target, true);
            if (outcome != Outcome.READY) return outcome;
            queue.remove(target); target = null;
        }
        return Outcome.READY;
    }
    private boolean seen(ServerLevel level, BlockPos pos) { return house.visible(level, owner.getEyePosition(), pos); }
    private BlockPos nearest(ServerLevel level, boolean onScaffold) {
        return queue.stream().filter(pos -> !onScaffold || seen(level, pos)).min(java.util.Comparator.comparingDouble(pos -> owner.distanceToSqr(pos.getCenter()))).orElse(null);
    }

    /** No column of dirt helps to reach the cell asked for: a block of the box he cannot reach ends the order (true), a cell round it is only left. */
    boolean noColumn(ServerLevel level) {
        BlockPos pos = needsColumn;
        needsColumn = null;
        if (extra.contains(pos)) { queue.remove(pos); target = null; cell = null; return false; }
        message = unreachableText(level, pos);
        return true;
    }

    /** What is left standing round the box where it can tell (above the roof, beside the walls), for the player when no way to the blocks is found; empty when nothing is. */
    String inTheWay(ServerLevel level, BlockPos base) {
        if (sizeY == 0) return "";
        for (int y = sizeY + HEADROOM - 1; y >= 1; y--) for (int x = -1; x <= sizeX; x++) for (int z = -1; z <= sizeZ; z++) {
            if (!outside(new BlockPos(x, 0, z)) && y < sizeY) continue;
            BlockPos pos = base.offset(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(level, pos).isEmpty() || natural(state) && terrain(state)) continue;
            return (y >= sizeY ? "Над крышей" : "Рядом со стенами") + " мешает «" + state.getBlock().getName().getString() + "» на " + pos.toShortString() + ". ";
        }
        return "";
    }

    /** One block: the way to it, then the swing with the right tool, then the harvest drops. READY once the cell is air. */
    Outcome clear(ServerLevel level, BlockPos base, BlockPos pos) { return clear(level, base, pos, false); }
    private Outcome clear(ServerLevel level, BlockPos base, BlockPos pos, boolean column) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { cell = null; breaking.reset(); return Outcome.READY; }
        String why = obstruction(level, pos);
        if (why != null) { message = why; breaking.reset(); return Outcome.BLOCKED; }
        if (!pos.equals(cell)) {
            cell = pos.immutable(); path = null; progress = level.getGameTime(); best = Double.MAX_VALUE; breaking.reset();
            owner.workStatus("Расчищаю место: " + state.getBlock().getName().getString() + " на " + pos.toShortString() + ".");
        }
        owner.getLookControl().setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (owner.onGround() && house.visible(level, owner.getEyePosition(), pos)) {
            owner.getNavigation().stop();
            owner.selectToolForBlock(state);
            if (!breaking.tick(level, pos)) return Outcome.WORKING;
            ItemStack tool = owner.getMainHandItem();
            var drops = CompanionTools.drops(state, level, pos, null, owner, tool);
            if (!owner.canStoreDrops(drops)) { message = "В сумке нет места для того, что расчищаю («" + state.getBlock().getName().getString() + "» на " + pos.toShortString() + "). Освободи место и скажи «дострой»."; return Outcome.BLOCKED; }
            if (!level.destroyBlock(pos, false, owner, 512)) return Outcome.WORKING;
            tool.getItem().mineBlock(tool, level, state, pos, owner);
            owner.swing(InteractionHand.MAIN_HAND);
            owner.dropHarvest(pos, drops);
            breaking.reset();
            owner.work.progressed(); cleared++;
            return Outcome.WORKING;
        }
        return approach(level, pos, column);
    }

    private Outcome approach(ServerLevel level, BlockPos pos, boolean column) {
        double distance = owner.distanceToSqr(pos.getCenter());
        if (distance + .05 < best) { best = distance; progress = level.getGameTime(); }
        if (level.getGameTime() - progress > GIVE_UP_TICKS) return unreachable(level, pos, column);
        if (path == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck()) {
            path = house.routeToBlock(level, pos);
            // This tick's cells went elsewhere: asked again next tick, not a failure.
            if (path == null && house.routeBusy()) return Outcome.WORKING;
            if (path == null || !path.canReach()) return unreachable(level, pos, column);
        }
        owner.work.route.walk(path);
        return Outcome.WORKING;
    }

    /** No way to a cell where he can work it from: a column of dirt is tried for it when the caller can put one up (HIGH), else the order ends. */
    private Outcome unreachable(ServerLevel level, BlockPos pos, boolean column) {
        path = null; cell = null; breaking.reset();
        owner.getNavigation().stop();
        if (column) { needsColumn = pos; return Outcome.HIGH; }
        message = unreachableText(level, pos);
        return Outcome.BLOCKED;
    }
    private static String unreachableText(ServerLevel level, BlockPos pos) {
        return "Не могу подойти к «" + level.getBlockState(pos).getBlock().getName().getString() + "» на " + pos.toShortString() + ", чтобы расчистить. Убери его сам и скажи «дострой».";
    }
}
