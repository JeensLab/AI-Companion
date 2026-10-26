package dev.aicompanion.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How much the player asked for, said the way people say it: «20», «двадцать», «пять», «пару», «десяток», «стак»,
 * «сколько сможешь». Game log 22.10: «добудь пять руды», «сколько сможешь», «бесконечно» all became a single block.
 */
public final class CompanionNumbers {
    /** «Сколько сможешь», «бесконечно», «много»: as much as one order allows. */
    public static final int AS_MANY = -2;
    /** «Больше», «побольше» without a number. */
    public static final int MORE = -3;
    /** Two different numbers in one request: which one belongs where is left to the model. */
    public static final int UNCLEAR = -1;

    private static final Map<String, Integer> UNITS = Map.ofEntries(
            Map.entry("один", 1), Map.entry("одну", 1), Map.entry("одного", 1), Map.entry("одна", 1), Map.entry("одно", 1),
            Map.entry("два", 2), Map.entry("две", 2), Map.entry("двух", 2), Map.entry("пару", 2), Map.entry("пара", 2), Map.entry("парочку", 2),
            Map.entry("три", 3), Map.entry("трех", 3), Map.entry("четыре", 4), Map.entry("четырех", 4),
            Map.entry("пять", 5), Map.entry("пяток", 5), Map.entry("пяти", 5), Map.entry("шесть", 6), Map.entry("семь", 7),
            Map.entry("восемь", 8), Map.entry("девять", 9));
    private static final Map<String, Integer> WHOLE = Map.ofEntries(
            Map.entry("десять", 10), Map.entry("десяток", 10), Map.entry("одиннадцать", 11), Map.entry("двенадцать", 12),
            Map.entry("дюжину", 12), Map.entry("тринадцать", 13), Map.entry("четырнадцать", 14), Map.entry("пятнадцать", 15),
            Map.entry("шестнадцать", 16), Map.entry("семнадцать", 17), Map.entry("восемнадцать", 18), Map.entry("девятнадцать", 19),
            Map.entry("стак", 64), Map.entry("стопку", 64), Map.entry("стопка", 64), Map.entry("полстака", 32), Map.entry("сотню", 100), Map.entry("сто", 100));
    private static final Map<String, Integer> TENS = Map.of("двадцать", 20, "тридцать", 30, "сорок", 40, "пятьдесят", 50, "шестьдесят", 60);
    private static final Pattern AS_MANY_WORDS = Pattern.compile("(?U)(сколько\\s+(сможешь|получится|найдешь|влезет|унесешь)|бесконечно|без\\s+конца"
            + "|до\\s+упора|максимум|как\\s+можно\\s+больше|\\bмного\\b|\\bвсё\\s+что|\\bвсе\\s+что|\\bвсю\\b|\\bвсе\\b|\\bвсё\\b)");
    private static final Pattern MORE_WORDS = Pattern.compile("(?U)\\b(больше|побольше|еще|ещё)\\b");
    private static final Pattern TOKEN = Pattern.compile("(?U)\\d+|[\\p{L}]+");

    private CompanionNumbers() { }

    /** The request with its quantity taken out, and the quantity: a count, 0 for none, or one of the markers above. */
    public record Said(int count, String rest) { }

    public static Said parse(String text) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        record Token(String word, int start, int end) { }
        List<Token> words = new ArrayList<>();
        for (Matcher matcher = TOKEN.matcher(value); matcher.find(); ) words.add(new Token(matcher.group(), matcher.start(), matcher.end()));
        List<Integer> found = new ArrayList<>();
        StringBuilder rest = new StringBuilder();
        int copied = 0;
        for (int index = 0; index < words.size(); index++) {
            Token token = words.get(index);
            String word = token.word();
            Integer number = word.chars().allMatch(Character::isDigit) ? Integer.valueOf(word.length() > 4 ? 9999 : Integer.parseInt(word))
                    : WHOLE.containsKey(word) ? WHOLE.get(word) : TENS.containsKey(word) ? TENS.get(word) : UNITS.get(word);
            if (number == null) continue;
            int end = token.end();
            // «двадцать пять»: tens followed by a unit are one number.
            if (TENS.containsKey(word) && index + 1 < words.size() && UNITS.containsKey(words.get(index + 1).word())) {
                number += UNITS.get(words.get(++index).word()); end = words.get(index).end();
            }
            found.add(number);
            rest.append(value, copied, token.start()).append(' ');
            copied = end;
        }
        rest.append(value.substring(copied));
        String remaining = rest.toString().replaceAll("\\s+", " ").trim();
        if (found.stream().distinct().count() > 1) return new Said(UNCLEAR, remaining);
        if (!found.isEmpty()) return new Said(found.getFirst(), remaining);
        Matcher many = AS_MANY_WORDS.matcher(remaining);
        if (many.find()) return new Said(AS_MANY, many.replaceAll(" ").replaceAll("\\s+", " ").trim());
        if (MORE_WORDS.matcher(remaining).find()) return new Said(MORE, MORE_WORDS.matcher(remaining).replaceAll(" ").replaceAll("\\s+", " ").trim());
        return new Said(0, remaining);
    }
}
