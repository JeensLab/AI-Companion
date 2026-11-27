package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionAutonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The closed list of what the player can ask him to do, kept honest by the code's own closed lists: every ability names
 * plan actions the codec accepts or deeds the autonomy can take up, and every accepted action and every deed belongs to
 * an ability. A new plan action or deed without a phrase fails the abilities GameTest, naming the constant (in the game
 * it is only logged); a phrase about something the code has no action or deed for fails the same way, so the list cannot drift
 * from the code. «Что ты умеешь» and «расскажи 10 умений» are answered from it without a model request, as a random
 * selection drawn without repeats, so two asks do not read back the same five (idea 4NTZD, issue #105).
 *
 * Scope: everything the player can ASK him to do — the plan codec's actions and the autonomy deeds
 * ({@link CompanionAutonomy.Deed}). The direct commands only reword those same actions, switch the deeds on and off, or
 * ask a question (abilities, memory, goal status), so they add no capability of their own. Passive behaviour — the health
 * bar, damage numbers, his gaze, idle murmurs, music by the fire — is not something the player asks for and is
 * deliberately not an ability.
 */
public final class CompanionAbilities {
    /**
     * One ability the player may hear about: a short phrase, the plan actions that really do it, and the autonomy deeds
     * that do it without an order.
     */
    public record Ability(String phrase, List<String> actions, List<String> deeds) { }

    /** «Что ты умеешь» names five: the headline works, one short phrase each. */
    public static final int DEFAULT_COUNT = 5;
    /** An ability is one phrase of a list, not a paragraph: a longer one is a wall of text. */
    public static final int MAX_PHRASE = 70;

    /**
     * Headline order: what a player asks about first comes first. An action may serve two phrases (obtain_item is both the
     * gathering of a named thing and the chores a tool in hand does), and every action of the codec and every deed of the
     * autonomy is named at least once.
     */
    private static final List<Ability> DECLARED = List.of(
            new Ability("добываю дерево, камень, землю, руду и разные вещи",
                    List.of("gather_wood", "gather_stone", "gather_dirt", "gather_loose", "mine_ore", "obtain_item", "pick_up"),
                    List.of("WOOD", "STONE", "COAL", "IRON")),
            new Ability("делаю вещи и плавлю руду в печи", List.of("craft_item", "smelt_items"),
                    List.of("PICK", "COOK", "SMELT_IRON", "IRON_PICK", "COLLECT")),
            new Ability("строю дом и загон для животных", List.of("build_house", "build_pen"), List.of()),
            new Ability("делаю грядки, собираю урожай и компост", List.of("farm_crops", "harvest_crops", "use_bone_meal", "compost_items"), List.of()),
            new Ability("раскладываю вещи по сундукам и забираю их оттуда", List.of("store_items", "take_items", "sort_chests"),
                    List.of("STORE", "TAKE_WOOD", "TAKE_STONE")),
            new Ability("развожу животных и охочусь на них", List.of("breed_animals", "hunt_animals"), List.of("HUNT")),
            new Ability("стригу овец, дою коров, собираю яйца и мёд", List.of("obtain_item"), List.of()),
            new Ability("бьюсь с монстрами и охраняю тебя", List.of("hunt_mobs", "protect", "stop_protect"), List.of()),
            new Ability("хожу за тобой, жду на месте и прихожу на зов", List.of("follow", "wait", "come", "wander", "stop"), List.of()),
            new Ability("запоминаю места и твой дом, вожу тебя туда", List.of("remember_place", "go_to_place", "set_home"), List.of()),
            new Ability("строю портал, генератор булыжника и голема", List.of("build_portal", "build_cobble_gen", "build_golem"), List.of()),
            new Ability("иду в нужную сторону и захожу в портал", List.of("walk_heading", "enter_portal"), List.of()),
            new Ability("веду к крепости Края по оку и вставляю очи в портал", List.of("walk_heading", "open_end_portal"), List.of()),
            new Ability("рыбачу удочкой", List.of("fish"), List.of()),
            new Ability("ищу и приручаю кота, пса или попугая", List.of("tame_cat", "tame_dog", "tame_parrot"), List.of()),
            new Ability("рассказываю о питомцах и где их видел", List.of("cat_status", "dog_status", "parrot_status", "pet_location"), List.of()),
            new Ability("нахожу своих питомцев и зову их за собой", List.of("find_cat", "find_dog", "find_parrot", "follow_cat", "follow_dog", "follow_parrot"), List.of()),
            new Ability("оставляю питомцев сидеть здесь или дома", List.of("sit_cat", "sit_dog", "sit_parrot"), List.of()),
            new Ability("надеваю снаряжение, чиню вещи и снимаю чары", List.of("equip_item", "unequip_item", "repair_item", "disenchant_item"), List.of()),
            new Ability("даю тебе вещи и ставлю сундук", List.of("give_items", "give_wood", "place_block"), List.of()),
            new Ability("ищу торговцев и торгую с ними", List.of("scout_traders", "trade_items"), List.of()),
            new Ability("берусь за долгую цель и иду к ней, докуда смогу дойти", List.of("pursue_goal"), List.of()),
            new Ability("играю на гитаре у костра", List.of("campfire_song"), List.of()));

    private static final List<Ability> ALL = loaded(DECLARED);

    /** The source of an answer the player did not seed: the same ask twice must not read back the same five. */
    private static final Random RANDOM = new Random();

    private CompanionAbilities() { }

    /** The registry, in its declared order; the answer draws from it in any order. */
    public static List<Ability> all() { return ALL; }

    /** The phrases alone, in that order: what the answer may say. */
    public static List<String> phrases() {
        List<String> phrases = new ArrayList<>();
        for (Ability ability : ALL) phrases.add(ability.phrase());
        return List.copyOf(phrases);
    }

    /** How many abilities there are: «расскажи все умения» shows them all. */
    public static int size() { return ALL.size(); }

    /** The answer to the local command «companion abilities <count>»: that many abilities, one short phrase each. */
    public static String answer(String command) {
        return answer(command, RANDOM);
    }

    /**
     * The same answer from a given source, so a test can hold the draw still. Two asks must not read back the same
     * five: the registry is drawn at random and a phrase never appears twice in one answer.
     */
    public static String answer(String command, Random random) {
        List<Ability> shown = selection(count(command), random);
        StringBuilder text = new StringBuilder("Умею вот что: ");
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) text.append("; ");
            text.append(i + 1).append(") ").append(shown.get(i).phrase());
        }
        return text.append('.').toString();
    }

    /** That many abilities drawn from the registry without repeats: a shuffle of the whole list, cut to the count. */
    public static List<Ability> selection(int count, Random random) {
        List<Ability> pool = new ArrayList<>(ALL);
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, Math.max(1, Math.min(ALL.size(), count))));
    }

    /**
     * A count the player said, clamped to the list: any number of digits means at most all of them, and no parse can
     * overflow on «расскажи 1000000000000 умений». «0 умений» is still one line rather than none.
     */
    public static int clampCount(String digits) {
        if (digits == null || digits.isEmpty()) return DEFAULT_COUNT;
        String significant = digits.replaceFirst("^0+(?!$)", "");
        // Longer than any count the list can hold: all of it, and Integer.parseInt never sees the long digits.
        if (significant.length() > 9) return ALL.size();
        return Math.max(1, Math.min(ALL.size(), Integer.parseInt(significant)));
    }

    private static int count(String command) {
        var matcher = java.util.regex.Pattern.compile("companion abilities\\s+(\\d+)").matcher(command == null ? "" : command);
        return matcher.find() ? clampCount(matcher.group(1)) : DEFAULT_COUNT;
    }

    /**
     * The registry as the game loads it: a mismatch with the code is written to the log, and the list stays as written. A
     * throw here failed the class, and «что ты умеешь» took the game down (review 24.11: fish merged without its phrase).
     */
    public static List<Ability> loaded(List<Ability> declared) {
        try {
            return verify(declared);
        } catch (IllegalStateException mismatch) {
            dev.aicompanion.AICompanionMod.LOGGER.error("AI Companion: {}", mismatch.getMessage());
            return List.copyOf(declared);
        }
    }

    /**
     * Both directions against the code: a phrase without a real plan action or deed, an action the codec does not accept,
     * or a deed the autonomy does not have, is a bug of this file, not a plan the codec would refuse later. The abilities
     * GameTest runs it on the shipped list and on probe lists and fails naming the constant; the game only logs it (loaded).
     */
    public static List<Ability> verify(List<Ability> declared) {
        Set<String> claimedActions = new HashSet<>();
        Set<String> claimedDeeds = new HashSet<>();
        List<String> knownDeeds = CompanionAutonomy.deedNames();
        for (Ability ability : declared) {
            if (ability.phrase().isBlank() || ability.phrase().length() > MAX_PHRASE)
                throw new IllegalStateException("CompanionAbilities: not a short phrase: «" + ability.phrase() + "»");
            if (ability.actions().isEmpty() && ability.deeds().isEmpty())
                throw new IllegalStateException("CompanionAbilities: no action or deed behind «" + ability.phrase() + "»");
            for (String action : ability.actions()) {
                if (!CompanionPlanCodec.ACTIONS.contains(action))
                    throw new IllegalStateException("CompanionAbilities: «" + ability.phrase() + "» names an action the codec refuses: " + action);
                claimedActions.add(action);
            }
            for (String deed : ability.deeds()) {
                if (!knownDeeds.contains(deed))
                    throw new IllegalStateException("CompanionAbilities: «" + ability.phrase() + "» names a deed the autonomy does not have: " + deed);
                claimedDeeds.add(deed);
            }
        }
        for (String action : CompanionPlanCodec.ACTIONS)
            if (!claimedActions.contains(action))
                throw new IllegalStateException("CompanionAbilities: the codec accepts " + action + ", but no ability names it");
        for (String deed : knownDeeds)
            if (!claimedDeeds.contains(deed))
                throw new IllegalStateException("CompanionAbilities: the autonomy has the deed " + deed + ", but no ability names it");
        return List.copyOf(declared);
    }
}
