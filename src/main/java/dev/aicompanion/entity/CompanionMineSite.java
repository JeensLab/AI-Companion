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
import java.util.Collections;
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
    private static final int MAX_STANDS = 512, MAX_TUNNELS = 16, MAX_SEALS = 1024, MAX_REFUSED = 128, MAX_FORBIDDEN = 256, MAX_HELD = 64, MAX_TUNNEL_LENGTH = CompanionShaftPlan.MAX_TUNNEL_CELLS;

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
    private BlockPos plug;
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
    }

    public void clear() {
        entrance = null;
        plug = null;
        stands.clear();
        tunnels.clear();
        seals.clear();
        refused.clear();
        forbidden.clear();
    }

    public BlockPos entrance() { return entrance; }

    public Direction stairDirection() { return stairDirection; }

    /** The stands reached after the entrance, in order; a landing of a turn is a stand beside the one before it. */
    public List<BlockPos> stands() { return Collections.unmodifiableList(stands); }

    /** The lowest stand reached, the entrance while none is. */
    public BlockPos foot() { return stands.isEmpty() ? entrance : stands.get(stands.size() - 1); }

    public void reached(BlockPos stand) {
        if (entrance == null || stands.size() >= MAX_STANDS || stand.equals(foot())) return;
        stands.add(stand.immutable());
    }

    /** Back to the first {@code count} stands (a retreat or a re-dig); tunnels hang on the foot, so they go with it. */
    public void truncate(int count) {
        if (count >= stands.size()) return;
        stands.subList(Math.max(0, count), stands.size()).clear();
        tunnels.clear();
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
        return true;
    }

    /** What the mine has opened so far: steps of the staircase and advances of tunnels; a measure of progress. */
    public int opened() {
        int total = stands.size();
        for (Tunnel tunnel : tunnels) total += tunnel.length();
        return total;
    }

    public Set<BlockPos> seals() { return Collections.unmodifiableSet(seals); }

    public void sealed(BlockPos cell) { if (entrance != null && seals.size() < MAX_SEALS) seals.add(cell.immutable()); }

    public void unsealed(BlockPos cell) { seals.remove(cell); }

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

    /** The remembered refusals as the hazard checks take them. */
    public CompanionShaftPlan.Avoid avoid() {
        return new CompanionShaftPlan.Avoid(Collections.unmodifiableSet(forbidden), this::refused);
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
    public int verify(ServerLevel level) {
        if (entrance == null) return 0;
        if (level.hasChunkAt(entrance) && !CompanionShaftPlan.columnOpen(level, entrance)) {
            truncate(0);
        } else {
            int kept = 0;
            while (kept < stands.size() && !closed(level, stands.get(kept))) kept++;
            truncate(kept);
        }
        seals.removeIf(cell -> level.hasChunkAt(cell) && level.getBlockState(cell).isAir());
        if (plug != null && level.hasChunkAt(plug) && level.getBlockState(plug).isAir()) plug = null;
        return stands.size();
    }

    /** The stand's column is open, or closed only by his own plug (a retreat leaves it; the next step takes it out), and a floor holds under it. */
    private boolean closed(ServerLevel level, BlockPos stand) {
        if (!level.hasChunkAt(stand)) return false;
        BlockPos floor = stand.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return true;
        for (BlockPos cell : CompanionShaftPlan.column(stand)) {
            if (!CompanionShaftPlan.open(level, cell) && !cell.equals(plug)) return true;
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
        if (!forbidden.isEmpty()) mine.putLongArray("Forbidden", forbidden.stream().mapToLong(BlockPos::asLong).toArray());
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
        for (long packed : mine.getLongArray("Forbidden")) if (forbidden.size() < MAX_FORBIDDEN) forbidden.add(BlockPos.of(packed));
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
