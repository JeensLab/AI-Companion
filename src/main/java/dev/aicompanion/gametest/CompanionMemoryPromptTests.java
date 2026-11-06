package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionMemoryCodec;
import dev.aicompanion.client.CompanionMemoryView;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.PromptMemory;
import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** How memory reaches the prompt: the section size, the persona rules and the way a recall is told. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemoryPromptTests {
    /** A companion that was never spawned, loaded from a save with two crafted deeds. */
    @GameTest(template="empty")
    public static void snapshotWithinLimitWhenMemoryAndBufferFull(GameTestHelper h) {
        List<CompanionMemory.Entry> memories = new ArrayList<>();
        for (int i = 0; i < CompanionMemory.MAX_MEMORIES; i++) {
            memories.add(new CompanionMemory.Entry(CompanionMemory.PLAYER_FACT, ("Факт " + i + " ").repeat(40).substring(0, CompanionMemory.MAX_TEXT), 1 + i % 5));
        }
        List<CompanionMemory.Talk> talk = new ArrayList<>();
        for (int i = 0; i < 6; i++) talk.add(new CompanionMemory.Talk(i % 2 == 0, "Реплика номер " + i));
        List<String> happenings = new ArrayList<>();
        for (int i = 0; i < 6; i++) happenings.add(("Давно: событие " + i + " ").repeat(20).substring(0, 170));
        happenings.add("только что: поделились едой");
        List<String> likes = new ArrayList<>(), dislikes = new ArrayList<>();
        for (int i = 0; i < CompanionMemory.MAX_PREFERENCES; i++) {
            likes.add(("нравится " + i + " ").repeat(30).substring(0, CompanionMemory.MAX_TEXT));
            dislikes.add(("не нравится " + i + " ").repeat(30).substring(0, CompanionMemory.MAX_TEXT));
        }
        var digest = new CompanionMemoryDigest.Digest(memories, "Шеф", likes, dislikes, talk, 90, "лучший друг", List.of("подарок", "общее дело"), happenings, Map.of());

        String section = PromptMemory.section(digest, "факт");
        h.assertTrue(section.length() <= 2500,
                "Section is " + section.length() + " chars, over the limit");
        h.assertTrue(section.contains("ваши отношения=лучший друг") && section.contains("подарок"), "Relationship lost: " + section);
        h.assertTrue(section.contains("только что: поделились едой"), "Latest happening lost: " + section);
        h.assertTrue(section.contains("что ты помнишь о товарище=") && section.contains("Факт"), "Memories lost entirely: " + section);
        h.assertTrue(PromptMemory.section(CompanionMemoryDigest.Digest.EMPTY, "").isEmpty(), "Empty digest adds text");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void systemPromptNamesCompanionOnce(GameTestHelper h) {
        String persona = OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша", Map.of());
        String prompt = OpenRouterClient.buildSystemPrompt(persona, true, "лучший друг");
        int names = prompt.split("Тебя зовут", -1).length - 1;
        h.assertTrue(names == 1, "«Тебя зовут» appears " + names + " times");
        h.assertTrue(prompt.contains("Тебя зовут Гоша"), "Name missing");
        h.assertTrue(prompt.contains("Тон общения по ступени связи «лучший друг»"), "Tier tone missing");
        h.assertTrue(prompt.contains("приказы игрока ты выполняешь при любой ступени"), "Orders-at-any-tier rule missing");
        String quiet = OpenRouterClient.buildSystemPrompt(persona, false, "лучший друг");
        h.assertTrue(!quiet.contains("Тон общения") && !quiet.contains("память о товарище"), "Memory rules leak while memory is off");
        h.assertTrue(quiet.contains("Разговаривай как приятель за совместной игрой"), "The persona lost its buddy base while memory is off");
        String acquaintance = OpenRouterClient.buildSystemPrompt(persona, true, "знакомый");
        int rule = acquaintance.indexOf("Тон общения");
        h.assertTrue(rule >= 0 && !acquaintance.contains("вежливо") && acquaintance.substring(rule).contains("приятел"),
                "The acquaintance tone is polite or not buddy-like: " + (rule < 0 ? "no tone rule" : acquaintance.substring(rule)));
        h.succeed();
    }

    @GameTest(template="empty")
    public static void systemPromptKeepsWorkProgressOutOfSmallTalk(GameTestHelper h) {
        String prompt = OpenRouterClient.buildSystemPrompt(OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша", Map.of()), true, "лучший друг");
        h.assertTrue(prompt.contains("справка для тебя, а не тема разговора") && prompt.contains("только если товарищ сам спросил"),
                "Nothing stops him reporting his work unasked");
        h.assertTrue(prompt.contains("не говори, что продолжаешь рубить"), "The rule against stale work claims is lost");
        h.succeed();
    }

    /** Issue #70: he called a hoglin peaceful, and hoglins are no monsters in the snapshot of threats. */
    @GameTest(template="empty")
    public static void systemPromptDoesNotCallHoglinsPeaceful(GameTestHelper h) {
        String prompt = OpenRouterClient.buildSystemPrompt(OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша", Map.of()), true, "лучший друг");
        h.assertTrue(prompt.contains("хоглины (кабаны в Незере) нападают сами"), "Nothing tells him hoglins are enemies");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void recallReadsCleanlyWithoutAddressRepeat(GameTestHelper h) {
        var memory = new CompanionMemory();
        for (String fact : List.of("Любит железо.", "Просит звать его Роб.", "Боится пауков!", "Роб строит базу у реки")) {
            memory.remember(CompanionMemory.PLAYER_FACT, fact);
        }
        h.assertTrue(memory.entries().size() == 4, "Fixture is wrong: " + memory.entries());
        var digest = new CompanionMemoryDigest.Digest(memory.entries(), "Роб", List.of("яблоки", "арбузы."), List.of(), List.of(), 20, "знакомый", List.of(), List.of(), Map.of());

        String told = CompanionMemoryView.recall(digest);
        h.assertTrue(!told.contains(".;") && !told.contains("..") && !told.contains("!;"), "Punctuation doubled up: " + told);
        // Review of PR #19: «звать тебя Серый; У игрока есть пёс» — one sentence per memory, the likes too.
        h.assertTrue(!told.contains(";") && told.contains(". Боится пауков") && told.contains(". Тебе нравится: яблоки"), "Not told in sentences: " + told);
        h.assertTrue(!told.contains("Просит звать"), "The address was told twice: " + told);
        h.assertTrue(told.contains("ты просил звать тебя Роб") && told.contains("Любит железо") && told.contains("Боится пауков")
                && told.contains("Роб строит базу у реки") && told.contains("арбузы"), "Something real was lost: " + told);

        var shown = digest.relevant("", 10);
        h.assertTrue(shown.size() == 3 && shown.stream().noneMatch(e -> e.text().startsWith("Просит звать")), "The address repeat was ranked: " + shown);
        h.assertTrue(CompanionMemory.repeatsAddress("Называет себя Робом", "Роб"), "«Называет себя Робом» is not a repeat");
        h.assertTrue(CompanionMemory.repeatsAddress("Просит звать его Фёдором", "Федор"), "ё and е differ in the name");
        h.assertTrue(!CompanionMemory.repeatsAddress("Называет себя Робом", ""), "A repeat is found while no name is set");
        h.assertTrue(!CompanionMemory.repeatsAddress("Роб строит базу у реки", "Роб"), "A name without a word of address is a repeat");

        String sent = CompanionMemoryCodec.request(List.of(new CompanionMemory.Talk(true, "Привет"))).toString();
        h.assertTrue(sent.contains("Не дублируй в memories") && sent.contains("верно только сейчас") && sent.contains("уже в likes или dislikes"),
                "The condensing prompt lacks the new rules: " + sent);
        h.succeed();
    }
}
