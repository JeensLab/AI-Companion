package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a companion fell with things worth going back for, kept with the world: the companion that comes back is a new
 * one, and the place must outlive it and a reload. One place per death, the dearest first, and never more than a handful:
 * a walk to an old place costs, and the world is not a warehouse of his mistakes.
 */
public final class CompanionDeathSpots extends SavedData {
    static final int MOST = 8;
    private static final Factory<CompanionDeathSpots> FACTORY =
            new Factory<>(CompanionDeathSpots::new, CompanionDeathSpots::load, null);

    /** A place where he fell, its price and when it happened. */
    public record Spot(ResourceKey<Level> dimension, BlockPos pos, double worth, long tick) { }

    private final Map<UUID, List<Spot>> spots = new HashMap<>();

    public static CompanionDeathSpots get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aicompanion_death_spots");
    }

    public void died(UUID owner, ResourceKey<Level> dimension, BlockPos pos, double worth, long tick) {
        List<Spot> list = spots.computeIfAbsent(owner, key -> new ArrayList<>());
        list.add(new Spot(dimension, pos.immutable(), worth, tick));
        while (list.size() > MOST) list.remove(0);
        setDirty();
    }

    /** The dearest place in this dimension, or null when there is none worth a walk. */
    public Spot dearest(UUID owner, ResourceKey<Level> dimension) {
        Spot best = null;
        for (Spot spot : spots.getOrDefault(owner, List.of())) {
            if (!spot.dimension().equals(dimension) || spot.worth() < CompanionBelongings.WORTH_GOING) continue;
            if (best == null || spot.worth() > best.worth()) best = spot;
        }
        return best;
    }

    public List<Spot> all(UUID owner) { return List.copyOf(spots.getOrDefault(owner, List.of())); }

    public void taken(UUID owner, Spot spot) {
        List<Spot> list = spots.get(owner);
        if (list != null && list.remove(spot)) {
            if (list.isEmpty()) spots.remove(owner);
            setDirty();
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        for (Map.Entry<UUID, List<Spot>> entry : spots.entrySet()) {
            ListTag list = new ListTag();
            for (Spot spot : entry.getValue()) {
                CompoundTag one = new CompoundTag();
                one.putString("dimension", spot.dimension().location().toString());
                one.putInt("x", spot.pos().getX());
                one.putInt("y", spot.pos().getY());
                one.putInt("z", spot.pos().getZ());
                one.putDouble("worth", spot.worth());
                one.putLong("tick", spot.tick());
                list.add(one);
            }
            owners.put(entry.getKey().toString(), list);
        }
        tag.put("owners", owners);
        return tag;
    }

    public static CompanionDeathSpots load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionDeathSpots data = new CompanionDeathSpots();
        CompoundTag owners = tag.getCompound("owners");
        for (String key : owners.getAllKeys()) {
            List<Spot> list = new ArrayList<>();
            for (Tag element : owners.getList(key, Tag.TAG_COMPOUND)) {
                CompoundTag one = (CompoundTag) element;
                ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(one.getString("dimension")));
                list.add(new Spot(dimension, new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")),
                        one.getDouble("worth"), one.getLong("tick")));
            }
            data.spots.put(UUID.fromString(key), list);
        }
        return data;
    }
}
