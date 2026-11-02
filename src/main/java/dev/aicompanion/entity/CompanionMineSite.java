package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The record of the companion's diamond mine: where it starts, the stands he has reached down the staircase, the tunnels and
 * the blocks he placed himself. It is a hint, the world is the truth: {@link #verify} cuts the staircase back to the part
 * still open. Saved under {@link #KEY}; a save without the key has no mine.
 */
public final class CompanionMineSite {
    public static final String KEY = "MineSite", HELD_KEY = "MineHeld";
    /** How long after a step that opened nothing the next one still finds the restarts of that step spent. */
    public static final int RESTARTS_CARRIED = 20 * 120;
    /** The ways of getting on from a stop that came again: the stairs anew from the spot, then the way up. Past them the stop is final until he stands elsewhere. */
    public static final int STOP_WAYS = 2;
    private static final int MAX_STANDS = 512, MAX_TUNNELS = 16, MAX_SEALS = 1024, MAX_TEMPORARY = 16, MAX_REFUSED = 128, MAX_FORBIDDEN = 256, MAX_HELD = 64, MAX_CAVE_CELLS = 2048, MAX_ABOVE = 1024, MAX_DUG = 4096, CAVE_FORGOTTEN = 8, MAX_TUNNEL_LENGTH = CompanionShaftPlan.MAX_TUNNEL_CELLS;

    /** Squared distance within which he counts as standing at a stand; the expedition's own measure of "the same spot". */
    private static final int NEAR_STAND = 12;

    private record Move(BlockPos stand, Direction direction) { }

    /** One tunnel from the stair foot: its direction and how many advances (of four cells) are open. */
    public record Tunnel(Direction direction, int length) { }

    private BlockPos entrance;
    private Direction stairDirection = Direction.NORTH;
    private final List<BlockPos> stands = new ArrayList<>();
    private final List<Tunnel> tunnels = new ArrayList<>();
    private final Set<BlockPos> seals = new LinkedHashSet<>();
    /** Moves (a stand and a direction) that stopped an earlier attempt for good, and cells that must not be opened again; oldest dropped first. */
    private final Set<Move> refused = new LinkedHashSet<>();
    private final Set<BlockPos> forbidden = new LinkedHashSet<>();
    /**
     * The caves he fled from as coarse cells of 4x4x4 (packed {@code BlockPos} of x>>2, y>>2, z>>2), nothing of the mine may be dug into
     * them again. Cells, not one box per cave: the box of a big cave would hold the whole staircase and shut every step down.
     */
    private final Set<Long> caves = new LinkedHashSet<>();
    /** How often the stairs were backed up from a dead end, a cave sent him up, and the mine was started again where he stood: each is bounded. */
    private int backtracks, retreats, restarts;
    /**
     * The surface entrance the first stairs began at, kept when a restart under the ground replaces {@link #entrance}, and the sides
     * stairs were begun to from it. When no side is left under the ground, the mine begins again from here to a side not tried.
     */
    private BlockPos origin;
    private final Set<Direction> originSides = EnumSet.noneOf(Direction.class);
    /** Stands and tunnel advances ever recorded, and the count when the last step began: a step that added none opened nothing. */
    private int opened, openedAtStep;
    private long lastStepAt;
    /** The mine was started again under the ground: its entrance is no way to the surface, and nothing can be topped up from it. */
    private boolean buried;
    /**
     * The way up that a restart under the ground leaves behind: the cells of the stairs and tunnel of the record it replaced (and of the ways
     * above those), from the deepest to the entrance on the surface. The new record knows only the pit; this is how he gets out of it.
     */
    private final List<BlockPos> above = new ArrayList<>();
    /** The cells of the records a restart replaced: air he dug himself, which the new record does not know and which is no cave; oldest dropped first. */
    private final Set<BlockPos> dug = new LinkedHashSet<>();
    /** Where the last order stopped short under the ground, how it ended, and how often the same stop came again since; a save without it has none. */
    private BlockPos stopAt;
    private int stopEnding, stopRepeats;
    private BlockPos plug;
    /**
     * Blocks he set down for a while and takes up again (a bench on a step): {@link #verify} reads the cell they stand in as open, so
     * a bench does not cut the staircase below it. Kept apart from {@link #seals}: a seal must cut the staircase, a temporary block must not.
     */
    private final Set<BlockPos> temporary = new LinkedHashSet<>();
    /** Sealing blocks held back mid-column. They are not part of the mine record: they outlive {@link #clear} and are saved apart, so a quit loses none. */
    private final List<Held> held = new ArrayList<>();
    private boolean loadedHeld;

    /** Drops of one broken cell, kept until the column is done. */
    public record Held(BlockPos pos, List<ItemStack> drops) { }

    public void hold(BlockPos pos, List<ItemStack> drops) { held.add(new Held(pos.immutable(), List.copyOf(drops))); }

    public List<Held> peekHeld() { return List.copyOf(held); }

    public int heldCount() {
        int count = 0;
        for (Held drop : held) for (ItemStack stack : drop.drops()) count += stack.getCount();
        return count;
    }

    /** Hands over everything held and forgets it, so what was dropped is never saved or dropped twice. */
    public List<Held> takeHeld() {
        List<Held> taken = List.copyOf(held);
        held.clear();
        loadedHeld = false;
        return taken;
    }

    /** True once after a load that brought held drops: no column is being dug then, so they fall at once. */
    public boolean loadedHeld() { return loadedHeld; }

    public boolean exists() { return entrance != null; }

    public void start(BlockPos entranceFeet, Direction stair) {
        clear();
        entrance = entranceFeet.immutable();
        stairDirection = stair;
        origin = entrance;
        originSides.add(stair);
    }

    /**
     * Starts the mine again at {@code here} (a new entrance cell where he stands), keeping what the caves taught and counting the restart.
     * The caves within {@link #CAVE_FORGOTTEN} blocks of {@code here} are forgotten: he already stands by that cave, and getting away from here
     * matters more than its ban, or the first step would be refused again. {@link #newStep} counts the restarts per step, and the goal gives up after a few fruitless steps.
     */
    public void restart(BlockPos here, Direction stair) { restart(here, stair, false); }

    /** As above; with {@code keepCaves} the caves beside him stay banned: a restart that is a change of way must not go where he fled from. */
    public void restart(BlockPos here, Direction stair, boolean keepCaves) {
        Set<Long> near = new HashSet<>();
        for (int x = (here.getX() - CAVE_FORGOTTEN) >> 2; x <= (here.getX() + CAVE_FORGOTTEN) >> 2; x++)
            for (int y = (here.getY() - CAVE_FORGOTTEN) >> 2; y <= (here.getY() + CAVE_FORGOTTEN) >> 2; y++)
                for (int z = (here.getZ() - CAVE_FORGOTTEN) >> 2; z <= (here.getZ() + CAVE_FORGOTTEN) >> 2; z++) near.add(BlockPos.asLong(x, y, z));
        List<Long> kept = caves.stream().filter(cell -> keepCaves || !near.contains(cell)).toList();
        int count = restarts + 1;
        // His plugs stay his: a block he laid must not read as a wall of the rock around the new mine.
        List<BlockPos> laid = List.copyOf(seals), banned = List.copyOf(forbidden), standing = List.copyOf(temporary);
        List<Move> moves = List.copyOf(refused);
        // Under the ground the entrance is no way to the surface: the surface one is kept. A restart on open ground makes that spot the surface entrance.
        BlockPos surface = buried || origin == null ? origin : entrance;
        List<BlockPos> up = wayAbove(here);
        Set<BlockPos> ownAir = new LinkedHashSet<>(dug);
        ownAir.addAll(CompanionCaveSense.mineCells(this));
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        if (buried || entrance.equals(origin)) sides.addAll(originSides); else sides.add(stairDirection);
        int openedBefore = opened, stepBefore = openedAtStep;
        long stepAt = lastStepAt;
        BlockPos stop = stopAt;
        int ending = stopEnding, repeats = stopRepeats;
        start(here, stair);
        stopAt = stop;
        stopEnding = ending;
        stopRepeats = repeats;
        opened = openedBefore;
        openedAtStep = stepBefore;
        lastStepAt = stepAt;
        origin = surface == null ? here.immutable() : surface;
        originSides.clear();
        originSides.addAll(sides);
        if (here.equals(surface)) originSides.add(stair);
        above.addAll(up);
        for (BlockPos cell : ownAir) remember(dug, cell, MAX_DUG);
        caves.addAll(kept);
        seals.addAll(laid);
        temporary.addAll(standing);
        forbidden.addAll(banned);
        refused.addAll(moves);
        restarts = count;
    }

    /** The cells of the record's own way up, and of the ways above it when it is buried, from the cell nearest {@code here} on: what lies below the restart is no way up. */
    private List<BlockPos> wayAbove(BlockPos here) {
        List<BlockPos> way = CompanionExpeditionClimb.wayToSurface(this);
        int from = 0;
        double nearest = NEAR_STAND;
        for (int i = 0; i < way.size(); i++) {
            double distance = way.get(i).distSqr(here);
            if (distance <= nearest) { from = i; nearest = distance; }
        }
        List<BlockPos> tail = way.subList(from, way.size());
        return List.copyOf(tail.subList(Math.max(0, tail.size() - MAX_ABOVE), tail.size()));
    }

    /** The way up that the restarts under the ground left behind, deepest cell first, the entrance on the surface last; empty for a mine not started again there. */
    public List<BlockPos> above() { return Collections.unmodifiableList(above); }

    /** The air of the records the restarts replaced; empty in a save from before it was kept. */
    public Set<BlockPos> dug() { return Collections.unmodifiableSet(dug); }

    public void clear() {
        entrance = null;
        above.clear();
        dug.clear();
        origin = null;
        originSides.clear();
        opened = openedAtStep = 0;
        lastStepAt = 0;
        stopAt = null;
        stopEnding = stopRepeats = 0;
        plug = null;
        caves.clear();
        backtracks = retreats = restarts = 0;
        buried = false;
        stands.clear();
        tunnels.clear();
        seals.clear();
        temporary.clear();
        refused.clear();
        forbidden.clear();
    }

    public BlockPos entrance() { return entrance; }

    public Direction stairDirection() { return stairDirection; }

    /** The surface entrance the stairs first began at: where the mine begins again when the spot under the ground has no side left. */
    public BlockPos origin() { return origin; }

    /** True when stairs were already begun from the origin to this side. */
    public boolean originTried(Direction side) { return originSides.contains(side); }

    /** The stands reached after the entrance, in order; a landing of a turn is a stand beside the one before it. */
    public List<BlockPos> stands() { return Collections.unmodifiableList(stands); }

    /** The lowest stand reached, the entrance while none is. */
    public BlockPos foot() { return stands.isEmpty() ? entrance : stands.get(stands.size() - 1); }

    public void reached(BlockPos stand) {
        if (entrance == null || stands.size() >= MAX_STANDS || stand.equals(foot())) return;
        stands.add(stand.immutable());
        opened++;
    }

    /** Back to the first {@code count} stands (a retreat or a re-dig); tunnels hang on the foot, so they go with it. */
    public void truncate(int count) {
        if (count >= stands.size()) return;
        stands.subList(Math.max(0, count), stands.size()).clear();
        tunnels.clear();
    }

    /**
     * Puts the base of the tunnels {@code length} cells on along {@code direction} from the foot: the cells become flat stands
     * and the tunnels are forgotten. False, nothing changed, when that would pass the cap.
     */
    public boolean rebase(int length, Direction direction) {
        if (entrance == null || length < 1 || stands.size() + length > MAX_STANDS) return false;
        BlockPos foot = foot();
        for (int k = 1; k <= length; k++) stands.add(foot.relative(direction, k).immutable());
        tunnels.clear();
        opened++;
        return true;
    }

    public List<Tunnel> tunnels() { return Collections.unmodifiableList(tunnels); }

    public void openTunnel(Direction direction) {
        if (entrance != null && tunnels.size() < MAX_TUNNELS) tunnels.add(new Tunnel(direction, 0));
    }

    /** One more advance in the latest tunnel; false when there is none or it has reached the cap. */
    public boolean extendTunnel() {
        if (tunnels.isEmpty()) return false;
        Tunnel last = tunnels.get(tunnels.size() - 1);
        if (last.length() >= MAX_TUNNEL_LENGTH) return false;
        tunnels.set(tunnels.size() - 1, new Tunnel(last.direction(), last.length() + 1));
        opened++;
        return true;
    }

    /** Stands and tunnel advances recorded since the mine began, a restart or a cut-back of the record included: it never goes down, so it can tell a step that dug from one that did not. */
    public int openedTotal() { return opened; }

    /** The cells the record holds now: steps of the staircase and advances of tunnels. */
    public int opened() {
        int total = stands.size();
        for (Tunnel tunnel : tunnels) total += tunnel.length();
        return total;
    }

    public Set<BlockPos> seals() { return Collections.unmodifiableSet(seals); }

    public void sealed(BlockPos cell) { if (entrance != null && seals.size() < MAX_SEALS) seals.add(cell.immutable()); }

    public void unsealed(BlockPos cell) { seals.remove(cell); }

    /** The cells of blocks he set down for a while and has not taken up yet. */
    public Set<BlockPos> temporary() { return Collections.unmodifiableSet(temporary); }

    public void temporary(BlockPos cell) { if (entrance != null) remember(temporary, cell.immutable(), MAX_TEMPORARY); }

    public void taken(BlockPos cell) { temporary.remove(cell); }

    /** Remembers that this step failed for a reason that will not change by itself, so a repeat attempt takes another move. */
    public void refuse(BlockPos stand, Direction direction) {
        if (entrance == null) return;
        remember(refused, new Move(stand.immutable(), direction), MAX_REFUSED);
    }

    public boolean refused(BlockPos stand, Direction direction) { return refused.contains(new Move(stand, direction)); }

    /** A cell that must not be opened again (a cave behind it plugged by him). */
    public void forbid(BlockPos cell) {
        if (entrance != null) remember(forbidden, cell.immutable(), MAX_FORBIDDEN);
    }

    public boolean forbidden(BlockPos cell) { return forbidden.contains(cell); }

    /** Remembers the cells of a cave he fled from, each air cell with its six neighbours (a block of margin); the oldest cells are dropped first. */
    public void shun(Collection<BlockPos> air) {
        if (entrance == null) return;
        Set<Long> cells = new LinkedHashSet<>();
        for (BlockPos pos : air) {
            cells.add(coarse(pos));
            for (Direction d : Direction.values()) cells.add(coarse(pos.relative(d)));
        }
        for (long cell : cells) remember(caves, cell, MAX_CAVE_CELLS);
    }

    public boolean shunned(BlockPos cell) { return caves.contains(coarse(cell)); }

    /** The remembered cave cells as packed positions, oldest first; the persistence probe reads them. */
    public Set<Long> caveCells() { return Collections.unmodifiableSet(caves); }

    private static long coarse(BlockPos pos) { return BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2); }

    /** The index of the first stand of the staircase inside a remembered cave, or -1. */
    public int firstShunned() {
        for (int i = 0; i < stands.size(); i++) if (shunned(stands.get(i))) return i;
        return -1;
    }

    public int backtracks() { return backtracks; }

    public void backtracked() { if (entrance != null) backtracks++; }

    public int retreats() { return retreats; }

    public void retreated() { if (entrance != null) retreats++; }

    public int restarts() { return restarts; }

    /** Each step of a goal gets its own tries: the counters bound one step, and a used-up mine must not stop every later order for good. */
    public void newStep() { backtracks = restarts = 0; }

    /**
     * As {@link #newStep()}, except that the restarts of a step that opened nothing are not given back to the step that follows it within
     * {@link #RESTARTS_CARRIED} ticks: the goal's retries would else spend three empty restarts each. The backtracks are always given back.
     */
    public void newStep(long now) {
        boolean fruitless = opened == openedAtStep && now - lastStepAt < RESTARTS_CARRIED;
        backtracks = 0;
        if (!fruitless) restarts = 0;
        openedAtStep = opened;
        lastStepAt = now;
    }

    public void retreatsForgotten() { retreats = 0; }

    /**
     * An order stopped short at {@code here} under the ground: the same spot with the same ending, or a stop of a step that opened nothing, counts
     * as the same stop again. {@code ending} is any number that tells the endings apart. A final stop is only left behind by standing elsewhere.
     */
    public void stopped(BlockPos here, int ending) {
        if (entrance == null) return;
        boolean same = stopAt != null && ending == stopEnding && stopAt.distSqr(here) <= NEAR_STAND;
        boolean fruitless = stopAt != null && opened == openedAtStep && !stopFinal();
        stopRepeats = same || fruitless ? Math.min(stopRepeats + 1, STOP_WAYS + 1) : 0;
        stopAt = here.immutable();
        stopEnding = ending;
    }

    /** The order got somewhere or stands on the surface: nothing is carried over to the next one. */
    public void stopForgotten() {
        stopAt = null;
        stopEnding = stopRepeats = 0;
    }

    /** How often the last stop came again: 1 and on up to {@link #STOP_WAYS} name the way due for the next order, 0 none. */
    public int stopRepeats() { return stopRepeats; }

    /** A stop stands and a way of getting on from it is still untried: the next order is no mere repeat of this one. */
    public boolean stopWayLeft() { return stopAt != null && stopRepeats <= STOP_WAYS; }

    /** The same stop came again after every way was tried. */
    public boolean stopFinal() { return stopAt != null && stopRepeats > STOP_WAYS; }

    /**
     * The way is looked at afresh: the refused moves and the restarts spent are given up. The plugged cells and the caves stay, and lava, water
     * and the world's edge are read from the world each time, so only what a past failure alone had shut comes open.
     */
    public void refusalsForgotten() {
        refused.clear();
        restarts = 0;
    }

    public boolean buried() { return buried; }

    public void buried(boolean now) {
        buried = now;
        if (!now) above.clear();
    }

    /** The remembered refusals as the hazard checks take them; a turn onto a stand of his own stairs is refused, or flat landings would lead him round in a ring. */
    public CompanionShaftPlan.Avoid avoid() {
        Set<BlockPos> own = new HashSet<>(stands);
        if (entrance != null) own.add(entrance);
        // The floor under a step is no cell to open either: a landing or a tunnel dug through it would drop the stairs below.
        Set<BlockPos> shut = new HashSet<>(forbidden);
        for (BlockPos stand : own) shut.add(stand.below());
        return new CompanionShaftPlan.Avoid(Collections.unmodifiableSet(shut), (at, d) -> refused(at, d) || own.contains(at.relative(d)), this::shunned);
    }

    /** The cell is the floor of a stand of his own stairs (or of the entrance), also of the stairs above a restart: breaking it would open a hole under the step. */
    public boolean carriesStand(BlockPos floor) {
        BlockPos above = floor.above();
        return above.equals(entrance) || stands.contains(above) || this.above.contains(above);
    }

    private static <T> void remember(Set<T> set, T entry, int cap) {
        set.remove(entry);
        if (set.size() >= cap) {
            Iterator<T> oldest = set.iterator();
            oldest.next();
            oldest.remove();
        }
        set.add(entry);
    }

    /** The cork set behind him on the first step up when retreating; the next step takes it out again. */
    public BlockPos plug() { return plug; }

    public void plugged(BlockPos cell) { plug = cell == null ? null : cell.immutable(); }

    /**
     * Cuts the staircase back to the stands whose three cells are all still open, from the entrance down, and forgets own
     * blocks that are gone. A stand in an unloaded chunk is not judged and stays (loading it here would stall the tick, and the stairs far below him
     * must not be cut off because the entrance end is out of range).
     * Returns how many stands remain; tunnels are dropped with any stand that goes.
     */
    public int verify(ServerLevel level) { return verify(level, null); }

    /**
     * As above, for a companion at {@code here}: a stand he is on or beside (null: none) starts the check, so a cut in the stairs above him
     * does not take the part below, which is the way he is going. With no stand near him the check starts at the entrance.
     */
    public int verify(ServerLevel level, BlockPos here) {
        if (entrance == null) return 0;
        int near = here == null ? 0 : routeNear(here);
        if (near == 0 && level.hasChunkAt(entrance) && !columnOpen(level, entrance)) {
            truncate(0);
        } else {
            int kept = Math.max(near - 1, 0);
            while (kept < stands.size() && !closed(level, stands.get(kept), kept == 0 ? entrance : stands.get(kept - 1))) kept++;
            truncate(kept);
        }
        seals.removeIf(cell -> level.hasChunkAt(cell) && level.getBlockState(cell).isAir());
        temporary.removeIf(cell -> level.hasChunkAt(cell) && level.getBlockState(cell).isAir());
        if (plug != null && level.hasChunkAt(plug) && level.getBlockState(plug).isAir()) plug = null;
        return stands.size();
    }

    /** The place on the route (the entrance is 0, the stands follow) he stands at or beside, as the expedition finds it; 0 also when none is near, as then the whole stairs are judged. */
    private int routeNear(BlockPos here) {
        int best = 0;
        double bestDistance = NEAR_STAND;
        for (int i = 0; i <= stands.size(); i++) {
            double distance = (i == 0 ? entrance : stands.get(i - 1)).distSqr(here);
            if (distance <= bestDistance) { best = i; bestDistance = distance; }
        }
        return best;
    }

    /** A stand's three cells are open, or held only by a temporary block of his. */
    private boolean columnOpen(ServerLevel level, BlockPos stand) {
        for (BlockPos cell : CompanionShaftPlan.column(stand)) if (!level.hasChunkAt(cell) || !CompanionShaftPlan.open(level, cell) && !temporary.contains(cell)) return false;
        return true;
    }

    /** The stand's column is open, or closed only by his own plug (a retreat leaves it; the next step takes it out) or a temporary block, and a floor holds under it. */
    private boolean closed(ServerLevel level, BlockPos stand, BlockPos previous) {
        if (!level.hasChunkAt(stand)) return false;
        BlockPos floor = stand.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return true;
        // A stand level with the one before it is the base of a tunnel, which is two cells high.
        List<BlockPos> column = stand.getY() == previous.getY() ? List.of(stand, stand.above()) : CompanionShaftPlan.column(stand);
        for (BlockPos cell : column) {
            if (!CompanionShaftPlan.open(level, cell) && !cell.equals(plug) && !temporary.contains(cell)) return true;
        }
        return false;
    }

    public void save(CompoundTag tag) {
        tag.remove(KEY);
        tag.remove(HELD_KEY);
        saveHeld(tag);
        if (entrance == null) return;
        CompoundTag mine = new CompoundTag();
        mine.putLong("Entrance", entrance.asLong());
        mine.putByte("Direction", (byte) stairDirection.get2DDataValue());
        mine.putLongArray("Stands", stands.stream().mapToLong(BlockPos::asLong).toArray());
        mine.putLongArray("Seals", seals.stream().mapToLong(BlockPos::asLong).toArray());
        if (plug != null) mine.putLong("Plug", plug.asLong());
        if (!temporary.isEmpty()) mine.putLongArray("Temporary", temporary.stream().mapToLong(BlockPos::asLong).toArray());
        if (!forbidden.isEmpty()) mine.putLongArray("Forbidden", forbidden.stream().mapToLong(BlockPos::asLong).toArray());
        if (!caves.isEmpty()) mine.putLongArray("Caves", caves.stream().mapToLong(Long::longValue).toArray());
        if (backtracks > 0) mine.putInt("Backtracks", backtracks);
        if (retreats > 0) mine.putInt("Retreats", retreats);
        if (restarts > 0) mine.putInt("Restarts", restarts);
        if (buried) mine.putBoolean("Buried", true);
        if (stopAt != null) {
            mine.putLong("StopAt", stopAt.asLong());
            mine.putInt("StopEnding", stopEnding);
            mine.putInt("StopRepeats", stopRepeats);
        }
        if (!above.isEmpty()) mine.putLongArray("Above", above.stream().mapToLong(BlockPos::asLong).toArray());
        if (!dug.isEmpty()) mine.putLongArray("Dug", dug.stream().mapToLong(BlockPos::asLong).toArray());
        if (origin != null && !origin.equals(entrance)) {
            mine.putLong("Origin", origin.asLong());
            int mask = 0;
            for (Direction side : originSides) mask |= 1 << side.get2DDataValue();
            mine.putByte("OriginSides", (byte) mask);
        }
        if (!refused.isEmpty()) {
            ListTag moves = new ListTag();
            for (Move move : refused) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Stand", move.stand().asLong());
                entry.putByte("Direction", (byte) move.direction().get2DDataValue());
                moves.add(entry);
            }
            mine.put("Refused", moves);
        }
        ListTag list = new ListTag();
        for (Tunnel tunnel : tunnels) {
            CompoundTag entry = new CompoundTag();
            entry.putByte("Direction", (byte) tunnel.direction().get2DDataValue());
            entry.putInt("Length", tunnel.length());
            list.add(entry);
        }
        mine.put("Tunnels", list);
        tag.put(KEY, mine);
    }

    private void saveHeld(CompoundTag tag) {
        if (held.isEmpty()) return;
        ListTag list = new ListTag();
        for (Held drop : held) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", drop.pos().asLong());
            ListTag stacks = new ListTag();
            for (ItemStack stack : drop.drops()) {
                CompoundTag item = new CompoundTag();
                item.putString("Item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                item.putInt("Count", stack.getCount());
                stacks.add(item);
            }
            entry.put("Drops", stacks);
            list.add(entry);
        }
        tag.put(HELD_KEY, list);
    }

    private void loadHeld(CompoundTag tag) {
        held.clear();
        ListTag list = tag.getList(HELD_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && held.size() < MAX_HELD; i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("Pos", Tag.TAG_LONG)) continue;
            List<ItemStack> drops = new ArrayList<>();
            ListTag stacks = entry.getList("Drops", Tag.TAG_COMPOUND);
            for (int j = 0; j < stacks.size(); j++) {
                CompoundTag item = stacks.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(item.getString("Item"));
                int count = Math.max(0, Math.min(64, item.getInt("Count")));
                if (id == null || count == 0) continue;
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(found -> drops.add(new ItemStack(found, count)));
            }
            if (!drops.isEmpty()) held.add(new Held(BlockPos.of(entry.getLong("Pos")), drops));
        }
        loadedHeld = !held.isEmpty();
    }

    public void load(CompoundTag tag) {
        clear();
        loadHeld(tag);
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag mine = tag.getCompound(KEY);
        if (!mine.contains("Entrance", Tag.TAG_LONG)) return;
        entrance = BlockPos.of(mine.getLong("Entrance"));
        stairDirection = Direction.from2DDataValue(mine.getByte("Direction"));
        for (long packed : mine.getLongArray("Stands")) if (stands.size() < MAX_STANDS) stands.add(BlockPos.of(packed));
        for (long packed : mine.getLongArray("Seals")) if (seals.size() < MAX_SEALS) seals.add(BlockPos.of(packed));
        if (mine.contains("Plug", Tag.TAG_LONG)) plug = BlockPos.of(mine.getLong("Plug"));
        for (long packed : mine.getLongArray("Temporary")) if (temporary.size() < MAX_TEMPORARY) temporary.add(BlockPos.of(packed));
        for (long packed : mine.getLongArray("Forbidden")) if (forbidden.size() < MAX_FORBIDDEN) forbidden.add(BlockPos.of(packed));
        for (long cell : mine.getLongArray("Caves")) if (caves.size() < MAX_CAVE_CELLS) caves.add(cell);
        backtracks = Math.max(0, mine.getInt("Backtracks"));
        retreats = Math.max(0, mine.getInt("Retreats"));
        restarts = Math.max(0, mine.getInt("Restarts"));
        buried = mine.getBoolean("Buried");
        if (mine.contains("StopAt", Tag.TAG_LONG)) {
            stopAt = BlockPos.of(mine.getLong("StopAt"));
            stopEnding = mine.getInt("StopEnding");
            stopRepeats = Math.max(0, Math.min(STOP_WAYS + 1, mine.getInt("StopRepeats")));
        }
        for (long packed : mine.getLongArray("Above")) if (above.size() < MAX_ABOVE) above.add(BlockPos.of(packed));
        for (long packed : mine.getLongArray("Dug")) if (dug.size() < MAX_DUG) dug.add(BlockPos.of(packed));
        origin = mine.contains("Origin", Tag.TAG_LONG) ? BlockPos.of(mine.getLong("Origin")) : entrance;
        if (mine.contains("OriginSides", Tag.TAG_BYTE)) {
            for (Direction side : Direction.Plane.HORIZONTAL) if ((mine.getByte("OriginSides") & 1 << side.get2DDataValue()) != 0) originSides.add(side);
        } else originSides.add(stairDirection);
        ListTag moves = mine.getList("Refused", Tag.TAG_COMPOUND);
        for (int i = 0; i < moves.size() && refused.size() < MAX_REFUSED; i++) {
            CompoundTag entry = moves.getCompound(i);
            if (entry.contains("Stand", Tag.TAG_LONG)) refused.add(new Move(BlockPos.of(entry.getLong("Stand")), Direction.from2DDataValue(entry.getByte("Direction"))));
        }
        ListTag list = mine.getList("Tunnels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && tunnels.size() < MAX_TUNNELS; i++) {
            CompoundTag entry = list.getCompound(i);
            tunnels.add(new Tunnel(Direction.from2DDataValue(entry.getByte("Direction")), Math.max(0, Math.min(MAX_TUNNEL_LENGTH, entry.getInt("Length")))));
        }
    }
}
