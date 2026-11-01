package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where he stands in a melee, as a player does it: a sword reaches farther than a zombie's arm, so between two swings he steps back out of
 * the arm's reach and the zombie spends its time catching up instead of striking. And what is on him is met before what is not: a zombie
 * in reach is the target while he is after a skeleton across the cave, and a drop is not picked up with a zombie at his back.
 * Nothing here moves him while the sword is ready (he closes in and strikes), while the shield is up, or away from a creeper (that is
 * {@link CompanionCreeperGuard}'s turn, at a higher priority).
 */
final class CompanionFootwork {
    /** How near a foe must be for the step back, how near is on him for the pickup, how far from where the fight began he goes, the pace of the step, how far he looks, the gap a step must win. */
    private static final double CLOSE = 2.6, ON_HIM = 3, LEASH = 6, SPEED = 1.2, LOOKOUT = 5, GAIN = .3;
    private static final int STEP_SAMPLES = 3;
    private static final double STEP_LENGTH = .5;
    /** The courses tried for the step: straight away from the foe, then turned off it. */
    private static final double[] TURNS = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};

    private final CompanionEntity owner;
    private Vec3 anchor;

    CompanionFootwork(CompanionEntity owner) { this.owner = owner; }

    /** A fight begins here: the steps back keep within {@link #LEASH} of this spot. */
    void begin() { anchor = owner.position(); }

    /** True when he stepped back this tick. Only between two swings, and only from a foe that is within its arm's reach of him. */
    boolean keepOut(ServerLevel level, boolean swordReady) {
        if (swordReady || anchor == null || owner.isBlocking()) return false;
        List<Monster> foes = foes(level, LOOKOUT);
        Monster nearest = null;
        double least = CLOSE * CLOSE;
        for (Monster foe : foes) {
            double distance = owner.distanceToSqr(foe);
            if (distance < least) { nearest = foe; least = distance; }
        }
        if (nearest == null) return false;
        double awayX = owner.getX() - nearest.getX(), awayZ = owner.getZ() - nearest.getZ();
        double length = Math.hypot(awayX, awayZ);
        if (length < 1.0E-3) return false;
        double bestGap = gap(owner.position(), foes) + GAIN;
        Vec3 best = null;
        for (double turn : TURNS) {
            double dx = (awayX * Math.cos(turn) - awayZ * Math.sin(turn)) / length, dz = (awayX * Math.sin(turn) + awayZ * Math.cos(turn)) / length;
            Vec3 end = CompanionCreeperGuard.runTo(owner, level, dx, dz, STEP_SAMPLES, STEP_LENGTH);
            if (end == null || end.distanceToSqr(anchor.x, end.y, anchor.z) > LEASH * LEASH) continue;
            double gap = gap(end, foes);
            if (gap > bestGap) { best = end; bestGap = gap; }
        }
        if (best == null) return false;
        owner.getNavigation().stop();
        CompanionCreeperGuard.faceTowards(owner, best);
        owner.getMoveControl().setWantedPosition(best.x, best.y, best.z, SPEED);
        return true;
    }

    /** A foe within reach of his blade while the one he is after is not: the one that can be struck now. Null when the target itself is in reach. */
    LivingEntity nearer(ServerLevel level, LivingEntity target) {
        if (target != null && owner.isWithinMeleeAttackRange(target)) return null;
        Monster best = null;
        double least = Double.MAX_VALUE;
        for (Monster foe : foes(level, ON_HIM + 1)) {
            double distance = owner.distanceToSqr(foe);
            if (foe != target && owner.isWithinMeleeAttackRange(foe) && distance < least) { best = foe; least = distance; }
        }
        return best;
    }

    /** A hostile mob he may fight is within a blow of him. */
    boolean onHim(ServerLevel level) {
        for (Monster foe : foes(level, ON_HIM)) if (owner.distanceToSqr(foe) < ON_HIM * ON_HIM) return true;
        return false;
    }

    /** The least distance from the spot to a foe: the step back is the one that leaves him farthest from all of them. */
    private static double gap(Vec3 spot, List<Monster> foes) {
        double least = Double.MAX_VALUE;
        for (Monster foe : foes) least = Math.min(least, Math.sqrt(foe.distanceToSqr(spot)));
        return least;
    }

    /** The mobs about that strike with a blade or a fist: awake, in sight, fair game and not for the guard (a creeper) or the bow (a skeleton that has one). */
    private List<Monster> foes(ServerLevel level, double range) {
        return level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(range), foe -> foe.isAlive() && !foe.isNoAi() && !(foe instanceof Creeper)
                && !(foe instanceof RangedAttackMob && foe.getMainHandItem().getItem() instanceof ProjectileWeaponItem)
                && owner.combat.fairTarget(foe, owner.combat.provokes(foe)) && !owner.work.expedition.shuns(foe) && owner.hasLineOfSight(foe));
    }
}
