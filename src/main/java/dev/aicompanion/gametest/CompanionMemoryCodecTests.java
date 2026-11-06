package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionMemoryCodec;
import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.MemoryTestKit.*;

import java.util.List;
import java.util.Map;

/** The condensing codec: what is accepted and refused from the model, and which memories are picked for a prompt. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemoryCodecTests {
    @GameTest(template="empty")
    public static void codecAcceptsValidMemoryJson(GameTestHelper h) {
        var plain = CompanionMemoryCodec.parse(VALID);
        h.assertTrue(plain.valid() && plain.memories().size() == 2 && plain.rejected() == 0, "Valid answer not accepted: " + plain);
        h.assertTrue(plain.callMe().equals("Роб"), "call_me lost: " + plain.callMe());
        h.assertTrue(plain.memories().getFirst().kind().equals("player_fact") && plain.memories().get(1).kind().equals("promise"), "Kinds changed: " + plain.memories());

        var fenced = CompanionMemoryCodec.parse("```json\n" + VALID + "\n```");
        h.assertTrue(fenced.valid() && fenced.memories().size() == 2 && fenced.callMe().equals("Роб"), "Fenced answer not accepted: " + fenced);

        StringBuilder many = new StringBuilder("{\"memories\":[");
        for (int i = 0; i < 7; i++) many.append(i > 0 ? "," : "").append("{\"kind\":\"joke\",\"text\":\"Шутка про лаву ").append(i).append("\"}");
        var trimmed = CompanionMemoryCodec.parse(many.append("]}").toString());
        h.assertTrue(trimmed.valid() && trimmed.memories().size() == CompanionMemoryCodec.MAX_MEMORIES, "More than five kept: " + trimmed.memories().size());

        h.assertTrue(plain.likes().isEmpty() && plain.dislikes().isEmpty(), "Preferences invented: " + plain);
        var liked = CompanionMemoryCodec.parse("{\"memories\":[],\"likes\":[\"арбузы\",\"  строить  дома \",\"арбузы\"],\"dislikes\":[\"крипер\"]}");
        h.assertTrue(liked.valid() && liked.likes().equals(List.of("арбузы", "строить дома")) && liked.dislikes().equals(List.of("крипер")), "Preferences lost: " + liked);
        h.assertTrue(CompanionMemoryCodec.request(List.of(new CompanionMemory.Talk(true, "Люблю арбузы"))).toString().contains("\\\"likes\\\""), "Prompt does not ask for likes");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void codecRejectsGarbageJson(GameTestHelper h) {
        List<String> garbage = List.of("", "Конечно! Вот память игрока.", "[1,2]", "{\"a\":1}", "{\"memories\":\"x\"}",
                "{\"memories\":[{\"kind\":\"joke\",\"text\":\"ab\"", "{memories:[]}", "{\"memories\":[]} хвост", "{\"memories\":[]}{\"x\":1}");
        for (String reply : garbage) {
            var parsed = CompanionMemoryCodec.parse(reply);
            h.assertTrue(!parsed.valid() && parsed.memories().isEmpty(), "Garbage accepted as valid: " + reply);
        }
        var malformed = CompanionMemoryCodec.parse("{\"memories\":[1,\"x\",{\"kind\":\"joke\"},{\"text\":\"a\"},{\"kind\":5,\"text\":\"a\"},null]}");
        h.assertTrue(malformed.valid() && malformed.memories().isEmpty() && malformed.rejected() == 6, "Malformed items not rejected: " + malformed);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void codecRejectsDangerousJson(GameTestHelper h) {
        String json = "{\"memories\":["
                + "{\"kind\":\"player_fact\",\"text\":\"Ключ sk-abcdefgh12345678 лежит дома\"},"
                + "{\"kind\":\"player_fact\",\"text\":\"Дом стоит на 100 64 -200\"},"
                + "{\"kind\":\"shared_moment\",\"text\":\"Нашли minecraft:diamond в шахте\"},"
                + "{\"kind\":\"secret\",\"text\":\"Что-то тайное\"},"
                + "{\"kind\":\"joke\",\"text\":\"" + "я".repeat(161) + "\"},"
                + "{\"kind\":\"joke\",\"text\":\"   \"},"
                + "{\"kind\":\"promise\",\"text\":\"Обещал сделать мост\"}],"
                + "\"call_me\":\"sk-abcdefgh12345678\"}";
        var parsed = CompanionMemoryCodec.parse(json);
        h.assertTrue(parsed.valid() && parsed.memories().size() == 1 && parsed.memories().getFirst().text().equals("Обещал сделать мост"),
                "Only the clean entry should survive: " + parsed.memories());
        h.assertTrue(parsed.rejected() == 6, "Rejected count is wrong: " + parsed.rejected());
        h.assertTrue(parsed.callMe().isEmpty(), "Sensitive call_me accepted: " + parsed.callMe());

        var prefs = CompanionMemoryCodec.parse("{\"memories\":[],\"likes\":[\"sk-abcdefgh12345678\",\"minecraft:stone\",\"" + "я".repeat(41) + "\",5,null,\"пароль: abc\","
                + "\"л1\",\"л2\",\"л3\",\"л4\",\"л5\",\"л6\",\"л7\"],\"dislikes\":\"паук\"}");
        h.assertTrue(prefs.valid() && prefs.likes().equals(List.of("л1", "л2", "л3", "л4", "л5", "л6")) && prefs.dislikes().isEmpty(),
                "Dangerous or surplus preferences survived: " + prefs);

        JsonArray request = CompanionMemoryCodec.request(List.of(new CompanionMemory.Talk(true, "мой ключ sk-abcdefgh1234567890XYZ"),
                new CompanionMemory.Talk(false, "Принял")));
        String sent = request.toString();
        h.assertTrue(!sent.contains("sk-abcdefgh") && sent.contains("Игрок:") && sent.contains("Компаньон: Принял"), "Transcript not masked: " + sent);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void relevantPicksTopFive(GameTestHelper h) {
        var memory = new CompanionMemory();
        memory.remember(CompanionMemory.PLAYER_FACT, "Любит железо в больших количествах");
        memory.remember(CompanionMemory.PLAYER_FACT, "Ищет железную руду на холмах");
        for (int i = 0; i < 3; i++) memory.remember(CompanionMemory.PLAYER_FACT, "Часто носит с собой яблоки");
        memory.remember(CompanionMemory.PROMISE, "Обещал построить мост");
        for (int i = 0; i < 8; i++) memory.remember(CompanionMemory.JOKE, "Смеялись над зелёным ящиком номер " + i);
        h.assertTrue(memory.entries().size() == 12, "Fixture is wrong: " + memory.entries().size());

        var top = memory.relevant("железные инструменты", 5);
        h.assertTrue(top.size() == 5, "Not five: " + top.size());
        h.assertTrue(top.subList(0, 2).stream().allMatch(e -> e.text().contains("желез")), "Matching memories not first: " + top);
        h.assertTrue(top.get(2).text().contains("яблоки"), "Heaviest memory not next: " + top);
        h.assertTrue(top.get(3).kind().equals(CompanionMemory.PROMISE), "Promise not ahead of the rest: " + top);

        var digest = new CompanionMemoryDigest.Digest(memory.entries(), "", List.of(), List.of(), List.of(), 20, "знакомый", List.of(), List.of(), Map.of());
        h.assertTrue(digest.relevant("железные инструменты", 5).equals(top), "Digest ranks differently from memory");
        h.succeed();
    }
}
