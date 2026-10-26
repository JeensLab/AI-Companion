package dev.aicompanion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Local admission/deduplication policy, shared by the client and deterministic tests. No model call to decide to talk. */
public final class CompanionConversation {
    public static final int MIN_GAP = 20 * 180, MAX_GAP = 20 * 300;
    private long nextOpportunity;
    private long quietUntil;
    private boolean awaitingAnswer;
    private long revision;
    private int observation;
    private final Deque<String> recent = new ArrayDeque<>();

    /** Prefix the server puts on a HIGH event so the client can tell it from ordinary observations; it never reaches the model. */
    public static final String URGENT_MARK = "[!]";

    public static boolean isUrgent(String text) { return text != null && text.startsWith(URGENT_MARK); }
    public static String stripUrgent(String text) { return isUrgent(text) ? text.substring(URGENT_MARK.length()).stripLeading() : text; }
    public static int gap(int jitter) { return MIN_GAP + Math.floorMod(jitter, MAX_GAP - MIN_GAP + 1); }
    public void reset(long now, int jitter) { recent.clear(); awaitingAnswer = false; observation = 0; nextOpportunity = now + gap(jitter); activity(now, jitter); }
    // Normal conversation needs a short pause, not another full initiative interval after every answer.
    public void activity(long now, int jitter) { revision++; quietUntil = now + 20 * 45 + Math.floorMod(jitter, 601); }
    public void playerMessage(long now, int jitter) { awaitingAnswer = false; activity(now, jitter); }
    public boolean ready(long now) { return now >= nextOpportunity && now >= quietUntil && !awaitingAnswer; }
    public boolean awaitingAnswer() { return awaitingAnswer; }
    public long nextOpportunity() { return nextOpportunity; }
    public long revision() { return revision; }
    public void trackObservation(int serial) { observation = serial; }
    public boolean newObservation(int serial) { return serial != 0 && serial != observation; }
    /** Consume skipped events too. A busy player never leaves a deferred conversation backlog. */
    public boolean takeObservation(int serial, long age, long now, boolean safe, boolean busy, int jitter) {
        if (!newObservation(serial)) return false;
        observation = serial;
        // Entity metadata can arrive a few client ticks ahead of the next world-time update.
        if (age < -20 || age > 400 || !safe || busy || !ready(now)) return false;
        nextOpportunity = now + gap(jitter); activity(now, jitter); return true;
    }
    public boolean acceptReply(long capturedRevision, String text, boolean safe, boolean busy) {
        return revision == capturedRevision && safe && !busy && !awaitingAnswer && text != null
                && text.length() <= 260 && text.chars().filter(c -> c == '?').count() <= 1 && fresh(text);
    }

    public boolean fresh(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return false;
        Set<String> words = words(normalized);
        for (String old : recent) {
            if (old.equals(normalized)) return false;
            Set<String> previous = words(old), common = new HashSet<>(words);
            common.retainAll(previous);
            if (common.size() >= 3 && common.size() >= Math.max(words.size(), previous.size()) * .75) return false;
        }
        return true;
    }
    public void delivered(String text, long now, int jitter) {
        if (text == null || text.isBlank()) return;
        recent.addLast(normalize(text));
        while (recent.size() > 12) recent.removeFirst();
        // A question remains open until an actual player message, even if several minutes pass.
        awaitingAnswer |= text.contains("?");
        activity(now, jitter);
    }
    public void initiativeDelivered(String text, long now, int jitter) {
        delivered(text, now, jitter); nextOpportunity = now + gap(jitter);
    }
    private static String normalize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    private static Set<String> words(String text) { return new HashSet<>(java.util.List.of(text.split("\\s+"))); }
}
