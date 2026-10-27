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
    private int urgentSerial;
    private long urgentSince;
    private int takenSerial, retriedSerial;
    private long takenSince;
    private final Deque<String> recent = new ArrayDeque<>();

    /** Prefix the server puts on a HIGH event so the client can tell it from ordinary observations; it never reaches the model. */
    public static final String URGENT_MARK = "[!]";
    /** How long an urgent remark may wait for the player to be able to hear it, and how old one may be when first seen. */
    public static final int URGENT_TTL = 20 * 60, URGENT_FRESH = 400;

    public static boolean isUrgent(String text) { return text != null && text.startsWith(URGENT_MARK); }
    public static String stripUrgent(String text) { return isUrgent(text) ? text.substring(URGENT_MARK.length()).stripLeading() : text; }
    public static int gap(int jitter) { return MIN_GAP + Math.floorMod(jitter, MAX_GAP - MIN_GAP + 1); }
    public void reset(long now, int jitter) { recent.clear(); awaitingAnswer = false; observation = 0; urgentSerial = 0; takenSerial = 0; retriedSerial = 0; nextOpportunity = now + gap(jitter); activity(now, jitter); }
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
    /** An urgent remark ignores the quiet window and an open question, and stays pending until it can be spoken or expires. */
    public boolean takeObservation(int serial, long age, long now, boolean safe, boolean busy, int jitter, boolean urgent) {
        if (!urgent) return takeObservation(serial, age, now, safe, busy, jitter);
        if (!newObservation(serial)) return false;
        observation = serial;
        if (age < -20 || age > URGENT_TTL) return false;
        urgentSerial = serial; urgentSince = now - age;
        return takePendingUrgent(now, safe, busy, jitter);
    }
    public int urgentPending() { return urgentSerial; }
    /** Never touches awaitingAnswer: an open question stays open while the urgent line is spoken. */
    public boolean takePendingUrgent(long now, boolean safe, boolean busy, int jitter) {
        if (urgentSerial == 0) return false;
        if (now - urgentSince > URGENT_TTL) { urgentSerial = 0; return false; }
        if (!safe || busy) return false;
        takenSerial = urgentSerial; takenSince = urgentSince;
        urgentSerial = 0; nextOpportunity = now + gap(jitter); activity(now, jitter); return true;
    }
    /** A lost urgent reply (failed request, empty answer, nobody to hear it) gets one more try per remark while the remark is fresh; a newer pending remark wins. */
    public boolean retryUrgent(long now) {
        if (takenSerial == 0 || takenSerial == retriedSerial || urgentSerial != 0 || now - takenSince > URGENT_TTL) return false;
        urgentSerial = takenSerial; urgentSince = takenSince; retriedSerial = takenSerial; return true;
    }
    /** The first snapshot after entering the world is not replayed, except a fresh urgent remark. */
    public void firstSight(int serial, long age, long now, boolean urgent) {
        observation = serial;
        if (urgent && serial != 0 && age >= -20 && age <= URGENT_FRESH) { urgentSerial = serial; urgentSince = now - age; }
    }
    public boolean acceptReply(long capturedRevision, String text, boolean safe, boolean busy) {
        return acceptReply(capturedRevision, text, safe, busy, false);
    }
    public boolean acceptReply(long capturedRevision, String text, boolean safe, boolean busy, boolean urgent) {
        return rejection(capturedRevision, text, safe, busy, urgent).isEmpty();
    }
    /** The first reason a reply is refused, in the order the checks run; empty when it is accepted. */
    public String rejection(long capturedRevision, String text, boolean safe, boolean busy, boolean urgent) {
        if (revision != capturedRevision) return "conversation_changed";
        if (!safe) return "not_allowed";
        if (busy) return "busy";
        if (!urgent && awaitingAnswer) return "awaiting_answer";
        // Punctuation-only text is as empty as a blank one: fresh() refuses both, and neither is a repeat.
        if (text == null || normalize(text).isBlank()) return "empty";
        if (text.length() > 260) return "too_long";
        if (text.chars().filter(c -> c == '?').count() > 1) return "too_many_questions";
        return fresh(text) ? "" : "repeat";
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
