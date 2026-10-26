package dev.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** The last things that happened to the player, newest last. The mod writes the text from facts; the key names the source. */
public final class CompanionHappenings {
    public static final int KEEP = 12;
    public static final int TEXT = 160;
    public static final int KEY = 64;

    public enum Importance { LOW, NORMAL, HIGH }

    public record Happening(String key, String text, long gameTime, Importance importance) { }

    private final List<Happening> events = new ArrayList<>();

    /** Event texts embed names and titles other players control, so they are masked, flattened and cut before they are stored or spoken. */
    public static String clean(String text) {
        String flat = CompanionMemory.mask(text).replaceAll("\\s+", " ").strip();
        return flat.length() <= TEXT ? flat : flat.substring(0, TEXT - 1).strip() + "…";
    }

    /** An event with a key already in the ring replaces the older one: the same thing is not told twice. */
    public void add(String key, String text, long gameTime, Importance importance) {
        String clean = clean(text);
        if (clean.isEmpty()) return;
        String id = key == null ? "" : key;
        if (!id.isEmpty()) events.removeIf(old -> old.key.equals(id));
        events.add(new Happening(id, clean, gameTime, importance == null ? Importance.NORMAL : importance));
        while (events.size() > KEEP) events.removeFirst();
    }

    /** Oldest first. */
    public List<Happening> all() { return List.copyOf(events); }

    /** The last {@code count} events, oldest first. */
    public List<Happening> recent(int count) {
        int from = Math.max(0, events.size() - Math.max(0, count));
        return List.copyOf(events.subList(from, events.size()));
    }

    public boolean has(String key) { return events.stream().anyMatch(event -> event.key.equals(key)); }

    public boolean isEmpty() { return events.isEmpty(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Happening event : events) {
            CompoundTag item = new CompoundTag();
            item.putString("Key", event.key); item.putString("Text", event.text);
            item.putLong("Time", event.gameTime); item.putString("Importance", event.importance.name());
            list.add(item);
        }
        tag.put("Events", list);
        return tag;
    }

    public static CompanionHappenings load(CompoundTag tag) {
        CompanionHappenings happenings = new CompanionHappenings();
        ListTag list = tag.getList("Events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            Importance importance = Importance.NORMAL;
            for (Importance candidate : Importance.values()) if (candidate.name().equals(item.getString("Importance"))) importance = candidate;
            happenings.add(cut(item.getString("Key")), item.getString("Text"), item.getLong("Time"), importance);
        }
        return happenings;
    }

    private static String cut(String key) { return key.length() > KEY ? key.substring(0, KEY) : key; }

    public CompanionHappenings copy() { return load(save()); }
}
