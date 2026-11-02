package dev.aicompanion;

/**
 * His health and armor as the player sees them: an armor strip laid over the health bar in the companion menu, and the
 * same block above his head while the crosshair is on him. Pure numbers, so the rules are tested without a game.
 */
public final class CompanionHealth {
    /** The ceiling the armor strip is drawn against: the game's own armor bar is twenty points wide. */
    public static final int ARMOR_BAR_CEILING = 20;

    /** The filled share of a bar, from 0 to 1; a missing maximum or a broken value shows nothing rather than a wrong bar. */
    public static float share(float value, float max) {
        if (!(max > 0.0F)) return 0.0F;
        float ratio = value / max;
        if (!(ratio > 0.0F)) return 0.0F;
        return Math.min(1.0F, ratio);
    }

    /** «XX/MAX» for his health: whole hit points, floored and never above the maximum or below zero. */
    public static String health(float health, float max) {
        long ceiling = max > 0.0F ? (long) Math.floor(max) : 0L;
        long shown = Math.min(Math.max(0L, (long) Math.floor(health)), ceiling);
        return shown + "/" + ceiling;
    }

    private CompanionHealth() {
    }
}
