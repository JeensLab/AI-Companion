package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionMemoryFlow;
import dev.aicompanion.client.CompanionMemoryView;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionBond;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionHappenings;
import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.MemoryTestKit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

/** Chat history belongs to one world session; talk survives a restore, a logout and a sync, and event remarks stay out of it. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemorySessionTests {
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

    @GameTest(template="empty", batch="memory_session_end")
    public static void sessionEndRecognizesLateAndRefusedReplies(GameTestHelper h) {
        try {
            long begun = OpenRouterClient.generation();
            h.assertTrue(!OpenRouterClient.sessionEnded(null, begun), "A reply of the current session was taken for a late one");
            h.assertTrue(!OpenRouterClient.sessionEnded(new RuntimeException("x"), begun), "A failure was taken for the end of the session");
            h.assertTrue(!OpenRouterClient.sessionEnded(new CompletionException(new RuntimeException("x")), begun), "A wrapped failure was taken for the end of the session");
            h.assertTrue(OpenRouterClient.sessionEnded(new CancellationException(), begun), "A refused request was not recognized as the end of the session");
            h.assertTrue(OpenRouterClient.sessionEnded(new CompletionException(new CancellationException()), begun), "A refusal wrapped in a CompletionException was not recognized");

            OpenRouterClient.resetSession();
            h.assertTrue(OpenRouterClient.sessionEnded(null, begun), "A reply accepted just before the end of the session was not recognized as late");
            h.succeed();
        } finally {
            resetFlow();
        }
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

    private static String role(List<JsonObject> history, int index) { return history.get(index).get("role").getAsString(); }

    private static String text(List<JsonObject> history, int index) { return history.get(index).get("content").getAsString(); }
}
