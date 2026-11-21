package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionAutonomy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The closed list of what the player can ask him to do, kept honest by the code's own closed lists: every ability names
 * plan actions the codec accepts or deeds the autonomy can take up, and every accepted action and every deed belongs to
 * an ability. A new plan action or deed without a phrase fails this class at load and the abilities GameTest, naming the
 * constant; a phrase about something the code has no action or deed for fails the same way, so the list cannot drift
 * from the code. «Что ты умеешь» and «расскажи 10 умений» are answered from it without a model request (idea 4NTZD,
 * issue #105).
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
            new Ability("раскладываю вещи по сундукам и ношу их в дом", List.of("store_items", "take_items", "sort_chests"),
                    List.of("STORE", "TAKE_WOOD", "TAKE_STONE")),
            new Ability("развожу животных и охочусь на них", List.of("breed_animals", "hunt_animals"), List.of("HUNT")),
            new Ability("стригу овец, дою коров, собираю яйца и мёд", List.of("obtain_item"), List.of()),
            new Ability("бьюсь с монстрами и охраняю тебя", List.of("hunt_mobs", "protect", "stop_protect"), List.of()),
            new Ability("хожу за тобой, жду на месте и прихожу на зов", List.of("follow", "wait", "come", "wander", "stop"), List.of()),
            new Ability("запоминаю места, вожу тебя к ним и ставлю дом", List.of("remember_place", "go_to_place", "set_home"), List.of()),
            new Ability("строю портал, генератор булыжника и голема", List.of("build_portal", "build_cobble_gen", "build_golem"), List.of()),
            new Ability("иду в нужную сторону и захожу в портал", List.of("walk_heading", "enter_portal"), List.of()),
            new Ability("надеваю снаряжение, чиню вещи и снимаю чары", List.of("equip_item", "unequip_item", "repair_item", "disenchant_item"), List.of()),
            new Ability("даю тебе вещи и ставлю сундук", List.of("give_items", "give_wood", "place_block"), List.of()),
            new Ability("ищу торговцев и торгую с ними", List.of("scout_traders", "trade_items"), List.of()),
            new Ability("берусь за долгую цель и довожу её до конца", List.of("pursue_goal"), List.of()),
            new Ability("играю на гитаре у костра", List.of("campfire_song"), List.of()));

    private static final List<Ability> ALL = verify(DECLARED);

    private CompanionAbilities() { }

    /** The registry, in the order the answer names it. */
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
        int asked = count(command);
        List<Ability> shown = ALL.subList(0, Math.min(Math.max(asked, 1), ALL.size()));
        StringBuilder text = new StringBuilder("Умею вот что: ");
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) text.append("; ");
            text.append(i + 1).append(") ").append(shown.get(i).phrase());
        }
        return text.append('.').toString();
    }

    private static int count(String command) {
        var matcher = java.util.regex.Pattern.compile("companion abilities\\s+(\\d+)").matcher(command == null ? "" : command);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : DEFAULT_COUNT;
    }

    /**
     * Both directions against the code: a phrase without a real plan action or deed, an action the codec does not accept,
     * or a deed the autonomy does not have, is a bug of this file, not a plan the codec would refuse later. It fails at
     * class load, before any answer is built; the GameTest calls it again on the shipped list and on probe lists.
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
