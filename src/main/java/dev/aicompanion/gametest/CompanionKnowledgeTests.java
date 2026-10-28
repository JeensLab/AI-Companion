package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionKnowledge;
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
        h.assertTrue(CompanionKnowledge.contextFor("Привет, как дела?").isEmpty(), "Small talk pulled an article");
        h.assertTrue(CompanionKnowledge.contextFor("Иди за мной").isEmpty(), "A command pulled an article");
        String enchant = CompanionKnowledge.contextFor("Как зачаровать меч на остроту?");
        h.assertTrue(enchant.contains("Справка") && enchant.length() < 7000, "Enchanting article missing or too long");
        String speedrun = CompanionKnowledge.contextFor("Расскажи, как пройти майнкрафт спидраном");
        h.assertTrue(speedrun.contains("Справка"), "Speedrun guide not found");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyArticleLoadsWithKeywordsAndBoundedBody(GameTestHelper h) {
        String article = CompanionKnowledge.contextFor("зелье незер дракон жители редстоун руда");
        h.assertTrue(article.contains("Справка"), "Topic words matched nothing");
        for (var a : CompanionKnowledge.loaded()) {
            h.assertTrue(!a.keywords().isEmpty(), "No keywords in " + a.name());
            h.assertTrue(!a.body().isEmpty() && a.body().length() <= 3200, "Body empty or too long in " + a.name());
        }
        h.succeed();
    }
}
