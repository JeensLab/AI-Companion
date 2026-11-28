package dev.aicompanion.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Places this companion has actually seen, shared across his dimensions but recalled in the current one. */
public final class LandmarkMemory {
    public static final String KEY = "TerrainLandmarks";
    private static final int LIMIT = 256;
    private static final int REFRESH_TICKS = 200;
    private static final Set<String> KINDS = Set.of("cave", "coal", "iron", "copper", "lapis", "gold", "redstone",
            "diamond", "emerald", "quartz", "debris");

    public record Entry(String dimension, String kind, BlockPos pos, BlockPos viewpoint, long lastSeen) {
        public String label() { return labelFor(kind); }

        public String descriptionFrom(BlockPos observer) {
            String where = TerrainReading.where(observer, pos);
            int dy = pos.getY() - observer.getY();
            if (Math.abs(dy) >= 4) where += dy < 0 ? ", ниже по высоте" : ", выше по высоте";
            int distance = (int) Math.round(Math.sqrt(observer.distSqr(pos)));
            return "Я видел " + label() + " " + where + ". До того места примерно " + distance + " бл.";
        }
    }

    static String labelFor(String kind) {
        return switch (kind) {
            case "cave" -> "возможный вход в пещеру";
            case "coal" -> "угольную руду";
            case "iron" -> "железную руду";
            case "copper" -> "медную руду";
            case "lapis" -> "лазуритовую руду";
            case "gold" -> "золотую руду";
            case "redstone" -> "редстоуновую руду";
            case "diamond" -> "алмазную руду";
            case "emerald" -> "изумрудную руду";
            case "quartz" -> "кварцевую руду";
            case "debris" -> "древние обломки";
            default -> throw new IllegalStateException("Unknown landmark kind: " + kind);
        };
    }

    private final List<Entry> entries = new ArrayList<>();
    private Entry discussed;
    private String focusDimension = "", focusKind = "";
    private ResourceLocation summaryDimension;
    private String summary = "";

    /** A compact hint for conversation. Reading it must never select the destination of «покажи». */
    public String witnessedKinds(ResourceLocation dimension) {
        if (!dimension.equals(summaryDimension)) {
            String id = dimension.toString();
            var labels = new TreeSet<String>();
            for (Entry entry : entries) {
                if (entry.dimension.equals(id)) labels.add(entry.label());
            }
            summary = String.join(", ", labels);
            summaryDimension = dimension;
        }
        return summary;
    }

    public static boolean validQuery(String query) {
        return query != null && (query.equals("last") || query.equals("ore") || KINDS.contains(query));
    }

    public static String missingHint(String query) {
        if ("last".equals(query)) return "Пока не вспомню место, куда мог бы тебя отвести.";
        if ("ore".equals(query)) return "В этом измерении я пока не запомнил ни одного места с открытой рудой.";
        return "В этом измерении я пока не запомнил, где видел " + labelFor(query) + ".";
    }

    public Entry observe(String dimension, String kind, BlockPos pos, BlockPos viewpoint, long tick) {
        return observe(dimension, kind, pos, viewpoint, tick, false);
    }

    /** A survey mentioned this specific place out loud: a later «покажи» refers to it. */
    public Entry discuss(String dimension, String kind, BlockPos pos, BlockPos viewpoint, long tick) {
        Entry entry = observe(dimension, kind, pos, viewpoint, tick, true);
        if (entry != null) select(entry);
        return entry;
    }

