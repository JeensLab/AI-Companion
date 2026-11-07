package dev.aicompanion.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The guard order («охраняй меня»): he watches the blocks around the player, not around himself, and goes for the hostile
 * mob nearest to the PLAYER, so that it is met on its way and does not reach him. A creeper stays the business of
 * CompanionCreeperGuard: it is a target only while the guard-order rule of CompanionCombat.isThreateningOwner takes it.
 */
final class CompanionGuardWatch extends Goal {
    /** How far from the player a mob is still watched, and how far a fight may take him from the player. */
    static final int GUARD_WATCH = 50;
    /** A new look at the mobs around the player every so many ticks, never every tick. */
    private static final int SCAN_INTERVAL = 10;
    /** A mob takes the target over from the current one only when it is this much nearer to the player: no flapping between two. */
    private static final double SWITCH_MARGIN = 2.0D;
    /** The target is let go after this long unseen by him and by the player, like the game's own target goals do. */
    private static final int UNSEEN_TICKS = 60;
    /** Outside a guard order a fight lets him go when he and the mob are both this far from the player. */
    private static final int STRAY_SELF = 16, STRAY_ENEMY = 14;

    /** A mob he could not get any closer to (or hurt) for CompanionWork.CHASE_TICKS is not taken again for this long, like an animal of a hunt. */
    private static final int GIVE_UP_TICKS = 20 * 30;

    private final CompanionEntity c;
    private Mob chosen;
    private int nextScan;
    private int lastSeen;
    private final Map<UUID, Long> givenUp = new HashMap<>();
    private double bestDistance;
    private float bestHealth;
    private long progressSince;

    CompanionGuardWatch(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.TARGET)); }

    /** The disengage rule of a fight for the player; a guard order widens it to the watch (a hunt keeps its own range). */
    static boolean strayed(CompanionEntity c, ServerPlayer owner, LivingEntity target) {
        boolean watching = c.guardByPlayer() && c.protecting && !c.work.isHunting();
        double self = watching ? GUARD_WATCH : STRAY_SELF, enemy = watching ? GUARD_WATCH : STRAY_ENEMY;
        return c.distanceToSqr(owner) > self * self && target.distanceToSqr(owner) > enemy * enemy;
    }

    @Override public boolean canUse() {
        if (!c.guardByPlayer() || !c.protecting || c.work.isHunting() || c.inventoryViewers != 0 || c.tickCount < nextScan) return false;
        nextScan = c.tickCount + SCAN_INTERVAL;
        chosen = nearestToOwner();
        return chosen != null;
    }

    @Override public void start() {
        lastSeen = c.tickCount;
        trackProgress();
        c.setTarget(chosen);
    }

    @Override public boolean canContinueToUse() {
        ServerPlayer owner = c.getOwnerPlayer();
        if (!c.guardByPlayer() || !c.protecting || c.work.isHunting() || owner == null || chosen == null || c.getTarget() != chosen || !watched(owner, chosen)) return false;
        if (sees(owner, chosen)) lastSeen = c.tickCount;
        return c.tickCount - lastSeen <= UNSEEN_TICKS && !unreachable();
    }

    /** Every tick: no step nearer and no wound for CHASE_TICKS means no way to it (a lone block, a ledge, a pit); it is let go and left for a while. */
    private boolean unreachable() {
        if (c.inventoryViewers != 0) { progressSince = c.level().getGameTime(); return false; }
        double distance = c.distanceToSqr(chosen);
        if (distance + .5 < bestDistance || chosen.getHealth() < bestHealth) {
            bestDistance = distance;
            bestHealth = chosen.getHealth();
            progressSince = c.level().getGameTime();
        }
        if (c.level().getGameTime() - progressSince <= CompanionWork.CHASE_TICKS) return false;
        givenUp.put(chosen.getUUID(), c.level().getGameTime() + GIVE_UP_TICKS);
        c.getNavigation().stop();
        return true;
    }

    private void trackProgress() {
        bestDistance = c.distanceToSqr(chosen);
        bestHealth = chosen.getHealth();
        progressSince = c.level().getGameTime();
    }

    @Override public void tick() {
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner == null || c.tickCount < nextScan) return;
        nextScan = c.tickCount + SCAN_INTERVAL;
        Mob nearest = nearestToOwner();
        if (nearest == null || nearest == chosen || nearest.distanceTo(owner) + SWITCH_MARGIN >= chosen.distanceTo(owner)) return;
        chosen = nearest;
        lastSeen = c.tickCount;
        trackProgress();
        c.setTarget(nearest);
    }

    @Override public void stop() {
        if (c.getTarget() == chosen) c.setTarget(null);
        chosen = null;
        nextScan = 0;
    }

    private boolean watched(ServerPlayer owner, Mob mob) {
        return !owner.isSpectator() && eligible(owner, mob);
    }

    private Mob nearestToOwner() {
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner == null || owner.isSpectator()) return null;
        long now = c.level().getGameTime();
        givenUp.values().removeIf(until -> until < now);
        return c.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(GUARD_WATCH), mob -> !givenUp.containsKey(mob.getUUID()) && eligible(owner, mob))
                .stream().sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(owner))).filter(mob -> sees(owner, mob)).findFirst().orElse(null);
    }

    /** Who may be the target, at the start and all through the fight; a creeper only while the guard-order threat rule takes it. */
    private boolean eligible(ServerPlayer owner, Mob mob) {
        if (!CompanionCombat.hostile(mob) || mob.distanceToSqr(owner) > GUARD_WATCH * GUARD_WATCH || !c.canAttack(mob)) return false;
        return mob instanceof Creeper ? c.combat.isThreateningOwner(mob) : c.combat.fairTarget(mob, c.combat.provokes(mob));
    }

    private boolean sees(ServerPlayer owner, LivingEntity mob) {
        return c.hasLineOfSight(mob) || owner.hasLineOfSight(mob);
    }
}
