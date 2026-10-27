package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounded persistent facts about observed/placed blocks; unloaded terrain remains unknown. */
final class CompanionPlaces {
    private record Place(String dimension, BlockPos pos, String block, boolean placed) { }
    private final CompanionEntity owner;
    private final LinkedHashMap<String, Place> places = new LinkedHashMap<>();
    private long nextDiscovery;

    CompanionPlaces(CompanionEntity owner) { this.owner = owner; }
    private String dimension() { return owner.level().dimension().location().toString(); }
    private String key(String dimension, BlockPos pos) { return dimension + ":" + pos.asLong(); }
    void remember(BlockPos pos, boolean placed) {
        if (owner.level().isClientSide || !owner.level().hasChunkAt(pos)) return;
        String block = BuiltInRegistries.BLOCK.getKey(owner.level().getBlockState(pos).getBlock()).toString();
        if (block.equals("minecraft:air")) return;
        String key = key(dimension(), pos);
        Place previous = places.get(key);
        places.put(key, new Place(dimension(), pos.immutable(), block, placed || previous != null && previous.block.equals(block) && previous.placed));
        if (previous == null || !previous.block.equals(block) || placed && !previous.placed)
            dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "place_remembered", "entity", owner.getUUID(),
                    "dimension", dimension(), "position", pos, "block", block, "placedByCompanion", placed);
        while (places.size() > 256) {
            String oldest = places.entrySet().stream().filter(entry -> !workplace(entry.getValue().block))
                    .map(java.util.Map.Entry::getKey).findFirst().orElse(places.keySet().iterator().next());
            places.remove(oldest);
        }
    }
    private static boolean workplace(String id) { return id.equals("minecraft:crafting_table") || id.equals("minecraft:chest") || id.equals("minecraft:furnace"); }
    private void validateLoaded() {
        places.values().removeIf(place -> place.dimension.equals(dimension()) && owner.level().hasChunkAt(place.pos)
                && !BuiltInRegistries.BLOCK.getKey(owner.level().getBlockState(place.pos).getBlock()).toString().equals(place.block));
    }
    void discoverTables() {
        if (owner.level().isClientSide || owner.level().getGameTime() < nextDiscovery) return;
        nextDiscovery = owner.level().getGameTime() + 100;
        BlockPos center = owner.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -6, -16), center.offset(16, 6, 16)))
            if (owner.level().hasChunkAt(pos) && owner.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) remember(pos, false);
    }
    List<BlockPos> tables(boolean loadedOnly) {
        validateLoaded();
        double radius = Math.max(32, owner.getSearchRadius());
        return places.values().stream().filter(place -> place.dimension.equals(dimension()) && place.block.equals("minecraft:crafting_table")
                        && place.pos.distSqr(owner.blockPosition()) <= radius * radius && (!loadedOnly || owner.level().hasChunkAt(place.pos)))
                .sorted(Comparator.comparingDouble(place -> place.pos.distSqr(owner.blockPosition()))).map(Place::pos).toList();
    }
    /** He set this block down or saw it: its place is known (what is inside a chest is learnt only by opening it). */
    boolean knows(BlockPos pos) { return places.containsKey(key(dimension(), pos)); }
    /** He set this block down himself and it is still the block he set (a chest, furnace or bench of his, not the player's). */
    boolean placedByCompanion(BlockPos pos) {
        Place place = places.get(key(dimension(), pos));
        return place != null && place.placed && (!owner.level().hasChunkAt(pos)
                || BuiltInRegistries.BLOCK.getKey(owner.level().getBlockState(pos).getBlock()).toString().equals(place.block));
    }
    /** Where he set down blocks of this kind himself, in this dimension. */
    List<BlockPos> placed(String block) {
        validateLoaded();
        return places.values().stream().filter(place -> place.placed && place.dimension.equals(dimension()) && place.block.equals(block)).map(Place::pos).toList();
    }
    String summary() {
        validateLoaded();
        var entries = new ArrayList<String>();
        places.values().stream().filter(place -> workplace(place.block)).sorted(Comparator
                .comparing((Place place) -> !place.dimension.equals(dimension()))
                .thenComparingDouble(place -> place.pos.distSqr(owner.blockPosition()))).limit(12).forEach(place ->
                entries.add(place.block + " на " + place.pos.toShortString() + " в " + place.dimension + (place.placed ? " (поставил)" : " (видел)")
                        + (place.dimension.equals(dimension()) && owner.level().hasChunkAt(place.pos) ? " (проверено)" : " (сейчас не проверено)")));
        var other = places.values().stream().filter(place -> place.placed && !workplace(place.block)).toList();
        for (int i = other.size() - 1, count = 0; i >= 0 && count < 6; i--, count++) {
            Place place = other.get(i);
            entries.add(place.block + " на " + place.pos.toShortString() + " в " + place.dimension + " (поставил)"
                    + (place.dimension.equals(dimension()) && owner.level().hasChunkAt(place.pos) ? " (проверено)" : " (сейчас не проверено)"));
        }
        return entries.isEmpty() ? "известных рабочих мест нет" : "Известные места: " + String.join("; ", entries);
    }
    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Place place : places.values()) {
            CompoundTag entry = new CompoundTag(); entry.putString("Dimension", place.dimension); entry.putLong("Pos", place.pos.asLong());
            entry.putString("Block", place.block); entry.putBoolean("Placed", place.placed); list.add(entry);
        }
        tag.put("KnownPlaces", list);
    }
    void load(CompoundTag tag) {
        places.clear(); nextDiscovery = 0;
        ListTag list = tag.getList("KnownPlaces", 10);
        for (int i = 0; i < Math.min(256, list.size()); i++) {
            CompoundTag entry = list.getCompound(i); BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            Place place = new Place(entry.getString("Dimension"), pos, entry.getString("Block"), entry.getBoolean("Placed"));
            places.put(key(place.dimension, pos), place);
        }
    }
}
