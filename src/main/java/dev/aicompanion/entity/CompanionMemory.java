package dev.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the companion remembers about one player: a few weighted memories, how to address the player, likes and dislikes,
 * and the last lines of the talk. Everything that reaches this class passes the server-side secret filter, so a key,
 * a coordinate or a registry id never lands in the saved world.
 */
public final class CompanionMemory {
    public static final int MAX_MEMORIES = 40;
    public static final int MAX_TEXT = 160;
    public static final int TALK_KEEP = 6;
    public static final int TALK_TEXT = 180;
    public static final int MAX_PREFERENCES = 6;
    public static final int PREFERENCE_TEXT = 40;
    public static final int CALL_ME_TEXT = 24;
    public static final int MAX_WEIGHT = 10;
    public static final String MASK = "[скрыто]";
    public static final String PLAYER_FACT = "player_fact";
    public static final String PROMISE = "promise";
    public static final String SHARED_MOMENT = "shared_moment";
    public static final String JOKE = "joke";
    public static final Set<String> KINDS = Set.of(PLAYER_FACT, PROMISE, SHARED_MOMENT, JOKE);

    public enum Outcome { ADDED, REINFORCED, REJECTED }

    public record Entry(String kind, String text, int weight) { }

    public record Talk(boolean fromPlayer, String text) { }

    // Each pattern is one family of things that must not be stored; order matters for masking (password words first).
    private static final List<Pattern> SENSITIVE = List.of(
            Pattern.compile("(?iu)(?<![\\p{L}\\d])(?:парол\\p{L}*|токен\\p{L}*|ключ\\p{L}*|password|passwd|pwd|token|secret|api[ _-]?key|key)\\s*(?:[:=]|—|-{1,2})\\s*\\S+"),
            Pattern.compile("\\b(?:sk-|ghp_|gho_|xox[abp]-|AKIA)[A-Za-z0-9_-]{8,}"),
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("[A-Za-z0-9+/_=-]{24,}"),
            Pattern.compile("\\b[0-9a-fA-F]{16,}\\b"),
            Pattern.compile("(?iu)(?<![\\p{L}\\d])[xyzх]\\s*[:=]\\s*-?\\d+"),
            Pattern.compile("(?iu)координат\\p{L}*"),
            Pattern.compile("-?\\d+(?:\\.\\d+)?[\\s,;]+-?\\d+(?:\\.\\d+)?[\\s,;]+-?\\d+(?:\\.\\d+)?"),
            Pattern.compile("(?i)\\b[a-z][a-z0-9_]*:[a-z0-9_][a-z0-9_./-]*"));
    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final int STEM = 5;

    private final List<Entry> memories = new ArrayList<>();
    private final List<Talk> talk = new ArrayList<>();
    private final List<String> likes = new ArrayList<>();
    private final List<String> dislikes = new ArrayList<>();
    private String callMe = "";

    /** True when the text carries something that must not be remembered or stored. */
    public static boolean isSensitive(String text) {
        if (text == null) return false;
        for (Pattern pattern : SENSITIVE) if (pattern.matcher(text).find()) return true;
        return false;
    }

    /** The text with every sensitive fragment replaced by {@link #MASK}. */
    public static String mask(String text) {
        String masked = text == null ? "" : text;
        for (Pattern pattern : SENSITIVE) masked = pattern.matcher(masked).replaceAll(MASK);
        return masked;
    }

    /** Rejects (never trims) a memory that is too long, of an unknown kind, or sensitive; a duplicate raises the weight. */
    public Outcome remember(String kind, String text) {
        String clean = flatten(text);
        if (!KINDS.contains(kind) || !isStorable(clean, MAX_TEXT)) return Outcome.REJECTED;
        return put(kind, clean, 1);
    }

    private Outcome put(String kind, String clean, int weight) {
        String key = normalize(clean);
        for (int i = 0; i < memories.size(); i++) {
            Entry old = memories.get(i);
            if (old.kind.equals(kind) && normalize(old.text).equals(key)) {
                memories.set(i, new Entry(kind, old.text, Math.min(MAX_WEIGHT, old.weight + weight)));
                return Outcome.REINFORCED;
            }
        }
        if (memories.size() >= MAX_MEMORIES) memories.remove(victim());
        memories.add(new Entry(kind, clean, Math.max(1, Math.min(MAX_WEIGHT, weight))));
        return Outcome.ADDED;
    }

    /** The lightest, then the oldest non-promise; a promise only goes when nothing else is left. */
    private int victim() {
        int best = -1;
        for (int pass = 0; pass < 2 && best < 0; pass++) {
            for (int i = 0; i < memories.size(); i++) {
                Entry entry = memories.get(i);
                boolean candidate = pass == 1 || !PROMISE.equals(entry.kind);
                if (candidate && (best < 0 || entry.weight < memories.get(best).weight)) best = i;
            }
        }
        return best;
    }

