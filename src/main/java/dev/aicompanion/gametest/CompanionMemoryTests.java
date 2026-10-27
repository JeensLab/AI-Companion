package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMemoryCodec;
import dev.aicompanion.client.CompanionMemoryFlow;
import dev.aicompanion.client.CompanionMemoryView;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.PromptMemory;
import dev.aicompanion.entity.CompanionBond;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionHappenings;
import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Chat history belongs to one world session; long-term memory is filled from the talk on the server and reaches the client by sync. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemoryTests {
    private static final String VALID = "{\"memories\":[{\"kind\":\"player_fact\",\"text\":\"Игрок любит железо\"},"
            + "{\"kind\":\"promise\",\"text\":\"Обещал показать шахту\"}],\"call_me\":\"Роб\"}";

    @GameTest(template="empty")
    public static void historyIsClearedOnLogoutAndLateRepliesAreDropped(GameTestHelper h) {
        OpenRouterClient.resetSession();
        long begun = OpenRouterClient.generation();
        h.assertTrue(OpenRouterClient.recordReply(begun, "Первая реплика."), "Reply of the current session was refused");
        h.assertTrue(OpenRouterClient.historySize() == 1, "History did not take the reply: " + OpenRouterClient.historySize());

        OpenRouterClient.resetSession();
        h.assertTrue(OpenRouterClient.historySize() == 0, "History survived the reset: " + OpenRouterClient.historySize());
        h.assertTrue(OpenRouterClient.generation() != begun, "Reset did not start a new generation");

        h.assertTrue(!OpenRouterClient.recordReply(begun, "Поздний ответ."), "Late reply was accepted");
        h.assertTrue(OpenRouterClient.historySize() == 0, "Late reply leaked into the new session: " + OpenRouterClient.historySize());

        h.assertTrue(OpenRouterClient.recordReply(OpenRouterClient.generation(), "Новая реплика."), "New session refused its own reply");
        h.assertTrue(OpenRouterClient.historySize() == 1, "New session history is wrong: " + OpenRouterClient.historySize());
        OpenRouterClient.resetSession();
        h.succeed();
    }

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

        var digest = new CompanionMemoryDigest.Digest(memory.entries(), "", List.of(), List.of(), List.of(), 20, "знакомый", List.of(), List.of());
        h.assertTrue(digest.relevant("железные инструменты", 5).equals(top), "Digest ranks differently from memory");
        h.succeed();
    }

    @GameTest(template="empty", batch="memory_restore")
    public static void restoredRepliesHaveNoDuplicates(GameTestHelper h) {
        List<CompanionMemory.Talk> talk = List.of(new CompanionMemory.Talk(true, "Привет, Олег"), new CompanionMemory.Talk(false, "Привет!"),
                new CompanionMemory.Talk(true, "Пойдём за железом"));
        try {
            OpenRouterClient.resetSession();
            long gen = OpenRouterClient.generation();
            h.assertTrue(!OpenRouterClient.restoreTalk(gen - 1, talk, () -> "system"), "Restore for an old session accepted");
            h.assertTrue(OpenRouterClient.restoreTalk(gen, talk, () -> "system"), "First restore refused");
            h.assertTrue(OpenRouterClient.historySize() == 4, "Restored history is wrong: " + OpenRouterClient.historySize());
            h.assertTrue(!OpenRouterClient.restoreTalk(gen, talk, () -> "system"), "Second restore of one session accepted");
            h.assertTrue(OpenRouterClient.historySize() == 4, "Second restore changed the history: " + OpenRouterClient.historySize());

            OpenRouterClient.resetSession();
            long next = OpenRouterClient.generation();
            OpenRouterClient.recordReply(next, "Привет!");
            h.assertTrue(OpenRouterClient.restoreTalk(next, talk, () -> "system"), "Restore into a session with a reply refused");
            var history = OpenRouterClient.historySnapshot();
            long greetings = history.stream().filter(m -> m.get("content").getAsString().equals("Привет!")).count();
            h.assertTrue(history.size() == 4 && greetings == 1, "Line already said was duplicated: " + history);
            h.succeed();
        } finally {
            OpenRouterClient.resetSession();
        }
    }

    @GameTest(template="empty", batch="memory_survive")
    public static void messageBeforeFirstSyncSurvivesRestore(GameTestHelper h) {
        try {
            OpenRouterClient.resetSession();
            long gen = OpenRouterClient.generation();
            h.assertTrue(OpenRouterClient.recordReply(gen, "Первая реплика после входа"), "Reply refused");
            h.assertTrue(OpenRouterClient.restoreTalk(gen, List.of(new CompanionMemory.Talk(true, "Старый вопрос"),
                    new CompanionMemory.Talk(false, "Старый ответ")), () -> "system"), "Restore refused");
            var history = OpenRouterClient.historySnapshot();
            h.assertTrue(history.size() == 4, "History size is wrong: " + history);
            h.assertTrue(role(history, 0).equals("system") && text(history, 0).equals("system"), "System prompt is not first: " + history);
            h.assertTrue(text(history, 1).equals("Старый вопрос") && text(history, 2).equals("Старый ответ"), "Restored lines are not before the reply: " + history);
            h.assertTrue(text(history, 3).equals("Первая реплика после входа"), "The early reply was lost or moved: " + history);
            h.succeed();
        } finally {
            OpenRouterClient.resetSession();
        }
    }

    @GameTest(template="empty", batch="memory_compress", timeoutTicks=200)
    public static void compressionTakesPendingTalkAndDropsOldGeneration(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        List<JsonArray> requests = new CopyOnWriteArrayList<>();
        List<CompletableFuture<String>> futures = new CopyOnWriteArrayList<>();
        try {
            var bonds = CompanionBonds.get(server);
            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> {
                requests.add(messages);
                var future = new CompletableFuture<String>();
                futures.add(future);
                return future;
            });
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server, owner, true);
            for (int i = 0; i < 11; i++) {
                CompanionMemoryFlow.playerSaid("Реплика игрока " + i, null);
                CompanionMemoryFlow.companionSaid("model", "Ответ " + i);
            }
            h.assertTrue(requests.isEmpty(), "A request was made before enough player lines");
            CompanionMemoryFlow.playerSaid("Реплика игрока 11", null);

            h.runAfterDelay(5, () -> {
                try {
                    h.assertTrue(requests.size() == 1, "Expected one compression request, got " + requests.size());
                    String sent = requests.getFirst().toString();
                    h.assertTrue(sent.contains("Реплика игрока 0") && sent.contains("Ответ 10") && sent.contains("Реплика игрока 11"), "Request does not carry the journal: " + sent);

                    // Lines said while a request is in flight start no second one and outlive the acknowledgement of the first.
                    for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Ещё реплика " + i, null);
                    futures.getFirst().complete("{\"memories\":[{\"kind\":\"player_fact\",\"text\":\"Любит железо\"}],\"call_me\":\"Роб\"}");
                    for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Третья реплика " + i, null);

                    h.runAfterDelay(5, () -> {
                        try {
                            var memory = bonds.memory(owner);
                            h.assertTrue(requests.size() == 2, "Expected two requests in all, none while one was in flight, got " + requests.size());
                            h.assertTrue(memory.entries().stream().anyMatch(e -> e.text().equals("Любит железо")) && memory.callMe().equals("Роб"),
                                    "Condensed memory was not stored: " + memory.entries());
                            var pending = bonds.pendingTalk(owner);
                            h.assertTrue(pending.size() == 24 && pending.getFirst().text().equals("Ещё реплика 0"), "The acknowledgement did not remove exactly the condensed lines: " + pending);
                            String second = requests.get(1).toString();
                            h.assertTrue(second.contains("Ещё реплика 0") && second.contains("Третья реплика 11") && !second.contains("Реплика игрока"), "Second request carries the wrong lines: " + second);
                            OpenRouterClient.resetSession();
                            futures.get(1).complete("{\"memories\":[{\"kind\":\"player_fact\",\"text\":\"Боится пауков\"}]}");

                            h.runAfterDelay(5, () -> {
                                try {
                                    boolean leaked = bonds.memory(owner).entries().stream().anyMatch(e -> e.text().equals("Боится пауков"));
                                    h.assertTrue(!leaked, "A reply of the previous session was stored");
                                    h.assertTrue(bonds.pendingTalk(owner).size() == 24, "A reply of the previous session acknowledged the journal: " + bonds.pendingTalk(owner).size());
                                    h.succeed();
                                } finally {
                                    resetFlow();
                                }
                            });
                        } catch (RuntimeException error) {
                            resetFlow();
                            throw error;
                        }
                    });
                } catch (RuntimeException error) {
                    resetFlow();
                    throw error;
                }
            });
        } catch (RuntimeException error) {
            resetFlow();
            throw error;
        }
    }

    @GameTest(template="empty", batch="memory_off", timeoutTicks=200)
    public static void disabledLongTermMemoryWritesAndCompressesNothing(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        int[] calls = {0};
        try {
            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> {
                calls[0]++;
                return new CompletableFuture<>();
            });
            OpenRouterClient.resetSession();
            var bonds = CompanionBonds.get(server);
            bonds.remember(owner, CompanionMemory.PLAYER_FACT, "Любит железо");
            bonds.rescued(owner, 3);
            h.assertTrue(!CompanionMemoryDigest.of(server, owner, true, 100).equals(CompanionMemoryDigest.Digest.EMPTY), "Control digest is empty");
            h.assertTrue(CompanionMemoryDigest.of(server, owner, false, 100).equals(CompanionMemoryDigest.Digest.EMPTY), "Digest not empty while memory is off");

            CompanionMemoryFlow.bind(server, owner, false);
            for (int i = 0; i < 15; i++) {
                CompanionMemoryFlow.playerSaid("Реплика " + i, null);
                CompanionMemoryFlow.companionSaid("model", "Ответ " + i);
            }
            h.assertTrue(calls[0] == 0, "Compression requested while memory is off: " + calls[0]);
            h.runAfterDelay(5, () -> {
                try {
                    h.assertTrue(calls[0] == 0, "Compression requested while memory is off: " + calls[0]);
                    h.assertTrue(bonds.memory(owner).recentTalk().isEmpty() && bonds.pendingTalk(owner).isEmpty(), "Talk was stored while memory is off");
                    h.succeed();
                } finally {
                    resetFlow();
                }
            });
        } catch (RuntimeException error) {
            resetFlow();
            throw error;
        }
    }

    @GameTest(template="empty", batch="memory_forget", timeoutTicks=200)
    public static void forgetAllClearsMemoryButNotDeeds(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        List<CompletableFuture<String>> futures = new CopyOnWriteArrayList<>();
        List<String> sent = new CopyOnWriteArrayList<>();
        try {
            var bonds = CompanionBonds.get(server);
            bonds.rescued(owner, 3);
            bonds.happened(owner, "rescue", "Спас от зомби", 1000, CompanionHappenings.Importance.HIGH);
            bonds.remember(owner, CompanionMemory.PLAYER_FACT, "Любит железо");
            bonds.recordTalk(owner, true, "Привет");
            bonds.ackPending(owner, bonds.pendingEnd(owner));
            bonds.setCallMe(owner, "Роб");

            CompanionEntityFixture fixture = CompanionEntityFixture.withDeeds(h);
            h.assertTrue(fixture.companion.deedCount() == 2 && fixture.companion.getDeedsForAi().contains("Скрафтил палки."), "Fixture deeds are wrong: " + fixture.companion.getDeedsForAi());
            int scoreBefore = bonds.bond(owner).score();
            var reasonsBefore = bonds.bond(owner).reasons();
            var happeningsBefore = bonds.happenings(owner).all();
            h.assertTrue(scoreBefore != CompanionBond.BASE && !reasonsBefore.isEmpty(), "Bond fixture did not move");

            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> {
                var future = new CompletableFuture<String>();
                futures.add(future);
                sent.add(messages.toString());
                return future;
            });
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server, owner, true);
            long pulledAt = CompanionMemoryView.forgetEpoch();
            var pulled = CompanionMemoryDigest.of(server, owner, true, 2000);
            CompanionMemoryView.deliver(OpenRouterClient.generation(), pulled, false);
            h.assertTrue(!CompanionMemoryView.current().memories().isEmpty(), "View did not take the digest");
            h.assertTrue(OpenRouterClient.restoreTalk(OpenRouterClient.generation(), List.of(new CompanionMemory.Talk(true, "Мой секрет про арбузы"),
                    new CompanionMemory.Talk(false, "Запомнила про арбузы")), () -> "system"), "History fixture was not restored");
            CompanionMemoryFlow.playerSaid("Мой секрет про арбузы", null);
            for (int i = 0; i < 11; i++) CompanionMemoryFlow.playerSaid("Реплика " + i, null);

            h.runAfterDelay(5, () -> {
                try {
                    h.assertTrue(futures.size() == 1 && sent.getFirst().contains("арбуз"), "In-flight compression was not started or lacks the journal: " + sent);
                    long oldEnd = bonds.pendingEnd(owner);

                    long preForget = OpenRouterClient.generation();
                    CompanionMemoryFlow.forget();
                    h.assertTrue(OpenRouterClient.historySnapshot().stream().noneMatch(m -> !"system".equals(m.get("role").getAsString())),
                            "History kept the forgotten talk: " + OpenRouterClient.historySnapshot());
                    h.assertTrue(!OpenRouterClient.recordReply(preForget, "Ответ на забытый вопрос")
                            && OpenRouterClient.historySnapshot().stream().allMatch(m -> "system".equals(m.get("role").getAsString())),
                            "A reply to a request begun before forgetting was stored again: " + OpenRouterClient.historySnapshot());
                    futures.getFirst().complete("{\"memories\":[{\"kind\":\"player_fact\",\"text\":\"Боится пауков\"}],\"call_me\":\"Бро\"}");

                    h.runAfterDelay(5, () -> {
                        try {
                            var after = CompanionBonds.get(server);
                            var memory = after.memory(owner);
                            h.assertTrue(memory.entries().isEmpty() && memory.callMe().isEmpty() && memory.recentTalk().isEmpty() && after.pendingTalk(owner).isEmpty(),
                                    "Memory survived forgetting: " + memory.save());
                            h.assertTrue(after.bond(owner).score() == scoreBefore && after.bond(owner).reasons().equals(reasonsBefore), "Forgetting changed the bond");
                            h.assertTrue(after.happenings(owner).all().equals(happeningsBefore), "Forgetting changed the happenings");
                            h.assertTrue(fixture.companion.deedCount() == 2 && fixture.companion.getDeedsForAi().contains("Не нашёл камень."), "Forgetting touched the deeds");
                            var view = CompanionMemoryView.current();
                            h.assertTrue(view.memories().isEmpty() && view.callMe().isEmpty() && view.score() == scoreBefore && !view.happenings().isEmpty(),
                                    "Client view kept the memory or lost the bond: " + view);
                            CompanionMemoryView.deliver(OpenRouterClient.generation(), pulledAt, pulled, false);
                            CompanionMemoryView.deliver(OpenRouterClient.generation() - 1, CompanionMemoryView.forgetEpoch(), pulled, false);
                            h.assertTrue(CompanionMemoryView.current().memories().isEmpty(), "A digest pulled before forgetting or for another session restored the memory");

                            for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Новая реплика " + i, null);

                            h.runAfterDelay(5, () -> {
                                try {
                                    h.assertTrue(futures.size() == 2, "Compression after forgetting was not started: " + futures.size());
                                    h.assertTrue(sent.get(1).contains("Новая реплика 0") && sent.get(1).contains("Новая реплика 11") && !sent.get(1).contains("арбуз"),
                                            "The request after forgetting carries forgotten talk or lacks the new lines: " + sent);
                                    h.assertTrue(after.pendingTalk(owner).size() == 12, "The journal after forgetting is wrong: " + after.pendingTalk(owner));
                                    // The acknowledgement of the request begun before forgetting arrives late and must not eat lines numbered after it.
                                    after.ackPending(owner, oldEnd);
                                    h.assertTrue(after.pendingTalk(owner).size() == 12, "A late acknowledgement removed lines said after forgetting: " + after.pendingTalk(owner).size());

                                    h.assertTrue("companion memory forget".equals(CompanionLocalCommands.route("Олег, забудь всё", "Олег")), "«забудь всё» not routed");
                                    h.assertTrue("companion memory show".equals(CompanionLocalCommands.route("что ты обо мне помнишь", "Олег")), "«что ты обо мне помнишь» not routed");
                                    h.assertTrue(CompanionLocalCommands.route("забудь всё и добудь дерево", "Олег") == null, "A compound order was taken for forgetting");
                                    h.assertTrue(!CompanionLocalCommands.cancelsPlans("companion memory forget"), "Memory command cancels plans");
                                    h.succeed();
                                } finally {
                                    resetFlow();
                                }
                            });
                        } catch (RuntimeException error) {
                            resetFlow();
                            throw error;
                        }
                    });
                } catch (RuntimeException error) {
                    resetFlow();
                    throw error;
                }
            });
        } catch (RuntimeException error) {
            resetFlow();
            throw error;
        }
    }

    @GameTest(template="empty", batch="memory_logout", timeoutTicks=200)
    public static void pendingTalkSurvivesLogoutAndIsCondensedNextSession(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        List<JsonArray> requests = new CopyOnWriteArrayList<>();
        try {
            var bonds = CompanionBonds.get(server);
            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> {
                requests.add(messages);
                if (requests.size() == 1) return CompletableFuture.failedFuture(new IllegalStateException("provider is down"));
                return CompletableFuture.completedFuture(VALID);
            });
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server, owner, true);
            for (int i = 0; i < 8; i++) {
                CompanionMemoryFlow.playerSaid("Реплика игрока " + i, null);
                CompanionMemoryFlow.companionSaid("model", "Ответ " + i);
            }

            h.runAfterDelay(5, () -> {
                try {
                    h.assertTrue(requests.isEmpty(), "A request was made before the session ended: " + requests.size());
                    var pending = bonds.pendingTalk(owner);
                    h.assertTrue(pending.size() == 16, "The journal does not hold the whole talk: " + pending);
                    h.assertTrue(CompanionMemoryDigest.of(server, owner, true, 100).talk().size() == CompanionMemory.TALK_KEEP, "The digest takes the journal instead of the last lines");
                    var registries = h.getLevel().registryAccess();
                    var reloaded = CompanionBonds.load(bonds.save(new CompoundTag(), registries), registries);
                    h.assertTrue(reloaded.pendingTalk(owner).equals(pending) && reloaded.pendingEnd(owner) == 16, "The journal differs after reload: " + reloaded.pendingTalk(owner));

                    var crowded = new CompanionMemory();
                    for (int i = 0; i < 45; i++) crowded.recordTalk(true, "Строка " + i);
                    h.assertTrue(crowded.pendingTalk().size() == CompanionMemory.PENDING_KEEP && crowded.pendingTalk().getFirst().text().equals("Строка 5")
                            && crowded.pendingEnd() == 45 && crowded.recentTalk().size() == CompanionMemory.TALK_KEEP,
                            "The journal overflow is wrong: " + crowded.pendingTalk().size() + " lines, end " + crowded.pendingEnd());

                    // Logout and login; binding twice in one session must not condense twice.
                    OpenRouterClient.resetSession();
                    CompanionMemoryFlow.unbind();
                    CompanionMemoryFlow.bind(server, owner, true);
                    CompanionMemoryFlow.bind(server, owner, true);

                    h.runAfterDelay(5, () -> {
                        try {
                            h.assertTrue(requests.size() == 1, "Expected one request for the new session, got " + requests.size());
                            String sent = requests.getFirst().toString();
                            h.assertTrue(sent.contains("Реплика игрока 0") && sent.contains("Ответ 7"), "Request does not carry the journal: " + sent);
                            h.assertTrue(bonds.pendingTalk(owner).size() == 16, "A failed request touched the journal: " + bonds.pendingTalk(owner).size());
                            OpenRouterClient.resetSession();
                            CompanionMemoryFlow.unbind();
                            CompanionMemoryFlow.bind(server, owner, true);
                            CompanionMemoryFlow.bind(server, owner, true);

                            h.runAfterDelay(5, () -> {
                                try {
                                    var memory = bonds.memory(owner);
                                    h.assertTrue(requests.size() == 2, "Expected a second request for the next session, got " + requests.size());
                                    h.assertTrue(memory.entries().stream().anyMatch(e -> e.text().equals("Игрок любит железо")) && memory.callMe().equals("Роб"),
                                            "Condensed memory was not stored: " + memory.entries());
                                    h.assertTrue(bonds.pendingTalk(owner).isEmpty(), "The condensed lines stayed in the journal: " + bonds.pendingTalk(owner));
                                    h.assertTrue(CompanionBonds.load(bonds.save(new CompoundTag(), registries), registries).pendingEnd(owner) == 16, "The journal numbering was lost on reload");
                                    h.succeed();
                                } finally {
                                    resetFlow();
                                }
                            });
                        } catch (RuntimeException error) {
                            resetFlow();
                            throw error;
                        }
                    });
                } catch (RuntimeException error) {
                    resetFlow();
                    throw error;
                }
            });
        } catch (RuntimeException error) {
            resetFlow();
            throw error;
        }
    }

    @GameTest(template="empty", batch="memory_events", timeoutTicks=200)
    public static void eventRemarksAndMemoryCommandsStayOutOfSavedTalk(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        try {
            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> new CompletableFuture<>());
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server, owner, true);
            CompanionMemoryFlow.playerSaid("Олег, забудь всё", "companion memory forget");
            CompanionMemoryFlow.playerSaid("Что ты обо мне помнишь", "companion memory show");
            for (String source : List.of("autonomous", "autonomy", "engine_summary", "local_acknowledgement", "local_memory", "unknown")) {
                CompanionMemoryFlow.companionSaid(source, "Реплика из источника " + source);
            }
            for (String source : List.of("model", "clarification", "local_identity")) CompanionMemoryFlow.companionSaid(source, "Слова " + source);
            CompanionMemoryFlow.playerSaid("Привет, Олег", null);

            h.runAfterDelay(5, () -> {
                try {
                    var bonds = CompanionBonds.get(server);
                    var expected = List.of(new CompanionMemory.Talk(false, "Слова model"), new CompanionMemory.Talk(false, "Слова clarification"),
                            new CompanionMemory.Talk(false, "Слова local_identity"), new CompanionMemory.Talk(true, "Привет, Олег"));
                    h.assertTrue(bonds.memory(owner).recentTalk().equals(expected), "The last lines hold remarks or memory commands: " + bonds.memory(owner).recentTalk());
                    h.assertTrue(bonds.pendingTalk(owner).equals(expected), "The journal holds remarks or memory commands: " + bonds.pendingTalk(owner));
                    h.succeed();
                } finally {
                    resetFlow();
                }
            });
        } catch (RuntimeException error) {
            resetFlow();
            throw error;
        }
    }

    @GameTest(template="empty", batch="memory_sync")
    public static void syncDeliversMemoryWhileCompanionUnloaded(GameTestHelper h) {
        try {
            var player = h.makeMockServerPlayerInLevel();
            var spawned = CompanionEvents.getOwnedCompanion(player);
            if (spawned != null) spawned.discard();
            h.assertTrue(CompanionEvents.getOwnedCompanion(player) == null, "The companion is still loaded");
            var server = h.getLevel().getServer();
            UUID owner = player.getUUID();
            long now = player.level().getGameTime();
            var bonds = CompanionBonds.get(server);
            for (int i = 0; i < CompanionMemory.MAX_MEMORIES; i++) {
                bonds.remember(owner, CompanionMemory.PLAYER_FACT, ("Любимое блюдо " + i + ": " + "вкусное и сытное ".repeat(7)).strip());
            }
            for (int i = 0; i < 8; i++) bonds.happened(owner, "k" + i, "Событие " + i, now - i * 1200L, CompanionHappenings.Importance.NORMAL);
            bonds.rescued(owner, 1);
            bonds.setCallMe(owner, "Роб");
            h.assertTrue(bonds.memory(owner).entries().size() == CompanionMemory.MAX_MEMORIES, "Fixture memory is not full");

            List<CompanionMemoryDigest.Digest> got = new ArrayList<>();
            CompanionMemoryView.pull(player, true, got::add);
            h.assertTrue(got.size() == 1, "Pull did not deliver exactly one digest: " + got.size());
            var digest = got.getFirst();
            h.assertTrue(!digest.memories().isEmpty() && digest.memories().size() < CompanionMemory.MAX_MEMORIES, "Memories not trimmed: " + digest.memories().size());
            h.assertTrue(digest.bytes() <= CompanionMemoryDigest.MAX_BYTES, "Digest over the cap: " + digest.bytes());
            h.assertTrue(!digest.tier().isEmpty() && digest.reasons().contains(CompanionBond.Reason.RESCUE.label()), "Bond missing: " + digest.tier() + " " + digest.reasons());
            h.assertTrue(digest.callMe().equals("Роб"), "call_me missing: " + digest.callMe());
            h.assertTrue(digest.happenings().size() == CompanionMemoryDigest.HAPPENINGS_SHOWN, "Wrong number of happenings: " + digest.happenings());
            h.assertTrue(digest.happenings().stream().allMatch(line -> line.matches("(только что|\\d+ мин назад|\\d+ ч назад|\\d+ дн назад): .+")),
                    "A happening has no time phrase: " + digest.happenings());

            List<CompanionMemoryDigest.Digest> off = new ArrayList<>();
            CompanionMemoryView.pull(player, false, off::add);
            h.assertTrue(off.size() == 1 && off.getFirst().equals(CompanionMemoryDigest.Digest.EMPTY), "Digest delivered while memory is off");

            CompanionMemoryView.deliver(OpenRouterClient.generation(), digest, false);
            h.assertTrue(CompanionMemoryView.current().equals(digest), "View did not take the delivered digest");
            h.succeed();
        } finally {
            OpenRouterClient.resetSession();
        }
    }

    private static void resetFlow() {
        CompanionMemoryFlow.useCompleter(null);
        CompanionMemoryFlow.unbind();
        OpenRouterClient.resetSession();
    }

    private static String role(List<JsonObject> history, int index) { return history.get(index).get("role").getAsString(); }

    private static String text(List<JsonObject> history, int index) { return history.get(index).get("content").getAsString(); }

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
        var digest = new CompanionMemoryDigest.Digest(memories, "Шеф", likes, dislikes, talk, 90, "лучший друг", List.of("подарок", "общее дело"), happenings);

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
        String persona = OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша");
        String prompt = OpenRouterClient.buildSystemPrompt(persona, true, "лучший друг");
        int names = prompt.split("Тебя зовут", -1).length - 1;
        h.assertTrue(names == 1, "«Тебя зовут» appears " + names + " times");
        h.assertTrue(prompt.contains("Тебя зовут Гоша"), "Name missing");
        h.assertTrue(prompt.contains("Тон общения по ступени связи «лучший друг»"), "Tier tone missing");
        h.assertTrue(prompt.contains("приказы игрока ты выполняешь при любой ступени"), "Orders-at-any-tier rule missing");
        String quiet = OpenRouterClient.buildSystemPrompt(persona, false, "лучший друг");
        h.assertTrue(!quiet.contains("Тон общения") && !quiet.contains("память о товарище"), "Memory rules leak while memory is off");
        h.succeed();
    }

    private record CompanionEntityFixture(dev.aicompanion.entity.CompanionEntity companion) {
        static CompanionEntityFixture withDeeds(GameTestHelper h) {
            var companion = AICompanionMod.COMPANION.get().create(h.getLevel());
            CompoundTag saved = new CompoundTag();
            companion.addAdditionalSaveData(saved);
            ListTag deeds = new ListTag();
            deeds.add(deed(0, "Скрафтил палки.", true));
            deeds.add(deed(600, "Не нашёл камень.", false));
            saved.put("Deeds", deeds);
            var loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
            loaded.readAdditionalSaveData(saved);
            return new CompanionEntityFixture(loaded);
        }

        private static CompoundTag deed(long tick, String text, boolean ok) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Tick", tick); tag.putString("Text", text); tag.putBoolean("Ok", ok);
            return tag;
        }
    }
}
