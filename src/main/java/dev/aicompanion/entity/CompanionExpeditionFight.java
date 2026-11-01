package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A hostile mob near him during an expedition is fought to the end with the sword in his hand, and the work goes on after it: while one is
 * about, the expedition does not dig, step or craft (the melee goal at its priority takes the turn, and this hold covers the ticks between
 * two kills, when no goal has a target yet). He leaves a fight only at low health, and only when leaving is possible: his own way up
 * is under him and nothing is at his back already. The time of the fight is given back to the step, and the tool he held comes back to
 * his hand.
 */
final class CompanionExpeditionFight {
    /** How far a mob in sight is met, and how far one that has chosen him. */
    private static final double ENGAGE = 9, AWARE = 16, CONTACT_SQR = 9;
    private static final int LONGEST = 20 * 45, PAUSE = 20 * 10;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private long since = -1, lastBusy, pausedUntil;
    private Item tool;

    CompanionExpeditionFight(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    /** True when the expedition must stand still this tick: a foe is to be fought. False lets the work (or the retreat) go on. */
    boolean hold(ServerLevel level, boolean retreating) {
        if (retreating) return false;
        long now = level.getGameTime();
        Monster foe = nearest(level);
        if (foe == null) { settle(now); return false; }
        if (now < pausedUntil) return false;
        began();
        if (now - since > LONGEST) { settle(now); pausedUntil = now + PAUSE; return false; }
        lastBusy = now;
        LivingEntity target = owner.getTarget();
        if (target == null || !target.isAlive() || !isFoe(target)) owner.setTarget(foe);
        owner.getNavigation().stop();
        owner.selectWeaponForCombat();
        return true;
    }

    /** The melee goal has taken the turn: from here on the fight is under way (called before the weapon is picked, so the tool in hand is still the work's). */
    void began() {
        if (since >= 0) return;
        since = lastBusy = owner.level().getGameTime();
        ItemStack hand = owner.getMainHandItem();
        tool = hand.isEmpty() || CompanionEquipment.isMelee(hand) ? null : hand.getItem();
    }

    void going() { lastBusy = owner.level().getGameTime(); }

    void reset() {
        since = -1;
        pausedUntil = 0;
        tool = null;
    }

    /** The fight is over: its time goes back to the step, and the pickaxe (or what was in his hand) comes back. */
    private void settle(long now) {
        if (since < 0) return;
        long spent = lastBusy - since;
        since = -1;
        if (spent > 0) w.credit(spent);
        if (tool != null && !owner.getMainHandItem().is(tool)) owner.equipForWork(BuiltInRegistries.ITEM.getKey(tool).toString());
        tool = null;
    }

    /** A mob he fights: hostile, one that may be fought, awake, and either come for him or near and in sight; not one he is leaving or may not follow. */
    boolean isFoe(LivingEntity entity) {
        if (!(entity instanceof Monster monster) || !monster.isAlive() || monster.isNoAi()) return false;
        if (!owner.combat.fairTarget(monster, owner.combat.provokes(monster))) return false;
        if (monster instanceof Creeper && !owner.creeperGuard.fights()) return false;
        if (w.expedition.shuns(monster)) return false;
        double distance = owner.distanceTo(monster);
        return monster.getTarget() == owner && distance <= AWARE || distance <= ENGAGE && owner.hasLineOfSight(monster);
    }

    /**
     * True when the melee goal must let go of this foe. Retreating, only one who has not caught up (the one who has is struck where he
     * stands, running from him would be a blow in the back). Otherwise at low health when leaving is possible.
     */
    boolean avoids(LivingEntity foe, boolean retreating) {
        if (foe == null) return false;
        if (retreating) return !owner.isWithinMeleeAttackRange(foe);
        return owner.getHealth() <= CompanionCaveSense.HEALTH_VERY && canEscape(foe);
    }

    /** His own way up under him, not at the entrance already, the foe not in contact and not one that shoots. */
    private boolean canEscape(LivingEntity foe) {
        if (foe instanceof RangedAttackMob || owner.distanceToSqr(foe) < CONTACT_SQR || !w.mine.exists()) return false;
        List<BlockPos> way = CompanionExpeditionClimb.wayUp(w.mine);
        int at = CompanionExpeditionClimb.nearest(way, owner.blockPosition());
        return at >= 0 && at < way.size() - 1;
    }

    private Monster nearest(ServerLevel level) {
        Monster best = null;
        double least = Double.MAX_VALUE;
        for (Monster monster : level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(AWARE), this::isFoe)) {
            double distance = owner.distanceToSqr(monster);
            if (distance < least) { best = monster; least = distance; }
        }
        return best;
    }
}