    /** Keeps the last lines of the talk; a secret is replaced by {@link #MASK} before the line is stored. */
    public void recordTalk(boolean fromPlayer, String text) {
        String clean = talkLine(text);
        if (clean.isEmpty()) return;
        talk.add(new Talk(fromPlayer, clean));
        while (talk.size() > TALK_KEEP) talk.removeFirst();
    }

    /** A talk line the way it is stored: masked, flattened and cut to {@link #TALK_TEXT}. */
    public static String talkLine(String text) {
        String clean = flatten(mask(flatten(text)));
        if (clean.length() > TALK_TEXT) clean = clean.substring(0, TALK_TEXT - 1).strip() + "…";
        return clean;
    }

    public boolean setCallMe(String name) {
        String clean = flatten(name);
        if (!isStorable(clean, CALL_ME_TEXT)) return false;
        callMe = clean;
        return true;
    }

    public boolean addLike(String thing) { return addPreference(likes, dislikes, thing); }

    public boolean addDislike(String thing) { return addPreference(dislikes, likes, thing); }

    private static boolean addPreference(List<String> into, List<String> other, String thing) {
        String clean = flatten(thing);
        if (!isStorable(clean, PREFERENCE_TEXT)) return false;
        String key = normalize(clean);
        into.removeIf(old -> normalize(old).equals(key));
        other.removeIf(old -> normalize(old).equals(key));
        into.add(clean);
        while (into.size() > MAX_PREFERENCES) into.removeFirst();
        return true;
    }

    /** Memories that share words with the query come first; the rest are ranked by weight, promises ahead, newest ahead. */
    public List<Entry> relevant(String query, int limit) { return rank(memories, query, limit); }

    public static List<Entry> rank(List<Entry> entries, String query, int limit) {
        Set<String> wanted = stems(query);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) order.add(i);
        order.sort(Comparator.<Integer>comparingInt(i -> score(entries.get(i), i, wanted)).reversed());
        List<Entry> chosen = new ArrayList<>();
        for (int i : order) {
            if (chosen.size() >= Math.max(0, limit)) break;
            chosen.add(entries.get(i));
        }
        return List.copyOf(chosen);
    }

    private static int score(Entry entry, int position, Set<String> wanted) {
        Set<String> own = stems(entry.text);
        own.retainAll(wanted);
        return own.size() * 1000 + entry.weight * 10 + (PROMISE.equals(entry.kind) ? 5 : 0) + position / 8;
    }

    private static Set<String> stems(String text) {
        Set<String> stems = new HashSet<>();
        var matcher = WORD.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            stems.add(word.length() > STEM ? word.substring(0, STEM) : word);
        }
        return stems;
    }

    public List<Entry> entries() { return List.copyOf(memories); }

    public List<Talk> recentTalk() { return List.copyOf(talk); }

    public String callMe() { return callMe; }

    public List<String> likes() { return List.copyOf(likes); }

    public List<String> dislikes() { return List.copyOf(dislikes); }

    public boolean isEmpty() { return memories.isEmpty() && talk.isEmpty() && likes.isEmpty() && dislikes.isEmpty() && callMe.isEmpty(); }

    public CompanionMemory copy() { return load(save()); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry entry : memories) {
            CompoundTag item = new CompoundTag();
            item.putString("Kind", entry.kind); item.putString("Text", entry.text); item.putInt("Weight", entry.weight);
            list.add(item);
        }
        tag.put("Memories", list);
        ListTag lines = new ListTag();
        for (Talk line : talk) {
            CompoundTag item = new CompoundTag();
            item.putBoolean("Player", line.fromPlayer); item.putString("Text", line.text);
            lines.add(item);
        }
        tag.put("Talk", lines);
        tag.putString("CallMe", callMe);
        tag.put("Likes", strings(likes));
        tag.put("Dislikes", strings(dislikes));
        return tag;
    }

    /** Loading re-applies every filter: a hand-edited or damaged save must not smuggle in what live input could not. */
    public static CompanionMemory load(CompoundTag tag) {
        CompanionMemory memory = new CompanionMemory();
        ListTag list = tag.getList("Memories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            String text = flatten(item.getString("Text"));
            if (KINDS.contains(item.getString("Kind")) && isStorable(text, MAX_TEXT)) memory.put(item.getString("Kind"), text, item.getInt("Weight"));
        }
        ListTag lines = tag.getList("Talk", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) memory.recordTalk(lines.getCompound(i).getBoolean("Player"), lines.getCompound(i).getString("Text"));
        memory.setCallMe(tag.getString("CallMe"));
        for (Tag like : tag.getList("Likes", Tag.TAG_STRING)) memory.addLike(like.getAsString());
        for (Tag dislike : tag.getList("Dislikes", Tag.TAG_STRING)) memory.addDislike(dislike.getAsString());
        return memory;
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) list.add(StringTag.valueOf(value));
        return list;
    }

    private static boolean isStorable(String clean, int limit) {
        return !clean.isEmpty() && clean.length() <= limit && !isSensitive(clean);
    }

    private static String flatten(String text) { return text == null ? "" : text.replaceAll("\\s+", " ").strip(); }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
