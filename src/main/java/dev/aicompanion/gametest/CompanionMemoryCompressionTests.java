package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMemoryFlow;
import dev.aicompanion.client.CompanionMemoryView;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionBond;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionHappenings;
import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.MemoryTestKit.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Long-term memory is filled from the talk on the server: the condensing, its failures, the off switch and forgetting. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemoryCompressionTests {
    private static final String CUT_OFF = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"memories\\\":[\"}}]}";

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

    @GameTest(template="empty", batch="memory_unfinished", timeoutTicks=200)
    public static void unfinishedCompressionReplyKeepsTheJournal(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        int[] asked = {0};
        try {
            var bonds = CompanionBonds.get(server);
            // An unreadable reply, an empty one, a blank one, and one cut off by the token limit, which the client reports as an error.
            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> switch (asked[0]++) {
                case 0 -> CompletableFuture.completedFuture("не JSON");
                case 1 -> CompletableFuture.completedFuture("");
                case 2 -> CompletableFuture.completedFuture(" \n ");
                default -> CompletableFuture.completedFuture(CUT_OFF).thenApply(CompanionMemoryCompressionTests::completionText);
            });
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server, owner, true);
            for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Нулевая реплика " + i, null);

            h.runAfterDelay(5, () -> {
                try {
                    // Unlike the replies below, an answer that cannot be read counts as handled: its lines leave the journal, so the batches below stay under its ceiling.
                    h.assertTrue(asked[0] == 1 && bonds.pendingTalk(owner).isEmpty(),
                            "An unreadable reply left its lines in the journal: " + asked[0] + " requests, " + bonds.pendingTalk(owner).size() + " lines");
                    var unreadable = bonds.memory(owner);
                    h.assertTrue(unreadable.entries().isEmpty() && unreadable.callMe().isEmpty(), "An unreadable reply was stored: " + unreadable.save());
                    for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Первая реплика " + i, null);

                    h.runAfterDelay(5, () -> {
                        try {
                            h.assertTrue(asked[0] == 2 && bonds.pendingTalk(owner).size() == 12,
                                    "An empty reply took lines out of the journal: " + asked[0] + " requests, " + bonds.pendingTalk(owner).size() + " lines");
                            for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Вторая реплика " + i, null);

                            h.runAfterDelay(5, () -> {
                                try {
                                    h.assertTrue(asked[0] == 3 && bonds.pendingTalk(owner).size() == 24,
                                            "A blank reply took lines out of the journal: " + asked[0] + " requests, " + bonds.pendingTalk(owner).size() + " lines");
                                    for (int i = 0; i < 12; i++) CompanionMemoryFlow.playerSaid("Третья реплика " + i, null);

                                    h.runAfterDelay(5, () -> {
                                        try {
                                            h.assertTrue(asked[0] == 4 && bonds.pendingTalk(owner).size() == 36,
                                                    "A cut-off reply took lines out of the journal: " + asked[0] + " requests, " + bonds.pendingTalk(owner).size() + " lines");
                                            var memory = bonds.memory(owner);
                                            h.assertTrue(memory.entries().isEmpty() && memory.callMe().isEmpty(), "An unfinished reply was stored: " + memory.save());

                                            boolean refused = false;
                                            try {
                                                completionText(CUT_OFF);
                                            } catch (IllegalStateException expected) {
                                                refused = true;
                                            }
                                            h.assertTrue(refused, "A reply cut off by the token limit was taken for an answer");
                                            String finished = completionText("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"x\"}}]}");
                                            h.assertTrue("x".equals(finished), "A finished reply lost its text: " + finished);
                                            String silent = completionText("{\"choices\":[{\"finish_reason\":null,\"message\":{\"content\":null}}]}");
                                            h.assertTrue(silent.isEmpty(), "A reply without text is not empty: " + silent);
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

    @GameTest(template="empty", batch="memory_forget_off", timeoutTicks=200)
    public static void forgetWithMemoryOffErasesTheWorld(GameTestHelper h) {
        var server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        try {
            var bonds = CompanionBonds.get(server);
            CompanionMemoryFlow.useCompleter((messages, maxTokens, trace) -> new CompletableFuture<>());
            OpenRouterClient.resetSession();
            CompanionMemoryFlow.bind(server, owner, true);
            bonds.remember(owner, CompanionMemory.PLAYER_FACT, "Любит железо");
            bonds.setCallMe(owner, "Роб");
            CompanionMemoryFlow.playerSaid("Мой секрет про арбузы", null);
            CompanionMemoryFlow.companionSaid("model", "Запомнил про арбузы");

            h.runAfterDelay(5, () -> {
                try {
                    var before = bonds.memory(owner);
                    h.assertTrue(!bonds.pendingTalk(owner).isEmpty() && !before.recentTalk().isEmpty() && !before.entries().isEmpty(), "Fixture is empty: " + before.save());

                    // The setting is switched off after the talk was saved: the world still holds it.
                    CompanionMemoryFlow.bind(server, owner, false);
                    CompanionMemoryFlow.forget();

                    h.runAfterDelay(5, () -> {
                        try {
                            var after = bonds.memory(owner);
                            h.assertTrue(bonds.pendingTalk(owner).isEmpty() && after.recentTalk().isEmpty() && after.entries().isEmpty() && after.callMe().isEmpty(),
                                    "Forgetting with memory off left the world's memory: " + after.save());
                            h.assertTrue(!bonds.isDirty(), "Forgetting was not written out at once: the bonds still wait for the autosave");
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
    }

    private static String completionText(String response) { return OpenRouterClient.completionText(JsonParser.parseString(response).getAsJsonObject()); }

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
