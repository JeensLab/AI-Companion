package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.client.CompanionAbilities;
import dev.aicompanion.client.CompanionLocalAnswers;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionAutonomy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «Что ты умеешь»: the answer is a short list of the abilities the code really has, built from the code's own closed lists —
 * the plan codec's actions and the autonomy deeds — and it never waits for the model. The registry cannot drift: an action
 * or a deed without a phrase, or a phrase without an action or deed, fails at class load and here, naming the constant.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAbilitiesTests {
    @GameTest(template = "empty")
    public static void fiveAbilitiesAnsweredWithoutTheModel(GameTestHelper h) {
        String command = CompanionLocalCommands.route("что ты умеешь", "Олег");
        h.assertTrue("companion abilities 5".equals(command), "«что ты умеешь» -> " + command);
        String answer = CompanionAbilities.answer(command);
        List<String> items = items(answer);
        h.assertTrue(items.size() == 5, "the answer names " + items.size() + " abilities: " + answer);
        // Every line of the answer is one of the registry's phrases, so no line promises what the code has no action for.
        for (String item : items)
            h.assertTrue(CompanionAbilities.phrases().contains(item), "not a registered ability: «" + item + "» in " + answer);
        h.assertTrue(!answer.contains("лета"), "the answer promises flying: " + answer);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theAnswerIsNotAlwaysTheSameFive(GameTestHelper h) {
        // The player's phrase twice, as the screen answers it: a fixed first five reads back the same list on every ask.
        String phrase = "что ты умеешь";
        String command = CompanionLocalCommands.route(phrase, "Олег");
        h.assertTrue("companion abilities 5".equals(command), "«" + phrase + "» -> " + command);
        Set<List<String>> answers = new HashSet<>();
        for (int draw = 0; draw < 20; draw++) {
            String answer = CompanionLocalAnswers.answer(command, phrase, CompanionDiagnostics.newTrace());
            h.assertTrue(answer != null, "the local answer path skipped «" + phrase + "»");
            List<String> items = items(answer);
            h.assertTrue(items.size() == 5, "the answer names " + items.size() + " abilities: " + answer);
            h.assertTrue(new HashSet<>(items).size() == items.size(), "the answer repeats an ability: " + answer);
            answers.add(List.copyOf(items));
        }
        h.assertTrue(answers.size() >= 2, "20 asks all gave the same five: " + answers.iterator().next());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void differentSeedsDrawDifferentAbilities(GameTestHelper h) {
        // The draw is seeded, so this proof does not depend on luck: the same ask with different sources gives
        // different subsets, and no subset ever names an ability twice.
        Set<Set<String>> subsets = new HashSet<>();
        for (int seed = 0; seed < 20; seed++) {
            String answer = CompanionAbilities.answer("companion abilities 5", new java.util.Random(seed));
            List<String> items = items(answer);
            h.assertTrue(items.size() == 5, "seed " + seed + " names " + items.size() + " abilities: " + answer);
            Set<String> subset = new HashSet<>(items);
            h.assertTrue(subset.size() == items.size(), "seed " + seed + " repeats an ability: " + answer);
            subsets.add(subset);
        }
        h.assertTrue(subsets.size() >= 2, "20 seeded asks all gave the same five");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aSelectionNeverRepeatsAnAbility(GameTestHelper h) {
        for (int seed = 0; seed < 50; seed++) {
            for (int count : new int[]{1, 5, 10, CompanionAbilities.size()}) {
                List<CompanionAbilities.Ability> picked = CompanionAbilities.selection(count, new java.util.Random(seed));
                h.assertTrue(picked.size() == count, "seed " + seed + " asked " + count + " and got " + picked.size());
                Set<String> phrases = new HashSet<>();
                for (CompanionAbilities.Ability ability : picked) phrases.add(ability.phrase());
                h.assertTrue(phrases.size() == picked.size(), "seed " + seed + " repeated an ability at count " + count);
            }
        }
        // A count past the list is still the whole list, drawn once each.
        h.assertTrue(CompanionAbilities.selection(999, new java.util.Random(0)).size() == CompanionAbilities.size(),
                "a count beyond the list was not clamped");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tenAbilitiesOnRequest(GameTestHelper h) {
        String command = CompanionLocalCommands.route("расскажи 10 умений", "Олег");
        h.assertTrue("companion abilities 10".equals(command), "«расскажи 10 умений» -> " + command);
        String answer = CompanionAbilities.answer(command, new java.util.Random(10));
        List<String> items = items(answer);
        h.assertTrue(items.size() == 10, "the answer names " + items.size() + " abilities: " + answer);
        h.assertTrue(new HashSet<>(items).size() == 10, "the answer repeats an ability: " + answer);
        for (String item : items)
            h.assertTrue(CompanionAbilities.phrases().contains(item), "not a registered ability: «" + item + "» in " + answer);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theAskInOtherWordsIsLocalAndOtherQuestionsGoToTheModel(GameTestHelper h) {
        for (String phrase : new String[]{"что ты умеешь", "что умеешь?", "Олег, что ты умеешь", "расскажи, что ты умеешь",
                "перечисли свои умения", "назови 3 умения", "расскажи мне 10 своих умений",
                "расскажи о своих умениях", "что ты умеешь, Олег",
                // The polite word in the middle and the word of repetition: the player says both, and neither is a question
                // about a thing, so the model must not be asked (review 24.11).
                "расскажи, пожалуйста, что ты умеешь", "расскажи пожалуйста свои умения", "что ты ещё умеешь",
                "Олег, что ты еще умеешь?", "расскажи ещё, что ты умеешь"})
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег").startsWith("companion abilities "),
                    "«" + phrase + "» -> " + CompanionLocalCommands.route(phrase, "Олег"));
        for (String phrase : new String[]{"расскажи, пожалуйста, что ты умеешь", "что ты ещё умеешь"})
            h.assertTrue("companion abilities 5".equals(CompanionLocalCommands.route(phrase, "Олег")),
                    "mnimar's phrasing «" + phrase + "» -> " + CompanionLocalCommands.route(phrase, "Олег"));
        for (String phrase : new String[]{"что ты умеешь делать с медью", "Ты умеешь строить дом?", "расскажи про лошадь"})
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег") == null,
                    "«" + phrase + "» -> " + CompanionLocalCommands.route(phrase, "Олег"));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aNumberTooBigToParseStillAnswers(GameTestHelper h) {
        // A player may say any number: «1000000000000 умений» is more than the list, not an error.
        String command = CompanionLocalCommands.route("расскажи 1000000000000 умений", "Олег");
        h.assertTrue(("companion abilities " + CompanionAbilities.size()).equals(command),
                "«расскажи 1000000000000 умений» -> " + command);
        String answer = CompanionAbilities.answer(command);
        h.assertTrue(items(answer).size() == CompanionAbilities.size(),
                "the answer names " + items(answer).size() + " abilities: " + answer);
        // Digits no int can hold either: the answer is still the whole list.
        String longDigits = "9".repeat(30);
        h.assertTrue(("companion abilities " + CompanionAbilities.size()).equals(CompanionLocalCommands.route("расскажи " + longDigits + " умений", "Олег")),
                "«расскажи " + longDigits + " умений» was not answered with the whole list");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theFeaturesOfTheReleasesAreNamedToo(GameTestHelper h) {
        // Features a player asks for by name that shipped before 0.0.5: the snow golem beside the iron one (PR #193),
        // the Nether trip for quartz and netherrack (PR #182), the village buildings (PR #165), and the dragon at the
        // end of the long goal (PR #38). None of them is a new action — they are all already in the codec's list — so
        // only the phrases can leave them out, and «что ты умеешь» would never mention them.
        String all = String.join(" | ", CompanionAbilities.phrases());
        h.assertTrue(all.contains("снеговик"), "the snow golem is not named: " + all);
        h.assertTrue(all.contains("Незер"), "the Nether trip is not named: " + all);
        h.assertTrue(all.contains("кузниц") || all.contains("постройк"), "the village buildings are not named: " + all);
        CompanionAbilities.Ability goal = abilityWithAction("pursue_goal");
        h.assertTrue(goal.phrase().contains("дракон"), "the long goal does not name the dragon: «" + goal.phrase() + "»");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void phrasesPromiseOnlyWhatTheCodeDelivers(GameTestHelper h) {
        CompanionAbilities.Ability goal = abilityWithAction("pursue_goal");
        h.assertTrue(goal.phrase().contains("смогу"), "the long goal does not say how far he goes: «" + goal.phrase() + "»");
        h.assertTrue(!goal.phrase().contains("до конца"), "the long goal claims to be finished: «" + goal.phrase() + "»");
        CompanionAbilities.Ability home = abilityWithAction("set_home");
        h.assertTrue(home.phrase().contains("запоминаю"), "the home is not named as a memory: «" + home.phrase() + "»");
        h.assertTrue(!home.phrase().contains("ставлю"), "the home reads as building a house: «" + home.phrase() + "»");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theChatPathAnswersTheAbilitiesQuestion(GameTestHelper h) {
        // The phrase as a player says it, routed as the chat screen routes it, then answered through the local path the
        // screen uses: the line it shows and the exchange it records for the model. Deleting the abilities branch of
        // that path leaves the phrase to the model, and this test goes red.
        String phrase = "расскажи 3 умения";
        String action = CompanionLocalCommands.route(phrase, "Олег");
        h.assertTrue(action != null, "«" + phrase + "» was not routed locally");
        String answer = CompanionLocalAnswers.answer(action, phrase, CompanionDiagnostics.newTrace(), new java.util.Random(7));
        h.assertTrue(answer != null, "the local answer path skipped «" + phrase + "»");
        h.assertTrue(answer.equals(CompanionAbilities.answer(action, new java.util.Random(7))), "the chat path answers «" + answer + "», not the registry");
        List<com.google.gson.JsonObject> history = OpenRouterClient.historySnapshot();
        h.assertTrue(history.size() >= 2, "the exchange was not recorded for the model: " + history.size() + " entries");
        var recorded = history.subList(history.size() - 2, history.size());
        h.assertTrue("user".equals(recorded.get(0).get("role").getAsString())
                        && phrase.equals(recorded.get(0).get("content").getAsString()),
                "the player's phrase is not the user turn: " + recorded.get(0));
        h.assertTrue("assistant".equals(recorded.get(1).get("role").getAsString())
                        && answer.equals(recorded.get(1).get("content").getAsString()),
                "his answer is not the assistant turn: " + recorded.get(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theRegistryMatchesTheActionsAndDeedsOfTheCode(GameTestHelper h) {
        // The same check the class runs at load: the shipped registry passes it in both directions.
        CompanionAbilities.verify(CompanionAbilities.all());
        Set<String> claimedActions = new HashSet<>();
        Set<String> claimedDeeds = new HashSet<>();
        for (CompanionAbilities.Ability ability : CompanionAbilities.all()) {
            h.assertTrue(!ability.phrase().isBlank() && ability.phrase().length() <= CompanionAbilities.MAX_PHRASE,
                    "not a short phrase: «" + ability.phrase() + "»");
            h.assertTrue(!ability.actions().isEmpty() || !ability.deeds().isEmpty(), "no action or deed behind «" + ability.phrase() + "»");
            for (String action : ability.actions()) {
                h.assertTrue(CompanionPlanCodec.ACTIONS.contains(action),
                        "«" + ability.phrase() + "» names an action the codec refuses: " + action);
                claimedActions.add(action);
            }
            for (String deed : ability.deeds()) {
                h.assertTrue(CompanionAutonomy.deedNames().contains(deed),
                        "«" + ability.phrase() + "» names a deed the autonomy does not have: " + deed);
                claimedDeeds.add(deed);
            }
        }
        for (String action : CompanionPlanCodec.ACTIONS)
            h.assertTrue(claimedActions.contains(action), "the codec accepts " + action + ", but no ability names it");
        for (String deed : CompanionAutonomy.deedNames())
            h.assertTrue(claimedDeeds.contains(deed), "the autonomy has the deed " + deed + ", but no ability names it");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeedWithoutAPhraseFailsNamingIt(GameTestHelper h) {
        List<CompanionAbilities.Ability> probe = withoutDeed(CompanionAbilities.all(), "COLLECT");
        try {
            CompanionAbilities.verify(probe);
            h.fail("A deed without a phrase was accepted: COLLECT");
        } catch (IllegalStateException refused) {
            h.assertTrue(refused.getMessage().contains("COLLECT"), "the refusal does not name the deed: " + refused.getMessage());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aPhraseAboutADeedThatDoesNotExistFailsNamingIt(GameTestHelper h) {
        List<CompanionAbilities.Ability> probe = new ArrayList<>(CompanionAbilities.all());
        probe.add(new CompanionAbilities.Ability("ловлю рыбу", List.of("obtain_item"), List.of("FISH")));
        try {
            CompanionAbilities.verify(probe);
            h.fail("A phrase about the deed FISH was accepted");
        } catch (IllegalStateException refused) {
            h.assertTrue(refused.getMessage().contains("FISH"), "the refusal does not name the deed: " + refused.getMessage());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aPlanActionWithoutAPhraseFailsNamingIt(GameTestHelper h) {
        List<CompanionAbilities.Ability> probe = withoutAction(CompanionAbilities.all(), "store_items");
        try {
            CompanionAbilities.verify(probe);
            h.fail("A plan action without a phrase was accepted: store_items");
        } catch (IllegalStateException refused) {
            h.assertTrue(refused.getMessage().contains("store_items"), "the refusal does not name the action: " + refused.getMessage());
        }
        h.succeed();
    }

    /**
     * A forgotten phrase is this test's failure, not the player's crash: with the fishing PR merged and no phrase for fish, the class
     * failed to load and «что ты умеешь» took the game down (review 24.11). At load the registry is kept as written.
     */
    @GameTest(template = "empty")
    public static void aForgottenPhraseAtLoadStillLeavesAnAnswer(GameTestHelper h) {
        List<CompanionAbilities.Ability> probe = withoutAction(CompanionAbilities.all(), "store_items");
        List<CompanionAbilities.Ability> loaded = CompanionAbilities.loaded(probe);
        h.assertTrue(loaded.equals(probe), "The registry at load is not the one written: " + loaded.size() + " of " + probe.size());
        h.succeed();
    }

    /** The registry with one deed taken out of every ability that names it, as a new deed without a phrase would look. */
    private static List<CompanionAbilities.Ability> withoutDeed(List<CompanionAbilities.Ability> abilities, String deed) {
        List<CompanionAbilities.Ability> probe = new ArrayList<>();
        for (CompanionAbilities.Ability ability : abilities) {
            List<String> deeds = new ArrayList<>(ability.deeds());
            deeds.remove(deed);
            probe.add(new CompanionAbilities.Ability(ability.phrase(), ability.actions(), deeds));
        }
        return probe;
    }

    /** The registry with one plan action taken out of every ability that names it, as a new action without a phrase would look. */
    private static List<CompanionAbilities.Ability> withoutAction(List<CompanionAbilities.Ability> abilities, String action) {
        List<CompanionAbilities.Ability> probe = new ArrayList<>();
        for (CompanionAbilities.Ability ability : abilities) {
            List<String> actions = new ArrayList<>(ability.actions());
            actions.remove(action);
            probe.add(new CompanionAbilities.Ability(ability.phrase(), actions, ability.deeds()));
        }
        return probe;
    }

    /** The ability that names the given plan action, so a test can check what its phrase promises. */
    private static CompanionAbilities.Ability abilityWithAction(String action) {
        for (CompanionAbilities.Ability ability : CompanionAbilities.all())
            if (ability.actions().contains(action)) return ability;
        throw new IllegalStateException("No ability names the action " + action);
    }

    /** The abilities the answer names, as the player reads them: a numbered line each. */
    private static List<String> items(String answer) {
        int at = answer.indexOf(": ");
        String body = at < 0 ? answer : answer.substring(at + 2);
        List<String> items = new ArrayList<>();
        for (String part : body.split("; ")) {
            String item = part.strip().replaceFirst("^\\d+\\)\\s*", "").replaceFirst("\\.$", "");
            if (!item.isEmpty()) items.add(item);
        }
        return items;
    }
}
