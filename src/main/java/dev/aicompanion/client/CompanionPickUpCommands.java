package dev.aicompanion.client;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * «Подбери <X>»: take what of X already lies on the ground here — «подбери вон ту курицу плавающую» is that chicken
 * on the water, not a hunt. «Принеси» is another order: there the thing may be far away and is got and handed over.
 * Only the things the companion knows by meaning become a local command; anything else is left to the model.
 */
public final class CompanionPickUpCommands {
    private CompanionPickUpCommands() { }

    private static final Pattern VERB = Pattern.compile("(?U)^(?:подбери|подберите|подобрать|подними|поднимите|поднять|подхвати)(?:-?ка)?\\b\\s*(.*)$");
    /** «Подбери из сундука камень» is a take order, not this one. */
    private static final Pattern CONTAINER = Pattern.compile("(?U).*\\b(из|в)\\b\\s+(сундук|бочк|печ|шкаф|воронк|раздатчик|контейнер)\\p{L}*.*");
    /** Words around the thing: «вон ту курицу плавающую», «мне», «рядом», «с земли». */
    private static final Pattern FILLER = Pattern.compile("(?U)\\b(вон|ту|тот|та|то|эту|этот|эти|лежащ\\p{L}*|плавающ\\p{L}*|мёртв\\p{L}*|дохл\\p{L}*"
            + "|мне|нам|себе|пожалуйста|с|со|в|на|земли|земле|рядом|поблизости|тут|здесь|и|а)\\b");

    /** The pick-up order for the phrase, or null for the model. */
    public static PlannedAction parse(String text, String name) {
        String value = plain(text, name);
        // Several things in one breath («подбери курицу и брёвна») are a plan, and plans are the model's.
        if (value.contains(" и ") || value.contains("потом") || value.contains("затем")) return null;
        var said = CompanionNumbers.parse(value);
        if (said.count() == CompanionNumbers.UNCLEAR) return null;
        String bare = said.rest().replaceAll("\\s+", " ").trim();
        var verb = VERB.matcher(bare);
        if (!verb.matches()) return null;
        String rest = verb.group(1).trim();
        if (rest.isEmpty() || CONTAINER.matcher(rest).matches()) return null;
        String noun = FILLER.matcher(rest).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (noun.isEmpty()) return null;
        String item = itemFor(noun.split(" ")[0]);
        if (item == null) return null;
        int count = said.count() > 0 ? Math.min(64, said.count()) : 64;
        return new PlannedAction("pick_up", 0, count, item);
    }

    /** The item a Russian word of the ground names, by its stem; null for a thing this order does not know. */
    private static String itemFor(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.startsWith("куриц") || w.startsWith("кур")) return "minecraft:chicken";
        if (w.startsWith("говяд")) return "minecraft:beef";
        if (w.startsWith("свинин")) return "minecraft:porkchop";
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
