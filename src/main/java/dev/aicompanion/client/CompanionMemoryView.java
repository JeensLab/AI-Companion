package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest;
import dev.aicompanion.entity.CompanionMemoryDigest.Digest;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** What the client knows about the player's memory, bond and happenings; refreshed by every sync from the server. */
public final class CompanionMemoryView {
    private static final int RECALL_MEMORIES = 5;

    private static volatile Digest current = Digest.EMPTY;

    private CompanionMemoryView() { }

    public static Digest current() { return current; }

    static void clear() { current = Digest.EMPTY; }

    static void forgetMemory() { current = current.withoutMemory(); }

    /** Runs on the server thread: reads the digest from the world. */
    public static void pull(ServerPlayer player, boolean longTerm, Consumer<Digest> to) {
        to.accept(CompanionMemoryDigest.of(player.getServer(), player.getUUID(), longTerm, player.level().getGameTime()));
    }

    /** Runs on the client thread; a digest made for an earlier session is not restored into the talk. */
    public static void deliver(long session, Digest digest, boolean enabled) {
        current = digest;
        if (enabled) OpenRouterClient.restoreTalk(session, digest.talk());
    }

    /** The answer to «что ты обо мне помнишь», told from the digest only. */
    public static String recall(Digest digest) {
        List<String> parts = new ArrayList<>();
        if (!digest.callMe().isEmpty()) parts.add("ты просил звать тебя " + digest.callMe());
        for (CompanionMemory.Entry entry : digest.relevant("", RECALL_MEMORIES)) parts.add(entry.text());
        if (!digest.likes().isEmpty()) parts.add("тебе нравится: " + String.join(", ", digest.likes()));
        if (!digest.dislikes().isEmpty()) parts.add("тебе не нравится: " + String.join(", ", digest.dislikes()));
        if (parts.isEmpty()) return "Пока ничего особенного не помню. Расскажи что-нибудь о себе.";
        return "Помню вот что: " + String.join("; ", parts) + ".";
    }
}
