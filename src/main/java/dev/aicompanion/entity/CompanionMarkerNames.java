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
    /** One word of a phrase, with the span it takes there: the same words {@link #SPLIT} cuts. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
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
        return refusal(name).isEmpty();
    }

    /**
     * The honest line for a name that is not a marker name, or "" when it is one: empty, longer than {@link #MAX} or a
     * coordinate triple. One source for speech, the store, the codec and the screen, so a name one of them accepts is a
     * name all of them accept.
     */
    public static String refusal(String name) {
        String clean = clean(name);
        if (clean.isEmpty()) return "Название метки не может быть пустым.";
        if (clean.length() > MAX)
            return "Название метки — до " + MAX + " символов: «" + clean + "» длиннее на " + (clean.length() - MAX) + ".";
        if (COORDINATES.matcher(clean).matches()) return "«" + clean + "» — это координаты, а не название.";
        return "";
    }

    /** True when the spoken phrase (a marker name in any case form) means this marker. */
    public static boolean matches(String markerName, String said) {
        return window(tokens(markerName), tokens(said)) >= 0;
    }

    /** «дом» and «домик» in any case form («дому», «домику»): the home he knows, which needs no marker of its own. */
    public static boolean isHome(String said) {
        for (String word : tokens(said)) {
            if (matches("дом", word) || matches("домик", word)) return true;
        }
        return false;
    }

    /**
     * The words of the phrase the name takes («дача» in «иди к даче, потом домой»), as the index of its first and last
     * word, or null when it fits none of them: the same window {@link #matches} finds.
     */
    public static int[] span(String markerName, String said) {
        List<String> name = tokens(markerName);
        int from = window(name, tokens(said));
        return from < 0 ? null : new int[]{from, from + name.size() - 1};
    }

    /**
     * The spoken phrase without the words the name matched: «даче, потом домой» without «дача» is «потом домой»,
     * and a phrase that is only the name leaves nothing. The words are dropped by their own span, so the punctuation
     * and the words around them stay as the player said them.
     */
    public static String without(String markerName, String said) {
        if (said == null) return "";
        List<String> name = tokens(markerName);
        List<String> words = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        var word = WORD.matcher(said);
        while (word.find()) {
            words.add(word.group().toLowerCase(Locale.ROOT).replace('ё', 'е'));
            spans.add(new int[]{word.start(), word.end()});
        }
        int from = window(name, words);
        if (from < 0) return said;
        return (said.substring(0, spans.get(from)[0]) + " " + said.substring(spans.get(from + name.size() - 1)[1])).strip();
    }

    /** The token index where the name starts in the phrase, or -1: the one comparison {@link #matches} makes. */
    private static int window(List<String> name, List<String> words) {
        if (name.isEmpty() || words.isEmpty() || words.size() < name.size()) return -1;
        for (int from = 0; from + name.size() <= words.size(); from++) {
            boolean same = true;
            for (int at = 0; at < name.size(); at++) if (!sameStem(name.get(at), words.get(from + at))) { same = false; break; }
            if (same) return from;
        }
        return -1;
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
