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
    private long lastActivity;
    private int windowJitter;
    private long revision;
    private int observation;
    private final Deque<String> recent = new ArrayDeque<>();

    public static int gap(int jitter) { return MIN_GAP + Math.floorMod(jitter, MAX_GAP - MIN_GAP + 1); }
    public static Scene scene(boolean fighting, boolean underground, boolean travelling, boolean working, long idleTicks) {
        if (fighting) return Scene.FIGHT;
        if (underground) return Scene.MINE;
        if (travelling) return Scene.TRAVEL;
        // Idle for a long while is when a friend starts talking; a minute of standing still is not yet.
        return working || idleTicks < 20 * 60 ? Scene.WORK : Scene.IDLE;
    }
    /** Silence before a topic of his own, from the last word of anyone: the scene's minimum and up to a minute more. */
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
    public void reset(long now, int jitter) { recent.clear(); awaitingAnswer = false; unansweredQuestion = false; answerUntil = 0; observation = 0; nextOpportunity = now + gap(jitter); activity(now, jitter); }
    // Normal conversation needs a short pause, not another full initiative interval after every answer.
    public void activity(long now, int jitter) { revision++; quietUntil = now + MIN_PAUSE + Math.floorMod(jitter, MAX_PAUSE - MIN_PAUSE + 1); lastActivity = now; windowJitter = jitter; }
    public void playerMessage(long now, int jitter) { awaitingAnswer = false; unansweredQuestion = false; activity(now, jitter); }
    public boolean ready(long now) {
        if (awaitingAnswer && now >= answerUntil) awaitingAnswer = false;
        return now >= nextOpportunity && now >= quietUntil && !awaitingAnswer;
    }
    public boolean awaitingAnswer() { return awaitingAnswer; }
    public boolean canAskQuestion() { return !unansweredQuestion; }
    /** His own topic waits for the scene's silence; never in a fight, nor over a question the player has not answered. */
    public boolean topicReady(long now, Scene scene) {
        return scene != Scene.FIGHT && canAskQuestion() && now >= lastActivity + topicGap(scene, windowJitter);
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
    public boolean acceptReply(long capturedRevision, String text, boolean safe, boolean busy) {
        return revision == capturedRevision && safe && !busy && !awaitingAnswer && text != null
                && text.length() <= 260 && text.chars().filter(c -> c == '?').count() <= 1
                && (canAskQuestion() || !text.contains("?")) && fresh(text);
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
