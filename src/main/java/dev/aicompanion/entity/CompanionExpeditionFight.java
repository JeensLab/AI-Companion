package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A hostile mob near him during an expedition is fought to the end with the sword in his hand, and the work goes on after it: while one is
 * about, the expedition does not dig, step or craft (the melee goal at its priority takes the turn, and this hold covers the ticks between
 * two kills, when no goal has a target yet). He leaves a fight only at low health, and only when leaving is possible: his own way up
 * is under him and no foe at his back is as fast as he is (a slower one, a zombie even on him, is left behind on the stairs: the retreat takes
 * the turn; a fight to the last health points is only for what he cannot get away from). The time of the fight is given back to the step, and
 * the tool he held comes back to his hand. A foe that is still alive after {@link #LONGEST} (one he sees and cannot get at, across a chasm or on a ledge) is let go for
 * the rest of the step, until it hurts him: the melee goal would otherwise hold the work for good, as the work does not tick while it runs.
 * A foe far below him is no foe: it cannot climb to him, and the chase to it is a step off his stairs or his bridge into its cave.
 */
final class CompanionExpeditionFight {
    /** How far a mob in sight is met, and how far one that has chosen him. */
    private static final double ENGAGE = 9, AWARE = 16, CONTACT_SQR = 9, FALL_LIMIT = 3, FUSE_NEAR = 12, OUTRUN = .8, LONE_FOE = 5, PER_EXTRA_FOE = 3;
    private static final int LONGEST = 20 * 45, HURT_MEMORY = 100;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private long since = -1, lastBusy, chasedSince;
    private Item tool;
    private LivingEntity chased;
    private final Set<UUID> givenUp = new HashSet<>();

    CompanionExpeditionFight(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    /** True when the expedition must stand still this tick: a foe is to be fought. False lets the work (or the retreat) go on. */
    boolean hold(ServerLevel level, boolean retreating) {
        if (retreating) return false;
        long now = level.getGameTime();
        // At low health with his way up under him and a mob on him that he can leave behind, the fight is over, whatever else is about: the climb takes the turn.
        if (fleeable()) { settle(now); return false; }
        Monster foe = nearest(level);
        if (foe == null) {
            Monster near = pressing(level);
            if (near == null) { settle(now); return false; }
            // No foe is chosen, yet nothing is dug or sealed and the pickaxe does not come back: the guard moves him, the sword stays.
            began();
            if (!worthFighting(near, now)) { settle(now); return false; }
            lastBusy = now;
            owner.selectWeaponForCombat();
            return true;
        }
        began();
        if (!worthFighting(foe, now)) { settle(now); return false; }
        lastBusy = now;
        LivingEntity target = owner.getTarget();
        if (target == null || !target.isAlive() || !isFoe(target) || target != foe && !inContact(target) && inContact(foe)) owner.setTarget(foe);
        owner.getNavigation().stop();
        owner.selectWeaponForCombat();
        return true;
    }

    /**
     * The mob for which the work waits with the sword in his hand, whoever is a foe: a lit fuse near him, or a hostile mob he has not let go
     * on him. A neutral spider or a warden is no such mob: he may not strike it, so nothing would ever end the wait.
     */
    private Monster pressing(ServerLevel level) {
        for (Monster monster : level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(FUSE_NEAR), mob -> mob.isAlive() && !mob.isNoAi())) {
            if (monster instanceof Creeper creeper && owner.creeperGuard.burns(creeper) && owner.distanceTo(creeper) < FUSE_NEAR) return monster;
            if (!letGo(monster) && inContact(monster) && owner.hasLineOfSight(monster) && !owner.combat.unarmedOnTask()
                    && owner.combat.fairTarget(monster, owner.combat.provokes(monster))) return monster;
        }
        return null;
    }

    /** The melee goal has taken the turn: from here on the fight is under way (called before the weapon is picked, so the tool in hand is still the work's). */
    void began() {
        if (since >= 0) return;
        since = lastBusy = owner.level().getGameTime();
        ItemStack hand = owner.getMainHandItem();
        tool = hand.isEmpty() || CompanionEquipment.isMelee(hand) ? null : hand.getItem();
    }

    void going() {
        long now = owner.level().getGameTime();
        lastBusy = now;
        if (!worthFighting(owner.getTarget(), now)) { owner.setTarget(null); settle(now); }
    }

    void reset() {
        since = -1;
        tool = null;
        chased = null;
        givenUp.clear();
    }

    /** False once the same foe has been fought for {@link #LONGEST} and lives: it is let go. */
    private boolean worthFighting(LivingEntity foe, long now) {
        if (foe == null) return true;
        if (foe != chased) { chased = foe; chasedSince = now; }
        if (now - chasedSince <= LONGEST) return true;
        givenUp.add(foe.getUUID());
        chased = null;
        return false;
    }

    /** A foe let go that has struck him since is a foe again. */
    private boolean letGo(LivingEntity foe) {
        return givenUp.contains(foe.getUUID()) && !(owner.getLastHurtByMob() == foe && owner.tickCount - owner.getLastHurtByMobTimestamp() < HURT_MEMORY);
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

    /** A mob he fights: hostile, one that may be fought, awake, in sight (not behind rock, where it can do nothing), and either come for him or near; not one he is leaving or may not follow. */
    boolean isFoe(LivingEntity entity) {
        if (!(entity instanceof Monster monster) || !monster.isAlive() || monster.isNoAi() || letGo(monster) || owner.combat.leavesAlone(monster)) return false;
        if (!owner.combat.fairTarget(monster, owner.combat.provokes(monster))) return false;
        // As anywhere: a creeper only when he fights it and it goes for him or the player; a blow wakes one that does not to a blast beside him.
        if (monster instanceof Creeper && (!owner.creeperGuard.fights() || !owner.combat.provokes(monster))) return false;
        if (w.expedition.shuns(monster) || below(monster)) return false;
        double distance = owner.distanceTo(monster);
        return owner.hasLineOfSight(monster) && (monster.getTarget() == owner && distance <= AWARE || distance <= ENGAGE);
    }

    /**
     * True when the melee goal must let go of this foe. Retreating, only one who has not caught up (the one who has is struck where he
     * stands, running from him would be a blow in the back). Otherwise at low health when leaving is possible.
     */
    boolean avoids(LivingEntity foe, boolean retreating) {
        if (foe == null) return false;
        if (letGo(foe)) return true;
        if (below(foe)) return true;
        if (retreating) return !owner.isWithinMeleeAttackRange(foe) || w.expedition.fleeing() && wounded() && outrun(foe);
        return fleeable();
    }

    /** Low health, his way up under him, and a mob about that he can leave behind: whatever mob is on him, the work takes the turn to climb away (the melee goal would hold it). */
    private boolean fleeable() { return wounded() && owner.level() instanceof ServerLevel level && hounded(level); }

    /** True when a mob is on him or on its way at low health and he can leave it: see {@link #canEscape}. */
    boolean hounded(ServerLevel level) {
        for (Monster monster : level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(ENGAGE), mob -> mob.isAlive() && !mob.isNoAi())) {
            if (!letGo(monster) && !below(monster) && owner.hasLineOfSight(monster) && owner.combat.fairTarget(monster, owner.combat.provokes(monster)) && leavable(monster)) return true;
        }
        return false;
    }

    /**
     * Health at which he leaves a fight. With nothing on him yet it is the very low level (the cave limit); one foe on him is fought lower, down to
     * {@link #LONE_FOE}; each foe more costs {@link #PER_EXTRA_FOE} health more, up to the wary level: a crowd gives no time to eat.
     */
    boolean wounded() {
        int near = onHim();
        double limit = near == 0 ? CompanionCaveSense.HEALTH_VERY : Math.min(LONE_FOE + PER_EXTRA_FOE * (near - 1), CompanionCaveSense.HEALTH_WARY);
        return owner.getHealth() <= limit;
    }

    /** How many mobs he may fight are within a blow of him. */
    private int onHim() {
        if (!(owner.level() instanceof ServerLevel level)) return 0;
        return level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(Math.sqrt(CONTACT_SQR)), mob -> mob.isAlive() && !mob.isNoAi() && !(mob instanceof Creeper) && !letGo(mob)
                && owner.hasLineOfSight(mob) && owner.combat.fairTarget(mob, owner.combat.provokes(mob))).size();
    }

    private boolean leavable(LivingEntity foe) { return wounded() && canEscape(foe); }

    /** A foe that cannot keep up with him on the way up: the blow it lands at the start is the last one. */
    private boolean outrun(LivingEntity foe) {
        return !(foe instanceof RangedAttackMob) && foe.getAttributeValue(Attributes.MOVEMENT_SPEED) <= owner.getAttributeValue(Attributes.MOVEMENT_SPEED) * OUTRUN;
    }

    private boolean inContact(LivingEntity foe) { return owner.distanceToSqr(foe) < CONTACT_SQR; }

    private boolean below(LivingEntity foe) { return foe.getY() < owner.getY() - FALL_LIMIT; }

    /** His own way up under him, not at the entrance already, the foe not one that shoots, and one that is on him slower than he is. */
    private boolean canEscape(LivingEntity foe) {
        if (foe instanceof RangedAttackMob || inContact(foe) && !outrun(foe) || !w.mine.exists()) return false;
        List<BlockPos> way = CompanionExpeditionClimb.wayUp(w.mine);
        int at = CompanionExpeditionRetreat.closest(way, owner.blockPosition());
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
