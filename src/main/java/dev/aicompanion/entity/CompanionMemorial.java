package dev.aicompanion.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A companion that died, kept with the world (not with the player, who may be offline at that moment): its UUID is never
 * waited for again, and its memory (journal of deeds, places, house) goes to the companion who comes back.
 */
public final class CompanionMemorial extends SavedData {
    private static final Factory<CompanionMemorial> FACTORY = new Factory<>(CompanionMemorial::new, CompanionMemorial::load, null);
    /** Past this many, the oldest records go; the order of insertion is the order of saving. */
    public static final int MAX_DEAD = 20;
    private final Set<UUID> dead = new LinkedHashSet<>();
    private final Map<UUID, CompoundTag> memories = new HashMap<>();
    private final Map<UUID, Long> returnAt = new HashMap<>();

    static CompanionMemorial get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aicompanion_memorial");
    }

    public boolean isDead(UUID companion) { return dead.contains(companion); }
    public boolean awaitsReturn(UUID owner) { return returnAt.containsKey(owner); }
    boolean mayReturn(UUID owner, long now) { return returnAt.containsKey(owner) && now >= returnAt.get(owner); }
    Set<UUID> waitingOwners() { return Set.copyOf(returnAt.keySet()); }

    public void died(UUID owner, UUID companion, CompoundTag memory, long returnTick) {
        remember(companion); memories.put(owner, memory.copy()); returnAt.put(owner, returnTick); setDirty();
    }

    private void remember(UUID companion) {
        dead.add(companion);
        for (Iterator<UUID> oldest = dead.iterator(); dead.size() > MAX_DEAD && oldest.hasNext(); ) { oldest.next(); oldest.remove(); }
    }

    /** The memory for the companion who comes back; the owner then no longer waits. */
    CompoundTag returned(UUID owner) {
        returnAt.remove(owner); setDirty();
        CompoundTag memory = memories.remove(owner);
        return memory == null ? new CompoundTag() : memory;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag deadList = new ListTag();
        for (UUID id : dead) deadList.add(NbtUtils.createUUID(id));
        tag.put("Dead", deadList);
        ListTag waiting = new ListTag();
        for (var entry : returnAt.entrySet()) {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("Owner", entry.getKey()); owner.putLong("ReturnAt", entry.getValue());
            owner.put("Memory", memories.getOrDefault(entry.getKey(), new CompoundTag()));
            waiting.add(owner);
        }
        tag.put("Waiting", waiting);
        return tag;
    }

    public static CompanionMemorial load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionMemorial memorial = new CompanionMemorial();
        for (Tag id : tag.getList("Dead", Tag.TAG_INT_ARRAY)) memorial.remember(NbtUtils.loadUUID(id));
        ListTag waiting = tag.getList("Waiting", Tag.TAG_COMPOUND);
        for (int i = 0; i < waiting.size(); i++) {
            CompoundTag owner = waiting.getCompound(i);
            if (!owner.hasUUID("Owner")) continue;
            memorial.returnAt.put(owner.getUUID("Owner"), owner.getLong("ReturnAt"));
            memorial.memories.put(owner.getUUID("Owner"), owner.getCompound("Memory"));
        }
        return memorial;
    }
}
