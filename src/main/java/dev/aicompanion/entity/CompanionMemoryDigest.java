package dev.aicompanion.entity;

import dev.aicompanion.CompanionPersonality;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The compact view of one owner's memory, bond and happenings, and of the companion's character, that travels to the client.
 * It is built on the server, so it works while the companion is unloaded, and it is capped in bytes so a full memory cannot
 * flood the client or the prompt.
 */
public final class CompanionMemoryDigest {
    public static final int MAX_BYTES = 8 * 1024;
    public static final int HAPPENINGS_SHOWN = 6;

    public record Digest(List<CompanionMemory.Entry> memories, String callMe, List<String> likes, List<String> dislikes,
                         List<CompanionMemory.Talk> talk, int score, String tier, List<String> reasons, List<String> happenings,
                         Map<CompanionPersonality.Trait, Integer> character) {
        public static final Digest EMPTY = new Digest(List.of(), "", List.of(), List.of(), List.of(), 0, "", List.of(), List.of(), Map.of());

        /** A memory that only repeats the form of address is left out: {@code callMe} is told on its own. */
        public List<CompanionMemory.Entry> relevant(String query, int limit) {
            List<CompanionMemory.Entry> own = memories.stream().filter(entry -> !CompanionMemory.repeatsAddress(entry.text(), callMe)).toList();
            return CompanionMemory.rank(own, query, limit);
        }

        /** UTF-8 size of every string in the digest. */
        public int bytes() {
            int total = size(callMe) + size(tier);
            for (CompanionMemory.Entry entry : memories) total += size(entry.kind()) + size(entry.text());
            for (String like : likes) total += size(like);
            for (String dislike : dislikes) total += size(dislike);
            for (CompanionMemory.Talk line : talk) total += size(line.text());
            for (String reason : reasons) total += size(reason);
            for (String happening : happenings) total += size(happening);
            return total;
        }

        /** What is left after the player asked to forget: facts, preferences, talk and the character shifted by it go; the bond and the happenings stay. */
        public Digest withoutMemory() {
            return new Digest(List.of(), "", List.of(), List.of(), List.of(), score, tier, reasons, happenings, Map.of());
        }

        private static int size(String text) { return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length; }
    }

    private CompanionMemoryDigest() { }

    /** {@link Digest#EMPTY} while long-term memory is off: nothing is read from the world, nothing is shown. */
    public static Digest of(MinecraftServer server, UUID owner, boolean longTermMemory, long now) {
        if (!longTermMemory || server == null) return Digest.EMPTY;
        CompanionBonds bonds = CompanionBonds.get(server);
        CompanionMemory memory = bonds.memory(owner);
        CompanionBond bond = bonds.bond(owner);
        bond.advanceTo(CompanionBond.dayOf(now));

        List<String> reasons = new ArrayList<>();
        for (CompanionBond.Reason reason : bond.reasons()) reasons.add(reason.label());
        List<String> happenings = new ArrayList<>();
        for (CompanionHappenings.Happening happening : bonds.happenings(owner).recent(HAPPENINGS_SHOWN)) {
            happenings.add(CompanionDeeds.ago(now - happening.gameTime()) + ": " + happening.text());
        }

        Digest base = new Digest(List.of(), memory.callMe(), memory.likes(), memory.dislikes(), memory.recentTalk(),
                bond.score(), bond.tier().label(), List.copyOf(reasons), List.copyOf(happenings), memory.soul().character());
        int room = MAX_BYTES - base.bytes();
        List<CompanionMemory.Entry> kept = new ArrayList<>();
        for (CompanionMemory.Entry entry : memory.relevant("", CompanionMemory.MAX_MEMORIES)) {
            int cost = entry.kind().getBytes(StandardCharsets.UTF_8).length + entry.text().getBytes(StandardCharsets.UTF_8).length;
            if (cost > room) continue;
            kept.add(entry);
            room -= cost;
        }
        return new Digest(List.copyOf(kept), base.callMe(), base.likes(), base.dislikes(), base.talk(),
                base.score(), base.tier(), base.reasons(), base.happenings(), base.character());
    }
}
