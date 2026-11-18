package dev.aicompanion.client;

/** Whether the player's line is about trading, so that the snapshot carries the trader list; pure, no client classes. */
public final class CompanionTradeWords {
    private static final String[] WORDS = {"купи*", "купл*", "прода*", "обмен*", "торг*", "жител*", "професс*", "изумруд*", "сделк*", "книг*", "зачаров*", "починк*"};

    private CompanionTradeWords() {}

    public static boolean about(String message) { return message != null && CompanionKnowledge.mentions(message, WORDS); }
}
