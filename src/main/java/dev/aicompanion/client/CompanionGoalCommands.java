package dev.aicompanion.client;

/** The first supported outcome. Order of recipients and request words is deliberately free. */
public final class CompanionGoalCommands {
    private CompanionGoalCommands() { }
    public static boolean pickaxesForTwo(String text) {
        String t = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        if (t.matches("(?sU).*\\bне\\s+(делай|сделай|крафти|скрафти|создавай|создай|нужно|надо)\\b.*")) return false;
        boolean ask = t.matches("(?sU).*\\b(сделай|скрафти|создай|изготовь|нужны)\\b.*");
        boolean stonePick = t.matches("(?sU).*\\bкаменн\\p{L}*\\s+кир\\p{L}*\\b.*")
                || t.matches("(?sU).*\\bкир\\p{L}*\\s+из\\s+камня\\b.*");
        boolean both = t.matches("(?sU).*\\b(нам|нас)\\b.*")
                || t.matches("(?sU).*\\b(себе|себя|тебе|тебя)\\b.*") && t.matches("(?sU).*\\b(мне|меня|мою)\\b.*");
        return ask && stonePick && both;
    }
}
