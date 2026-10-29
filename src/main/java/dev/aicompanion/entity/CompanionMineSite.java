package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The record of the companion's diamond mine: where it starts, the stands he has reached down the staircase, the tunnels and
 * the blocks he placed himself. It is a hint, the world is the truth: {@link #verify} cuts the staircase back to the part
 * still open. Saved under {@link #KEY}; a save without the key has no mine.
 */
public final class CompanionMineSite {
    public static final String KEY = "MineSite";
    private static final int MAX_STANDS = 512, MAX_TUNNELS = 16, MAX_SEALS = 1024, MAX_TUNNEL_LENGTH = CompanionShaftPlan.MAX_TUNNEL_CELLS;

    /** One tunnel from the stair foot: its direction and how many advances (of four cells) are open. */
    public record Tunnel(Direction direction, int length) { }

    private BlockPos entrance;
    private Direction stairDirection = Direction.NORTH;
    private final List<BlockPos> stands = new ArrayList<>();
    private final List<Tunnel> tunnels = new ArrayList<>();
    private final Set<BlockPos> seals = new LinkedHashSet<>();
    private BlockPos plug;

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

    /** The cork set behind him on the first step up when retreating; the next step takes it out again. */
    public BlockPos plug() { return plug; }

    public void plugged(BlockPos cell) { plug = cell == null ? null : cell.immutable(); }

    /**
     * Cuts the staircase back to the stands whose three cells are all still open, from the entrance down, and forgets own
     * blocks that are gone. An unloaded chunk ends the verified part (loading it here would stall the tick).
     * Returns how many stands remain; tunnels are dropped with any stand that goes.
     */
    public int verify(ServerLevel level) {
        if (entrance == null) return 0;
        if (!CompanionShaftPlan.columnOpen(level, entrance)) {
            truncate(0);
        } else {
            int kept = 0;
            while (kept < stands.size() && CompanionShaftPlan.columnOpen(level, stands.get(kept))) kept++;
            truncate(kept);
        }
        seals.removeIf(cell -> !level.hasChunkAt(cell) || level.getBlockState(cell).isAir());
        if (plug != null && (!level.hasChunkAt(plug) || level.getBlockState(plug).isAir())) plug = null;
        return stands.size();
    }

    public void save(CompoundTag tag) {
        tag.remove(KEY);
        if (entrance == null) return;
        CompoundTag mine = new CompoundTag();
        mine.putLong("Entrance", entrance.asLong());
        mine.putByte("Direction", (byte) stairDirection.get2DDataValue());
        mine.putLongArray("Stands", stands.stream().mapToLong(BlockPos::asLong).toArray());
        mine.putLongArray("Seals", seals.stream().mapToLong(BlockPos::asLong).toArray());
        if (plug != null) mine.putLong("Plug", plug.asLong());
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

    public void load(CompoundTag tag) {
        clear();
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag mine = tag.getCompound(KEY);
        if (!mine.contains("Entrance", Tag.TAG_LONG)) return;
        entrance = BlockPos.of(mine.getLong("Entrance"));
        stairDirection = Direction.from2DDataValue(mine.getByte("Direction"));
        for (long packed : mine.getLongArray("Stands")) if (stands.size() < MAX_STANDS) stands.add(BlockPos.of(packed));
        for (long packed : mine.getLongArray("Seals")) if (seals.size() < MAX_SEALS) seals.add(BlockPos.of(packed));
        if (mine.contains("Plug", Tag.TAG_LONG)) plug = BlockPos.of(mine.getLong("Plug"));
        ListTag list = mine.getList("Tunnels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && tunnels.size() < MAX_TUNNELS; i++) {
            CompoundTag entry = list.getCompound(i);
            tunnels.add(new Tunnel(Direction.from2DDataValue(entry.getByte("Direction")), Math.max(0, Math.min(MAX_TUNNEL_LENGTH, entry.getInt("Length")))));
        }
    }
}
