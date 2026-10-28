package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionAgenda.State;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Long goals of each player, saved with the world rather than with the companion: a goal outlives his death (he comes back
 * with a new UUID), an unloaded chunk and a trip to another dimension. Only the outcome is kept, never a route or a step
 * number: the next step is always chosen again from the facts. Every change calls {@link #setDirty()}.
 */
public final class CompanionAgendaData extends SavedData {
    private static final Factory<CompanionAgendaData> FACTORY = new Factory<>(CompanionAgendaData::new, CompanionAgendaData::load, null);
    static final int SCHEMA = 1, MAX_RESULTS = 24, MAX_OWNERS = 64;

    /** One goal; the fields below {@code results} live only while the server runs. */
    public static final class Goal {
        final UUID id;
        final String playbook;
        final int tier;
        State state = State.ACTIVE;
        String reason = "";
        boolean fallback;
        int deaths;
        UUID companion;
        String announced = "";
        int done;
        long workTicks;
        final Map<String, int[]> counters = new LinkedHashMap<>();
        final List<CompoundTag> results = new ArrayList<>();
        int safeTicks;
        /** Time spent paused by danger, so a fight that never ends stops the goal and asks for help. */
        int pausedTicks;

        Goal(UUID id, String playbook, int tier) { this.id = id; this.playbook = playbook; this.tier = tier; }

        public UUID id() { return id; }
        public String playbook() { return playbook; }
        public int tier() { return tier; }
        public State state() { return state; }
        public String reason() { return reason; }
        public boolean fallback() { return fallback; }
        public int deaths() { return deaths; }
        public int resultCount() { return results.size(); }
        /** {attempts, failures in a row} of the milestone. */
        public int[] counter(String key) { return counters.computeIfAbsent(key, unused -> new int[2]); }
        public CompanionPlaybooks.Playbook book() { return CompanionPlaybooks.of(playbook, tier); }

        void record(String milestone, long workId, long tick, UUID companion) {
            CompoundTag result = new CompoundTag();
            result.putString("Milestone", milestone); result.putLong("WorkId", workId); result.putLong("Tick", tick);
            if (companion != null) result.putUUID("Companion", companion);
            results.add(result);
            while (results.size() > MAX_RESULTS) results.removeFirst();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Schema", SCHEMA); tag.putUUID("Id", id); tag.putString("Playbook", playbook); tag.putInt("Tier", tier);
            tag.putString("State", state.name()); tag.putString("Reason", reason); tag.putBoolean("Fallback", fallback);
            tag.putInt("Deaths", deaths); if (companion != null) tag.putUUID("Companion", companion);
            tag.putString("Announced", announced); tag.putInt("Done", done); tag.putLong("WorkTicks", workTicks);
            ListTag counterList = new ListTag();
            counters.forEach((key, value) -> {
                CompoundTag counter = new CompoundTag();
                counter.putString("Key", key); counter.putInt("Attempts", value[0]); counter.putInt("Failures", value[1]);
                counterList.add(counter);
            });
            tag.put("Counters", counterList);
            ListTag resultList = new ListTag();
            results.forEach(result -> resultList.add(result.copy()));
            tag.put("Results", resultList);
            return tag;
        }

        /** Null for a record this version cannot run safely: an unknown schema, goal or state. */
        static Goal load(CompoundTag tag) {
            if (tag.getInt("Schema") != SCHEMA || !tag.hasUUID("Id") || !tag.contains("State", Tag.TAG_STRING)) return null;
            int tier = tag.getInt("Tier");
            var book = CompanionPlaybooks.of(tag.getString("Playbook"), tier);
            if (book == null || book.tier() != tier) return null;
            Goal goal = new Goal(tag.getUUID("Id"), book.id(), tier);
            try { goal.state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException unknown) { return null; }
            // The request that interrupted it and the fight that paused it ended with the world; the facts decide from here.
            if (goal.state == State.INTERRUPTED || goal.state == State.PAUSED_DANGER) goal.state = State.ACTIVE;
            goal.reason = tag.getString("Reason"); goal.fallback = tag.getBoolean("Fallback");
            goal.deaths = Math.max(0, Math.min(CompanionAgenda.MAX_DEATHS + 1, tag.getInt("Deaths")));
            goal.companion = tag.hasUUID("Companion") ? tag.getUUID("Companion") : null;
            goal.announced = tag.getString("Announced"); goal.done = Math.max(0, tag.getInt("Done"));
            goal.workTicks = Math.max(0, tag.getLong("WorkTicks"));
            ListTag counterList = tag.getList("Counters", Tag.TAG_COMPOUND);
            for (int i = 0; i < counterList.size(); i++) {
                CompoundTag counter = counterList.getCompound(i);
                if (book.milestone(counter.getString("Key")) == null) continue;
                goal.counters.put(counter.getString("Key"), new int[]{Math.max(0, Math.min(CompanionAgenda.MAX_ATTEMPTS, counter.getInt("Attempts"))),
                        Math.max(0, Math.min(CompanionAgenda.MAX_FAILURES, counter.getInt("Failures")))});
            }
            ListTag resultList = tag.getList("Results", Tag.TAG_COMPOUND);
            for (int i = Math.max(0, resultList.size() - MAX_RESULTS); i < resultList.size(); i++) goal.results.add(resultList.getCompound(i).copy());
            return goal;
        }
    }

    private final Map<UUID, Goal> active = new LinkedHashMap<>();
    private final Map<UUID, Goal> deferred = new LinkedHashMap<>();

    public static CompanionAgendaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aicompanion_agenda");
    }

    public Goal active(UUID owner) { return owner == null ? null : active.get(owner); }
    public Goal deferred(UUID owner) { return owner == null ? null : deferred.get(owner); }
    Set<UUID> owners() { return Set.copyOf(active.keySet()); }

    void setActive(UUID owner, Goal goal) {
        if (goal == null) active.remove(owner); else active.put(owner, goal);
        trim(active);
        setDirty();
    }

    void setDeferred(UUID owner, Goal goal) {
        if (goal == null) deferred.remove(owner); else deferred.put(owner, goal);
        trim(deferred);
        setDirty();
    }

    /** A damaged or hand-edited file must not grow without bound: the oldest owners go first. */
    private static void trim(Map<UUID, Goal> goals) {
        while (goals.size() > MAX_OWNERS) goals.remove(goals.keySet().iterator().next());
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Schema", SCHEMA);
        tag.put("Active", saveAll(active));
        tag.put("Deferred", saveAll(deferred));
        return tag;
    }

    private static ListTag saveAll(Map<UUID, Goal> goals) {
        ListTag list = new ListTag();
        goals.forEach((owner, goal) -> {
            CompoundTag entry = goal.save();
            entry.putUUID("Owner", owner);
            list.add(entry);
        });
        return list;
    }

    public static CompanionAgendaData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionAgendaData data = new CompanionAgendaData();
        if (tag.getInt("Schema") != SCHEMA) return data;
        loadAll(tag.getList("Active", Tag.TAG_COMPOUND), data.active);
        loadAll(tag.getList("Deferred", Tag.TAG_COMPOUND), data.deferred);
        return data;
    }

    private static void loadAll(ListTag list, Map<UUID, Goal> into) {
        for (int i = Math.max(0, list.size() - MAX_OWNERS); i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Goal goal = entry.hasUUID("Owner") ? Goal.load(entry) : null;
            if (goal == null) { AICompanionMod.LOGGER.warn("Skipped a long goal this version cannot read safely."); continue; }
            into.put(entry.getUUID("Owner"), goal);
        }
    }
}
