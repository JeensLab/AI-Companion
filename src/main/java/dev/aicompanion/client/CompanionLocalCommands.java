package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionMarkerNames;

/**
 * The player's short orders that never wait for the model («стоп», «иди за мной», «стой», «иди ко мне», simple work requests).
 * No client classes, so the chat screen and the GameTests route a phrase the same way.
 */
public final class CompanionLocalCommands {
    private static final String NEAR_PLAYER = "near_player";
    /** Token positions of "companion work <type> <count> <item> [location]"; the location is optional. */
    private static final int WORK_COUNT = 3;
    private static final int WORK_ITEM = 4;
    private static final int WORK_LOCATION = 5;

    private CompanionLocalCommands() { }

    /** The local command for the phrase ("companion stop", "companion work gather_stone 8 resources"…), or null for the model. */
    public static String route(String text, String companionName) {
        // One snapshot of the client's copy: the marker names and the switch come from the same publication.
        CompanionMarkerView.Snapshot known = CompanionMarkerView.snapshot();
        // The names the player's own language gives the game's things: a client showing en_us must still know «тыкву».
        return route(text, companionName, CompanionClientNames.names(), known.names(), known.enabled());
    }

    /**
     * The same, by the names the game shows for its items. The client's are the player's language; a GameTest, whose
     * server runs en_us, passes its own stand-in for the Russian names the player sees.
     */
    public static String route(String text, String companionName, java.util.function.Function<net.minecraft.world.item.Item, String> names) {
        CompanionMarkerView.Snapshot known = CompanionMarkerView.snapshot();
        return route(text, companionName, names, known.names(), known.enabled());
    }

    /** The same routing with the marker names the caller knows: the chat screen uses the client's copy, tests pass their own. */
    public static String route(String text, String companionName, java.util.List<String> markers, boolean markersEnabled) {
        return route(text, companionName, CompanionClientNames.names(), markers, markersEnabled);
    }

    /** Both: the game's item names and the marker names the caller knows. */
    public static String route(String text, String companionName, java.util.function.Function<net.minecraft.world.item.Item, String> names,
                               java.util.List<String> markers, boolean markersEnabled) {
        String command = routeInner(text, companionName, names, markers, markersEnabled);
        // «Ещё» repeats only the pickup that was the last order; any other phrase ends that window.
        if (command == null || !command.startsWith("companion work pick_up ")) CompanionPickUpCommands.forget();
        return command;
    }

