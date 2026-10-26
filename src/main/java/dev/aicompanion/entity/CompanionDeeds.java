package dev.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal of finished deeds with their real outcome. It survives leaving the world, so Oleg answers «what did you do»
 * and «why did it not work» from facts instead of inventing reasons (idea from the neighbour mod review, section 5.4).
 */
final class CompanionDeeds {
    static final int KEEP = 24;
    static final int SHOWN = 8;
    static final int TEXT = 170;
    private record Deed(long tick, String text, boolean ok) { }
    private final List<Deed> deeds = new ArrayList<>();

    /** Adds a finished deed; the same text again within half a minute only refreshes its time. */
    void add(long tick, String message, boolean ok) {
        String text = clean(message);
        if (text.isEmpty()) return;
        if (!deeds.isEmpty()) {
            Deed last = deeds.getLast();
            if (last.text.equals(text) && last.ok == ok && tick - last.tick < 600) { deeds.set(deeds.size() - 1, new Deed(tick, text, ok)); return; }
        }
        deeds.add(new Deed(tick, text, ok));
        while (deeds.size() > KEEP) deeds.removeFirst();
    }

    /** The bag is listed elsewhere and goes stale; the deed keeps what was done and why. */
    static String clean(String message) {
        String text = message == null ? "" : message.replaceAll("(?s)\\s*В сумке:.*?(\\.(?=\\s|$)|$)", "").strip();
        if (text.length() > TEXT) text = text.substring(0, TEXT - 1).strip() + "…";
        return text;
    }

    /** Oldest first, with time in words: the model reads it, the player hears it. */
    String summary(long now) {
        if (deeds.isEmpty()) return "пока нет";
        List<String> lines = new ArrayList<>();
        for (Deed deed : deeds.subList(Math.max(0, deeds.size() - SHOWN), deeds.size()))
            lines.add(ago(now - deed.tick) + ": " + (deed.ok ? "сделано — " : "не вышло — ") + deed.text);
        return String.join("; ", lines);
    }

    static String ago(long ticks) {
        long minutes = Math.max(0, ticks) / (20 * 60);
        if (minutes < 1) return "только что";
        if (minutes < 60) return minutes + " мин назад";
        long hours = minutes / 60;
        return hours < 48 ? hours + " ч назад" : hours / 24 + " дн назад";
    }

    int size() { return deeds.size(); }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Deed deed : deeds) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Tick", deed.tick); entry.putString("Text", deed.text); entry.putBoolean("Ok", deed.ok);
            list.add(entry);
        }
        tag.put("Deeds", list);
    }

    /** A world saved before the journal existed simply has none yet. */
    void load(CompoundTag tag) {
        deeds.clear();
        ListTag list = tag.getList("Deeds", 10);
        for (int i = Math.max(0, list.size() - KEEP); i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String text = clean(entry.getString("Text"));
            if (!text.isEmpty()) deeds.add(new Deed(entry.getLong("Tick"), text, entry.getBoolean("Ok")));
        }
    }
}
