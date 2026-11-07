package dev.aicompanion.client;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Сложи 10 камня в бочку», «забери всё из печи», «положи вещи в сундук»: a store or take order, with the container it names.
 * The block id travels in the step's {@code location}; «сундук» names none (the chests, as before) and keeps its old reading.
 */
final class CompanionContainerCommands {
    private static final int MAX_COUNT = 64;
    private static final String CHEST = "chest";
    private static final String ITEMS = "(?<items>все вещи|вещи|все|ресурсы|дерево|бревна|бревен|камень|камня|булыжник|булыжника|землю|земли)";
    private static final String COUNT = "(?:\\s+(?<count>\\d{1,3}))?";
    private static final Map<String, String> KINDS = kinds();
    private static final String PLACES = "(?<place>" + String.join("|", KINDS.keySet()) + ")";
    private static final Pattern TAKE = Pattern.compile("^(?:забери|возьми|достань)" + COUNT + "\\s+" + ITEMS + "\\s+из\\s+" + PLACES + "$");
    private static final Pattern STORE = Pattern.compile("^(?:сложи|положи|выгрузи|убери)" + COUNT + "\\s+" + ITEMS + "\\s+в\\s+" + PLACES + "$");

    private CompanionContainerCommands() { }

    private static Map<String, String> kinds() {
        Map<String, String> kinds = new HashMap<>();
        for (String word : new String[] {"сундук", "сундука"}) kinds.put(word, CHEST);
        for (String word : new String[] {"бочку", "бочки"}) kinds.put(word, "minecraft:barrel");
        for (String word : new String[] {"печь", "печку", "печи", "печки"}) kinds.put(word, "minecraft:furnace");
        for (String word : new String[] {"плавильную печь", "плавильной печи"}) kinds.put(word, "minecraft:blast_furnace");
        for (String word : new String[] {"коптильню", "коптильни"}) kinds.put(word, "minecraft:smoker");
        for (String word : new String[] {"воронку", "воронки"}) kinds.put(word, "minecraft:hopper");
        for (String word : new String[] {"раздатчик", "раздатчика"}) kinds.put(word, "minecraft:dispenser");
        for (String word : new String[] {"выбрасыватель", "выбрасывателя"}) kinds.put(word, "minecraft:dropper");
        for (String word : new String[] {"шалкер", "шалкера"}) kinds.put(word, "minecraft:shulker_box");
        return Map.copyOf(kinds);
    }

    /** The order, or null when the text is not one (or says a number the work cannot do). */
    static PlannedAction parse(String value) {
        Matcher take = TAKE.matcher(value);
        if (take.matches()) {
            int count = take.group("count") == null ? MAX_COUNT : Integer.parseInt(take.group("count"));
            return count >= 1 && count <= MAX_COUNT ? order("take_items", count, take) : null;
        }
        Matcher store = STORE.matcher(value);
        if (!store.matches()) return null;
        String said = store.group("count");
        // «В сундук» has always meant the whole load of that kind, whatever number is said: a number goes to the model.
        if (CHEST.equals(KINDS.get(store.group("place")))) return said == null ? order("store_items", 1, store) : null;
        int count = said == null ? PlannedAction.ALL_CARRIED : Integer.parseInt(said);
        return said != null && (count < 1 || count > MAX_COUNT) ? null : order("store_items", count, store);
    }

    private static PlannedAction order(String type, int count, Matcher words) {
        String filter = switch (words.group("items")) {
            case "все", "все вещи" -> "all";
            case "вещи" -> "take_items".equals(type) ? "all" : "resources";
            case "дерево", "бревна", "бревен" -> "logs";
            case "камень", "камня", "булыжник", "булыжника" -> "stone";
            case "землю", "земли" -> "minecraft:dirt";
            default -> "resources";
        };
        String kind = KINDS.get(words.group("place"));
        return new PlannedAction(type, 0, count, filter, "auto", CHEST.equals(kind) ? "near_player" : kind);
    }
}
