package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionAbilities;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «Что ты умеешь»: the answer is a short list of the abilities the code really has, built from the plan codec's own set of
 * actions, and it never waits for the model. The registry cannot drift: an action the codec accepts without a phrase, or a
 * phrase without an action, fails here.
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
        h.assertTrue(answer.contains("добываю"), "the answer lost the gathering: " + answer);
        h.assertTrue(!answer.contains("лета"), "the answer promises flying: " + answer);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tenAbilitiesOnRequest(GameTestHelper h) {
        String command = CompanionLocalCommands.route("расскажи 10 умений", "Олег");
        h.assertTrue("companion abilities 10".equals(command), "«расскажи 10 умений» -> " + command);
        String answer = CompanionAbilities.answer(command);
        List<String> items = items(answer);
        h.assertTrue(items.size() == 10, "the answer names " + items.size() + " abilities: " + answer);
        h.assertTrue(items.get(9).equals(CompanionAbilities.phrases().get(9)), "the tenth ability is not the tenth of the registry: " + items.get(9));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theAskInOtherWordsIsLocalAndOtherQuestionsGoToTheModel(GameTestHelper h) {
        for (String phrase : new String[]{"что ты умеешь", "что умеешь?", "Олег, что ты умеешь", "расскажи, что ты умеешь",
                "перечисли свои умения", "назови 3 умения", "расскажи мне 10 своих умений"})
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег").startsWith("companion abilities "),
                    "«" + phrase + "» -> " + CompanionLocalCommands.route(phrase, "Олег"));
        for (String phrase : new String[]{"что ты умеешь делать с медью", "Ты умеешь строить дом?", "расскажи про лошадь"})
            h.assertTrue(CompanionLocalCommands.route(phrase, "Олег") == null,
                    "«" + phrase + "» -> " + CompanionLocalCommands.route(phrase, "Олег"));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theRegistryMatchesTheActionsTheCodecAccepts(GameTestHelper h) {
        Set<String> claimed = new HashSet<>();
        for (CompanionAbilities.Ability ability : CompanionAbilities.all()) {
            h.assertTrue(!ability.phrase().isBlank() && ability.phrase().length() <= CompanionAbilities.MAX_PHRASE,
                    "not a short phrase: «" + ability.phrase() + "»");
            h.assertTrue(!ability.actions().isEmpty(), "no action behind «" + ability.phrase() + "»");
            for (String action : ability.actions()) {
                h.assertTrue(CompanionPlanCodec.ACTIONS.contains(action),
                        "«" + ability.phrase() + "» names an action the codec refuses: " + action);
                claimed.add(action);
            }
        }
        for (String action : CompanionPlanCodec.ACTIONS)
            h.assertTrue(claimed.contains(action), "the codec accepts " + action + ", but no ability names it");
        h.succeed();
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
