package dev.aicompanion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The names of the player's map markers: what a name may be, and whether a spoken phrase names a marker.
 * <p>
 * A name is the player's own word («дача», «колодец», «железный рудник», «дом2»), never a coordinate. Recognition is
 * generic: a phrase word is compared with a name word by its stem, so Russian case forms and the fleeting vowel of
 * «колодец/колодцу» meet, without a list of known place words.
 */
public final class CompanionMarkerNames {
    public static final int MAX = 24;
    private static final Pattern COORDINATES = Pattern.compile("-?\\d+\\s*[,;]\\s*-?\\d+.*");
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    /** Longest first: an ending is taken once, and only while the stem stays at least three letters. */
    private static final String[] ENDINGS = {"ыми", "ими", "ого", "его", "ому", "ему", "ами", "ями", "ах", "ях",
            "ов", "ев", "ей", "ой", "ый", "ий", "ом", "ем", "ою", "ею", "а", "я", "о", "е", "у", "ю", "ы", "и", "ь"};
    private static final String VOWELS = "аеёиоуыэюя";

    private CompanionMarkerNames() { }

    /** Trimmed, without wrapping quotes, doubled spaces and trailing punctuation; a name keeps its digits («дом2»). */
    public static String clean(String raw) {
        if (raw == null) return "";
        String value = raw.strip().replaceAll("^[\"'«»„“”]+|[\"'«»„“”]+$", "").replaceAll("\\s+", " ").strip();
        return value.replaceAll("[.,;:!?]+$", "").strip();
    }

    /** 1..24 characters and not a coordinate triple. */
    public static boolean valid(String name) {
        String clean = clean(name);
        return !clean.isEmpty() && clean.length() <= MAX && !COORDINATES.matcher(clean).matches();
    }

    /** True when the spoken phrase (a marker name in any case form) means this marker. */
    public static boolean matches(String markerName, String said) {
        List<String> name = tokens(markerName), words = tokens(said);
        if (name.isEmpty() || words.isEmpty() || words.size() < name.size()) return false;
        for (int from = 0; from + name.size() <= words.size(); from++) {
            boolean same = true;
            for (int at = 0; at < name.size(); at++) if (!sameStem(name.get(at), words.get(from + at))) { same = false; break; }
            if (same) return true;
        }
        return false;
    }

    /** The words of a name or a phrase, lower case, ё as е, punctuation dropped. */
    public static List<String> tokens(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        List<String> words = new ArrayList<>();
        for (String word : SPLIT.split(value)) if (!word.isBlank()) words.add(word);
        return words;
    }

    /** Two words of the same stem: «колодцу» and «колодец» meet through the fleeting vowel, «даче» and «дача» by the ending. */
    private static boolean sameStem(String a, String b) {
        String left = stem(a), right = stem(b);
        if (left.equals(right)) return true;
        if (Math.abs(left.length() - right.length()) != 1) return false;
        String longer = left.length() > right.length() ? left : right;
        String shorter = left.length() > right.length() ? right : left;
        for (int at = 0; at < longer.length(); at++) {
            if (VOWELS.indexOf(longer.charAt(at)) < 0) continue;
            if ((longer.substring(0, at) + longer.substring(at + 1)).equals(shorter)) return true;
        }
        return false;
    }

    private static String stem(String word) {
        for (String ending : ENDINGS) {
            if (word.length() - ending.length() < 3 || !word.endsWith(ending)) continue;
            return word.substring(0, word.length() - ending.length());
        }
        return word;
    }
}
