package dev.aicompanion.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Memory, bond and happenings per OWNER, kept with the world: the companion is unloaded or replaced (a new UUID after death)
 * while the player's events still happen, and the one who comes back sees the same memory. Reads return copies and never create
 * data; every change goes through a method here, which marks the data dirty.
 */
public final class CompanionBonds extends SavedData {
    private static final Factory<CompanionBonds> FACTORY = new Factory<>(CompanionBonds::new, CompanionBonds::load, null);

    private static final class Slot {
        CompanionMemory memory = new CompanionMemory();
        CompanionBond bond = new CompanionBond();
        CompanionHappenings happenings = new CompanionHappenings();
        long lastSeen = -1;
    }

    private final Map<UUID, Slot> slots = new HashMap<>();

    public static CompanionBonds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aicompanion_bonds");
    }

    private Slot slot(UUID owner) { return slots.computeIfAbsent(owner, id -> new Slot()); }

    public CompanionMemory memory(UUID owner) {
        Slot slot = slots.get(owner);
        return slot == null ? new CompanionMemory() : slot.memory.copy();
    }

    public CompanionBond bond(UUID owner) {
        Slot slot = slots.get(owner);
        return slot == null ? new CompanionBond() : slot.bond.copy();
    }

    public CompanionHappenings happenings(UUID owner) {
        Slot slot = slots.get(owner);
        return slot == null ? new CompanionHappenings() : slot.happenings.copy();
    }

    /** Real-time millis of the last moment the player was seen in the world; empty when never recorded. */
    public OptionalLong lastSeen(UUID owner) {
        Slot slot = slots.get(owner);
        return slot == null || slot.lastSeen < 0 ? OptionalLong.empty() : OptionalLong.of(slot.lastSeen);
    }

    public CompanionMemory.Outcome remember(UUID owner, String kind, String text) {
        Slot slot = slot(owner);
        CompanionMemory.Outcome outcome = slot.memory.remember(kind, text);
        if (outcome != CompanionMemory.Outcome.REJECTED) setDirty();
        return outcome;
    }

    public void recordTalk(UUID owner, boolean fromPlayer, String text) {
        slot(owner).memory.recordTalk(fromPlayer, text);
        setDirty();
    }

    public boolean setCallMe(UUID owner, String name) {
        boolean stored = slot(owner).memory.setCallMe(name);
        if (stored) setDirty();
        return stored;
    }

    public boolean addLike(UUID owner, String thing) {
        boolean stored = slot(owner).memory.addLike(thing);
        if (stored) setDirty();
        return stored;
    }

    public boolean addDislike(UUID owner, String thing) {
        boolean stored = slot(owner).memory.addDislike(thing);
        if (stored) setDirty();
        return stored;
    }

    /** Forgets what the companion remembers about the player; the bond and the happenings stay. */
    public void forget(UUID owner) {
        Slot slot = slots.get(owner);
        if (slot == null) return;
        slot.memory = new CompanionMemory();
        setDirty();
    }

    public int gift(UUID owner, long gameDay, List<CompanionBond.ItemChange> changes) {
        int moved = slot(owner).bond.gift(gameDay, changes);
        setDirty();
        return moved;
    }

    public int rescued(UUID owner, long gameDay) {
        int moved = slot(owner).bond.rescued(gameDay);
        setDirty();
        return moved;
    }

    public int sharedDeed(UUID owner, long gameDay) {
        int moved = slot(owner).bond.sharedDeed(gameDay);
        setDirty();
        return moved;
    }

    public int hitByPlayer(UUID owner, long gameDay, long gameTime, boolean fightingMonster) {
        int moved = slot(owner).bond.hitByPlayer(gameDay, gameTime, fightingMonster);
        setDirty();
        return moved;
    }

    public int leftIdleFar(UUID owner, long gameDay, long idleTicks) {
        int moved = slot(owner).bond.leftIdleFar(gameDay, idleTicks);
        setDirty();
        return moved;
    }

    /** Applies the daily decay and resets the daily counters up to {@code gameDay}. */
    public void advanceBond(UUID owner, long gameDay) {
        slot(owner).bond.advanceTo(gameDay);
        setDirty();
    }

    public void happened(UUID owner, String key, String text, long gameTime, CompanionHappenings.Importance importance) {
        slot(owner).happenings.add(key, text, gameTime, importance);
        setDirty();
    }

    public void markSeen(UUID owner, long realMillis) {
        slot(owner).lastSeen = Math.max(0, realMillis);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (var entry : slots.entrySet()) {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("Owner", entry.getKey());
            owner.put("Memory", entry.getValue().memory.save());
            owner.put("Bond", entry.getValue().bond.save());
            owner.put("Happenings", entry.getValue().happenings.save());
            if (entry.getValue().lastSeen >= 0) owner.putLong("LastSeen", entry.getValue().lastSeen);
            owners.add(owner);
        }
        tag.put("Owners", owners);
        return tag;
    }

    public static CompanionBonds load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionBonds bonds = new CompanionBonds();
        ListTag owners = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag owner = owners.getCompound(i);
            if (!owner.hasUUID("Owner")) continue;
            Slot slot = new Slot();
            slot.memory = CompanionMemory.load(owner.getCompound("Memory"));
            slot.bond = CompanionBond.load(owner.getCompound("Bond"));
            slot.happenings = CompanionHappenings.load(owner.getCompound("Happenings"));
            slot.lastSeen = owner.contains("LastSeen") ? owner.getLong("LastSeen") : -1;
            bonds.slots.put(owner.getUUID("Owner"), slot);
        }
        return bonds;
    }
}
