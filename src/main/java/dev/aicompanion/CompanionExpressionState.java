package dev.aicompanion;

/** Hysteresis for continuous states, bounded event pulses. All times are server ticks. */
public final class CompanionExpressionState {
    private long dangerUntil, combatUntil, focusUntil, pulseUntil;
    private CompanionExpression pulse = CompanionExpression.CALM;

    public void react(CompanionExpression expression, long now, int ticks) {
        if (expression == CompanionExpression.CALM || expression == CompanionExpression.FOCUSED || expression == CompanionExpression.COMBAT) return;
        pulse = expression;
        pulseUntil = now + Math.max(1, Math.min(160, ticks));
    }

    public CompanionExpression update(long now, boolean danger, boolean combat, boolean working) {
        if (danger) dangerUntil = now + 40;
        if (combat) combatUntil = now + 40;
        if (working) focusUntil = now + 30;
        if (now < dangerUntil) return CompanionExpression.WORRIED;
        if (now < combatUntil) return CompanionExpression.COMBAT;
        if (now < pulseUntil) return pulse;
        if (now < focusUntil) return CompanionExpression.FOCUSED;
        return CompanionExpression.CALM;
    }
}