    /** A vein or mouth has one record. Its representative block and viewpoint always come from the same observation. */
    private Entry observe(String dimension, String kind, BlockPos pos, BlockPos viewpoint, long tick, boolean spoken) {
        if (!KINDS.contains(kind)) return null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry old = entries.get(i);
            if (!old.dimension.equals(dimension) || !old.kind.equals(kind) || Math.abs(old.pos.getY() - pos.getY()) > 1
                    || old.pos.distSqr(pos) > (kind.equals("cave") ? 9 : 3)) continue;
            if (!spoken && tick >= old.lastSeen && tick - old.lastSeen < REFRESH_TICKS
                    && old.viewpoint.distSqr(viewpoint) < 9) return old;
            Entry updated = new Entry(dimension, kind, pos.immutable(), viewpoint.immutable(), tick);
            entries.remove(i);
            entries.add(updated);
            if (old == discussed) discussed = updated;
            return updated;
        }
        if (entries.size() == LIMIT) {
            Entry forgotten = entries.removeFirst();
            if (forgotten == discussed) discussed = null;
        }
        Entry entry = new Entry(dimension, kind, pos.immutable(), viewpoint.immutable(), tick);
        entries.add(entry);
        summaryDimension = null;
        return entry;
    }

    private void select(Entry entry) {
        discussed = entry;
        focusDimension = entry.dimension;
        focusKind = entry.kind;
    }

    /** Pick the latest matching sighting. A subsequent «покажи» keeps that choice even after new observations. */
    public Entry recall(String dimension, String query) {
        if (!validQuery(query)) return null;
        if (query.equals("last") && discussed != null && discussed.dimension.equals(dimension)) return discussed;
        if (query.equals("last") && focusDimension.equals(dimension)) query = focusKind;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.dimension.equals(dimension) && (query.equals("last") || query.equals(entry.kind)
                    || query.equals("ore") && !entry.kind.equals("cave"))) {
                select(entry);
                return entry;
            }
        }
        if (!query.equals("last")) {
            discussed = null;
            focusDimension = dimension;
            focusKind = query;
        }
        return null;
    }

    /** Remove disproved evidence at this block; a separately confirmed neighbour can still be valid. */
    public void forget(Entry disproved) {
        if (entries.removeIf(entry -> entry.pos.equals(disproved.pos) && entry.dimension.equals(disproved.dimension)
                && entry.kind.equals(disproved.kind))) summaryDimension = null;
        if (discussed != null && !entries.contains(discussed)) discussed = null;
    }

    public void save(CompoundTag tag) {
        if (entries.isEmpty() && focusKind.isEmpty()) { tag.remove(KEY); return; }
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag row = new CompoundTag();
            row.putString("Dim", entry.dimension);
            row.putString("Kind", entry.kind);
            row.putLong("Pos", entry.pos.asLong());
            row.putLong("View", entry.viewpoint.asLong());
            row.putLong("Seen", entry.lastSeen);
            list.add(row);
        }
        CompoundTag memory = new CompoundTag();
        memory.put("Places", list);
        if (discussed != null) memory.putInt("Discussed", entries.indexOf(discussed));
        else if (!focusKind.isEmpty()) {
            memory.putString("FocusDim", focusDimension);
            memory.putString("FocusKind", focusKind);
        }
        tag.put(KEY, memory);
    }

    public void load(CompoundTag tag) {
        entries.clear();
        summaryDimension = null;
        discussed = null;
        focusDimension = focusKind = "";
        CompoundTag memory = tag.getCompound(KEY);
        ListTag list = memory.getList("Places", Tag.TAG_COMPOUND);
        int selected = memory.contains("Discussed", Tag.TAG_INT) ? memory.getInt("Discussed") : -1;
        for (int i = Math.max(0, list.size() - LIMIT); i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            String dim = row.getString("Dim"), kind = row.getString("Kind");
            if (dim.isEmpty() || dim.length() > 255 || ResourceLocation.tryParse(dim) == null || !KINDS.contains(kind)
                    || !row.contains("Pos", Tag.TAG_LONG) || !row.contains("View", Tag.TAG_LONG)) continue;
            Entry entry = new Entry(dim, kind, BlockPos.of(row.getLong("Pos")), BlockPos.of(row.getLong("View")), row.getLong("Seen"));
            entries.add(entry);
            if (i == selected) select(entry);
        }
        String dim = memory.getString("FocusDim"), kind = memory.getString("FocusKind");
        if (discussed == null && !dim.isEmpty() && dim.length() <= 255 && ResourceLocation.tryParse(dim) != null
                && validQuery(kind) && !kind.equals("last")) {
            focusDimension = dim;
            focusKind = kind;
        }
    }
}
