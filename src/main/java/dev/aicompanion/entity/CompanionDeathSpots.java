package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a companion fell with things worth going back for, kept with the world: the companion that comes back is a new
 * one, and the place must outlive it and a reload. A place carries the manifest of what fell from him — for every stack
 * the entity, the exact point it fell from, the item and the count — and that manifest, not a scan of the ground, is what
 * the walk fetches: another death's pile at the same spot and a stranger's drop are not his.
 * <p>
 * The manifest is pruned the moment a stack is taken by anybody, so a place somebody already looted is emptied before a
 * walk ever starts for it.
 */
public final class CompanionDeathSpots extends SavedData {
    static final int MOST = 8;
    private static final Factory<CompanionDeathSpots> FACTORY =
            new Factory<>(CompanionDeathSpots::new, CompanionDeathSpots::load, null);

    /** One stack that fell from him: its entity, the exact point it fell from, the item and how many there were. */
    public record Entry(UUID id, Vec3 pos, String item, int count) {
        public BlockPos blockPos() { return BlockPos.containing(pos); }
    }

    /** A place where he fell: its price, when it happened, the budget spent on it and the manifest of what fell there. */
    public record Spot(UUID id, ResourceKey<Level> dimension, BlockPos pos, double worth, long tick, int attempts, int deaths,
                       List<Entry> manifest) {
        public Spot { manifest = List.copyOf(manifest); }
    }

    private final Map<UUID, List<Spot>> spots = new HashMap<>();

