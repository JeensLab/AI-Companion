package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionItemLookup;
import dev.aicompanion.entity.CompanionPickUp;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * «Подбери <X>»: take what of X already lies on the ground here — «подбери вон ту курицу плавающую» is that chicken
 * on the water, not a hunt. «Принеси» is another order: there the thing may be far away and is got and handed over.
 *
 * The word is resolved against the game's own registry ({@link CompanionItemLookup}), never against a list here: any
 * item of any mod answers to the name its lang file gives it in the player's language. A word the registry cannot
 * answer — «кура», «тухлое мясо» — is left to the model, which picks among the drops actually lying here: one small
 * request, only in that case ({@link #ask}, {@link #prompt}, {@link #chosen}).
 */
public final class CompanionPickUpCommands {
    private CompanionPickUpCommands() { }

    private static final Pattern VERB = Pattern.compile("(?U)^(?:подбери|подберите|подобрать|подними|поднимите|поднять|подхвати)(?:-?ка)?\\b\\s*(.*)$");
    /** The same verb at the end: «жареная свининка в воде кусок подбери» is the same order as «подбери свининку». */
    private static final Pattern VERB_TAIL = Pattern.compile("(?U)^(.*?)\\s+(?:подбери|подберите|подобрать|подними|поднимите|поднять|подхвати)(?:-?ка)?\\s*$");
    /** «Подбери из сундука камень» is a take order, not this one. */
    private static final Pattern CONTAINER = Pattern.compile("(?U).*\\b(из|в)\\b\\s+(сундук|бочк|печ|шкаф|воронк|раздатчик|контейнер)\\p{L}*.*");
    /** Words around the thing: «вон ту курицу плавающую», «мне», «рядом», «с земли». */
    private static final Pattern FILLER = Pattern.compile("(?U)\\b(вон|ту|тот|та|то|эту|этот|эти|лежащ\\p{L}*|лежат|лежит|лежал|лежали|плавающ\\p{L}*|мёртв\\p{L}*|дохл\\p{L}*"
            + "|мне|нам|себе|пожалуйста|с|со|в|на|земли|земле|полу|пол|вал\\p{L}*|рядом|поблизости|тут|здесь|котор\\p{L}*|что|и|а)\\b");

    /** Words that name everything lying around rather than one thing: «подбери всё», «подбери предметы». */
    private static final Set<String> ALL_WORDS = Set.of("все", "весь", "вся", "вещи", "вещь", "всякое", "предмет", "предметы", "предмета", "предметов");

    /** Words a «ещё» may carry around it: «ещё, пожалуйста», «ещё чуть-чуть», «ещё подбери всё». */
    private static final Pattern MORE_FILLER = Pattern.compile("(?U)\\b(пожалуйста|чуть-чуть|чуть|немного|давай|ну|раз|подбери|подберите|"
            + "все|весь|вся|всё|вещи|вещь|предмет|предметы|предмета|предметов)\\b");

    /** The pickup order for the phrase, or null when the registry cannot tell what it names. */
    public static PlannedAction parse(String text, String name) {
        return parse(text, name, CompanionItemLookup.GAME_NAMES);
    }

    /** The same, by the names the game shows (or a stand-in for the language in a test). */
    public static PlannedAction parse(String text, String name, Function<net.minecraft.world.item.Item, String> names) {
        Said said = said(text, name);
        if (said == null) return null;
        if (said.noun().isEmpty()) return all(said.count());
        String id = CompanionItemLookup.itemFor(said.noun(), names);
        // «Подбери все вещи в воде»: the all-word still names the whole ground when the rest names nothing.
        if (id == null) return said.all() ? all(said.count()) : null;
        return new PlannedAction("pick_up", 0, count(said.count()), id);
    }

    /** What a «подбери» phrase names and how much, or null for a phrase that is no pickup at all. */
    public record Ask(String noun, int count) { }

    /**
     * The pickup phrase whose word the registry cannot name, or null when the phrase is no pickup or its word is
     * already resolved: then the model is asked which of the drops lying here the player meant.
     */
    public static Ask ask(String text, String name) {
        return ask(text, name, CompanionItemLookup.GAME_NAMES);
    }

    public static Ask ask(String text, String name, Function<net.minecraft.world.item.Item, String> names) {
        Said said = said(text, name);
        if (said == null || said.noun().isEmpty() || said.all()) return null;
        return CompanionItemLookup.itemFor(said.noun(), names) == null ? new Ask(said.noun(), count(said.count())) : null;
    }

    /** The one question put to the model: only the drops lying here, and nothing invented outside them. */
    public static String prompt(Ask ask, List<CompanionPickUp.Drop> drops) {
        StringBuilder list = new StringBuilder();
        for (CompanionPickUp.Drop drop : drops) {
            if (list.length() > 0) list.append("; ");
            list.append(drop.id()).append(" — ").append(drop.name()).append(" (").append(drop.count()).append(')');
        }
        return "Игрок сказал: «подбери " + ask.noun() + "». Рядом лежат: " + list + ". "
                + "Что из этого он имел в виду? Ответь только одним id из списка, без пояснений и без другого текста.";
    }

    /** The action for the model's answer: only a drop of the list, or null when it named none of them. */
    public static PlannedAction chosen(Ask ask, List<CompanionPickUp.Drop> drops, String answer) {
        if (answer == null || answer.isBlank()) return null;
        String said = answer.toLowerCase(Locale.ROOT);
        // The answer is the id itself: an exact one wins, so «minecraft:coal_block» is not the coal it begins with.
        for (CompanionPickUp.Drop drop : drops) {
            if (said.trim().equals(drop.id().toLowerCase(Locale.ROOT))) return new PlannedAction("pick_up", 0, ask.count(), drop.id());
        }
        // A sentence around the id: the longest id named wins, so «coal_block» beats the «coal» inside it.
        CompanionPickUp.Drop longest = null;
        for (CompanionPickUp.Drop drop : drops) {
            String id = drop.id().toLowerCase(Locale.ROOT);
            if (said.contains(id) && (longest == null || id.length() > longest.id().length())) longest = drop;
        }
        if (longest != null) return new PlannedAction("pick_up", 0, ask.count(), longest.id());
        // A reply that names the thing instead of its id is still the thing lying on the ground: the longest name wins
        // for the same reason, «угольный блок» over «уголь».
        CompanionPickUp.Drop named = null;
        for (CompanionPickUp.Drop drop : drops) {
            String name = drop.name().toLowerCase(Locale.ROOT);
            if (!name.isBlank() && said.contains(name) && (named == null || name.length() > named.name().length())) named = drop;
        }
        return named == null ? null : new PlannedAction("pick_up", 0, ask.count(), named.id());
    }

    /** The noun and the number of a pickup phrase, before the registry is asked what the noun is. */
    private record Said(String noun, int count, boolean all) { }

    private static Said said(String text, String name) {
        String value = plain(text, name);
        // Several things in one breath («подбери курицу и брёвна») are a plan, and plans are the model's.
        if (value.contains(" и ") || value.contains("потом") || value.contains("затем")) return null;
        var said = CompanionNumbers.parse(value);
        if (said.count() == CompanionNumbers.UNCLEAR) return null;
        String bare = said.rest().replaceAll("\\s+", " ").trim();
        String rest;
        var verb = VERB.matcher(bare);
        if (verb.matches()) rest = verb.group(1).trim();
        else {
            // The verb last is the same order: «жареная свининка в воде кусок подбери».
            var tail = VERB_TAIL.matcher(bare);
            if (!tail.matches()) return null;
            rest = tail.group(1).trim();
        }
        // A bare «подбери» takes whatever lies around; «подбери из сундука камень» is a take order, not this one.
        if (rest.isEmpty()) return new Said("", said.count(), true);
        if (CONTAINER.matcher(rest).matches()) return null;
        String noun = FILLER.matcher(rest).replaceAll(" ").replaceAll("\\s+", " ").trim();
        // «Всё» names the whole ground; a noun after it («все алмазы») still names one thing.
        return new Said(withoutAllWords(noun), said.count(), hasAllWord(noun));
    }

    private static boolean hasAllWord(String noun) {
        for (String word : noun.split(" ")) if (isAll(word)) return true;
        return false;
    }

    private static String withoutAllWords(String noun) {
        StringBuilder named = new StringBuilder();
        for (String word : noun.split(" ")) {
            if (word.isBlank() || isAll(word)) continue;
            if (named.length() > 0) named.append(' ');
            named.append(word);
        }
        return named.toString();
    }

    private static PlannedAction all(int count) {
        return new PlannedAction("pick_up", 0, count(count), CompanionPickUp.ANY_ITEM);
    }

    /** The number the player said, or 64 for «no number»: the route's own «all». */
    private static int count(int said) {
        return said > 0 ? Math.min(64, said) : 64;
    }

    private static boolean isAll(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return ALL_WORDS.contains(w) || w.startsWith("предмет") || w.startsWith("вещ");
    }

    /** The pickup the player last had done: «ещё» right after it takes the rest of the same thing. */
    private static volatile String lastPickUp;

    /** «Ещё», «ещё, пожалуйста», «ещё чуть-чуть», «ещё подбери всё»: the last pickup again, taking the rest. */
    public static PlannedAction more(String text, String name) {
        String item = lastPickUp;
        if (item == null) return null;
        String value = MORE_FILLER.matcher(plain(text, name)).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (!value.matches("^(?:еще|ещё)$")) return null;
        return new PlannedAction("pick_up", 0, 64, item);
    }

    /** Keeps the meaning of a pickup just routed, so «ещё» repeats it rather than guessing another thing. */
    public static void remember(PlannedAction action) {
        if (action != null && action.item() != null && !action.item().isBlank()) lastPickUp = action.item();
    }

    /** A routed phrase that is no pickup ends the «ещё» window: the next «ещё» belongs to the model. */
    public static void forget() {
        lastPickUp = null;
    }

    /**
     * The order to run when the model's plan for a «подбери» phrase was rejected: the pickup the phrase names, or every
     * drop lying around when it names nothing this order knows. Null for a phrase that is no pickup at all.
     */
    public static PlannedAction fallback(String text, String name) {
        PlannedAction parsed = parse(text, name);
        if (parsed != null) return parsed;
        String value = plain(text, name);
        if (!VERB.matcher(value).matches() && !VERB_TAIL.matcher(value).matches()) return null;
        if (CONTAINER.matcher(value).matches()) return null;
        return all(64);
    }

    /** Lower case, ё as е, without his name in front, the commas out and the full stop or «!» at the end. */
    private static String plain(String text, String name) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        if (name != null && !name.isBlank()) value = value.replaceFirst("^" + Pattern.quote(name.toLowerCase(Locale.ROOT).replace('ё', 'е')) + "[,! ]+", "");
        return value.replaceAll("[.!]+$", "").replace(',', ' ').replaceAll("\\s+", " ").trim();
    }
}
