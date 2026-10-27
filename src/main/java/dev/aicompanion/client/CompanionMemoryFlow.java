package dev.aicompanion.client;

import com.google.gson.JsonArray;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionMemory;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fills long-term memory from the talk: the player's lines and the companion's answers go to the journal kept in the world,
 * which survives logout, and a model condenses the journal into memories every 12 player lines and once when a session starts.
 * A condensed batch leaves the journal by its number, and only after its reply has been handled, so a failed, outdated,
 * empty or cut-off request leaves the lines for the next attempt. Lock order is always history lock, then {@link #LOCK};
 * nothing here calls the network or the server while holding either.
 */
public final class CompanionMemoryFlow {
    public record Target(MinecraftServer server, UUID owner) { }

    @FunctionalInterface
    public interface Completer {
        CompletableFuture<String> complete(JsonArray messages, int maxTokens, String trace);
    }

    private static final int PLAYER_LINES_PER_BATCH = 12;
    // Reasoning tokens count against max_tokens, and a full reply (five memories of 160 characters, likes, dislikes) in Russian is a few hundred tokens by itself.
    private static final int COMPRESSION_TOKENS = 1200;
    private static final Set<String> MEMORY_COMMANDS = Set.of("companion memory forget", "companion memory show");
    // Only answers to the player are talk; remarks about events, action summaries and acknowledgements would crowd the memory out.
    private static final Set<String> ANSWER_SOURCES = Set.of("model", "clarification", "local_identity");

    private static final Object LOCK = new Object();
    private static int playerLines;
    private static long inFlight = -1;
    // The generation whose start has been condensed already: bind() runs every couple of seconds and must not ask again.
    private static long started = -1;
    private static long epoch;
    private static volatile Target target;
    // The world of this session even while memory is off: «Забудь всё» must still erase what earlier sessions left in it.
    private static volatile Target world;
    private static volatile Completer completer = OpenRouterClient::complete;

    private CompanionMemoryFlow() { }

    public static void useCompleter(Completer replacement) { completer = replacement == null ? OpenRouterClient::complete : replacement; }

    public static void bind(MinecraftServer server, UUID owner, boolean enabled) {
        world = server == null ? null : new Target(server, owner);
        if (enabled && server != null) {
            target = new Target(server, owner);
            long gen = OpenRouterClient.generation();
            boolean sessionStart;
            synchronized (LOCK) {
                sessionStart = started != gen;
                started = gen;
            }
            if (sessionStart) condense();
            return;
        }
        target = null;
        synchronized (LOCK) { playerLines = 0; epoch++; }
    }

    public static void unbind() { bind(null, null, false); }

    /** Called with the history lock held, when a new session starts. The binding itself survives: the same world goes on. */
    static void reset() {
        synchronized (LOCK) { playerLines = 0; inFlight = -1; }
        CompanionMemoryView.clear();
    }

    /** A line of the player; {@code route} is what the local parser made of it, and the memory commands are not part of the talk. */
    public static void playerSaid(String text, String route) {
        if (route != null && MEMORY_COMMANDS.contains(route)) return;
        if (!record(true, text)) return;
        boolean due = false;
        synchronized (LOCK) {
            if (++playerLines >= PLAYER_LINES_PER_BATCH) {
                playerLines = 0;
                due = true;
            }
        }
        if (due) condense();
    }

    /** A line of the companion; {@code source} says where it came from. */
    public static void companionSaid(String source, String text) {
        if (source != null && ANSWER_SOURCES.contains(source)) record(false, text);
    }

    private static boolean record(boolean fromPlayer, String text) {
        Target t = target;
        if (t == null || text == null || text.isBlank()) return false;
        long gen = OpenRouterClient.generation();
        t.server().execute(() -> {
            if (!t.server().isRunning() || gen != OpenRouterClient.generation()) return;
            CompanionBonds.get(t.server()).recordTalk(t.owner(), fromPlayer, text);
        });
        return true;
    }

    /** Reads the journal on the server thread, so a line queued just before is in it; nothing is sent when it is empty or a request is out. */
    private static void condense() {
        Target t = target;
        if (t == null) return;
        long gen = OpenRouterClient.generation();
        long batchEpoch;
        synchronized (LOCK) { batchEpoch = epoch; }
        t.server().execute(() -> {
            if (!t.server().isRunning() || isStale(t, gen, batchEpoch)) return;
            CompanionBonds bonds = CompanionBonds.get(t.server());
            List<CompanionMemory.Talk> lines = bonds.pendingTalk(t.owner());
            if (lines.isEmpty()) return;
            synchronized (LOCK) {
                if (inFlight == gen) return;
                inFlight = gen;
            }
            request(t, gen, batchEpoch, lines, bonds.pendingEnd(t.owner()));
        });
    }

    private static void request(Target t, long gen, long batchEpoch, List<CompanionMemory.Talk> lines, long end) {
        String trace = CompanionDiagnostics.newTrace();
        CompanionDiagnostics.event(trace, "memory_compression_queued", "lines", lines.size());
        CompletableFuture<String> reply;
        try {
            reply = completer.complete(CompanionMemoryCodec.request(lines), COMPRESSION_TOKENS, trace);
        } catch (RuntimeException error) {
            reply = CompletableFuture.failedFuture(error);
        }
        reply.whenComplete((text, error) -> {
            boolean stale = isStale(t, gen, batchEpoch);
            if (stale || error != null) {
                release(gen);
                if (!stale) CompanionDiagnostics.failure(trace, "memory_compression", error);
                return;
            }
            if (text == null || text.isBlank()) {
                // Nothing was said about the lines, unlike a reply that is only unreadable: they wait for the next attempt.
                release(gen);
                CompanionDiagnostics.event(trace, "memory_compression_empty");
                return;
            }
            CompanionMemoryCodec.Parsed parsed = CompanionMemoryCodec.parse(text);
            if (!parsed.valid()) {
                CompanionDiagnostics.event(trace, "memory_compression_garbage", "replyLength", text.length());
            }
            t.server().execute(() -> settle(t, gen, batchEpoch, end, trace, parsed));
        });
    }

    /** Stores the reply and takes the condensed lines out of the journal in one server task, so the request is never sent twice for them. */
    private static void settle(Target t, long gen, long batchEpoch, long end, String trace, CompanionMemoryCodec.Parsed parsed) {
        try {
            if (!t.server().isRunning() || isStale(t, gen, batchEpoch)) return;
            CompanionBonds bonds = CompanionBonds.get(t.server());
            if (parsed.valid()) store(t, bonds, trace, parsed);
            // A non-empty reply that cannot be read counts as handled too: the same lines would only buy the same garbage again.
            bonds.ackPending(t.owner(), end);
        } finally {
            release(gen);
        }
    }

    private static void store(Target t, CompanionBonds bonds, String trace, CompanionMemoryCodec.Parsed parsed) {
        int accepted = 0;
        for (CompanionMemoryCodec.Memory memory : parsed.memories()) {
            if (bonds.remember(t.owner(), memory.kind(), memory.text()) != CompanionMemory.Outcome.REJECTED) accepted++;
        }
        if (!parsed.callMe().isEmpty()) bonds.setCallMe(t.owner(), parsed.callMe());
        parsed.likes().forEach(like -> bonds.addLike(t.owner(), like));
        parsed.dislikes().forEach(dislike -> bonds.addDislike(t.owner(), dislike));
        CompanionDiagnostics.event(trace, "memory_compression_stored", "accepted", accepted,
                "rejectedByCodec", parsed.rejected(), "rejectedByServer", parsed.memories().size() - accepted);
    }

    private static void release(long gen) {
        synchronized (LOCK) { if (inFlight == gen) inFlight = -1; }
    }

    private static boolean isStale(Target t, long gen, long batchEpoch) {
        if (gen != OpenRouterClient.generation() || !t.equals(target)) return true;
        synchronized (LOCK) { return epoch != batchEpoch; }
    }

    /** «Забудь всё»: facts, preferences and talk go from the world, the client view and the chat history; the bond and the deeds stay. */
    public static void forget() {
        OpenRouterClient.forgetTalk();
        // Forgetting starts a new generation but not a new session: there is no earlier journal left to condense.
        long gen = OpenRouterClient.generation();
        synchronized (LOCK) { playerLines = 0; epoch++; started = gen; }
        CompanionMemoryView.forgetMemory();
        Target t = world;
        if (t == null) return;
        t.server().execute(() -> {
            if (t.server().isRunning()) CompanionBonds.get(t.server()).forget(t.owner());
        });
    }
}
