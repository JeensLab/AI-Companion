package dev.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.text.Normalizer;
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
    // The first one is the secret keyword followed by anything (a separator, «это», «is» or nothing at all): the value is
    // not known to be one word, so everything up to the end of the clause goes. The second needs a separator, because
    // «ключ»/«key» alone are ordinary words.
    private static final List<Pattern> SENSITIVE = List.of(
            Pattern.compile("(?imu)(?<![\\p{L}\\d])(?:парол\\p{L}{0,3}|токен\\p{L}{0,3}|passwords?|passwd|pwd|tokens?|secrets?|passphrases?|passcodes?|pin|api[ _-]?(?:keys?|ключ)|ключ\\s+api)(?![\\p{L}\\d])"
                    + "[\\s:=–—-]*(?:(?:это|есть|is|was|are)\\s+)?[^\\s.!?;].*?(?=[.!?;]+(?:\\s|$)|$)"),
            Pattern.compile("(?iu)(?<![\\p{L}\\d])(?:ключ\\p{L}*|key)\\s*(?:[:=]|[–—]|-{1,2})\\s*\\S+"),
            Pattern.compile("\\b(?:sk-|ghp_|gho_|ghs_|github_pat_|xox[abp]-|AKIA|ASIA)[A-Za-z0-9_-]{8,}"),
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("[A-Za-z0-9+/_=-]{24,}"),
            Pattern.compile("\\b[0-9a-fA-F]{16,}\\b"),
            Pattern.compile("(?iu)(?<![\\p{L}\\d])[xyzх]\\s*[:=]\\s*-?\\d+"),
            Pattern.compile("(?iu)координат\\p{L}*"),
            Pattern.compile("-?\\d+(?:\\.\\d+)?[\\s,;]+-?\\d+(?:\\.\\d+)?[\\s,;]+-?\\d+(?:\\.\\d+)?"),
            Pattern.compile("(?i)\\b[a-z][a-z0-9_]*:[a-z0-9_][a-z0-9_./-]*"));
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final int MASK_INPUT = TALK_TEXT * 4;
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
        return matchesAny(unobscured(text)) || matchesAny(text) || matchesAny(separated(text));
    }

    /**
     * The text with every sensitive fragment replaced by {@link #MASK}. The folded text is scanned only up to {@link #MASK_INPUT}
     * and is what comes back when it was cut, because the unscanned rest must not survive. When the whole of it was scanned and
     * nothing matched, the original comes back as it was (emoji joiners and direction marks included). Folding can glue a letter
     * onto a keyword or turn a symbol into a digit, so the fold alone misses some matches: when the raw text or the text with
     * hidden and compatibility characters turned into separators has more matches than the fold, the whole line goes.
     */
    public static String mask(String text) {
        if (text == null) return "";
        String folded = unobscured(text);
        boolean whole = folded.length() <= MASK_INPUT;
        String masked = whole ? folded : folded.substring(0, MASK_INPUT);
        String capped = cut(text);
        int foldedHits = countMatches(masked);
        if (Math.max(countMatches(capped), countMatches(cut(separated(capped)))) > foldedHits) return MASK;
        if (foldedHits == 0) return whole ? capped : masked;
        for (Pattern pattern : SENSITIVE) masked = pattern.matcher(masked).replaceAll(MASK);
        return masked;
    }

    private static String cut(String text) {
        return text.length() > MASK_INPUT ? text.substring(0, MASK_INPUT) : text;
    }

    private static int countMatches(String seen) {
        int hits = 0;
        for (Pattern pattern : SENSITIVE) {
            var matcher = pattern.matcher(seen);
            while (matcher.find()) hits++;
        }
        return hits;
    }

    private static boolean matchesAny(String seen) {
        for (Pattern pattern : SENSITIVE) if (pattern.matcher(seen).find()) return true;
        return false;
    }

    /** Compatibility forms (full-width letters) fold to plain ones and invisible format characters go, so neither hides a secret. */
    private static String unobscured(String text) {
        return text == null ? "" : INVISIBLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
    }

    /** Like {@link #unobscured}, but a hidden character or a non-letter that folds into something else becomes a space, so it cannot glue onto a keyword. */
    private static String separated(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            boolean hidden = Character.getType(codePoint) == Character.FORMAT;
            boolean folds = !Character.isLetter(codePoint) && !Normalizer.isNormalized(new String(Character.toChars(codePoint)), Normalizer.Form.NFKC);
            if (hidden || folds) out.append(' '); else out.appendCodePoint(codePoint);
        });
        return Normalizer.normalize(out, Normalizer.Form.NFKC);
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
            if (KINDS.contains(item.getString("Kind")) && isStorable(text, MAX_TEXT)) memory.put(item.getString("Kind"), text, Math.max(1, Math.min(MAX_WEIGHT, item.getInt("Weight"))));
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
