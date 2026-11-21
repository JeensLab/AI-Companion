package dev.aicompanion.entity;

/** Why the expedition must go up now: a cave too dangerous, a fire or low health; a meal eaten on the spot is given time to mend (moved out of {@link CompanionExpedition}). */
final class CompanionExpeditionDanger {
    static final String CAVE_DANGER = "в пещере слишком опасно";
    /** How long a meal is given to raise his health before the climb, and how recent a blow from a mob keeps the climb. */
    private static final int MEAL_GRACE = 200, RECENT_HURT = 100;
    private final CompanionEntity owner;
    private final CompanionExpeditionCave cave;
    private final CompanionExpeditionFight fight;
    private long mealStart = -1;
    private float mealHealth;

    CompanionExpeditionDanger(CompanionEntity owner, CompanionExpeditionCave cave, CompanionExpeditionFight fight) {
        this.owner = owner;
        this.cave = cave;
        this.fight = fight;
    }

    /** A new danger check starts from the health at that time. */
    void reset() { mealStart = -1; }

    /** Why he must go up now (a very dangerous cave dealt with, or health at the cave limit), or null. */
    String now() {
        if (!cave.busy() && cave.takeRetreat()) return CAVE_DANGER;
        // The cave sense reads a fire only inside a cave episode, and the goal no longer stops a step that owns the expedition for one.
        if (owner.isOnFire() || owner.isInLava()) return "горю";
        float health = owner.getHealth();
        if (health > CompanionCaveSense.HEALTH_VERY && !fight.wounded()) { mealStart = -1; return null; }
        // With food in the bag and no mob on him he eats on the spot (a fall, a blast, /damage); climbing at once would give up the order for a meal.
        // A living attacker that struck lately (a skeleton out of the fight's sight) or a harmful effect means a wound a meal does not end, and so does a meal that does not mend.
        if (CompanionEating.hasFood(owner) && !fight.hounded((net.minecraft.server.level.ServerLevel) owner.level()) && !hurtByMobLately() && !hasHarmfulEffect() && mealMends(health)) return null;
        return "мало здоровья (" + (int) Math.ceil(health) + ")";
    }

    private boolean hurtByMobLately() { return owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT_HURT; }

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
