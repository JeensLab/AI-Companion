package dev.aicompanion.client;

import com.google.gson.JsonArray;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionMemory;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fills long-term memory from the talk: every line goes to the server-side talk log, and history that falls out of the chat
 * window is condensed by a model into memories. Lock order is always history lock, then {@link #LOCK}; nothing here
 * calls the network or the server while holding either.
 */
public final class CompanionMemoryFlow {
    public record Target(MinecraftServer server, UUID owner) { }

    @FunctionalInterface
    public interface Completer {
        CompletableFuture<String> complete(JsonArray messages, int maxTokens, String trace);
    }

    private static final int BUFFER_CAP = 30;
    private static final int LINE_TEXT = 300;
    private static final int PLAYER_LINES_PER_BATCH = 12;
    private static final int COMPRESSION_TOKENS = 300;

    private static final Object LOCK = new Object();
    private static final List<CompanionMemory.Talk> dropped = new ArrayList<>();
    private static int playerLines;
    private static long inFlight = -1;
    private static long epoch;
    private static volatile Target target;
    private static volatile Completer completer = OpenRouterClient::complete;

    private CompanionMemoryFlow() { }

    public static void useCompleter(Completer replacement) { completer = replacement == null ? OpenRouterClient::complete : replacement; }

    public static void bind(MinecraftServer server, UUID owner, boolean enabled) {
        if (enabled && server != null) {
            target = new Target(server, owner);
            return;
        }
        target = null;
        synchronized (LOCK) { clearBuffer(); epoch++; }
    }

    public static void unbind() { bind(null, null, false); }

    /** Called with the history lock held, when a new session starts. The binding itself survives: the same world goes on. */
    static void reset() {
        synchronized (LOCK) { clearBuffer(); inFlight = -1; }
        CompanionMemoryView.clear();
    }

    /** A history message that was pushed out of the chat window; it waits here to be condensed. Called with the history lock held. */
    public static void dropped(String role, String text) {
        if (target == null || text == null || text.startsWith("[")) return;
        boolean fromPlayer = "user".equals(role);
        if (!fromPlayer && !"assistant".equals(role)) return;
        String line = text.length() > LINE_TEXT ? text.substring(0, LINE_TEXT) : text;
        synchronized (LOCK) {
            dropped.add(new CompanionMemory.Talk(fromPlayer, line));
            while (dropped.size() > BUFFER_CAP) dropped.removeFirst();
        }
    }

    public static void talk(boolean fromPlayer, String text) {
        Target t = target;
        if (t == null || text == null || text.isBlank()) return;
        long gen = OpenRouterClient.generation();
        t.server().execute(() -> {
            if (!t.server().isRunning() || gen != OpenRouterClient.generation()) return;
            CompanionBonds.get(t.server()).recordTalk(t.owner(), fromPlayer, text);
        });
        if (!fromPlayer) return;

        List<CompanionMemory.Talk> batch = null;
        long batchEpoch;
        synchronized (LOCK) {
            batchEpoch = epoch;
            if (++playerLines >= PLAYER_LINES_PER_BATCH && !dropped.isEmpty() && inFlight != gen) {
                batch = List.copyOf(dropped);
                dropped.clear();
                playerLines = 0;
                inFlight = gen;
            }
        }
        if (batch != null) request(t, gen, batchEpoch, batch);
    }

    private static void request(Target t, long gen, long batchEpoch, List<CompanionMemory.Talk> batch) {
        String trace = CompanionDiagnostics.newTrace();
        CompanionDiagnostics.event(trace, "memory_compression_queued", "lines", batch.size());
        CompletableFuture<String> reply;
        try {
            reply = completer.complete(CompanionMemoryCodec.request(batch), COMPRESSION_TOKENS, trace);
        } catch (RuntimeException error) {
            reply = CompletableFuture.failedFuture(error);
        }
        reply.whenComplete((text, error) -> {
            synchronized (LOCK) { if (inFlight == gen) inFlight = -1; }
            if (isStale(t, gen, batchEpoch)) return;
            if (error != null) {
                CompanionDiagnostics.failure(trace, "memory_compression", error);
                return;
            }
            CompanionMemoryCodec.Parsed parsed = CompanionMemoryCodec.parse(text);
            if (!parsed.valid()) {
                CompanionDiagnostics.event(trace, "memory_compression_garbage", "replyLength", text == null ? 0 : text.length());
                return;
            }
            t.server().execute(() -> store(t, gen, batchEpoch, trace, parsed));
        });
    }

    private static void store(Target t, long gen, long batchEpoch, String trace, CompanionMemoryCodec.Parsed parsed) {
        if (!t.server().isRunning() || isStale(t, gen, batchEpoch)) return;
        CompanionBonds bonds = CompanionBonds.get(t.server());
        int accepted = 0;
        for (CompanionMemoryCodec.Memory memory : parsed.memories()) {
            if (bonds.remember(t.owner(), memory.kind(), memory.text()) != CompanionMemory.Outcome.REJECTED) accepted++;
        }
        if (!parsed.callMe().isEmpty()) bonds.setCallMe(t.owner(), parsed.callMe());
        CompanionDiagnostics.event(trace, "memory_compression_stored", "accepted", accepted,
                "rejectedByCodec", parsed.rejected(), "rejectedByServer", parsed.memories().size() - accepted);
    }

    private static boolean isStale(Target t, long gen, long batchEpoch) {
        if (gen != OpenRouterClient.generation() || !t.equals(target)) return true;
        synchronized (LOCK) { return epoch != batchEpoch; }
    }

    /** «Забудь всё»: facts, preferences and talk go from the world and from the client view; the bond and the deeds stay. */
    public static void forget() {
        synchronized (LOCK) { clearBuffer(); epoch++; }
        CompanionMemoryView.forgetMemory();
        Target t = target;
        if (t == null) return;
        t.server().execute(() -> {
            if (t.server().isRunning()) CompanionBonds.get(t.server()).forget(t.owner());
        });
    }

    private static void clearBuffer() {
        dropped.clear();
        playerLines = 0;
    }
}
