package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The named places the owner asked the companion to remember, kept with the world so they outlive a reload and the
 * companion itself: a name, the dimension, the point and when it was made. Each player has his own list, bounded at
 * {@link #MOST}; the oldest marker makes room for a new one and is named in the answer.
 * <p>
 * A marker is not a block and not a promise: walking to one uses the normal path, and another dimension is said as it is.
 */
public final class CompanionMarkers extends SavedData {
    public static final int MOST = 20;
    private static final String DATA_NAME = "aicompanion_markers";
    private static final Factory<CompanionMarkers> FACTORY = new Factory<>(CompanionMarkers::new, CompanionMarkers::load, null);

    /** One remembered place: its id (what the settings screen renames, moves or deletes), its name and where it is. */
    public record Marker(UUID id, String name, ResourceKey<Level> dimension, BlockPos pos, long created) { }

    /** A marker saved now; {@code evicted} is the oldest one that made room for it, if the list was full. */
    public record Added(Marker marker, Marker evicted, boolean moved, String refusal) {
        static Added refused(String line) { return new Added(null, null, false, line); }
        /** True when the name was not a marker name: nothing was stored, and {@link #refusal()} is the honest line. */
        public boolean refused() { return marker == null; }
    }

    /** Why a rename was refused, or that it was accepted: the screen says which cause, not one line for all of them. */
    public enum Renamed { RENAMED, NOT_FOUND, EMPTY_NAME, TOO_LONG, COORDINATES, TAKEN }

    private final Map<UUID, List<Marker>> markers = new HashMap<>();

    public static CompanionMarkers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** The everyday name of a dimension as the player reads it in the settings. */
    public static String worldName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) return "world";
        if (dimension.equals(Level.NETHER)) return "nether";
        if (dimension.equals(Level.END)) return "end";
        return dimension.location().getPath();
    }

    /**
     * Saves a marker at the companion's point. The same name again moves that marker instead of making a second one, so
     * one name never means two places. A full list loses its oldest marker. A name the rename and the load refuse is not
     * stored here either: otherwise it would be saved and silently dropped on the next load.
     */
    public Added add(UUID owner, String name, ResourceKey<Level> dimension, BlockPos pos, long tick) {
        String clean = CompanionMarkerNames.clean(name);
        String refusal = CompanionMarkerNames.refusal(clean);
        if (!refusal.isEmpty()) return Added.refused(refusal);
        List<Marker> list = markers.computeIfAbsent(owner, key -> new ArrayList<>());
        for (int at = 0; at < list.size(); at++) {
            Marker marker = list.get(at);
            if (!marker.name().equalsIgnoreCase(clean)) continue;
            Marker moved = new Marker(marker.id(), clean, dimension, pos.immutable(), marker.created());
            list.set(at, moved);
            setDirty();
            return new Added(moved, null, true, "");
        }
        Marker marker = new Marker(UUID.randomUUID(), clean, dimension, pos.immutable(), tick);
        Marker evicted = null;
        if (list.size() >= MOST) {
            evicted = list.stream().min(Comparator.comparingLong(Marker::created)).orElse(null);
            if (evicted != null) list.remove(evicted);
        }
        list.add(marker);
        setDirty();
        return new Added(marker, evicted, false, "");
    }

    /** Every marker of the owner, oldest first. */
    public List<Marker> all(UUID owner) {
        List<Marker> list = new ArrayList<>(markers.getOrDefault(owner, List.of()));
        list.sort(Comparator.comparingLong(Marker::created));
        return List.copyOf(list);
    }

    public Marker byId(UUID owner, UUID markerId) {
        for (Marker marker : markers.getOrDefault(owner, List.of())) if (marker.id().equals(markerId)) return marker;
        return null;
    }

    /** The markers a spoken or written name means, in any Russian case form. */
    public List<Marker> matching(UUID owner, String said) {
        if (said == null || said.isBlank()) return List.of();
        List<Marker> found = new ArrayList<>();
        for (Marker marker : all(owner)) if (CompanionMarkerNames.matches(marker.name(), said)) found.add(marker);
        return List.copyOf(found);
    }

    /** Renames a marker; a name another marker already has is refused, so one name never means two places. */
    public boolean rename(UUID owner, UUID markerId, String name) {
        return renameChecked(owner, markerId, name) == Renamed.RENAMED;
    }

    /** The rename and the reason it was refused: the marker first, then the name, so each cause has its own answer. */
    public Renamed renameChecked(UUID owner, UUID markerId, String name) {
        String clean = CompanionMarkerNames.clean(name);
        List<Marker> list = markers.get(owner);
        Marker marker = markerId == null || list == null ? null : byId(owner, markerId);
        if (marker == null) return Renamed.NOT_FOUND;
        if (clean.isEmpty()) return Renamed.EMPTY_NAME;
        if (clean.length() > CompanionMarkerNames.MAX) return Renamed.TOO_LONG;
        if (!CompanionMarkerNames.valid(clean)) return Renamed.COORDINATES;
        for (Marker other : list) if (!other.id().equals(markerId) && other.name().equalsIgnoreCase(clean)) return Renamed.TAKEN;
        list.set(list.indexOf(marker), new Marker(marker.id(), clean, marker.dimension(), marker.pos(), marker.created()));
        setDirty();
        return Renamed.RENAMED;
    }

    /** Moves a marker to another point of its own dimension. */
    public boolean move(UUID owner, UUID markerId, BlockPos pos) {
        List<Marker> list = markers.get(owner);
        if (list == null) return false;
        Marker marker = byId(owner, markerId);
        if (marker == null) return false;
        list.set(list.indexOf(marker), new Marker(marker.id(), marker.name(), marker.dimension(), pos.immutable(), marker.created()));
        setDirty();
        return true;
    }

    /** Forgets one marker by its own id; a neighbour with another name stays. */
    public boolean remove(UUID owner, UUID markerId) {
        List<Marker> list = markers.get(owner);
        if (list == null) return false;
        boolean removed = list.removeIf(marker -> marker.id().equals(markerId));
        if (!removed) return false;
        if (list.isEmpty()) markers.remove(owner);
        setDirty();
        return true;
    }

    /** The one honest question for a name that fits more than one marker. */
    public static String questionFor(List<Marker> matches) {
        return "Под это название подходят метки: " + quoted(matches) + ". Какую ты имеешь в виду?";
    }

    /** The one honest answer for a name that fits no marker: what he does know, or that he knows nothing yet. */
    public static String unknown(String said, List<Marker> known) {
        if (known.isEmpty()) return "Меток пока нет. Скажи «запомни это место, " + CompanionMarkerNames.clean(said) + "», и я запомню.";
        return "Не знаю метки «" + CompanionMarkerNames.clean(said) + "». Известные метки: " + quoted(known) + ". Скажи, к какой идти.";
    }

    private static String quoted(List<Marker> markers) {
        return String.join(", ", markers.stream().map(marker -> "«" + marker.name() + "»").toList());
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        for (Map.Entry<UUID, List<Marker>> entry : markers.entrySet()) {
            ListTag list = new ListTag();
            for (Marker marker : entry.getValue()) {
                CompoundTag one = new CompoundTag();
                one.putUUID("id", marker.id());
                one.putString("name", marker.name());
                one.putString("dimension", marker.dimension().location().toString());
                one.putInt("x", marker.pos().getX());
                one.putInt("y", marker.pos().getY());
                one.putInt("z", marker.pos().getZ());
                one.putLong("created", marker.created());
                list.add(one);
            }
            owners.put(entry.getKey().toString(), list);
        }
        tag.put("owners", owners);
        return tag;
    }

    public static CompanionMarkers load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionMarkers data = new CompanionMarkers();
        CompoundTag owners = tag.getCompound("owners");
        for (String key : owners.getAllKeys()) {
            UUID owner;
            try { owner = UUID.fromString(key); } catch (IllegalArgumentException ignored) { continue; }
            List<Marker> list = new ArrayList<>();
            for (Tag element : owners.getList(key, Tag.TAG_COMPOUND)) {
                CompoundTag one = (CompoundTag) element;
                String name = CompanionMarkerNames.clean(one.getString("name"));
                String dimension = one.getString("dimension");
                if (name.isEmpty() || !CompanionMarkerNames.valid(name) || dimension.isEmpty()) continue;
                ResourceLocation id = ResourceLocation.tryParse(dimension);
                if (id == null) continue;
                list.add(new Marker(one.hasUUID("id") ? one.getUUID("id") : UUID.randomUUID(),
                        name, ResourceKey.create(Registries.DIMENSION, id),
                        new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")), one.getLong("created")));
            }
            list.sort(Comparator.comparingLong(Marker::created));
            if (!list.isEmpty()) data.markers.put(owner, list);
        }
        return data;
    }
}
