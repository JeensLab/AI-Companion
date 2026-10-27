package dev.aicompanion.client;

import java.util.List;
import java.util.regex.Pattern;

/** Player-facing language. Raw plans and exact inventory results remain engine data. */
public final class CompanionDialogue {
    private CompanionDialogue() { }

    public static String cleanReply(String text) {
        return names(text.replaceAll("(?s)\\[(?:Запланировано|Фактический отчёт|Что вышло на деле|Контекст мира).*", "")
                .replaceAll("(?s)PlannedAction\\[.*", "")
                .replaceAll("(?s)```.*?```", "")
                .replaceAll("\\{[^{}]*\"(?:action|steps)\"[^{}]*}", "")).replaceAll("[ \\t]+", " ").trim();
    }

    private static final Pattern ID = Pattern.compile("\\b(?:minecraft|aicompanion):([a-z0-9_/.]+)");

    /** «minecraft:crafting_table» becomes «Верстак»: a friend says the name, not the ID (game log 22.10). */
    static String names(String text) {
        var matcher = ID.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = itemName(matcher.group());
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(name.equals(matcher.group()) ? matcher.group(1).replace('_', ' ') : name));
        }
        return matcher.appendTail(result).toString();
    }

    /**
     * What Oleg says aloud after work: the gist in a sentence or two. Lists of places, the bag, the recipe chain and coordinates
     * stay in the journal (game log 22.10: 465 characters of «minecraft:furnace на 21, 70, -129 … (проверено)» read aloud for a minute).
     */
    public static String speakable(String text) {
        if (text == null) return "";
        String result = text.split("Известные места:|В сумке:|У меня в сумке:|Цепочка:|Координаты:|Материалы сохранены")[0];
        // «(например, 295, 75, 184)» goes as a whole: only the numbers used to go, and «(например,» was read aloud (review of PR #6).
        result = names(cleanReply(result)).replaceAll("\\s*\\((?:например,?\\s*)?(?:на\\s+)?-?\\d+, -?\\d+, -?\\d+\\)", "")
                .replaceAll("(?:\\s(?:на|в))?\\s*\\(?-?\\d+, -?\\d+, -?\\d+\\)?", "")
                .replaceAll("\\s+([.,;:!?])", "$1").replaceAll("\\s+", " ").trim();
        if (result.length() <= 200) return result;
        int end = Math.max(result.lastIndexOf(". ", 200), Math.max(result.lastIndexOf("! ", 200), result.lastIndexOf("? ", 200)));
        return end > 40 ? result.substring(0, end + 1) : result.substring(0, 197).trim() + "…";
    }

    public static String acknowledge(List<PlannedAction> actions) {
        boolean wood = actions.stream().anyMatch(action -> "gather_wood".equals(action.type()));
        boolean crafting = actions.stream().anyMatch(action -> "craft_item".equals(action.type()));
        if (wood && crafting) return "Окей, схожу за деревом и сделаю инструменты.";
        if (wood) return "Ага, я за деревом.";
        if (actions.isEmpty()) return "";
        return switch (actions.getFirst().type()) {
            case "gather_dirt" -> "Окей, накопаю земли.";
            case "gather_stone" -> "Ага, возьму кирку и добуду камня.";
            case "store_items" -> "Сейчас сложу в сундук.";
            case "take_items" -> "Сейчас заберу из сундука.";
            case "build_house" -> "resume".equals(actions.getFirst().item()) ? "Окей, дострою дом." : "Окей, выбери, где поставить дом по схеме.";
            case "craft_item" -> "Сейчас сделаю.";
            case "equip_item" -> "Сейчас возьму.";
            case "unequip_item" -> "Ага, уберу.";
            case "place_block" -> "Поставлю вот тут.";
            case "wander" -> "Ладно, осмотрюсь. Я недалеко.";
            case "follow" -> "Буду держаться рядом.";
            case "come" -> "Попробую подойти.";
            case "wait" -> "Хорошо, подожду.";
            case "protect" -> "Я прикрою.";
            case "hunt_mobs" -> "Окей, разберусь с монстрами поблизости.";
            case "smelt_items" -> "Ага, растоплю печь.";
            case "mine_ore" -> "Ага, возьму кирку и поищу руду.";
            case "hunt_animals" -> "Ага, пойду поохочусь.";
            case "obtain_item" -> "Ага, добуду.";
            default -> "Окей.";
        };
    }

    /** Engine report line listing plan steps that were not started after a stop. */
    public static final String SKIPPED_PREFIX = "Не выполнено: ";

    public static String describe(PlannedAction action) {
        return switch (action.type()) {
            case "gather_wood" -> "нарубить брёвна (" + action.count() + " шт.)";
            case "gather_dirt" -> "накопать землю (" + action.count() + " шт.)";
            case "gather_stone" -> "добыть камень (" + action.count() + " шт.)";
            case "mine_ore" -> {
                var kind = dev.aicompanion.entity.CompanionOre.Kind.of(action.item());
                yield "добыть " + (kind == null ? "руду" : kind.word) + " (" + action.count() + " бл. руды)";
            }
            case "hunt_animals" -> {
                var game = dev.aicompanion.entity.CompanionGame.Kind.of(action.item());
                yield game == null ? "поохотиться (" + action.count() + " животн.)" : "добыть " + game.count(action.count());
            }
            case "obtain_item" -> "добыть «" + itemName(action.item()) + "» (" + action.count() + " шт.)";
            case "verify_item" -> "проверить, что «" + itemName(action.item()) + "» у меня";
            case "craft_item" -> "сделать «" + itemName(action.item()) + "»";
            case "place_block" -> "поставить «" + itemName(action.item()) + "»";
            case "equip_item" -> "взять «" + itemName(action.item()) + "»";
            case "unequip_item" -> "убрать предмет из руки";
            case "store_items" -> "сложить вещи в сундук";
            case "take_items" -> "забрать вещи из сундука";
            case "build_house" -> "continue".equals(action.item()) ? "решить, что нужно стройке дальше" : "построить дом по схеме";
            case "build_schematic" -> "строить дом (" + action.count() + " бл.)";
            case "verify_house" -> "проверить, что дом стоит целиком";
            case "hunt_mobs" -> "разобраться с монстрами";
            case "give_wood" -> "отдать тебе дерево";
            case "give_items" -> "отдать тебе «" + itemName(action.item()) + "»";
            case "smelt_items" -> dev.aicompanion.entity.CompanionGame.ANY_MEAT.equals(action.item()) ? "пожарить мясо" : "переплавить «" + itemName(action.item()) + "» ("
                    + (action.count() == PlannedAction.ALL_CARRIED ? "всё, что есть" : action.count() + " шт.") + ")";
            case "come" -> "подойти к тебе";
            case "follow" -> "идти за тобой";
            case "wait" -> "подождать";
            case "protect" -> "прикрывать тебя";
            case "stop_protect" -> "закончить охрану";
            case "wander" -> "осмотреться";
            case "goal_pickaxes" -> "сделать каменные кирки для вас обоих";
            case "goal_resume" -> "продолжить сохранённую цель";
            case "goal_status" -> "проверить сохранённую цель";
            case "autonomy" -> "on".equals(action.item()) ? "заняться полезными делами" : "закончить самостоятельную работу";
            case "stop" -> "остановиться";
            default -> action.type();
        };
    }

    /**
     * Steps not done, said once each (game log 23.10: «сложить вещи в сундук» four times — one store step per tool). Storing
     * named items is one phrase: «сложить в сундук «Каменная кирка», «Каменный топор»».
     */
    public static String describeAll(List<PlannedAction> steps) {
        List<String> parts = new java.util.ArrayList<>();
        List<String> stored = new java.util.ArrayList<>();
        int storeAt = -1;
        for (PlannedAction step : steps) {
            if ("store_items".equals(step.type()) && step.item().contains(":")) {
                if (storeAt < 0) { storeAt = parts.size(); parts.add(""); }
                String name = "«" + itemName(step.item()) + "»";
                if (!stored.contains(name)) stored.add(name);
                continue;
            }
            String said = describe(step);
            if (!parts.contains(said)) parts.add(said);
        }
        if (storeAt >= 0) parts.set(storeAt, "сложить в сундук " + String.join(", ", stored));
        return String.join(", ", parts);
    }

    private static String itemName(String id) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null) return id;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key)
                .map(item -> new net.minecraft.world.item.ItemStack(item).getHoverName().getString()).orElse(id);
    }

    public static String summarize(List<PlannedAction> actions, String report) {
        if (report == null || report.isBlank()) return "";
        String skipped = "";
        int marker = report.lastIndexOf(SKIPPED_PREFIX);
        if (marker >= 0 && (marker == 0 || report.charAt(marker - 1) == '\n')) {
            skipped = report.substring(marker + SKIPPED_PREFIX.length()).trim();
            report = report.substring(0, marker).trim();
        }
        String spoken = summarizeSteps(actions, report);
        // Unstarted steps remain visible in Tasks. Reading them aloud turns one problem into a roll call.
        return spoken.isBlank() && !skipped.isEmpty() ? "Пока получилось выполнить только часть поручения." : spoken;
    }

    private static String summarizeSteps(List<PlannedAction> actions, String report) {
        if (report.isBlank()) return "";
        if (actions.stream().anyMatch(action -> action.type().startsWith("goal_"))) return report.replace("План остановлен: ", "");
        String lower = report.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("такое поручение уже выполняется")) return "Я уже занимаюсь этим поручением.";
        if (lower.contains("отменён") || lower.contains("остановки")) return "Ладно, остановился.";
        int stoppedAt = report.lastIndexOf("План остановлен:");
        String last = stoppedAt >= 0 ? report.substring(stoppedAt) : report.substring(report.lastIndexOf('\n') + 1);
        if (last.contains("План остановлен:")) {
            String failure = dev.aicompanion.CompanionFailureMessages.primary(last);
            // A house order ends with its own words: how far it got and what is missing (no schematics, a chest in the way…).
            if (actions.stream().anyMatch(action -> "build_house".equals(action.type()))) return speakable(failure);
            String partial = partialGather(actions, failure);
            if (partial != null) return partial;
            if (failure.contains("Для подъёма не хватает земли")) return "До верхушки без опоры не достану. Дай мне немного земли.";
            if (failure.contains("сумке нет места") || failure.contains("сумка заполнена")) return "У меня сумка забита. Освободишь немного места?";
            if (failure.contains("недостаточно") || failure.contains("не хватает") || failure.contains("Не хватает")) return "На всё пока не хватает материалов.";
            if (failure.contains("дерев") || failure.contains("брёвен")) return "Доступного дерева больше не нашёл. Давай поищем в другом месте.";
            if (failure.contains("безопасное место")) return "Здесь не получается поставить. Нужно место посвободнее.";
            return dev.aicompanion.CompanionFailureMessages.explain(actions.isEmpty() ? "" : actions.getFirst().type(), failure);
        }
        var crafted = actions.stream().filter(action -> "craft_item".equals(action.type())).toList();
        if (actions.stream().anyMatch(action -> "build_house".equals(action.type()))) return "Дом готов! Пойдём посмотрим.";
        long tools = crafted.stream().filter(action -> action.item().matches(".*_(sword|pickaxe|axe|shovel|hoe)")).count();
        if (tools > 1) return "Готово, инструменты сделал.";
        if (!crafted.isEmpty()) {
            String item = crafted.getLast().item();
            if (item.endsWith("crafting_table")) return "Верстак готов.";
            if (item.endsWith("_sword")) return "Меч готов.";
            if (item.endsWith("_pickaxe")) return "Кирка готова.";
            return "Готово, сделал. Можешь посмотреть в сумке.";
        }
        if (actions.stream().anyMatch(action -> "obtain_item".equals(action.type()))) return speakable(last);
        if (actions.stream().anyMatch(action -> "hunt_animals".equals(action.type()))) {
            var got = Pattern.compile("(?iu)добыл (d+ [^ :.,]+)").matcher(report);
            boolean cooked = actions.stream().anyMatch(action -> "smelt_items".equals(action.type()));
            return (got.find() ? "Готово, добыл " + got.group(1) : "С охотой закончил") + (cooked ? " и пожарил мясо." : ".");
        }
        if (actions.stream().anyMatch(action -> "smelt_items".equals(action.type()))) return "Готово, переплавил и забрал из печи.";
        var ore = actions.stream().filter(action -> "mine_ore".equals(action.type())).findFirst();
        if (ore.isPresent()) {
            // Game log 22.10: «+1 «Рудное железо»» after every order sounded like a machine. A friend says what he dug.
            int blocks = Pattern.compile("Добыл (\\d+) блок").matcher(report).results().mapToInt(match -> Integer.parseInt(match.group(1))).sum();
            var kind = dev.aicompanion.entity.CompanionOre.Kind.of(ore.get().item());
            String what = kind == null ? "руды" : kind.genitive;
            String still = report.contains("Наверх пока не выбрался") ? " Я пока внизу, в своём ходе." : "";
            if (blocks <= 0) return "С рудой закончил." + still;
            return (blocks == 1 ? "Готово, накопал " + what + " — один блок руды." : "Готово, накопал " + what + ": " + blocks(blocks) + " руды.") + still;
        }
        if (actions.stream().anyMatch(action -> "gather_wood".equals(action.type()))) {
            var count = Pattern.compile("(?iu)срубил (\\d+)").matcher(report);
            return count.find() ? "Набрал " + count.group(1) + " брёвен. Возвращаюсь." : "С деревом закончил, возвращаюсь.";
        }
        if (actions.stream().anyMatch(action -> "gather_dirt".equals(action.type()))) return "Землю накопал, возвращаюсь.";
        if (actions.stream().anyMatch(action -> "gather_stone".equals(action.type()))) {
            int stone = Pattern.compile("Добыл (\\d+) блоков камня").matcher(report).results().mapToInt(match -> Integer.parseInt(match.group(1))).sum();
            return (stone > 0 ? "Готово, накопал камня: " + stone + "." : "Камень добыл, он у меня.")
                    + (report.contains("наверх пока не выбрался") ? " Я пока внизу, на своей лестнице." : "");
        }
        if (actions.stream().anyMatch(action -> "store_items".equals(action.type()))) return last.contains("Лишних вещей") ? "Лишних вещей для сундука сейчас нет." : "Сложил вещи в сундук.";
        if (actions.stream().anyMatch(action -> "take_items".equals(action.type()))) return "Забрал вещи, они у меня в сумке.";
        if (actions.stream().anyMatch(action -> "place_block".equals(action.type()) && "minecraft:chest".equals(action.item()))) {
            int placed = Pattern.compile("Поставил сундуков: (\\d+)").matcher(report).results().mapToInt(match -> Integer.parseInt(match.group(1))).sum();
            return placed > 0 ? "Готово, сундуков поставлено: " + placed + "." : speakable(last);
        }
        if (actions.stream().anyMatch(action -> "come".equals(action.type()))) return speakable(last);
        if (actions.stream().anyMatch(action -> "hunt_mobs".equals(action.type()))) return speakable(last);
        if (actions.stream().allMatch(action -> List.of("follow", "wait", "protect", "stop_protect", "wander").contains(action.type()))) return "";
        return speakable(last);
    }

    /** «Добыл 1 из 5» for ore or stone, said like a person: how many, of how many, and the plain reason. */
    private static String partialGather(List<PlannedAction> actions, String last) {
        if (actions.stream().anyMatch(action -> "hunt_animals".equals(action.type())) && last.matches("(?s).*(Добыл|Никого не добыл).*")) {
            String why = last.contains("детёныш") ? "остальные — детёныши"
                    : last.contains("чьи-то") ? "остальные чьи-то, с именем или на поводке" : last.contains("не нашёл пути") ? "до остальных не добраться" : "больше рядом не нашёл";
            var got = Pattern.compile("Добыл (d+ [^ :.,]+)").matcher(last);
            return got.find() ? "Добыл только " + got.group(1) + " — " + why + "." : "Никого не добыл — " + why + ".";
        }
        var counts = Pattern.compile("Добыл (\\d+) из (\\d+)").matcher(last);
        if (!counts.find()) return null;
        String got = counts.group(1), wanted = counts.group(2).replaceAll("\\D+$", "");
        var ore = actions.stream().filter(action -> "mine_ore".equals(action.type())).findFirst();
        // obtain_item expands on the server. The original request may only mention armour, while its failed step mined ore.
        if (last.contains("руд")) {
            var kind = ore.isPresent() ? dev.aicompanion.entity.CompanionOre.Kind.of(ore.get().item())
                    : java.util.Arrays.stream(dev.aicompanion.entity.CompanionOre.Kind.values())
                    .filter(value -> last.contains(value.oreName)).findFirst().orElse(null);
            String what = kind == null ? "руды" : kind.genitive;
            String why = last.contains("вода или лава") ? "остальная у воды или лавы, туда не полез"
                    : last.contains("безопасного хода") ? "до остальной не докопаться безопасно"
                    : "больше " + what + " рядом не нашёл";
            if (!got.equals("0")) return "Накопал только " + got + " из " + wanted + " — " + why + ".";
            return why.startsWith("больше") ? "Не нашёл " + what + " поблизости." : "Руду нашёл, но " + why + ".";
        }
        if (actions.stream().anyMatch(action -> "gather_stone".equals(action.type())) && last.contains("камн"))
            return got.equals("0") ? "Камня не добыл — не нашёл, как безопасно к нему подобраться." : "Накопал только " + got + " из " + wanted + " камня — больше безопасного камня рядом не нашёл.";
        return null;
    }

    private static String blocks(int count) {
        int last = count % 10, tens = count % 100;
        return count + (last == 1 && tens != 11 ? " блок" : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? " блока" : " блоков");
    }

    public static boolean continueAfterPartialGather(PlannedAction action, List<PlannedAction> later, boolean success) {
        return continueAfterPartialGather(action, later, success, "");
    }

    public static boolean continueAfterPartialGather(PlannedAction action, List<PlannedAction> later, boolean success, String report) {
        return success || (("gather_wood".equals(action.type()) || "gather_dirt".equals(action.type()))
                && !later.isEmpty() && "craft_item".equals(later.getFirst().type()))
                // Fewer animals than asked: what was taken still goes into the furnace. Nothing taken: the hunt's own reason is
                // the answer (game log 22.10: «У меня нет сырого мяса, жарить нечего» hid «nobody within reach»).
                || "hunt_animals".equals(action.type()) && !later.isEmpty() && "smelt_items".equals(later.getFirst().type())
                && !report.startsWith(dev.aicompanion.entity.CompanionWork.NOTHING_HUNTED);
    }
}
