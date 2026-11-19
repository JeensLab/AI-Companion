package dev.aicompanion.client;

/** Whether the player's line is about trading, so that the snapshot carries the trader list; pure, no client classes. */
public final class CompanionTradeWords {
    private static final String[] WORDS = {"купи*", "купл*", "прода*", "обмен*", "торг*", "жител*", "професс*", "изумруд*", "сделк*", "книг*", "зачаров*", "починк*"};

    /** How long after a line about trading the list still rides along: «да», «давай», «а ещё что?» answer it without a trade word. */
    public static final long FOLLOW_UP_MILLIS = 3 * 60_000L;
    private static volatile long lastAbout = Long.MIN_VALUE / 2;

    private CompanionTradeWords() {}

    public static boolean about(String message) { return message != null && CompanionKnowledge.mentions(message, WORDS); }

    /** {@link #about} or said within {@link #FOLLOW_UP_MILLIS} of a line that was: the player's answers to a trade question. */
    public static boolean relevant(String message, long nowMillis) {
        if (about(message)) { lastAbout = nowMillis; return true; }
        return nowMillis - lastAbout < FOLLOW_UP_MILLIS;
    }
}
