package dev.aicompanion.entity;

/** Why the expedition must go up now: a cave too dangerous, a fire or low health; a meal eaten on the spot is given time to mend (moved out of {@link CompanionExpedition}). */
final class CompanionExpeditionDanger {
    private static final String CAVE_DANGER = "в пещере слишком опасно";
    /** How long a meal is given to raise his health before the climb, and how recent a blow from a mob keeps the climb. */
    private static final int MEAL_GRACE = 200, RECENT_HURT = 100;
    private final CompanionEntity owner;
    private final CompanionExpeditionCave cave;
    private long mealStart = -1;
    private float mealHealth;

    CompanionExpeditionDanger(CompanionEntity owner, CompanionExpeditionCave cave) {
        this.owner = owner;
        this.cave = cave;
    }

    /** A new danger check starts from the health at that time. */
    void reset() { mealStart = -1; }

    /** Why he must go up now (a very dangerous cave dealt with, a fire, or health at the cave limit), or null. */
    String now() {
        if (!cave.busy() && cave.takeRetreat()) return CAVE_DANGER;
        // The cave sense reads a fire only inside a cave episode.
        if (owner.isOnFire() || owner.isInLava()) return "горю";
        float health = owner.getHealth();
        if (health > CompanionCaveSense.HEALTH_VERY) { mealStart = -1; return null; }
        // With food in the bag he eats on the spot (a fall, a blast, /damage); climbing at once would give up the order for a meal.
        // A living attacker that struck lately (a skeleton out of the fight's sight) or a harmful effect means a wound a meal does not end, and so does a meal that does not mend.
        if (CompanionEating.hasFood(owner) && !hurtByMobLately(owner) && !hasHarmfulEffect() && mealMends(health)) return null;
        return "мало здоровья (" + (int) Math.ceil(health) + ")";
    }

    /** A living attacker that struck within the last five seconds. */
    static boolean hurtByMobLately(CompanionEntity owner) { return owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT_HURT; }

    private boolean hasHarmfulEffect() {
        for (var effect : owner.getActiveEffects()) if (!effect.getEffect().value().isBeneficial()) return true;
        return false;
    }

    /** True while the meal is young, or the health has risen since it began: the next check starts from the health now. */
    private boolean mealMends(float health) {
        long now = owner.level().getGameTime();
        if (mealStart < 0 || health > mealHealth) { mealStart = now; mealHealth = health; return true; }
        return now - mealStart < MEAL_GRACE;
    }
}
