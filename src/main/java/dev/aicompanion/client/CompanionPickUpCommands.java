package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionPickUp;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * «Подбери <X>»: take what of X already lies on the ground here — «подбери вон ту курицу плавающую» is that chicken
 * on the water, not a hunt. «Принеси» is another order: there the thing may be far away and is got and handed over.
 * Only the things the companion knows by meaning become a local command; anything else is left to the model.
 */
public final class CompanionPickUpCommands {
    private CompanionPickUpCommands() { }

    private static final Pattern VERB = Pattern.compile("(?U)^(?:подбери|подберите|подобрать|подними|поднимите|поднять|подхвати)(?:-?ка)?\\b\\s*(.*)$");
    /** The same verb at the end: «жареная свининка в воде кусок подбери» is the same order as «подбери свининку». */
    private static final Pattern VERB_TAIL = Pattern.compile("(?U)^(.*?)\\s+(?:подбери|подберите|подобрать|подними|поднимите|поднять|подхвати)(?:-?ка)?\\s*$");
    /** «Подбери из сундука камень» is a take order, not this one. */
    private static final Pattern CONTAINER = Pattern.compile("(?U).*\\b(из|в)\\b\\s+(сундук|бочк|печ|шкаф|воронк|раздатчик|контейнер)\\p{L}*.*");
    /** Words around the thing: «вон ту курицу плавающую», «мне», «рядом», «с земли». */
    private static final Pattern FILLER = Pattern.compile("(?U)\\b(вон|ту|тот|та|то|эту|этот|эти|лежащ\\p{L}*|плавающ\\p{L}*|мёртв\\p{L}*|дохл\\p{L}*"
            + "|мне|нам|себе|пожалуйста|с|со|в|на|земли|земле|рядом|поблизости|тут|здесь|и|а)\\b");

    /** Words that name everything lying around rather than one thing: «подбери всё», «подбери вещи». */
    private static final Set<String> ALL_WORDS = Set.of("все", "весь", "вся", "вещи", "вещь", "всякое");

    /** The pick-up order for the phrase, or null for the model. */
    public static PlannedAction parse(String text, String name) {
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
        if (rest.isEmpty()) return all(said.count());
        if (CONTAINER.matcher(rest).matches()) return null;
        String noun = FILLER.matcher(rest).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (noun.isEmpty()) return all(said.count());
        // The thing may come after a word about it («жареная свининка», «гнилая плоть», «в воде кусок»): the first word
        // that names something counts, and an adjective («жареную», «алмазную») only when no noun does. «Всё» names the
        // whole ground, so a noun after it («все алмазы») still wins.
        String item = null;
        for (String word : noun.split(" ")) {
            String found = itemFor(word);
            if (found == null) continue;
            if (isAll(word)) { if (item == null) item = found; continue; }
            if (adjective(word)) { if (item == null) item = found; continue; }
            item = found;
            break;
        }
        if (item == null) return null;
        return new PlannedAction("pick_up", 0, count(said.count()), item);
    }

    private static PlannedAction all(int count) {
        return new PlannedAction("pick_up", 0, count(count), CompanionPickUp.ANY_ITEM);
    }

    /** The number the player said, or 64 for «no number»: the route's own «all». */
    private static int count(int said) {
        return said > 0 ? Math.min(64, said) : 64;
    }

    private static boolean isAll(String word) {
        return ALL_WORDS.contains(word.toLowerCase(Locale.ROOT));
    }

    /** A word that describes the thing rather than naming it: «жареную», «гнилую», «алмазную», «вон ту». */
    private static boolean adjective(String word) {
        for (String ending : new String[]{"ая", "яя", "ое", "ее", "ый", "ий", "ой", "ую", "юю", "ые", "ие", "ых", "их", "ого", "его", "ому", "ему", "ым", "им"})
            if (word.endsWith(ending)) return true;
        return false;
    }

    /** The item a Russian word of the ground names, by its stem; null for a thing this order does not know. */
    private static String itemFor(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (isAll(w)) return CompanionPickUp.ANY_ITEM;
        if (w.startsWith("куриц") || w.startsWith("кур")) return "minecraft:chicken";
        if (w.startsWith("говяд")) return "minecraft:beef";
        if (w.startsWith("свинин")) return "minecraft:porkchop";
        if (w.startsWith("гнил") || w.startsWith("плот")) return "minecraft:rotten_flesh";
        if (w.startsWith("ведр")) return "minecraft:bucket";
        if (w.startsWith("чернил")) return "minecraft:ink_sac";
        if (w.startsWith("баранин")) return "minecraft:mutton";
        if (w.startsWith("крол")) return "minecraft:rabbit";
        if (w.startsWith("мяс")) return "minecraft:meat";
        if (w.startsWith("алмаз")) return "minecraft:diamond";
        if (w.startsWith("изумруд")) return "minecraft:emerald";
        if (w.startsWith("угл")) return "minecraft:coal";
        if (w.startsWith("желез")) return "minecraft:iron_ingot";
        if (w.startsWith("мед")) return "minecraft:copper_ingot";
        if (w.startsWith("золот")) return "minecraft:gold_ingot";
        if (w.startsWith("лазурит")) return "minecraft:lapis_lazuli";
        if (w.startsWith("редстоун")) return "minecraft:redstone";
        if (w.startsWith("бревн") || w.startsWith("бревен") || w.startsWith("брус") || w.startsWith("дерев")) return "minecraft:oak_log";
        if (w.startsWith("доск") || w.startsWith("дощ")) return "minecraft:oak_planks";
        if (w.startsWith("камен") || w.startsWith("камн") || w.startsWith("булыж")) return "minecraft:cobblestone";
        if (w.startsWith("песк")) return "minecraft:sand";
        if (w.startsWith("грави")) return "minecraft:gravel";
        if (w.startsWith("крем")) return "minecraft:flint";
        if (w.startsWith("земл")) return "minecraft:dirt";
        if (w.startsWith("яйц")) return "minecraft:egg";
        if (w.startsWith("пер")) return "minecraft:feather";
        if (w.startsWith("кож")) return "minecraft:leather";
        if (w.startsWith("шерст")) return "minecraft:white_wool";
        if (w.startsWith("палк") || w.startsWith("палок")) return "minecraft:stick";
        if (w.startsWith("семен") || w.startsWith("семеч")) return "minecraft:wheat_seeds";
        if (w.startsWith("пшен")) return "minecraft:wheat";
        if (w.startsWith("морков")) return "minecraft:carrot";
        if (w.startsWith("картош") || w.startsWith("картофел")) return "minecraft:potato";
        if (w.startsWith("свекл")) return "minecraft:beetroot";
        return null;
    }

    /** Lower case, ё as е, without his name in front and the full stop or «!» at the end. */
    private static String plain(String text, String name) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        if (name != null && !name.isBlank()) value = value.replaceFirst("^" + Pattern.quote(name.toLowerCase(Locale.ROOT).replace('ё', 'е')) + "[,! ]+", "");
        return value.replaceAll("[.!]+$", "").trim();
    }
}
