package dev.aicompanion.entity;

/**
 * The one HIGH event waiting to be spoken. It is released only after the owner has been within earshot for five seconds
 * without a break and it is safe; it expires after ten minutes (it stays in the happenings), and two are never spoken within two
 * minutes. A newer event replaces the waiting one. Time and conditions are parameters, so the rules are testable without a world.
 */
public final class CompanionUrgent {
    public static final long WAIT_TICKS = 20L * 60 * 10;
    public static final long HEARD_TICKS = 20L * 5;
    public static final long GAP_TICKS = 20L * 60 * 2;

    private String fact = "";
    private long since;
    private long heardSince = -1;
    private long lastSpoken = Long.MIN_VALUE / 2;

    /** {@code since} is when the event happened: the ten minutes are counted from it, not from the moment it was handed over. */
    public void offer(String fact, long since) {
        if (fact == null || fact.isBlank()) return;
        this.fact = fact;
        this.since = since;
    }

    public boolean pending() { return !fact.isEmpty(); }

    /** The event to speak now, or null. {@code heard}: the owner is in the same dimension within earshot right now. */
    public String step(long now, boolean heard, boolean safe) {
        if (fact.isEmpty()) return null;
        if (now - since > WAIT_TICKS) { fact = ""; heardSince = -1; return null; }
        if (!heard) { heardSince = -1; return null; }
        if (heardSince < 0) heardSince = now;
        if (!safe || now - heardSince < HEARD_TICKS || now - lastSpoken < GAP_TICKS) return null;
        String said = fact;
        fact = "";
        lastSpoken = now;
        return said;
    }
}
