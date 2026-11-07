package dev.aicompanion.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * The guard order («охраняй меня»): he watches the blocks around the player, not around himself, and goes for the hostile
 * mob nearest to the PLAYER, so that it is met on its way and does not reach him. A creeper stays the business of
 * CompanionCreeperGuard: it is a target only when the guard-order rule of CompanionCombat.isThreateningOwner takes it.
 */
final class CompanionGuardWatch extends Goal {
    /** How far from the player a mob is still watched, and how far a fight may take him from the player. */
    static final int GUARD_WATCH = 50;
    /** A new look at the mobs around the player every so many ticks, never every tick. */
    private static final int SCAN_INTERVAL = 10;
    /** A mob takes the target over from the current one only when it is this much nearer to the player: no flapping between two. */
    private static final double SWITCH_MARGIN = 2.0D;
    /** Outside a guard order a fight lets him go when he and the mob are both this far from the player. */
    private static final int STRAY_SELF = 16, STRAY_ENEMY = 14;

    private final CompanionEntity c;
    private LivingEntity chosen;
    private int nextScan;

    CompanionGuardWatch(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.TARGET)); }

    /** The disengage rule of a fight for the player; a guard order widens it to the watch (a hunt keeps its own range). */
    static boolean strayed(CompanionEntity c, ServerPlayer owner, LivingEntity target) {
        boolean watching = c.protecting && !c.work.isHunting();
        double self = watching ? GUARD_WATCH : STRAY_SELF, enemy = watching ? GUARD_WATCH : STRAY_ENEMY;
        return c.distanceToSqr(owner) > self * self && target.distanceToSqr(owner) > enemy * enemy;
    }

    @Override public boolean canUse() {
        if (!c.protecting || c.work.isHunting() || c.tickCount < nextScan) return false;
        nextScan = c.tickCount + SCAN_INTERVAL;
        chosen = nearestToOwner();
        return chosen != null;
    }

    @Override public void start() { c.setTarget(chosen); }

    @Override public boolean canContinueToUse() {
        ServerPlayer owner = c.getOwnerPlayer();
        return c.protecting && !c.work.isHunting() && owner != null && chosen != null && c.getTarget() == chosen && watched(owner, chosen);
    }

    @Override public void tick() {
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner == null || c.tickCount < nextScan) return;
        nextScan = c.tickCount + SCAN_INTERVAL;
        LivingEntity nearest = nearestToOwner();
        if (nearest == null || nearest == chosen || nearest.distanceTo(owner) + SWITCH_MARGIN >= chosen.distanceTo(owner)) return;
        chosen = nearest;
        c.setTarget(nearest);
    }

    @Override public void stop() {
        if (c.getTarget() == chosen) c.setTarget(null);
        chosen = null;
        nextScan = 0;
    }

    private boolean watched(ServerPlayer owner, LivingEntity mob) {
        return !owner.isSpectator() && mob.distanceToSqr(owner) <= GUARD_WATCH * GUARD_WATCH && c.combat.fairTarget(mob, c.combat.provokes(mob));
    }

    private LivingEntity nearestToOwner() {
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner == null || owner.isSpectator()) return null;
        return c.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(GUARD_WATCH), mob -> candidate(owner, mob))
                .stream().min(Comparator.comparingDouble(mob -> mob.distanceToSqr(owner))).orElse(null);
    }

    private boolean candidate(ServerPlayer owner, Mob mob) {
        if (!CompanionCombat.hostile(mob) || mob.distanceToSqr(owner) > GUARD_WATCH * GUARD_WATCH || !c.canAttack(mob)) return false;
        boolean fair = mob instanceof Creeper ? c.combat.isThreateningOwner(mob) : c.combat.fairTarget(mob, c.combat.provokes(mob));
        return fair && (c.hasLineOfSight(mob) || owner.hasLineOfSight(mob));
    }
}
