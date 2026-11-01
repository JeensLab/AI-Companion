package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounded persistent facts about observed/placed blocks; unloaded terrain remains unknown. */
final class CompanionPlaces {
    private record Place(String dimension, BlockPos pos, String block, boolean placed) { }
    private record HomePlace(String dimension, BlockPos pos) { }
    /** Where home is, and what makes it home. */
    record Home(BlockPos pos, String source) { }
    private static final int HOME_FINDS = 16;
    private static final int HOME_FINDS_FROM = 24;
    private static final int HOME_FINDS_MAX = 24;
    private final CompanionEntity owner;
    private final LinkedHashMap<String, Place> places = new LinkedHashMap<>();
    private long nextDiscovery;
    private HomePlace namedHome;

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
            String oldest = places.entrySet().stream().filter(entry -> !workplace(entry.getValue().block)).map(java.util.Map.Entry::getKey).findFirst()
                    .or(() -> places.entrySet().stream().filter(entry -> !entry.getValue().placed).map(java.util.Map.Entry::getKey).findFirst())
                    .orElse(places.keySet().iterator().next());
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
    /** Chests and furnaces of the home as seen from there: seen, never «set down by him», so the player's stay the player's. */
    void discoverHome() {
        Home home = home();
        if (home == null || !(owner.level() instanceof ServerLevel level) || home.pos.distSqr(owner.blockPosition()) > HOME_FINDS_FROM * HOME_FINDS_FROM) return;
        int chests = 0, furnaces = 0;
        for (BlockPos pos : owner.work.chests.blockEntitiesNear(level, home.pos, HOME_FINDS)) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.CHEST) && chests < HOME_FINDS_MAX) { chests++; remember(pos, false); }
            else if (state.is(Blocks.FURNACE) && furnaces < HOME_FINDS_MAX) { furnaces++; remember(pos, false); }
        }
    }
    /** The player's word: «это наш дом». Stands above anything he worked out himself. */
    void nameHome(BlockPos pos) {
        namedHome = new HomePlace(dimension(), pos.immutable());
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "home_named", "entity", owner.getUUID(), "dimension", dimension(), "position", pos);
    }
    /** Home in this dimension: named by the player, else where the player builds, else the house he finished himself. Null when there is none. */
    Home home() {
        if (!(owner.level() instanceof ServerLevel level)) return null;
        if (namedHome != null && namedHome.dimension.equals(dimension())) return new Home(namedHome.pos, "назвал игрок");
        BlockPos built = owner.getOwnerId() == null ? null : CompanionPlayerBuilds.get(level.getServer()).home(owner.getOwnerId(), level);
        if (built != null) return new Home(built, "здесь строит игрок");
        BlockPos house = owner.work.house.finishedAnchor(level);
        return house == null ? null : new Home(house, "мой достроенный дом");
    }
    /** Remembered blocks of this kind in this dimension, nearest first; the player's own chests count without a visit. */
    List<BlockPos> stations(String block) {
        validateLoaded();
        var found = new java.util.LinkedHashSet<>(places.values().stream().filter(place -> place.dimension.equals(dimension()) && place.block.equals(block)).map(Place::pos).toList());
        if (block.equals("minecraft:chest") && owner.level() instanceof ServerLevel level && owner.getOwnerId() != null)
            found.addAll(CompanionPlayerBuilds.get(level.getServer()).chests(owner.getOwnerId(), level));
        return found.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(owner.blockPosition()))).toList();
    }
    List<BlockPos> tables(boolean loadedOnly) {
        validateLoaded();
        double radius = Math.max(32, owner.getSearchRadius());
        return places.values().stream().filter(place -> place.dimension.equals(dimension()) && place.block.equals("minecraft:crafting_table")
                        && place.pos.distSqr(owner.blockPosition()) <= radius * radius && (!loadedOnly || owner.level().hasChunkAt(place.pos)))
                .sorted(Comparator.comparingDouble(place -> place.pos.distSqr(owner.blockPosition()))).map(Place::pos).toList();
    }
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
        Home home = home();
        if (home != null) entries.add("дом на " + home.pos.toShortString() + " (" + home.source + ")");
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
        if (namedHome != null) {
            CompoundTag home = new CompoundTag(); home.putString("Dimension", namedHome.dimension); home.putLong("Pos", namedHome.pos.asLong());
            tag.put("HomePlace", home);
        }
    }
    void load(CompoundTag tag) {
        places.clear(); nextDiscovery = 0;
        namedHome = null;
        if (tag.contains("HomePlace", 10)) {
            CompoundTag home = tag.getCompound("HomePlace");
            namedHome = new HomePlace(home.getString("Dimension"), BlockPos.of(home.getLong("Pos")));
        }
        ListTag list = tag.getList("KnownPlaces", 10);
        for (int i = 0; i < Math.min(256, list.size()); i++) {
            CompoundTag entry = list.getCompound(i); BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            Place place = new Place(entry.getString("Dimension"), pos, entry.getString("Block"), entry.getBoolean("Placed"));
            places.put(key(place.dimension, pos), place);
        }
    }
}
