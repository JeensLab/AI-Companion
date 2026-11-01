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
    /** «добыл 2 коровы», «Добыл 1 свинью из 3», «3 животных»: the hunt's own count, not the blocks of stone or ore in the same report. */
    private static final Pattern HUNTED = Pattern.compile("(?iu)добыл (\\d+ (?:" + java.util.Arrays.stream(dev.aicompanion.entity.CompanionGame.Kind.values())
            .flatMap(kind -> java.util.stream.Stream.of(kind.one, kind.few, kind.many)).collect(java.util.stream.Collectors.joining("|")) + "|животное|животных))(?![а-яё])");

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
        result = names(cleanReply(result)).replaceAll("(?:\\s(?:на|в))?\\s*\\(?-?\\d+, -?\\d+, -?\\d+\\)?", "")
                .replaceAll("\\s+([.,;:!?])", "$1").replaceAll("\\s+", " ").trim();
        if (result.length() <= 200) return result;
        int end = Math.max(result.lastIndexOf(". ", 200), Math.max(result.lastIndexOf("! ", 200), result.lastIndexOf("? ", 200)));
        return end > 40 ? result.substring(0, end + 1) : result.substring(0, 197).trim() + "…";
    }

    public static String acknowledge(List<PlannedAction> actions) {
        boolean wood = actions.stream().anyMatch(action -> "gather_wood".equals(action.type()));
        boolean crafting = actions.stream().anyMatch(action -> "craft_item".equals(action.type()));
        if (wood && crafting) return "Окей, схожу за деревом и сделаю инструменты.";
        if (wood) return "Ладно, я за деревом. Лесопилка на выезде.";
        if (actions.isEmpty()) return "";
        return switch (actions.getFirst().type()) {
            case "gather_dirt" -> "Окей, накопаю земли.";
            case "gather_loose" -> "Ладно, возьму лопату и накопаю " + looseWord(actions.getFirst()) + ".";
            case "gather_stone" -> "Ладно, возьму кирку и пойду за камнем.";
            case "store_items" -> "Сейчас сложу в сундук.";
            case "sort_chests" -> "Сейчас разложу по сундукам.";
            case "take_items" -> "Сейчас заберу из сундука.";
            case "build_house" -> "Окей, построю нам небольшой деревянный дом.";
            case "craft_item" -> "Сейчас сделаю.";
            case "equip_item" -> "Сейчас возьму.";
            case "unequip_item" -> "Ага, уберу.";
            case "place_block" -> "Поставлю вот тут.";
            case "wander" -> "Ладно, осмотрюсь. Я недалеко.";
            case "follow" -> "Буду держаться рядом.";
            case "come" -> "Попробую подойти.";
            case "enter_portal" -> "Попробую зайти в портал.";
            case "walk_heading" -> "Веду " + dev.aicompanion.entity.CompanionCourse.where(actions.getFirst().item()) + ". Не отставай.";
            case "wait" -> "Ладно, жду здесь.";
            case "protect" -> "Держись рядом, я прикрою.";
            case "hunt_mobs" -> "Окей, разберусь с монстрами поблизости.";
            case "smelt_items" -> "Ага, растоплю печь.";
            case "mine_ore" -> "Ага, возьму кирку и поищу руду.";
            case "hunt_animals" -> "Ага, пойду поохочусь.";
            case "build_pen" -> "Окей, сделаю загон.";
            case "breed_animals" -> "Ага, займусь разведением.";
            case "farm_crops" -> "Ага, сделаю грядки у воды.";
            case "harvest_crops" -> "Ага, пойду соберу урожай.";
            case "obtain_item" -> "Ага, добуду.";
            case "pursue_goal" -> "Дело долгое. Прикину, докуда смогу дойти.";
            default -> "Окей.";
        };
    }

    /** The note of a sorting that an order ended while he carried things (CompanionSorting): said whatever step of the plan it came with. */
    public static final String BAG_NOTE = "Часть вещей у меня в сумке.";

    /**
     * What the chat says of a finished plan: a local order with nothing to report says its acknowledgement, and the bag note goes after
     * it («стой» → «Ладно, жду здесь. Часть вещей…»), unless the player heard it just now from the stop itself (review round 3, M2).
     */
    public static String heard(List<PlannedAction> actions, String spoken, boolean local, boolean noteSaidLately) {
        String bare = spoken.replace(" " + BAG_NOTE, "").replace(BAG_NOTE, "").strip();
        if (local && bare.isBlank()) bare = acknowledge(actions);
        return spoken.contains(BAG_NOTE) && !noteSaidLately ? (bare + " " + BAG_NOTE).strip() : bare;
    }

    /** Engine report line listing plan steps that were not started after a stop. */
    public static final String SKIPPED_PREFIX = "Не выполнено: ";
    /** Engine report line: one «get it» order failed for good, yet the rest of the plan went on. */
    public static final String PARTIAL_PREFIX = "Часть не вышла: ";

    public static String describe(PlannedAction action) {
        return switch (action.type()) {
            case "gather_wood" -> "нарубить брёвна (" + action.count() + " шт.)";
            case "gather_dirt" -> "накопать землю (" + action.count() + " шт.)";
            case "gather_loose" -> "накопать " + looseWord(action) + " (" + action.count() + " шт.)";
            case "gather_stone" -> "добыть камень (" + action.count() + " шт.)";
            case "mine_ore" -> {
                var kind = dev.aicompanion.entity.CompanionOre.Kind.of(action.item());
                yield "добыть " + (kind == null ? "руду" : kind.word) + " (" + action.count() + " бл. руды)";
            }
            case "hunt_animals" -> {
                var game = dev.aicompanion.entity.CompanionGame.Kind.of(action.item());
                yield game == null ? "поохотиться (" + action.count() + " животн.)" : "добыть " + game.count(action.count());
            }
            case "build_pen" -> {
                var animal = dev.aicompanion.entity.CompanionGame.Kind.of(action.item());
                yield "построить загон" + (animal == null ? "" : " для " + animal.many) + " (держать до " + action.count() + ")";
            }
            case "breed_animals" -> {
                var animal = dev.aicompanion.entity.CompanionGame.Kind.of(action.item());
                yield "развести " + (animal == null ? "животных" : animal.many) + " (пар: " + action.count() + ")";
            }
            case "farm_crops" -> {
                var crop = dev.aicompanion.entity.CompanionCrops.Kind.of(action.item());
                yield "посадить " + (crop == null ? "культуру" : crop.plant) + " (грядок: " + action.count() + ")";
            }
            case "harvest_crops" -> "собрать урожай";
            case "obtain_item" -> "добыть «" + itemName(action.item()) + "» (" + action.count() + " шт.)";
            case "verify_item" -> "проверить, что «" + itemName(action.item()) + "» у меня";
            case "craft_item" -> "сделать «" + itemName(action.item()) + "»";
            case "place_block" -> "поставить «" + itemName(action.item()) + "»";
            case "equip_item" -> "взять «" + itemName(action.item()) + "»";
            case "unequip_item" -> "убрать предмет из руки";
            case "store_items" -> "сложить вещи в сундук";
            case "sort_chests" -> "разложить по сундукам";
            case "take_items" -> "забрать вещи из сундука";
            case "build_house" -> "построить дом";
            case "hunt_mobs" -> "разобраться с монстрами";
            case "give_wood" -> "отдать тебе дерево";
            case "give_items" -> "отдать тебе «" + itemName(action.item()) + "»";
            case "smelt_items" -> dev.aicompanion.entity.CompanionGame.ANY_MEAT.equals(action.item()) ? "пожарить мясо" : "переплавить «" + itemName(action.item()) + "» ("
                    + (action.count() == PlannedAction.ALL_CARRIED ? "всё, что есть" : action.count() + " шт.") + ")";
            case "come" -> "подойти к тебе";
            case "enter_portal" -> "зайти в портал";
            case "walk_heading" -> "провести тебя " + dev.aicompanion.entity.CompanionCourse.where(action.item());
            case "follow" -> "идти за тобой";
            case "wait" -> "подождать";
            case "protect" -> "прикрывать тебя";
            case "stop_protect" -> "закончить охрану";
            case "wander" -> "осмотреться";
            case "pursue_goal" -> {
                var book = dev.aicompanion.entity.CompanionPlaybooks.of(action.item(), action.count());
                yield "взяться за долгую цель" + (book == null ? "" : ": " + book.title());
            }
            case "goal_pickaxes" -> "сделать каменные кирки для вас обоих";
            case "goal_resume" -> "продолжить сохранённую цель";
            case "goal_cancel" -> "отменить цель";
            case "goal_defer" -> "отложить цель";
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

    public static String itemName(String id) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null) return id;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key)
                .map(item -> new net.minecraft.world.item.ItemStack(item).getHoverName().getString()).orElse(id);
    }

    public static String summarize(List<PlannedAction> actions, String report) {
        String spoken = summarizeReport(actions, report);
        // Review round 3, finding 1: the note came with the step that ended the sorting («стоп», «за мной»), the plan ended with another one.
        return report != null && report.contains(BAG_NOTE) && !spoken.contains("у меня в сумке") ? (spoken + " " + BAG_NOTE).strip() : spoken;
    }

    private static String summarizeReport(List<PlannedAction> actions, String report) {
        if (report == null || report.isBlank()) return "";
        String skipped = "";
        int marker = report.lastIndexOf(SKIPPED_PREFIX);
        if (marker >= 0 && (marker == 0 || report.charAt(marker - 1) == '\n')) {
            skipped = report.substring(marker + SKIPPED_PREFIX.length()).trim();
            report = report.substring(0, marker).trim();
        }
        String failed = actions.stream().anyMatch(CompanionDialogue::isGoalAction) ? "" : failedParts(report);
        if (!failed.isEmpty()) {
            // Parts given up while the rest went on: the player hears what was not made and why, once, not the steps that went with it.
            if (!report.contains("План остановлен:")) return "Остальное сделал. " + failed;
            // The plan stopped on the last part given up (same reason on the line before the stop): it is one «Не вышло», said once.
            if (stoppedOnLastPart(report)) return failed;
            String lower = report.toLowerCase(java.util.Locale.ROOT);
            String stopped = summarizeSteps(actions, report);
            return lower.contains("отменён") || lower.contains("остановки") ? stopped : stopped + " " + failed;
        }
        String spoken = summarizeSteps(actions, report);
        // Unstarted steps remain visible in Tasks. Reading them aloud turns one problem into a roll call.
        return spoken.isBlank() && !skipped.isEmpty() ? "Пока получилось выполнить только часть поручения." : spoken;
    }

    private static boolean stoppedOnLastPart(String report) {
        String[] lines = report.split("\n");
        for (int i = lines.length - 1; i > 0; i--) {
            if (!lines[i].startsWith("План остановлен:")) continue;
            String part = lines[i - 1];
            String rest = part.startsWith(PARTIAL_PREFIX) ? part.substring(PARTIAL_PREFIX.length()) : "";
            int end = rest.startsWith("«") ? rest.indexOf("»: ") : -1;
            return end > 0 && rest.substring(end + 3).strip().equals(lines[i].substring("План остановлен:".length()).strip());
        }
        return false;
    }

    /** «Не вышло: «A», «B» — причина.» from the give-up lines of the report; empty if there are none. */
    private static String failedParts(String report) {
        List<String> names = new java.util.ArrayList<>(), whys = new java.util.ArrayList<>();
        for (String line : report.split("\n")) {
            if (!line.startsWith(PARTIAL_PREFIX)) continue;
            String rest = line.substring(PARTIAL_PREFIX.length());
            int end = rest.startsWith("«") ? rest.indexOf("»: ") : -1;
            if (end > 0) { String name = rest.substring(0, end + 1); if (!names.contains(name)) names.add(name); rest = rest.substring(end + 3); }
            // Only ore digging speaks of ore: an unsafe route to it is said as ore out of reach, not as a lost way (game 27.10).
            String why = dev.aicompanion.CompanionFailureMessages.explain(rest.contains("руд") ? "mine_ore" : "", rest);
            // The engine's own words for a reason that has no kind (a planner's refusal, «Два раза подряд ничего не прибавилось.»), if short.
            String own = dev.aicompanion.CompanionFailureMessages.primary(rest);
            if (dev.aicompanion.CompanionFailureMessages.kind(rest) == dev.aicompanion.CompanionFailureMessages.Kind.OTHER && !own.isEmpty() && own.length() <= 100) why = own;
            // Two reasons at most: four parts with three reasons ran over two hundred characters.
            if (!whys.contains(why) && whys.size() < 2) whys.add(why);
        }
        if (whys.isEmpty()) return "";
        if (names.isEmpty()) return "Часть не вышла: " + String.join(" ", whys);
        String why = String.join(" ", whys);
        return "Не вышло: " + String.join(", ", names) + " — " + Character.toLowerCase(why.charAt(0)) + why.substring(1);
    }

    /** How the refusals of the sorting itself begin (CompanionSorting); any other stop of a plan with sorting in it goes the usual way. */
    private static final List<String> SORT_OWN = List.of("Рядом с тобой сундуков", "Не смог дойти до", "Не получилось разложить", "Сумка забита, сначала", "Пропустил",
            "Не успел закончить работу");

    /** The reply of a goal order is said whole: its summary names what he cannot yet, and a cut sentence would not. */
    private static boolean isGoalAction(PlannedAction action) {
        return action.type().startsWith("goal_") || "pursue_goal".equals(action.type());
    }

    private static String summarizeSteps(List<PlannedAction> actions, String report) {
        if (report.isBlank()) return "";
        if (actions.stream().anyMatch(CompanionDialogue::isGoalAction)) return report.replace("План остановлен: ", "");
        String lower = report.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("такое поручение уже выполняется")) return "Я уже занимаюсь этим поручением.";
        boolean sorting = actions.stream().anyMatch(action -> "sort_chests".equals(action.type()));
        // A stop in the middle of sorting: the server's note that things are in his bag is part of the answer (game rule: nothing lost silently).
        if (sorting && lower.contains("работа прервана")) return "Ладно, остановился." + (lower.contains("в сумке") ? " Часть вещей у меня в сумке." : "");
        if (lower.contains("отменён") || lower.contains("остановки")) return "Ладно, остановился.";
        int stoppedAt = report.lastIndexOf("План остановлен:");
        String last = stoppedAt >= 0 ? report.substring(stoppedAt) : report.substring(report.lastIndexOf('\n') + 1);
        // Sorting words its own refusals («Рядом с тобой сундуков не вижу.», «…Что не разложил — у меня в сумке.»): they are said as they are; the raw line of another step of the plan is not.
        if (sorting && last.contains("План остановлен:")) {
            String own = last.substring(last.indexOf("План остановлен:") + "План остановлен:".length()).strip();
            if (SORT_OWN.stream().anyMatch(own::startsWith)) return own;
        }
        if (last.contains("План остановлен:")) {
            String failure = dev.aicompanion.CompanionFailureMessages.primary(last);
            // A pen or a breeding order says its own facts (what stands, who is inside, what is missing), not a general excuse.
            if (actions.stream().anyMatch(action -> "build_pen".equals(action.type()) || "breed_animals".equals(action.type()))) return speakable(failure);
            String partial = partialGather(actions, failure);
            if (partial != null) return partial;
            if (failure.contains("Для подъёма не хватает земли")) return "До верхушки без опоры не достану. Дай мне немного земли.";
            if (failure.contains("сумке нет места") || failure.contains("сумка заполнена")) return "Бля, сумка забита. Нужно освободить место.";
            if (failure.contains("недостаточно") || failure.contains("не хватает") || failure.contains("Не хватает")) return "На всё пока не хватает материалов.";
            if (failure.contains("дерев") || failure.contains("брёвен")) return "Доступного дерева больше не нашёл. Давай поищем в другом месте.";
            if (failure.contains("безопасное место")) return "Здесь не получается поставить. Нужно место посвободнее.";
            // The farm's own words carry what is missing and why: no water, no seeds, no ripe crops.
            if (actions.stream().anyMatch(action -> "farm_crops".equals(action.type()) || "harvest_crops".equals(action.type()))) return speakable(failure);
            // The engine's own words for a reason that has no kind, if short, as in failedParts (game 29.10: «Не нашёл ровную свободную
            // площадку 7×7 рядом…» for a house was heard as «С этим пока не справился»): its first line, spoken without IDs or
            // coordinates; a receipt with Latin letters besides known item IDs («предмета «sword»», an unknown ID) is not said.
            String first = failure.lines().findFirst().orElse(""), own = speakable(first);
            String raw = ID.matcher(first).replaceAll(id -> itemName(id.group()).equals(id.group()) ? java.util.regex.Matcher.quoteReplacement(id.group()) : "");
            if (dev.aicompanion.CompanionFailureMessages.kind(first) == dev.aicompanion.CompanionFailureMessages.Kind.OTHER && !own.isEmpty()
                    && own.length() <= 100 && !raw.matches("(?s).*[A-Za-z].*")) return Character.toUpperCase(own.charAt(0)) + own.substring(1);
            return dev.aicompanion.CompanionFailureMessages.explain(actions.isEmpty() ? "" : actions.getFirst().type(), failure);
        }
        var crafted = actions.stream().filter(action -> "craft_item".equals(action.type())).toList();
        if (actions.stream().anyMatch(action -> "build_house".equals(action.type()))) return "Дом готов, заебись! Пойдём посмотрим.";
        // Made and handed over: «посмотри в сумке» would send the player to a bag the things already left (PR #29, the armour order).
        if (!crafted.isEmpty() && actions.stream().anyMatch(action -> "give_items".equals(action.type()) || "give_wood".equals(action.type())))
            return actions.stream().anyMatch(action -> "equip_item".equals(action.type())) ? "Готово: сделал, своё надел, твоё отдал." : "Готово, сделал и отдал тебе.";
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
            var got = HUNTED.matcher(report);
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
        if (actions.stream().anyMatch(action -> "gather_loose".equals(action.type()))) {
            // The order is met only when that many of the item are in the bag, so the ordered counts are what he dug.
            String dug = actions.stream().filter(action -> "gather_loose".equals(action.type()))
                    .map(action -> looseGenitive(action) + ": " + action.count()).collect(java.util.stream.Collectors.joining(", "));
            return "Готово, накопал " + dug + ". Возвращаюсь.";
        }
        if (actions.stream().anyMatch(action -> "gather_stone".equals(action.type()))) {
            int stone = Pattern.compile("Добыл (\\d+) блоков камня").matcher(report).results().mapToInt(match -> Integer.parseInt(match.group(1))).sum();
            return (stone > 0 ? "Готово, накопал камня: " + stone + "." : "Камень добыл, он у меня.")
                    + (report.contains("наверх пока не выбрался") ? " Я пока внизу, на своей лестнице." : "");
        }
        if (actions.stream().anyMatch(action -> "sort_chests".equals(action.type()))) return last.strip();
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
        var hunt = HUNTED.matcher(last);
        boolean hunted = hunt.find();
        if (actions.stream().anyMatch(action -> "hunt_animals".equals(action.type())) && (hunted || last.contains(dev.aicompanion.entity.CompanionWork.NOTHING_HUNTED))) {
            String why = last.contains("детёныш") ? "остальные — детёныши"
                    : last.contains("чьи-то") ? "остальные чьи-то, с именем или на поводке" : last.contains("не нашёл пути") ? "до остальных не добраться" : "больше рядом не нашёл";
            return hunted ? "Добыл только " + hunt.group(1) + " — " + why + "." : "Никого не добыл — " + why + ".";
        }
        var loose = actions.stream().filter(action -> "gather_loose".equals(action.type())).findFirst();
        if (loose.isPresent() && CompanionPlanCodec.FLINT.equals(loose.get().item())) {
            var dropped = Pattern.compile("Выпало (\\d+) из (\\d+) кремня(?:: (.*))?").matcher(last);
            if (dropped.find()) {
                String why = dropped.group(3) == null || dropped.group(3).isBlank() ? "." : ": " + dropped.group(3).strip();
                return dropped.group(1).equals("0") ? "Кремень так и не выпал" + why
                        : "Кремня выпало только " + dropped.group(1) + " из " + dropped.group(2) + why;
            }
        }
        if (loose.isPresent() && last.contains(looseGenitive(loose.get()))) {
            var found = Pattern.compile("(?:Добыл|Нашёл только) (\\d+) из (\\d+)").matcher(last);
            if (found.find()) return found.group(1).equals("0")
                    ? "Не добыл " + looseGenitive(loose.get()) + " — не нашёл открытого безопасного участка рядом."
                    : "Накопал только " + found.group(1) + " из " + found.group(2) + " " + looseGenitive(loose.get()) + " — больше безопасного участка рядом не нашёл.";
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

    /** «песок» for an ordered loose item; the plan codec allows nothing else for gather_loose. */
    private static String looseWord(PlannedAction action) {
        if (CompanionPlanCodec.FLINT.equals(action.item())) return "кремень";
        var ground = dev.aicompanion.entity.CompanionGround.of(action.item());
        return ground == null ? "сыпучие блоки" : ground.accusative();
    }

    private static String looseGenitive(PlannedAction action) {
        if (CompanionPlanCodec.FLINT.equals(action.item())) return "кремня";
        var ground = dev.aicompanion.entity.CompanionGround.of(action.item());
        return ground == null ? "сыпучих блоков" : ground.genitive();
    }

    private static String blocks(int count) {
        int last = count % 10, tens = count % 100;
        return count + (last == 1 && tens != 11 ? " блок" : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? " блока" : " блоков");
    }

    /** Fewer flints than ordered: a craft made of flint cannot go on, and its own failure would hide the honest flint report. */
    private static boolean needsMissingFlint(PlannedAction gather, PlannedAction next) {
        return CompanionPlanCodec.FLINT.equals(gather.item()) && java.util.Set.of("minecraft:flint_and_steel", "minecraft:arrow", "minecraft:fletching_table").contains(next.item());
    }

    public static boolean continueAfterPartialGather(PlannedAction action, List<PlannedAction> later, boolean success) {
        return continueAfterPartialGather(action, later, success, "");
    }

    public static boolean continueAfterPartialGather(PlannedAction action, List<PlannedAction> later, boolean success, String report) {
        return success || (("gather_wood".equals(action.type()) || "gather_dirt".equals(action.type()) || "gather_loose".equals(action.type()))
                && !later.isEmpty() && "craft_item".equals(later.getFirst().type()) && !needsMissingFlint(action, later.getFirst()))
                // Fewer animals than asked: what was taken still goes into the furnace. Nothing taken: the hunt's own reason is
                // the answer (game log 22.10: «У меня нет сырого мяса, жарить нечего» hid «nobody within reach»).
                || "hunt_animals".equals(action.type()) && !later.isEmpty() && "smelt_items".equals(later.getFirst().type())
                && !report.startsWith(dev.aicompanion.entity.CompanionWork.NOTHING_HUNTED);
    }
}
