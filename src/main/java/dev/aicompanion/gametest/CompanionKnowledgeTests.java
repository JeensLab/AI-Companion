package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionKnowledge;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Local knowledge lookup; no cloud calls. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionKnowledgeTests {
    @GameTest(template = "empty")
    public static void questionsFindTheirArticleAndSmallTalkFindsNone(GameTestHelper h) {
        h.assertTrue(CompanionKnowledge.match("Привет, как дела?").isEmpty(), "Small talk pulled an article");
        h.assertTrue(CompanionKnowledge.match("Иди за мной").isEmpty(), "A command pulled an article");
        String enchant = CompanionKnowledge.contextFor(CompanionKnowledge.match("Как зачаровать меч на остроту?"));
        h.assertTrue(enchant.contains("Справка") && enchant.length() < 7000, "Enchanting article missing or too long");
        assertFinds(h, "Расскажи, как пройти майнкрафт спидраном", "guide-speedrun.md");
        assertFinds(h, "Как убить эндер-дракона?", "end-dragon.md");
        assertFinds(h, "Через сколько снова откроется хранилище в камере испытаний?", "trial-chambers.md");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oreQuestionsInAnyCaseFindOresButCraftAndBuildOrdersDoNot(GameTestHelper h) {
        for (String question : List.of("Где найти железо?", "Добудь 10 железа", "Накопай угля", "Добудь руду", "Где найти медь?")) {
            assertFinds(h, question, "ores-heights.md");
        }
        for (String order : List.of("Сделай железную кирку", "Дай мне золотое яблоко",
                "Построй башню высотой 10 блоков", "Подожди у входа в шахту")) {
            h.assertTrue(!names(order).contains("ores-heights.md"), "Ore article pulled by: " + order);
        }
        for (String order : List.of("Можешь быстро пройти к дому?", "Построй крепость", "Можешь убить эндермена?",
                "Положи вещи в хранилище", "Иди в деревню", "Убей зомби", "Бей монстров", "Принеси морковь",
                "Дерни рычаг", "Поставь воронку", "Наруби дерева", "Обменяй со мной вещи", "Убей животное")) {
            h.assertTrue(CompanionKnowledge.match(order).isEmpty(), "Everyday order pulled an article: " + order);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyArticleLoadsWithKeywordsAndBoundedVerifiedBody(GameTestHelper h) {
        h.assertTrue(!CompanionKnowledge.match("зелье незер дракон жители редстоун руда").isEmpty(), "Topic words matched nothing");
        for (var a : CompanionKnowledge.loaded()) {
            h.assertTrue(!a.keywords().isEmpty(), "No keywords in " + a.name());
            h.assertTrue(!a.body().isEmpty() && a.body().length() <= 3200, "Body empty or too long in " + a.name());
            // The model repeats remembered numbers as facts, so an article carries only what the wiki confirms.
            String body = a.body().toLowerCase(Locale.ROOT);
            h.assertTrue(!body.contains("по памяти") && !body.contains("не подтвержд"), "Unverified line in " + a.name());
        }
        h.succeed();
    }

    private static void assertFinds(GameTestHelper h, String question, String article) {
        h.assertTrue(names(question).contains(article), article + " not found for: " + question);
    }

    private static List<String> names(String text) {
        return CompanionKnowledge.match(text).stream().map(CompanionKnowledge.Article::name).toList();
    }
}
