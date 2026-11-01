package dev.aicompanion.client;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * «Веди на северо-восток», «пройди 200 блоков на запад», «веди на юг, пока не найдём деревню» (issue #71): the whole phrase is
 * the order, so a request with more in it («…и добудь дерево») goes to the model.
 */
final class CompanionHeadingCommands {
    private static final String DISTANCE = "(\\d{1,4})\\s*(?:блок\\p{L}*|шаг\\p{L}*|метр\\p{L}*)?";
    private static final Pattern ORDER = Pattern.compile("(?sU)(?:(?:ну|а|давай|пожалуйста)[\\s,]+)*"
            + "(?:веди|поведи|отведи|проведи|иди|пойди|пошли|пойдем|идем|двигай|двигаем|шагай|пройди|пройдем)(?:\\s+(?:нас|меня))?"
            + "(?:\\s+" + DISTANCE + ")?"
            + "\\s+(?:на|в\\s+сторону|к)\\s+((?:северо|юго)[\\s-]?(?:запад|восток)\\p{L}*|(?:север|юг|запад|восток)\\p{L}*)"
            + "(?:\\s+" + DISTANCE + ")?"
            + "(?:[\\s,]+(?:пока|покуда)\\s+не\\s+(?:(?:найдем|найдешь|увидим|увидишь|встретим)\\s+(\\p{L}+)|скажу\\s+стоп))?"
            + "[\\s,.!]*(?:пожалуйста)?[\\s.!]*");
    private static final Map<String, String> TARGETS = Map.ofEntries(Map.entry("деревн", "village"), Map.entry("вод", "water"),
            Map.entry("озер", "water"), Map.entry("рек", "water"), Map.entry("мор", "water"), Map.entry("лес", "forest"),
            Map.entry("деревь", "forest"), Map.entry("животн", "animals"), Map.entry("коров", "cow"), Map.entry("свин", "pig"),
            Map.entry("овц", "sheep"), Map.entry("овец", "sheep"), Map.entry("кур", "chicken"), Map.entry("кролик", "rabbit"));

    private CompanionHeadingCommands() { }

    /** "companion work walk_heading <blocks, 0 until «стоп»> <heading[:target]>", or null. {@code normalized}: lower case, ё as е. */
    static String parse(String normalized, String companionName) {
        String rest = normalized.strip();
        String name = companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').strip();
        if (!name.isEmpty() && rest.startsWith(name)) rest = rest.substring(name.length()).replaceFirst("(?sU)^[\\s\\p{Punct}—]+", "");
        var matcher = ORDER.matcher(rest);
        if (!matcher.matches()) return null;
        String heading = heading(matcher.group(2));
        String distance = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
        String target = matcher.group(4) == null ? "" : target(matcher.group(4));
        if (heading == null || target == null || matcher.group(1) != null && matcher.group(3) != null) return null;
        return "companion work walk_heading " + (distance == null ? 0 : Integer.parseInt(distance)) + " " + heading + (target.isEmpty() ? "" : ":" + target);
    }

    /** «северо-востока», «юго запад», «западу» → northeast, southwest, west. */
    private static String heading(String word) {
        String w = word.replaceAll("[\\s-]", "");
        String northSouth = w.startsWith("северо") ? "north" : w.startsWith("юго") ? "south" : "";
        String rest = northSouth.isEmpty() ? w : w.substring(northSouth.equals("north") ? 6 : 3);
        if (northSouth.isEmpty() && rest.startsWith("север")) return "north";
        if (northSouth.isEmpty() && rest.startsWith("юг")) return "south";
        if (rest.startsWith("запад")) return northSouth + "west";
        if (rest.startsWith("восток")) return northSouth + "east";
        return null;
    }

    private static String target(String word) {
        for (var entry : TARGETS.entrySet()) if (word.startsWith(entry.getKey())) return entry.getValue();
        return null;
    }
}
