package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A parked batch on disk. The recipe is not stored, only its item ids: the world's recipes decide again when the batch is read. */
final class CompanionFurnaceBatchNbt {
    static final String KEY = "FurnaceBatches";
    private static final int MAX_COUNT = 64 * 36;

    private CompanionFurnaceBatchNbt() { }

    static CompoundTag save(CompanionFurnaceBatches.Batch batch) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Id", batch.id);
        tag.putString("Dimension", batch.dimension.location().toString());
        tag.putString("Input", id(batch.recipe.input()));
        tag.putString("Output", id(batch.recipe.result().getItem()));
        tag.putInt("RequestedCount", batch.requestedCount);
        tag.putInt("SmeltCount", batch.smeltCount);
        tag.putBoolean("SmeltAll", batch.smeltAll);
        tag.putBoolean("MixedMeat", batch.mixedMeat);
        tag.putBoolean("FurnacesShort", batch.furnacesShort);
        tag.putInt("MixedCooked", batch.mixedCooked);
        tag.putInt("MixedKinds", batch.mixedKinds);
        ListTag mixed = new ListTag();
        for (Item item : batch.mixedInputs) mixed.add(StringTag.valueOf(id(item)));
        tag.put("MixedInputs", mixed);
        tag.putString("Status", batch.status.name());
        tag.putBoolean("Orphan", batch.orphan);
        tag.putInt("Failures", batch.failures);
        ListTag lots = new ListTag();
        for (CompanionWork.Lot lot : batch.lots) lots.add(saveLot(lot));
        tag.put("Lots", lots);
        return tag;
    }

    /** Null when the world no longer smelts this input: such a batch cannot be tended. Whatever is read, the batch comes back an orphan: its queue task is gone. */
    static CompanionFurnaceBatches.Batch load(CompoundTag tag, ServerLevel level) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.contains("Id")) return null;
        CompanionSmelting.Recipe recipe = CompanionSmelting.resolve(level, tag.getString("Input"), List.of());
        if (recipe == null) return null;
        List<CompanionWork.Lot> lots = new ArrayList<>();
        ListTag saved = tag.getList("Lots", Tag.TAG_COMPOUND);
        for (Tag entry : saved.subList(0, Math.min(saved.size(), CompanionFurnaceBatches.MAX_LOTS))) lots.add(loadLot((CompoundTag) entry));
        if (lots.isEmpty()) return null;
        Set<Item> mixed = new HashSet<>();
        for (Tag entry : tag.getList("MixedInputs", Tag.TAG_STRING)) {
            Item item = item(entry.getAsString());
            if (item != null) mixed.add(item);
        }
        CompanionFurnaceBatches.Batch batch = new CompanionFurnaceBatches.Batch(tag.getLong("Id"), ResourceKey.create(Registries.DIMENSION, dimension), recipe,
                count(tag, "RequestedCount"), count(tag, "SmeltCount"), tag.getBoolean("SmeltAll"), tag.getBoolean("MixedMeat"), tag.getBoolean("FurnacesShort"),
                count(tag, "MixedCooked"), count(tag, "MixedKinds"), mixed, lots);
        try { batch.status = CompanionFurnaceBatches.Status.valueOf(tag.getString("Status")); } catch (IllegalArgumentException ignored) { /* WAITING until the first read says otherwise */ }
        batch.orphan = true;
        batch.failures = Math.clamp(tag.getInt("Failures"), 0, 100);
        return batch;
    }

    /** What the player is told about a batch that could not be read back. */
    static String lostNote(CompoundTag tag) {
        Item input = item(tag.getString("Input"));
        String name = input == null ? tag.getString("Input") : CompanionWork.itemName(input);
        List<String> places = new ArrayList<>();
        ListTag saved = tag.getList("Lots", Tag.TAG_COMPOUND);
        for (Tag entry : saved.subList(0, Math.min(saved.size(), CompanionFurnaceBatches.MAX_LOTS))) places.add(BlockPos.of(((CompoundTag) entry).getLong("Pos")).toShortString());
        return "Плавку «" + name + "» не забрать: рецепт или данные партии потеряны."
                + (places.isEmpty() ? "" : " Что осталось, лежит в " + (places.size() == 1 ? "печи" : "печах") + " на " + String.join("; ", places) + ".");
    }

    private static CompoundTag saveLot(CompanionWork.Lot lot) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", lot.pos.asLong());
        tag.putBoolean("Placed", lot.placed);
        tag.putInt("Count", lot.count); tag.putInt("OutputBefore", lot.outputBefore); tag.putInt("Ready", lot.ready); tag.putInt("Taken", lot.taken);
        tag.putInt("RawBack", lot.rawBack); tag.putInt("FuelOurs", lot.fuelOurs); tag.putInt("FuelTheirs", lot.fuelTheirs); tag.putInt("Misses", lot.misses);
        if (lot.fuelPut != null) tag.putString("FuelPut", id(lot.fuelPut));
        tag.putBoolean("Loaded", lot.loaded); tag.putBoolean("Emptied", lot.emptied); tag.putBoolean("GaveUp", lot.gaveUp);
        tag.putBoolean("Stuck", lot.stuck); tag.putBoolean("Stalled", lot.stalled);
        tag.putLong("ProgressTick", lot.progressTick);
        return tag;
    }

    private static CompanionWork.Lot loadLot(CompoundTag tag) {
        CompanionWork.Lot lot = new CompanionWork.Lot(BlockPos.of(tag.getLong("Pos")), tag.getBoolean("Placed"));
        lot.count = count(tag, "Count"); lot.outputBefore = count(tag, "OutputBefore"); lot.ready = count(tag, "Ready"); lot.taken = count(tag, "Taken");
        lot.rawBack = count(tag, "RawBack"); lot.fuelOurs = count(tag, "FuelOurs"); lot.fuelTheirs = count(tag, "FuelTheirs"); lot.misses = count(tag, "Misses");
        lot.fuelPut = tag.contains("FuelPut") ? item(tag.getString("FuelPut")) : null;
        lot.loaded = tag.getBoolean("Loaded"); lot.emptied = tag.getBoolean("Emptied"); lot.gaveUp = tag.getBoolean("GaveUp");
        lot.stuck = tag.getBoolean("Stuck"); lot.stalled = tag.getBoolean("Stalled");
        lot.progressTick = tag.getLong("ProgressTick");
        return lot;
    }

    private static int count(CompoundTag tag, String key) { return Math.clamp(tag.getInt(key), 0, MAX_COUNT); }
    private static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null || !BuiltInRegistries.ITEM.containsKey(key) ? null : BuiltInRegistries.ITEM.get(key);
    }
}
