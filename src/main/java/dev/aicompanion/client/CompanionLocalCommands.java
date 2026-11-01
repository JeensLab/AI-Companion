package dev.aicompanion.client;

/**
 * The player's short orders that never wait for the model («стоп», «иди за мной», «стой», «иди ко мне», simple work requests).
 * No client classes, so the chat screen and the GameTests route a phrase the same way.
 */
public final class CompanionLocalCommands {
    private CompanionLocalCommands() { }

    /** The local command for the phrase ("companion stop", "companion work gather_stone 8 resources"…), or null for the model. */
    public static String route(String text, String companionName) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        String memory = memoryCommand(normalized, companionName);
        if (memory != null) return memory;
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
        if (heading != null) return heading;
        if (normalized.matches("(?sU).*\\b(стоп|отмена|отмени|остановись)\\b.*")
                || normalized.contains("перестань рубить") || normalized.contains("прекрати рубить")
                || normalized.contains("хватит рубить") || normalized.contains("брось рубить")
                || normalized.contains("положи топор") || normalized.contains("останови добычу")
                || normalized.contains("прекрати добычу") || normalized.contains("отмени задачу")
                || normalized.contains("перестань добывать") || normalized.contains("хватит копать") || normalized.contains("прекрати копать")
                || normalized.contains("перестань строить") || normalized.contains("хватит строить") || normalized.contains("прекрати строить")
                || normalized.contains("останови стройку") || normalized.contains("останови строительство")
                || normalized.contains("останови задачу")) {
            return "companion stop";
        }
        if (normalized.matches("(?sU).*\\b(продолжай|продолжи|возобнови)\\s+(работу|заказ|цель|дело)\\b.*")) return "companion goal resume";
        // His own hints say «скажи «продолжай»»: the bare word, alone or after his name, resumes what waits; «продолжай рассказ» is not it.
        if (normalized.matches("(?sU)\\s*(?:" + java.util.regex.Pattern.quote(companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е')) + "[\\s,!]+)?(?:давай\\s+|ну\\s+)?(?:продолжай|продолжи|продолжаем)[\\s.!]*")) return "companion goal resume";
        if (normalized.matches("(?sU).*\\b(что ты (?:сейчас )?делаешь|чем ты (?:сейчас )?занят|как там (?:наши )?кирки|как идет (?:работа|заказ))\\b.*")) return "companion goal status";
        if (CompanionGoalCommands.pickaxesForTwo(normalized)) return "companion goal pickaxes";
        PlannedAction workAction = CompanionWorkCommands.parse(text, companionName);
        if (workAction != null) return "companion work " + workAction.type() + " " + workAction.count() + " " + (workAction.item().isEmpty() ? "resources" : workAction.item());
        if (normalized.contains(" и ") || normalized.contains("потом") || normalized.contains("затем")
                || normalized.matches("(?s).*\\d.*")) return null;
        // Before «стой»: «зайди в портал, жди там» is an order to go in.
        if (normalized.matches("(?sU).*(?<!\\bне\\s{1,3})\\b(зайди|заходи|войди|входи|шагни|прыгни|пройди|проходи|иди)\\s+(в|через)\\s+(\\p{L}+\\s+)?(портал|незер)\\b.*")) return "companion enter_portal";
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
        return CompanionEquipmentCommands.parse(normalized);
    }

    /** The plan step a local command runs. */
    public static PlannedAction action(String command) {
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
            case "autonomy" -> new PlannedAction("autonomy", 0, 1, parts[2]);
            case "work" -> new PlannedAction(parts[2], 0, Integer.parseInt(parts[3]), parts[4]);
            default -> new PlannedAction(parts[1], 0, 1);
        };
    }

    /** «Забудь всё» and «что ты обо мне помнишь»: only the whole message counts, so «забудь всё и добудь дерево» stays an order. */
    private static String memoryCommand(String normalized, String companionName) {
        String rest = normalized.strip();
        String name = companionName == null ? "" : companionName.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').strip();
        if (!name.isEmpty() && rest.startsWith(name)) rest = rest.substring(name.length()).replaceFirst("(?sU)^[\\s\\p{Punct}—]+", "");
        rest = rest.replaceFirst("(?sU)^(а|ну)\\s+", "");
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
        return !command.startsWith("companion work ") && !command.startsWith("companion autonomy ") || command.startsWith("companion work hunt_mobs ");
    }

    /** The reason {@code cancelPendingPlans} gets for a cancel word: the queue ends its tasks as the player's cancel, so a parked smelt is released. */
    public static final String PLAYER_CANCEL = "player_cancel";

    /** «Отмена» and «отмени…» cancel for good; «стоп» and the other stop words only stop, and a smelt parked meanwhile is orphaned for him to collect. */
    public static String cancelReason(String text, String action) {
        boolean cancel = "companion stop".equals(action) && text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').matches("(?sU).*\\b(отмена|отмени)\\b.*");
        return cancel ? PLAYER_CANCEL : "local_command";
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
        return !actions.isEmpty() && java.util.List.of("stop", "come", "equip_item", "unequip_item", "hunt_mobs", "follow", "wait", "protect", "stop_protect", "enter_portal")
                .contains(actions.getFirst().type());
    }
}
