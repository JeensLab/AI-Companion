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
import net.minecraft.world.level.pathfinder.Path;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Nor is one in sight that no walk of his leads to and that has not struck him ({@link #noWayTo}): {@link #LONGEST} is given to one foe at a
 * time, and there may be any number of them (run p20261031-d2/void-4: eight creepers on a shelf across a gap from his bridge, and he stood
 * on it with the sword for 6000 ticks).
 */
final class CompanionExpeditionFight {
    /** How far a mob in sight is met, and how far one that has chosen him. */
    private static final double ENGAGE = 9, AWARE = 16, CONTACT_SQR = 9, FALL_LIMIT = 3, FUSE_NEAR = 12, OUTRUN = .8, LONE_FOE = 5, PER_EXTRA_FOE = 3;
    private static final int LONGEST = 20 * 45, HURT_MEMORY = 100;
    /** The way to a foe is looked for again this often, and no farther round than this. */
    private static final int WAY_EVERY = 40;
    private static final float WAY_RANGE = 24;
    /** A zombie's blows come twenty ticks apart: a blow this recent from a foe in reach means it keeps up with the run ({@link #caught}). */
    private static final int CAUGHT_TICKS = 20;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private long since = -1, lastBusy, chasedSince;
    /** His own tick count when the retreat now under way began ({@link #caught}); below nought while he is not retreating. */
    private int retreatSince = -1;
    private Item tool;
    private LivingEntity chased;
    private final Set<UUID> givenUp = new HashSet<>();
    /** When the way to a foe was last looked for, and whether there was none. */
    private record Way(long at, boolean none) { }
    private final Map<UUID, Way> ways = new HashMap<>();

    CompanionExpeditionFight(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    /** True when the expedition must stand still this tick: a foe is to be fought. False lets the work (or the retreat) go on. */
    boolean hold(ServerLevel level, boolean retreating) {
        if (retreating) {
            if (retreatSince < 0) retreatSince = owner.tickCount;
            // The one that has caught him is the one to strike back, and the melee goal strikes its target only: a skeleton out of his reach
            // whose arrow came last would else stay his target, not fought on the retreat, with a zombie on his way up striking at will.
            LivingEntity by = owner.getLastHurtByMob();
            if (caught() && by != owner.getTarget() && isFoe(by)) owner.setTarget(by);
            return false;
        }
        retreatSince = -1;
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

    /** The fight alone, for a tick in which the work waits for a drop: true when a foe is fought. */
    boolean holdFight(ServerLevel level) { return w.expedition.active() && level.getGameTime() < w.expedition.stepEnd() && hold(level, w.expedition.retreating()); }

    /** A mob the expedition fights now, wherever it stands within sight: the melee goal goes for it even from afar. */
    boolean engages(LivingEntity target) { return w.expedition.active() && isFoe(target); }

    void fightBegan() { if (w.expedition.active()) began(); }

    void fightGoing() {
        if (!w.expedition.active()) return;
        going();
        CompanionChunkTicket.follow(owner);
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
        retreatSince = -1;
        tool = null;
        chased = null;
        givenUp.clear();
        ways.clear();
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
    private boolean letGo(LivingEntity foe) { return givenUp.contains(foe.getUUID()) && !struckBy(foe); }

    private boolean struckBy(LivingEntity foe) { return owner.getLastHurtByMob() == foe && owner.tickCount - owner.getLastHurtByMobTimestamp() < HURT_MEMORY; }

    /**
     * A foe in sight, out of his reach, that has not struck him and that no walk of his leads to (across a chasm, on a shelf or a pillar): the last
     * search said so, and it is made again every {@link #WAY_EVERY} ticks. Where no search can be made (he is in the air, as on every step of his
     * stairs) what was known stands, and a foe not yet looked at is a foe: one that comes for him is met with no wait for the ground.
     * A foe the bow takes now needs no walk: it is the bow's. One the bow does not take (nearer than it is drawn at, or while the blade is out
     * for someone on the line) is the sword's, and needs one.
     */
    private boolean noWayTo(LivingEntity foe) {
        if (owner.isWithinMeleeAttackRange(foe) || struckBy(foe) || owner.archery.takes(foe)) return false;
        long now = owner.level().getGameTime();
        Way known = ways.get(foe.getUUID());
        if (known != null && now - known.at() < WAY_EVERY) return known.none();
        if (!owner.hasLineOfSight(foe)) return false;
        Path path = ((CompanionNavigation) owner.getNavigation()).search(Set.of(foe.blockPosition()), 1, WAY_RANGE, true);
        if (path == null) return known != null && known.none();
        ways.put(foe.getUUID(), new Way(now, !path.canReach()));
        return !path.canReach();
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
        // The distance before the walk to it: a search for one costs the navigation its cells of the tick, and is not made for a mob too far to be a foe.
        double distance = owner.distanceTo(monster);
        if (!(monster.getTarget() == owner && distance <= AWARE || distance <= ENGAGE) || !owner.hasLineOfSight(monster)) return false;
        return !w.expedition.shuns(monster) && !below(monster);
    }

    /**
     * True when the melee goal must let go of this foe. Retreating, only one who has not caught up (the one who has is struck where he
     * stands, running from him would be a blow in the back), or, wounded and fleeing, one he outruns while the run is not {@link #caught}.
     * Otherwise at low health when leaving is possible.
     */
    boolean avoids(LivingEntity foe, boolean retreating) {
        if (foe == null) return false;
        if (letGo(foe)) return true;
        if (below(foe) || noWayTo(foe)) return true;
        if (retreating) return !owner.isWithinMeleeAttackRange(foe) || w.expedition.fleeing() && wounded() && outrun(foe) && !caught();
        return fleeable();
    }

    /**
     * A hostile foe within his reach struck him since the retreat began, less than {@link #CAUGHT_TICKS} ago: the run up is not leaving it
     * behind, whatever its speed (its blows throw him back, or it stands on his way up). The blow that sent him up does not count: an outrun
     * foe lands that one at the start. Run ct68 20261031 descent: sixty ticks of the retreat with zombies in reach and his swing ready, four
     * blows taken and none dealt.
     */
    private boolean caught() {
        LivingEntity by = owner.getLastHurtByMob();
        int at = owner.getLastHurtByMobTimestamp();
        return by instanceof Monster && by.isAlive() && !(by instanceof RangedAttackMob) && owner.isWithinMeleeAttackRange(by)
                && retreatSince >= 0 && at > retreatSince && owner.tickCount - at < CAUGHT_TICKS;
    }

    /** Low health, his way up under him, and a mob about that he can leave behind: whatever mob is on him, the work takes the turn to climb away (the melee goal would hold it). */
    boolean fleeable() { return wounded() && owner.level() instanceof ServerLevel level && hounded(level); }

    /** True when a mob is on him or on its way at low health and he can leave it: see {@link #canEscape}. */
    boolean hounded(ServerLevel level) {
        for (Monster monster : level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(ENGAGE), mob -> mob.isAlive() && !mob.isNoAi())) {
            if (!letGo(monster) && !below(monster) && owner.hasLineOfSight(monster) && owner.combat.fairTarget(monster, owner.combat.provokes(monster)) && leavable(monster)) return true;
        }
        return false;
    }

    /**
     * Health at which he leaves a fight. With nothing on him yet it is the very low level (the cave limit); one foe on him is fought lower, down to
     * {@link #LONE_FOE}; each foe more costs {@link #PER_EXTRA_FOE} health more, up to the wary level: a crowd gives no time to eat. A foe that
     * strikes hard (a zombie with a sword) is left while two of its blows are still to be taken, not one (run s7-d2/mobs: six and a half a blow,
     * the climb began at five health and the next blow ended it); a bare zombie's three leave the lone-foe level as it is.
     */
    boolean wounded() {
        List<Monster> on = onHim();
        double limit = on.isEmpty() ? CompanionCaveSense.HEALTH_VERY : Math.min(LONE_FOE + PER_EXTRA_FOE * (on.size() - 1), CompanionCaveSense.HEALTH_WARY);
        for (Monster foe : on) {
            double blow = foe.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? foe.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
            limit = Math.max(limit, Math.min(2 * blow - 1, CompanionCaveSense.HEALTH_WARY));
        }
        return owner.getHealth() <= limit;
    }

    /** The mobs he may fight within a blow of him. */
    private List<Monster> onHim() {
        if (!(owner.level() instanceof ServerLevel level)) return List.of();
        return level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(Math.sqrt(CONTACT_SQR)), mob -> mob.isAlive() && !mob.isNoAi() && !(mob instanceof Creeper) && !letGo(mob)
                && owner.hasLineOfSight(mob) && owner.combat.fairTarget(mob, owner.combat.provokes(mob)));
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