    public static CompanionDeathSpots get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aicompanion_death_spots");
    }

    /** The manifest of a fall: every stack that fell from him, with the entity it became. */
    public static List<Entry> manifestOf(List<ItemEntity> fallen) {
        List<Entry> manifest = new ArrayList<>();
        for (ItemEntity item : fallen) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;
            manifest.add(new Entry(item.getUUID(), item.position(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
        }
        return manifest;
    }

    /** A place without its own id or manifest: an old record, trusted until its stacks are seen. */
    public void died(UUID owner, ResourceKey<Level> dimension, BlockPos pos, double worth, long tick) {
        died(owner, UUID.randomUUID(), dimension, pos, worth, tick, List.of());
    }

    public void died(UUID owner, UUID id, ResourceKey<Level> dimension, BlockPos pos, double worth, long tick, List<Entry> manifest) {
        List<Spot> list = spots.computeIfAbsent(owner, key -> new ArrayList<>());
        list.add(new Spot(id, dimension, pos.immutable(), worth, tick, 0, 0, manifest));
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

    /** The record of one fall, by its own id: two falls at the same coordinates are two places. */
    public Spot byId(UUID owner, UUID spotId) {
        for (Spot spot : spots.getOrDefault(owner, List.of())) if (spot.id().equals(spotId)) return spot;
        return null;
    }

    /** The stacks still recorded as lying at a place. */
    public List<Entry> manifestOf(UUID owner, UUID spotId) {
        Spot spot = byId(owner, spotId);
        return spot == null ? List.of() : spot.manifest();
    }

    /** A stack was taken away: it leaves the manifest, so no walk ever sets out for it again. */
    public boolean pruneEntry(UUID owner, UUID spotId, UUID itemId) {
        if (owner != null && pruneEntryIn(owner, spotId, itemId)) return true;
        // The owner's mark can be missing on an old drop: the spot's own id is enough to find it.
        for (UUID other : List.copyOf(spots.keySet())) if (pruneEntryIn(other, spotId, itemId)) return true;
        return false;
    }

    private boolean pruneEntryIn(UUID owner, UUID spotId, UUID itemId) {
        List<Spot> list = spots.get(owner);
        if (list == null) return false;
        for (int index = 0; index < list.size(); index++) {
            Spot spot = list.get(index);
            if (!spot.id().equals(spotId)) continue;
            List<Entry> manifest = new ArrayList<>(spot.manifest());
            if (!manifest.removeIf(entry -> entry.id().equals(itemId))) return false;
            list.set(index, new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(), spot.attempts(), spot.deaths(), manifest));
            setDirty();
            return true;
        }
        return false;
    }

    /** A part of a stack was taken: the manifest keeps the stack with what is left of it. */
    public void setEntryCount(UUID owner, UUID spotId, UUID itemId, int left) {
        if (owner != null && setEntryCountIn(owner, spotId, itemId, left)) return;
        // The owner's mark can be missing on an old drop: the spot's own id is enough to find it.
        for (UUID other : List.copyOf(spots.keySet())) if (setEntryCountIn(other, spotId, itemId, left)) return;
    }

    private boolean setEntryCountIn(UUID owner, UUID spotId, UUID itemId, int left) {
        List<Spot> list = spots.get(owner);
        if (list == null) return false;
        for (int index = 0; index < list.size(); index++) {
            Spot spot = list.get(index);
            if (!spot.id().equals(spotId)) continue;
            List<Entry> manifest = new ArrayList<>(spot.manifest());
            for (int at = 0; at < manifest.size(); at++) {
                Entry entry = manifest.get(at);
                if (!entry.id().equals(itemId) || entry.count() == left) continue;
                manifest.set(at, new Entry(entry.id(), entry.pos(), entry.item(), left));
                list.set(index, new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(), spot.attempts(), spot.deaths(), manifest));
                setDirty();
                return true;
            }
            // The stack is in this place but already counted so: nothing to write, and no other place is asked.
            for (Entry entry : manifest) if (entry.id().equals(itemId)) return true;
            return false;
        }
        return false;
    }

    /** A player took a stack of a fall: what he emptied leaves the manifest, a part he took keeps the rest. */
    public static void onPlayerPickup(ItemEntityPickupEvent.Post event) {
        ItemEntity item = event.getItemEntity();
        CompoundTag data = item.getPersistentData();
        if (!data.hasUUID(CompanionBag.BELONGINGS_SPOT)) return;
        if (!(item.level() instanceof net.minecraft.server.level.ServerLevel level) || level.getServer() == null) return;
        UUID owner = data.hasUUID(CompanionBag.BELONGINGS) ? data.getUUID(CompanionBag.BELONGINGS) : null;
        UUID spotId = data.getUUID(CompanionBag.BELONGINGS_SPOT);
        CompanionDeathSpots data0 = get(level.getServer());
        if (event.getCurrentStack().isEmpty()) data0.pruneEntry(owner, spotId, item.getUUID());
        else data0.setEntryCount(owner, spotId, item.getUUID(), event.getCurrentStack().getCount());
    }

    /** He arrived at the place: one more attempt spent on it. */
    public void attempted(UUID owner, UUID spotId) {
        replaceById(owner, spotId, spot -> new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(),
                spot.attempts() + 1, spot.deaths(), spot.manifest()));
    }

    /** A walk that ended only because his bag was full gives the try back: the place is not closer to being given up. */
    public void refundAttempt(UUID owner, UUID spotId) {
        replaceById(owner, spotId, spot -> new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(),
                Math.max(0, spot.attempts() - 1), spot.deaths(), spot.manifest()));
    }

    /** He died on the way to it: the place remembers how many times it cost him his life. */
    public void deathOnTheWay(UUID owner, UUID spotId) {
        replaceById(owner, spotId, spot -> new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(),
                spot.attempts(), spot.deaths() + 1, spot.manifest()));
    }

    public void attempted(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        replace(owner, dimension, pos, spot -> new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(),
                spot.attempts() + 1, spot.deaths(), spot.manifest()));
    }

    public void deathOnTheWay(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        replace(owner, dimension, pos, spot -> new Spot(spot.id(), spot.dimension(), spot.pos(), spot.worth(), spot.tick(),
                spot.attempts(), spot.deaths() + 1, spot.manifest()));
    }

    private void replaceById(UUID owner, UUID spotId, java.util.function.UnaryOperator<Spot> change) {
        List<Spot> list = spots.get(owner);
        if (list == null) return;
        for (int index = 0; index < list.size(); index++) if (list.get(index).id().equals(spotId)) {
            list.set(index, change.apply(list.get(index)));
            setDirty();
            return;
        }
    }

    private void replace(UUID owner, ResourceKey<Level> dimension, BlockPos pos, java.util.function.UnaryOperator<Spot> change) {
        List<Spot> list = spots.get(owner);
        if (list == null) return;
        for (int index = 0; index < list.size(); index++) if (list.get(index).pos().equals(pos) && list.get(index).dimension().equals(dimension)) {
            list.set(index, change.apply(list.get(index)));
            setDirty();
            return;
        }
    }

    public List<Spot> all(UUID owner) { return List.copyOf(spots.getOrDefault(owner, List.of())); }

    /**
     * The owner's remembered places in the order he would walk them: the dearest first. The number in his spoken line and
     * in the settings list is a place's position here, so the two can never disagree.
     */
    public List<Spot> ranked(UUID owner) {
        List<Spot> ranked = new ArrayList<>(all(owner));
        ranked.sort(Comparator.comparingDouble(Spot::worth).reversed());
        return ranked;
    }

    /** Forgetting a place by its own id: another fall at the same coordinates is another place. */
    public void taken(UUID owner, UUID spotId) {
        List<Spot> list = spots.get(owner);
        if (list == null) return;
        boolean removed = list.removeIf(spot -> spot.id().equals(spotId));
        if (removed) {
            if (list.isEmpty()) spots.remove(owner);
            setDirty();
        }
    }

    /** Forgetting a place by its dimension and position, for a record read before it was charged. */
    public void taken(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        List<Spot> list = spots.get(owner);
        if (list == null) return;
        boolean removed = list.removeIf(spot -> spot.pos().equals(pos) && spot.dimension().equals(dimension));
        if (removed) {
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
                one.putUUID("id", spot.id());
                one.putString("dimension", spot.dimension().location().toString());
                one.putInt("x", spot.pos().getX());
                one.putInt("y", spot.pos().getY());
                one.putInt("z", spot.pos().getZ());
                one.putDouble("worth", spot.worth());
                one.putLong("tick", spot.tick());
                one.putInt("attempts", spot.attempts());
                one.putInt("deaths", spot.deaths());
                ListTag manifest = new ListTag();
                for (Entry stack : spot.manifest()) {
                    CompoundTag saved = new CompoundTag();
                    saved.putUUID("id", stack.id());
                    saved.putDouble("px", stack.pos().x);
                    saved.putDouble("py", stack.pos().y);
                    saved.putDouble("pz", stack.pos().z);
                    saved.putString("item", stack.item());
                    saved.putInt("count", stack.count());
                    manifest.add(saved);
                }
                one.put("manifest", manifest);
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
                        ResourceLocation.parse(one.getString("dimension")));
                List<Entry> manifest = new ArrayList<>();
                // An old save has no manifest: the place is trusted until its stacks are seen (and then left, if none is).
                for (Tag stack : one.getList("manifest", Tag.TAG_COMPOUND)) {
                    CompoundTag saved = (CompoundTag) stack;
                    if (!saved.hasUUID("id")) continue;
                    manifest.add(new Entry(saved.getUUID("id"),
                            new Vec3(saved.getDouble("px"), saved.getDouble("py"), saved.getDouble("pz")),
                            saved.getString("item"), saved.getInt("count")));
                }
                // An old save can lack the place's id: it gets one now, so a walk can charge and forget it by id later.
                UUID spotId = one.hasUUID("id") ? one.getUUID("id") : UUID.randomUUID();
                list.add(new Spot(spotId, dimension, new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")),
                        one.getDouble("worth"), one.getLong("tick"), one.getInt("attempts"), one.getInt("deaths"), manifest));
            }
            data.spots.put(UUID.fromString(key), list);
        }
        return data;
    }
}