    private static String routeInner(String text, String companionName, java.util.function.Function<net.minecraft.world.item.Item, String> names,
                                     java.util.List<String> markers, boolean markersEnabled) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        String memory = memoryCommand(normalized, companionName);
        if (memory != null) return memory;
        // «Что ты умеешь», «расскажи 10 умений»: the list of his real abilities is answered from the code, without the model.
        String abilities = abilitiesCommand(normalized, companionName);
        if (abilities != null) return abilities;
        // «Порядок и запас» on and off (CompanionAutonomy). Before «стоп»: «хватит, просто будь рядом» is not a stop of an order.
        if (normalized.contains("просто будь рядом") || normalized.contains("просто держись рядом") || normalized.matches("(?sU).*\\bне будь самостоятельн.*")
                || normalized.matches("(?sU).*\\b(хватит|перестань|прекрати|выключи|отключи)\\b.{0,12}самостоятельн.*")) return "companion autonomy off";
        if (!normalized.matches("(?sU).*\\bне\\s+(делай|займись|занимайся|продолжай)\\b.*")
                && (normalized.matches("(?sU).*\\b(будь|стань)\\s+самостоятельн.*") || normalized.matches("(?sU).*\\b(включи|включай)\\b.{0,12}самостоятельн.*")
                // Game 23.10: «делай что хочешь» went to the model, which sent him for a walk instead; «займись пока чем нибудь» went
                // there too, and the model sent him for stone. A word or two between («пока», «тут», «сам») is the same request.
                || normalized.matches("(?sU).*\\bделай\\b(\\s+\\p{L}+){0,2}?\\s+(что\\s+хочешь|что\\s+угодно|что-нибудь|что\\s+нибудь)\\b.*")
                || normalized.matches("(?sU).*\\bзаймись\\b(\\s+\\p{L}+){0,2}?\\s+(чем|своими\\s+делами|делом)\\b.*")
                || normalized.matches("(?sU).*\\bчем[- ]?(нибудь|то)\\s+займись\\b.*")
                || normalized.matches("(?sU).*\\b(продолжай\\s+)?занимайся\\b.{0,18}\\b(чем|своими\\s+делами)\\b.*")
                || normalized.matches("(?sU).*\\bпродолжай\\s+заниматься\\s+(чем|своими\\s+делами)\\b.*")
                || normalized.contains("займись делом") || normalized.contains("найди себе дело") || normalized.contains("занимайся сам")))
            // «Займись чем-нибудь, но сначала добудь камня» is a request with steps: the model plans it.
            if (!normalized.matches("(?sU).*\\b(но|сначала|потом|затем)\\b.*")) return "companion autonomy on";
        // The long goal is named before «стоп» and «отмени»: «отмени цель» ends the goal, while «отмени задачу» stays a stop of the work.
        if (normalized.matches("(?sU).*\\bотложи\\b(\\s+\\p{L}+){0,3}?\\s+цель\\b.*")) return "companion goal defer";
        if (normalized.matches("(?sU).*\\b(вернись|вернемся)\\s+к\\b(\\s+\\p{L}+){0,2}?\\s+цели\\b.*")) return "companion goal resume";
        if (normalized.matches("(?sU).*\\bкак\\s+там\\b(\\s+\\p{L}+){0,2}?\\s+цель\\b.*")) return "companion goal status";
        // A cancel is terminal: only filler words may stand between the verb and the goal, and «не забудь про цель» is no cancel.
        if (normalized.matches("(?sU).*(?<!\\bне\\s{1,3})\\b(отмени|отмена|забудь)\\b[\\s,]*(?:(?:эту|ту|мою|нашу|долгую|большую|всю|про|пожалуйста)\\b[\\s,]*)*(?:цель|цели|дракона)\\b.*"))
            return "companion goal cancel";
        // Before «стоп»: «веди на север, пока не скажу стоп» is the walk, not a stop.
        String heading = CompanionHeadingCommands.parse(normalized, companionName);
        // «Веди к крепости» with a marker the player named so is about that marker (below), not the eye of ender's walk;
        // «брось око» is the eye's whatever the markers.
        if (heading != null && !(heading.endsWith(" stronghold") && normalized.contains("крепост") && markersEnabled
                && markers.stream().anyMatch(CompanionPlaceSpeech::fortress))) return heading;
        if (normalized.matches("(?sU).*\\b(стоп|отмена|отмени|остановись)\\b.*")
                || normalized.contains("перестань рубить") || normalized.contains("прекрати рубить")
                || normalized.contains("хватит рубить") || normalized.contains("брось рубить")
                || normalized.contains("положи топор") || normalized.contains("останови добычу")
                || normalized.contains("прекрати добычу") || normalized.contains("отмени задачу")
                || normalized.contains("перестань добывать") || normalized.contains("хватит копать") || normalized.contains("прекрати копать")
                || normalized.contains("перестань строить") || normalized.contains("хватит строить") || normalized.contains("прекрати строить")
                || normalized.contains("останови стройку") || normalized.contains("останови строительство")
                || normalized.contains("останови задачу")
                // The music by the fire (CompanionCampfire): «хватит играть» puts the guitar away like any stop.
                || normalized.matches("(?sU).*\\b(хватит|перестань|прекрати|кончай|заканчивай)\\s+(играть|бренчать|музыку)\\b.*") || normalized.contains("убери гитару")) {
            return "companion stop";
        }
        if (normalized.matches("(?sU).*\\b(продолжай|продолжи|возобнови)\\s+(работу|заказ|цель|дело)\\b.*")) return "companion goal resume";
        // His own hints say «скажи «продолжай»»: the bare word, alone or after his name, resumes what waits; «продолжай рассказ» is not it.
        if (normalized.matches("(?sU)\\s*(?:" + java.util.regex.Pattern.quote(companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е')) + "[\\s,!]+)?(?:давай\\s+|ну\\s+)?(?:продолжай|продолжи|продолжаем)[\\s.!]*")) return "companion goal resume";
        if (normalized.matches("(?sU).*\\b(что ты (?:сейчас )?делаешь|чем ты (?:сейчас )?занят|как там (?:наши )?кирки|как идет (?:работа|заказ))\\b.*")) return "companion goal status";
        if (CompanionGoalCommands.pickaxesForTwo(normalized)) return "companion goal pickaxes";
        // Before the work phrases: «поставь костёр и сыграй» is the evening by the fire, not a block to place.
        if (campfireSong(normalized, companionName)) return "companion campfire" + tuneMood(normalized);
        // «Подбери» takes what lies on the ground here; «принеси» is the work orders below, where the thing may be far.
        // «Ещё» right after a pickup takes the rest of that same thing, not whatever the model guesses.
        PlannedAction more = CompanionPickUpCommands.more(text, companionName);
        if (more != null) {
            CompanionPickUpCommands.remember(more);
            return "companion work " + more.type() + " " + more.count() + " " + more.item();
        }
        PlannedAction picked = CompanionPickUpCommands.parse(text, companionName, names);
        if (picked != null) {
            CompanionPickUpCommands.remember(picked);
            return "companion work " + picked.type() + " " + picked.count() + " " + picked.item();
        }
        PlannedAction workAction = CompanionWorkCommands.parse(text, companionName);
        if (workAction != null) return "companion work " + workAction.type() + " " + workAction.count() + " "
                // The command is a line of words: a building's name of several («деревенский дом») goes as one, joined.
                + (workAction.item().isEmpty() ? "resources" : "build_house".equals(workAction.type()) ? workAction.item().replace(' ', '_') : workAction.item())
                + (NEAR_PLAYER.equals(workAction.location()) ? "" : " " + workAction.location());
        String water = CompanionWorkCommands.water(text, companionName);
        if (water != null) return water;
        String obsidian = CompanionWorkCommands.obsidian(text, companionName);
        if (obsidian != null) return obsidian;
        // Before the equipment: «сними чары с меча» is the grindstone, not «сними меч».
        String grind = CompanionWorkCommands.grind(text, companionName);
        if (grind != null) return grind;
        String compost = CompanionWorkCommands.compost(text, companionName);
        if (compost != null) return compost;
        // «Все такие блоки добудь что видишь», «все заборы сломай»: every block of the kind in his search box, a count the
        // server resolves against the world — before the single order, which would read «все заборы» as one thing named.
        String all = CompanionWorkCommands.allItems(text, companionName, names);
        if (all != null) return all;
        // «Добудь тыкву», «сорви блок тыквы», «принеси 2 тыквы», «сломай вишнёвый забор»: any thing the game's own registry names, by the same obtain
        // path the ore and obsidian take. After the orders above, so «добудь обсидиан» keeps its ten and «принеси воды» its
        // hand-over; before the places, so «принеси мне тыкву» is no walk to a marker.
        String item = CompanionWorkCommands.itemOrder(text, companionName, names);
        if (item != null) return item;
        // «Запомни это место, это дом» names a marker: after the work grammar, so «запомни это место как дом» stays the
        // home it always was.
        CompanionPlaceSpeech.Result remembered = CompanionPlaceSpeech.remember(text, companionName, markersEnabled);
        if (remembered.kind() == CompanionPlaceSpeech.Kind.REMEMBER) return "companion place remember " + remembered.name();
        // A named place he remembers: «иди к колодцу», «проведи меня до железного рудника», «отведи меня к даче»,
        // «охраняй дачу», «отнеси ресы в дом». What is left after the name decides: a name that itself holds «и»
        // («сад и огород», «дом и баня») leaves nothing, so it stays one local step; «иди к даче, потом домой» leaves
        // «потом домой», and the model plans both legs, which one local walk would lose.
        CompanionPlaceSpeech.Result place = CompanionPlaceSpeech.parse(text, companionName, markers, markersEnabled);
        // «Забудь дачу», «удали метку дачу», «забудь всё»: the list is the player's, and the word takes markers off it.
        // The chat answers it from the store, so no step is checked here and no plan is made.
        if (place.kind() == CompanionPlaceSpeech.Kind.FORGET || place.kind() == CompanionPlaceSpeech.Kind.FORGET_ALL)
            return "companion place forget" + (place.name().isEmpty() ? "" : " " + place.name());
        if (place.kind() == CompanionPlaceSpeech.Kind.GO || place.kind() == CompanionPlaceSpeech.Kind.GUARD
                || place.kind() == CompanionPlaceSpeech.Kind.CARRY) {
            if (!oneStep(place.leftover())) return null;
            String verb = place.kind() == CompanionPlaceSpeech.Kind.GO ? "go"
                    : place.kind() == CompanionPlaceSpeech.Kind.GUARD ? "guard" : "carry";
            return "companion place " + verb + " " + place.name();
        }
        // Only when the place parse had nothing to say: a request with steps («иди к озеру, потом домой») stays the model's,
        // an unknown or ambiguous name is the chat screen's question, and a digit is no reason to leave the places.
        if (normalized.contains(" и ") || normalized.contains("потом") || normalized.contains("затем")) return null;
        // Before «стой»: «зайди в портал, жди там» is an order to go in.
        if (normalized.matches("(?sU).*(?<!\\bне\\s{1,3})\\b(зайди|заходи|войди|входи|шагни|прыгни|пройди|проходи|иди)\\s+(в|через)\\s+(\\p{L}+\\s+)?(портал|незер)\\b.*")) return "companion enter_portal";
        // «Не стой столбом» is no order; in «стой, не иди туда» only the negated verb is cut out, the rest is read below.
        normalized = normalized.replaceAll("(?U)\\bне\\s+(стой|останься|жди|защищай|защити|прикрывай|помогай|(?:пре)?следуй(?:те)?|иди|пойдем|пошли|приди|подойди|вернись|появись|телепортируйся)\\b", " ");
        if (normalized.matches("(?sU).*\\b(стой|останься|жди)\\b.*")
                || normalized.contains("стой здесь") || normalized.contains("жди тут")) {
            return "companion stay";
        }
        if (normalized.contains("защищай меня") || normalized.contains("защити меня")
                || normalized.contains("прикрывай меня") || normalized.contains("помогай в бою")) {
            return "companion guard";
        }
        if (normalized.contains("прекрати защищать") || normalized.contains("хватит защищать")
                || normalized.contains("перестань атаковать")) {
            return "companion unguard";
        }
        if (normalized.contains("следуй") || normalized.contains("иди за мной")
                || normalized.contains("пойдем со мной") || normalized.contains("пошли со мной")) {
            return "companion follow";
        }
        if (normalized.contains("приди ко мне") || normalized.contains("телепортируйся ко мне")
                || normalized.contains("появись рядом") || normalized.contains("иди ко мне")
                || normalized.contains("подойди ко мне") || normalized.contains("вернись ко мне")) {
            return "companion summon";
        }
        if (normalized.contains("погуляй") || normalized.contains("прогуляйся") || normalized.contains("осмотрись")) return "companion roam";
        if (normalized.matches("(?s).*\\d.*")) return null;
        return CompanionEquipmentCommands.parse(normalized);
    }

    /** A building's name out of the command line: its words joined there are apart again, and the word that stands for «no item» is no name (the plank house). */
    private static String buildingName(String word) { return "resources".equals(word) ? "" : word.replace('_', ' '); }

    /** Words that only soften the order: «и всё», «пожалуйста» after the place ask no second step of him, and «и обратно» is the same one place walked to and back. */
    private static final java.util.Set<String> SOFTENERS = java.util.Set.of("и", "все", "всё", "пожалуйста", "пожалуста",
            "пожалуй", "спасибо", "ладно", "хорошо", "ок", "ok", "давай", "ну", "плиз", "обратно");

    /**
     * True when the words left after the place name are no second step: nothing, or only the words that soften the
     * order. Anything else — «потом домой», «и домой», «возьми кирку» — is a plan with steps, and the model makes it.
     */
    private static boolean oneStep(String leftover) {
        for (String word : CompanionMarkerNames.tokens(leftover)) if (!SOFTENERS.contains(word)) return false;
        return true;
    }

    /** The plan step a local command runs. */
    public static PlannedAction action(String command) {
        if (command.startsWith("companion place ")) return placeAction(command);
        String[] parts = command.split(" ");
        return switch (parts[1]) {
            case "goal" -> new PlannedAction("goal_" + parts[2], 0, 1);
            case "equip" -> new PlannedAction("equip_item", 0, 1, parts[2], parts[3]);
            case "unequip" -> new PlannedAction("unequip_item", 0, 1, "", parts[2]);
            case "stay" -> new PlannedAction("wait", 1, 1);
            case "guard" -> new PlannedAction("protect", 0, 1);
            case "unguard" -> new PlannedAction("stop_protect", 0, 1);
            case "summon" -> new PlannedAction("come", 0, 1);
            case "roam" -> new PlannedAction("wander", 0, 1);
            case "campfire" -> new PlannedAction("campfire_song", 0, 1, parts.length > 2 ? parts[2] : "");
            case "autonomy" -> new PlannedAction("autonomy", 0, 1, parts[2]);
            case "work" -> new PlannedAction(parts[2], 0, Integer.parseInt(parts[WORK_COUNT]), "build_house".equals(parts[2]) ? buildingName(parts[WORK_ITEM]) : parts[WORK_ITEM], "auto",
                    parts.length > WORK_LOCATION ? parts[WORK_LOCATION] : NEAR_PLAYER);
            default -> new PlannedAction(parts[1], 0, 1);
        };
    }

    /** The plan a local command runs: its step, and for «принеси воды» the hand-over of what it got after it. */
    public static java.util.List<PlannedAction> actions(String command) {
        if (command.startsWith("companion place guard "))
            return java.util.List.of(action(command), new PlannedAction("protect", 0, 1));
        if (command.startsWith("companion place carry "))
            return java.util.List.of(action(command), new PlannedAction("store_items", 0, 1, "resources"));
        // " give" is not a container: the step is parsed without it, or it would be read as the place to store into.
        boolean give = command.endsWith(" give");
        PlannedAction action = action(give ? command.substring(0, command.length() - " give".length()) : command);
        java.util.List<PlannedAction> steps = new java.util.ArrayList<>();
        // A leaf order of several stacks («добудь 500 листвы») is taken as growing targets of a stack each, as the model's plan
        // is split: the bag is checked against the whole count, and no order is refused for being longer than a stack (19.11).
        if ("obtain_item".equals(action.type()) && dev.aicompanion.entity.CompanionLeaves.isFamily(action.item()) && action.count() > 64) {
            for (int at = 64; at < action.count(); at += 64) steps.add(new PlannedAction("obtain_item", 0, at, action.item()));
            steps.add(new PlannedAction("obtain_item", 0, action.count(), action.item()));
        } else steps.add(action);
        // «Добудь листву» ends with the shears in his hand: the gather puts the tool away after every cut, the leaves stay in the bag.
        if ("obtain_item".equals(action.type()) && dev.aicompanion.entity.CompanionLeaves.isFamily(action.item()))
            steps.add(new PlannedAction("equip_item", 0, 1, "minecraft:shears", "mainhand"));
        if (give) steps.add(new PlannedAction("give_items", 0, action.count(), action.item()));
        return java.util.List.copyOf(steps);
    }

    /**
     * «Сыграй на гитаре», «сыграй что-нибудь», «поставь костёр и сыграй», «посидим у костра», or a bare «сыграй» (after his name too):
     * the evening by the fire. «Не играй» and «сыграем в прятки» are not it. The change requests are local too, so a bare «другую»,
     * «повеселее», «погрустнее», «самую грустную» or «самую весёлую» reaches the guitar without the model: the model used to answer
     * some of them with a sentence and no step at all, and the sound did not change.
     */
    static boolean campfireSong(String normalized, String companionName) {
        if (normalized.matches("(?sU).*\\bне\\s+(играй|сыграй|поиграй|надо|нужно)\\b.*")) return false;
        String name = java.util.regex.Pattern.quote(companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е'));
        boolean play = normalized.matches("(?sU).*\\b(сыграй|сгырай|згырай|поиграй|сыграешь|поиграешь|сыграть|поиграть|сбацай|забацай|слабай)\\b.*");
        boolean music = normalized.matches("(?sU).*(гитар|песн|песен|мелоди|музык|кост[её]?р|что[- ]?(нибудь|то)).*");
        boolean bare = normalized.matches("(?sU)\\s*(?:" + name + "[\\s,!]+)?(?:давай\\s+|ну\\s+|а\\s+)?(?:сыграй|поиграй)(?:\\s+(?:нам|мне|пожалуйста))*[\\s.!]*");
        boolean evening = normalized.matches("(?sU).*\\b(посидим|посиди|сядь|садись|присядь|присядем)\\b.{0,20}\\bу\\s+костр.*");
        // «Давай другую мелодию», «следующую песню»: another tune, the verb left out; and a bare «другую» while he plays.
        boolean another = normalized.matches("(?sU).*\\b(другую|другой|следующую|еще\\s+одну|смени)\\b.{0,12}(мелоди|песн|музык).*");
        // A bare change request: «другую», «ещё одну», «ещё песню», with up to two words before it that only soften the order
        // («ну», «давай», «а», «и») — «ну давай другую», «ну, давай другую», «давай ещё одну» reach the guitar the same way.
        boolean anotherBare = normalized.matches("(?sU)\\s*(?:" + name + "[\\s,!]+)?(?:(?:а|и|ну|давай|ещё|еще)[\\s,]+){0,2}"
                + "(?:другую|другой|следующую|(?:ещё|еще)\\s+(?:одну|песню|мелодию|музыку))(?:\\s+(?:мелодию|песню|музыку|пожалуйста|нам|мне))*[\\s.!]*");
        // A comparative or the extreme asked for on its own: «повеселее», «погрустнее», «самую грустную».
        boolean moodBare = normalized.matches("(?sU).*\\b(повеселее|веселее|погрустнее|грустнее|печальнее|тоскливее|пободрее|бодрее|самую\\s+грустную|самую\\s+вес[её]лую)\\b.*");
        // «давай ещё сыграй»: the verb with a word of repetition, not the bare form.
        boolean more = normalized.matches("(?sU).*\\b(ещё|еще)\\s+(сыграй|сгырай|поиграй|сбацай)\\b.*");
        // «Сыграй из майнкрафта», «включи майнкрафт»: standard Minecraft music, whatever the folder holds.
        boolean vanilla = normalized.contains("майнкрафт") || normalized.contains("minecraft");
        boolean vanillaAsk = vanilla && normalized.matches("(?sU).*\\b(включи|включай|вруби|поставь|сыграй|поиграй|сбацай)\\b.*");
        return play && (music || vanilla || !tuneMood(normalized).isEmpty()) || bare || evening || another || anotherBare || moodBare || more || vanillaAsk;
    }

    /**
     * The kind of tune asked for, as the step's item after a space: the lowest («самую грустную»), the nearest lower («погрустнее»),
     * the nearest higher («повеселее»), the highest («самую весёлую»), simply another, standard Minecraft music, or nothing named.
     * The comparative and extreme words are read before the plain mood words, so «самую грустную» is not just a sad tune.
     */
    static String tuneMood(String normalized) {
        // «Из майнкрафта» names the source, not a mood: it wins over a mood word beside it.
        if (normalized.contains("майнкрафт") || normalized.contains("minecraft")) return " vanilla";
        if (normalized.matches("(?sU).*(самую\\s+(грустную|печальную|тоскливую)|\\bсамая\\s+грустная\\b|\\bсамой\\s+грустной\\b).*")) return " saddest";
        if (normalized.matches("(?sU).*(самую\\s+(вес[её]лую|радостную|зажигательную)|\\bсамая\\s+вес[её]лая\\b|\\bсамой\\s+вес[её]лой\\b).*")) return " happiest";
        if (normalized.matches("(?sU).*\\b(погрустнее|грустнее|печальнее|тоскливее)\\b.*")) return " sadder";
        if (normalized.matches("(?sU).*\\b(повеселее|веселее|порадостнее|радостнее|пободрее|бодрее)\\b.*")) return " happier";
        if (normalized.matches("(?sU).*(грустн|печальн|тосклив|спокойн|тих|медленн|лиричн|нежн|задумчив).*")) return " saddest";
        if (normalized.matches("(?sU).*(весел|бодр|задорн|быстр|танцевальн|зажигательн|радостн).*")) return " happiest";
        if (normalized.matches("(?sU).*\\b(другую|другой|следующую|смени)\\b.*")
                || normalized.matches("(?sU).*\\b(ещё|еще)\\s+(одну|песню|мелодию|музыку)\\b.*")) return " next";
        return "";
    }

    /** «companion place remember|go|guard|carry <name>»: the name is the rest of the command and may hold spaces.
     * «companion place forget …» never comes here: the chat answers it from the player's own list. */
    private static PlannedAction placeAction(String command) {
        String rest = command.substring("companion place ".length());
        int cut = rest.indexOf(' ');
        String kind = cut < 0 ? rest : rest.substring(0, cut);
        String name = cut < 0 ? "" : rest.substring(cut + 1);
        return "remember".equals(kind) ? new PlannedAction("remember_place", 0, 1, name)
                : new PlannedAction("go_to_place", 0, 1, name);
    }

    /** The message without the companion's name and a leading «а»/«ну»: what is left is the ask itself. */
    private static String withoutAddress(String normalized, String companionName) {
        String rest = normalized.strip();
        String name = companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').strip();
        if (!name.isEmpty() && rest.startsWith(name)) rest = rest.substring(name.length()).replaceFirst("(?sU)^[\\s\\p{Punct}—]+", "");
        return rest.replaceFirst("(?sU)^(а|ну)\\s+", "");
    }

    /**
     * «Что ты умеешь», «расскажи, что умеешь», «расскажи 10 умений», «назови свои умения»: a question about himself alone, so the
     * count is the only thing that varies. A question about a thing («что ты умеешь делать с медью») is still the model's.
     */
    private static String abilitiesCommand(String normalized, String companionName) {
        String rest = withoutTrailingAddress(withoutAddress(normalized, companionName), companionName);
        String question = "(?sU)(?:что\\s+(?:ты\\s+)?(?:вообще\\s+|сейчас\\s+|такого\\s+)*умеешь(?:\\s+делать)?"
                + "|(?:расскажи|рассказывай|перечисли|назови|скажи|озвучь)\\s*,?\\s*(?:мне\\s+|нам\\s+)?"
                + "(?:что\\s+(?:ты\\s+)?(?:вообще\\s+|сейчас\\s+|такого\\s+)*умеешь(?:\\s+делать)?"
                + "|(?:\\d+\\s+|про\\s+|о\\s+|свои\\s+|своих\\s+|все\\s+|всё\\s+){0,3}умени[а-я]*))"
                + "(?:\\s*,?\\s*пожалуйста)?\\W*";
        if (!rest.matches(question)) return null;
        int count = CompanionAbilities.DEFAULT_COUNT;
        if (rest.matches("(?sU).*\\b(все|всё)\\b.*")) count = CompanionAbilities.size();
        else {
            var digits = java.util.regex.Pattern.compile("\\d+").matcher(rest);
            // «Расскажи 1000000000000 умений» is more than the list, not an arithmetic overflow.
            if (digits.find()) count = CompanionAbilities.clampCount(digits.group());
        }
        return "companion abilities " + count;
    }

    /** «Что ты умеешь, Олег»: the name after the ask is the address, not part of the ask. */
    private static String withoutTrailingAddress(String text, String companionName) {
        String name = companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').strip();
        if (name.isEmpty()) return text;
        return text.replaceFirst("(?sU)[\\s,]+" + java.util.regex.Pattern.quote(name) + "\\W*$", "");
    }

    /** «Забудь всё» and «что ты обо мне помнишь»: only the whole message counts, so «забудь всё и добудь дерево» stays an order. */
    private static String memoryCommand(String normalized, String companionName) {
        String rest = withoutAddress(normalized, companionName);
        if (rest.matches("(?sU)(забудь|забывай)\\s+(все|всю память|все обо мне|обо мне все)\\W*")
                || rest.matches("(?sU)(очисти|сотри|стери)\\s+(свою\\s+)?память\\W*")) return "companion memory forget";
        if (rest.matches("(?sU)что ты (обо мне|про меня) (помнишь|знаешь)\\W*")
                || rest.matches("(?sU)что ты (помнишь|знаешь) (обо мне|про меня)\\W*")
                || rest.matches("(?sU)что ты помнишь\\W*")) return "companion memory show";
        return null;
    }

    /** Every local command but a work request, and the model's plans that start with these, replace the plans in the queue. */
    public static boolean cancelsPlans(String command) {
        // The mode of his own deeds is no order to drop the plans the player gave.
        if (command.equals("companion goal status") || command.equals("companion goal cancel") || command.equals("companion goal defer")) return false;
        if (command.startsWith("companion memory ")) return false;
        // Naming a place is a word, not an order: whatever he is busy with goes on, as with the home.
        if (command.startsWith("companion place remember ")) return false;
        // Forgetting a marker is a word about the player's list too: the walk he is on goes on.
        if (command.startsWith("companion place forget")) return false;
        // «Построй новый портал» answers his offer to lead the player to one: it replaces that walk.
        return !command.startsWith("companion work ") && !command.startsWith("companion autonomy ") || command.startsWith("companion work hunt_mobs ")
                || command.equals("companion work build_portal 1 new");
    }

    /** The reason {@code cancelPendingPlans} gets for a cancel word: the queue ends its tasks as the player's cancel, so a parked smelt is released. */
    public static final String PLAYER_CANCEL = "player_cancel";

    /**
     * «Отмена» and «отмени…» cancel for good; «стоп» and the other stop words only stop, and a smelt parked meanwhile is orphaned for him to collect.
     * «Иди сюда», «стой» and «защищай» give URGENT_ORDER: the dig that is running is put aside for later instead.
     */
    public static String cancelReason(String text, String action) {
        boolean cancel = "companion stop".equals(action) && text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').matches("(?sU).*\\b(отмена|отмени)\\b.*");
        return cancel ? PLAYER_CANCEL : URGENT_COMMANDS.contains(action) ? URGENT_ORDER : "local_command";
    }

    /** The reason for an order that cannot wait for the dig to end («иди сюда», «стой», «защищай»): the dig is put aside for later, not stopped. */
    public static final String URGENT_ORDER = "urgent_order";
    private static final java.util.Set<String> URGENT_COMMANDS = java.util.Set.of("companion summon", "companion stay", "companion guard");
    private static final java.util.Set<String> URGENT_ACTIONS = java.util.Set.of("come", "wait", "protect");

    /** The reason a model's plan that starts with a priority order cancels the queue for. */
    public static String priorityReason(java.util.List<PlannedAction> actions) {
        return URGENT_ACTIONS.contains(actions.getFirst().type()) ? URGENT_ORDER : "priority_player_order";
    }

    public static final String FIGHT_HUSH = "Помолчи, я тут занят мобом.";
    /**
     * In a fight he does not chat: a question of small talk gets FIGHT_HUSH. Whatever asks nothing may be an order and reaches him as
     * always («дай мне меч», «бежим», «иди домой» were hushed — review of PR #28), and so does a question asking for help or a deed.
     */
    public static boolean hushed(boolean fighting, String text) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        return fighting && normalized.contains("?") && !OpenRouterClient.requiresAction(text)
                && !normalized.matches("(?sU).*(убей|убь|убива|бей\\b|атак|мочи\\b|дерис|драт|драк|защищ|защит|охран|прикр|помог|помож|спаси|"
                        + "беги|бежим|валим|отойди|отступ|назад|уходи|уйди|сюда|ко мне|за мной|\\bиди\\b|идем|пошли|пойдем|домой|"
                        + "\\b(по)?стой\\b|\\b(подо)?жди\\b|подожд|не двигайся|замри|останься|лечись|поешь|съешь|осторожн|сзади|крипер|скелет|зомби|паук|\\bмоб|монстр|"
                        // «Дай мне еды» in a fight is a hand-over, not small talk.
                        + "\\b(от|по|пере|вы)?да(й|йте|шь)\\b|\\bкин(ь|ешь)|\\bброс(ь|ай|ишь)\\b|\\bверн(и|ешь)|\\bпринес|\\bдержи\\b).*");
    }

    public static boolean priorityOrder(java.util.List<PlannedAction> actions) {
        return !actions.isEmpty() && (java.util.List.of("stop", "come", "equip_item", "unequip_item", "hunt_mobs", "follow", "wait", "protect", "stop_protect", "enter_portal")
                .contains(actions.getFirst().type())
                || "build_portal".equals(actions.getFirst().type()) && "new".equals(actions.getFirst().item()));
    }
}
