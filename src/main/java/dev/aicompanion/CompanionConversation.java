package dev.aicompanion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Local admission/deduplication policy, shared by the client and deterministic tests. No model call to decide to talk. */
public final class CompanionConversation {
    public static final int MIN_GAP = 20 * 60, MAX_GAP = 20 * 120;
    public static final int MIN_PAUSE = 20 * 15, MAX_PAUSE = 20 * 30;
    public static final int ANSWER_WAIT = 20 * 90;
    /** What is going on sets how soon he opens a topic of his own: never in a fight, soon on the road or in a mine. */
    public enum Scene { FIGHT, TRAVEL, MINE, IDLE, WORK }
    private long nextOpportunity;
    private long quietUntil;
    private boolean awaitingAnswer;
    private long answerUntil;
    private boolean unansweredQuestion;
    private long lastTalk;
    private int talkJitter;
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
    public static Scene scene(boolean fighting, boolean underground, boolean travelling, boolean working, long idleTicks) {
        if (fighting) return Scene.FIGHT;
        if (underground) return Scene.MINE;
        if (travelling) return Scene.TRAVEL;
        // Idle for a long while is when a friend starts talking; a minute of standing still is not yet.
        return working || idleTicks < 20 * 60 ? Scene.WORK : Scene.IDLE;
    }
    /** The pause before a topic of his own: the scene's minimum and up to a minute more. */
    public static int topicGap(Scene scene, int jitter) {
        int minimum = switch (scene) {
            case FIGHT -> Integer.MAX_VALUE;
            case TRAVEL -> 20 * 60;
            case MINE -> 20 * 75;
            case IDLE -> 20 * 90;
            // At work the work itself gives remarks every one to two minutes; a topic of his own comes rarely.
            case WORK -> 20 * 180;
        };
        return scene == Scene.FIGHT ? minimum : minimum + Math.floorMod(jitter, MAX_GAP - MIN_GAP + 1);
    }
    public void reset(long now, int jitter) {
        recent.clear(); awaitingAnswer = false; unansweredQuestion = false; answerUntil = 0; observation = 0;
        urgentSerial = 0; takenSerial = 0; retriedSerial = 0; nextOpportunity = now + gap(jitter); talked(now, jitter); activity(now, jitter);
    }
    // Normal conversation needs a short pause, not another full initiative interval after every answer.
    public void activity(long now, int jitter) { revision++; quietUntil = now + MIN_PAUSE + Math.floorMod(jitter, MAX_PAUSE - MIN_PAUSE + 1); }
    public void playerMessage(long now, int jitter) { awaitingAnswer = false; unansweredQuestion = false; talked(now, jitter); activity(now, jitter); }
    /** The player's word or a topic of his own starts the pause before his next topic; remarks on events do not. */
    public void talked(long now, int jitter) { lastTalk = now; talkJitter = jitter; }
    public boolean ready(long now) {
        if (awaitingAnswer && now >= answerUntil) awaitingAnswer = false;
        return now >= nextOpportunity && now >= quietUntil && !awaitingAnswer;
    }
    public boolean awaitingAnswer() { return awaitingAnswer; }
    public boolean canAskQuestion() { return !unansweredQuestion; }
    /**
     * Once the scene's pause since the last word of the player or his last topic is over, a topic of his own takes the next free
     * initiative slot, shared with remarks on events: these come every one to two minutes and would never leave a pause of silence.
     * Never in a fight. Over a question the player has not answered he asks nothing, as in remarks on events, but goes on sharing:
     * the topic is then one without a question (review of PR #28: one question in a remark on chests silenced his topics for good).
     */
    public boolean topicReady(long now, Scene scene) {
        return scene != Scene.FIGHT && ready(now) && now >= lastTalk + topicGap(scene, talkJitter);
    }
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
        // PR #19 review: after a death the model asked «…рухнул? Живой хоть?» twice and both were dropped; an urgent line is
        // answered anyway, its prompt asks for one question at most.
        if (!urgent && text.chars().filter(c -> c == '?').count() > 1) return "too_many_questions";
        // An ignored question must not silence an urgent line either; its prompt still asks it to ask none.
        if (!urgent && !canAskQuestion() && text.contains("?")) return "unanswered_question";
        return fresh(text) ? "" : "repeat";
    }

    public boolean fresh(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return false;
        for (String old : recent) if (similar(old, normalized)) return false;
        return true;
    }
    /** The same words in another order or with other punctuation: a repeat. Both texts already normalized. */
    static boolean similar(String one, String other) {
        if (one.equals(other)) return true;
        Set<String> words = words(one), previous = words(other), common = new HashSet<>(words);
        common.retainAll(previous);
        return common.size() >= 3 && common.size() >= Math.max(words.size(), previous.size()) * .75;
    }
    public void delivered(String text, long now, int jitter) {
        if (text == null || text.isBlank()) return;
        recent.addLast(normalize(text));
        while (recent.size() > 12) recent.removeFirst();
        // Silence abandons the topic, never grants consent or invites another question.
        if (text.contains("?")) { awaitingAnswer = true; unansweredQuestion = true; answerUntil = now + ANSWER_WAIT; }
        activity(now, jitter);
    }
    public void initiativeDelivered(String text, long now, int jitter) {
        delivered(text, now, jitter); nextOpportunity = now + gap(jitter);
    }
    static String normalize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    private static Set<String> words(String text) { return new HashSet<>(java.util.List.of(text.split("\\s+"))); }
}
