package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    /** How near a foe must be for the step back, how near is on him, how far from where the fight began he goes, the pace of the step, how far he looks, the gap a step must win. */
    private static final double CLOSE = 2.6, ON_HIM = 3, LEASH = 6, SPEED = 1.2, LOOKOUT = 5, GAIN = .3;
    /** What a skeleton's arrows take from him in a second at point-blank range, and how much more a foe must hurt for the target to be changed. */
    private static final double BOW_DAMAGE = 2, SWITCH = 1.25;
    /** The target stays while it is within his reach plus this margin, or for this many ticks after a blow of his: a knocked-back foe is not swapped for the next one. */
    private static final double REACH = 3, STICKY = 1.5;
    private static final int STRUCK_TICKS = 20;
    /** How far from every foe the withdrawal of a man with no blade ends. */
    static final double SAFE = 4.5;
    private static final int STEP_SAMPLES = 3;
    private static final double STEP_LENGTH = .5;
    /** The courses tried for the step: straight away from the foe, then turned off it. */
    private static final double[] TURNS = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};

    private final CompanionEntity owner;
    private Vec3 anchor;
    /** The target the anchor was set for, and whether he has been in its reach since. */
    private LivingEntity anchoredTo;
    private boolean engaged;
    /** The foe struck last and when: a target he has just struck stays the target while knockback pushes it out of reach. */
    private LivingEntity struck;
    private int struckAt;

    CompanionFootwork(CompanionEntity owner) { this.owner = owner; }

    /** A fight begins here: the steps back keep within {@link #LEASH} of this spot. */
    void begin() { anchor = owner.position(); }

    /** The anchor follows the fight: a new target, or the moment he first gets into reach of it, sets it where he stands (a fight met far from where it began still steps back). */
    void follow(LivingEntity target) {
        if (target != anchoredTo) { anchoredTo = target; engaged = false; }
        if (target != null && !engaged && owner.isWithinMeleeAttackRange(target)) { engaged = true; anchor = owner.position(); }
    }

    void struck(LivingEntity target) { struck = target; struckAt = owner.tickCount; }

    /** True when he stepped back this tick. Only between two swings, and only from a foe that is within its arm's reach of him. */
    boolean keepOut(ServerLevel level, boolean swordReady) {
        return !swordReady && stepBack(level, CLOSE);
    }

    /** True when he stepped back this tick from a foe within {@link #SAFE}: with no blade the foe is left behind, not fought. */
    boolean withdraw(ServerLevel level) { return stepBack(level, SAFE); }

    private boolean stepBack(ServerLevel level, double reach) {
        if (anchor == null || owner.isBlocking()) return false;
        List<Monster> foes = foes(level, LOOKOUT);
        Monster nearest = null;
        double least = reach * reach;
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
            Vec3 end = runTo(owner, level, dx, dz, STEP_SAMPLES, STEP_LENGTH);
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

    /**
     * The foe to strike instead of the target. Out of reach of the target: the nearest one within reach of the blade. In reach of it: only a foe
     * that hurts him clearly more for its health (the blows a second over the health it has left), so that a skeleton he is finishing does not
     * keep him from the zombie on his back. Null when the target stays.
     */
    LivingEntity nearer(ServerLevel level, LivingEntity target) {
        boolean reached = target != null && owner.isWithinMeleeAttackRange(target);
        if (reached && !(target instanceof Monster)) return null;
        if (!reached && target instanceof Monster && target.isAlive() && (owner.tickCount - struckAt <= STRUCK_TICKS && target == struck || owner.distanceToSqr(target) <= (REACH + STICKY) * (REACH + STICKY))) return null;
        double bar = reached ? threat(target) * SWITCH : -Double.MAX_VALUE;
        Monster best = null;
        for (Monster foe : foes(level, ON_HIM + 1)) {
            if (foe == target || !owner.isWithinMeleeAttackRange(foe)) continue;
            double score = reached ? threat(foe) : -owner.distanceToSqr(foe);
            if (score > bar) { best = foe; bar = score; }
        }
        return best;
    }

    /** What a foe takes from him in a second for each health it has left: the one to strike first is the one that gives the most of it. */
    private static double threat(LivingEntity foe) {
        boolean bow = foe instanceof RangedAttackMob && foe.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
        double blows = bow ? BOW_DAMAGE : foe.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? foe.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        return blows / Math.max(1, foe.getHealth());
    }

    boolean onHim(ServerLevel level) { return near(level, ON_HIM); }

    /** A hostile mob he may fight is within this many blocks of him. */
    boolean near(ServerLevel level, double range) {
        for (Monster foe : foes(level, range)) if (owner.distanceToSqr(foe) < range * range) return true;
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

    /** A run along a heading takes a rise or a drop of one block as a step. */
    private static final double[] RISES = {0, 1, -1};
    private static final double EDGE = .02, SETTLED = .001;

    /** Where a run along the heading ends, or null where the floor fails him, a wall stops him or a fluid lies in the way. */
    private static Vec3 runTo(CompanionEntity owner, ServerLevel level, double dx, double dz, int samples, double step) {
        Vec3 from = owner.position(), at = from;
        for (int i = 1; i <= samples; i++) {
            double x = from.x + dx * i * step, z = from.z + dz * i * step;
            Vec3 next = null;
            for (double rise : RISES) {
                if (stands(owner, level, x, at.y + rise, z)) { next = new Vec3(x, at.y + rise, z); break; }
            }
            if (next == null || next.y < from.y - 1 || next.y > from.y + 1) return null;
            at = next;
        }
        return at;
    }

    /** He can stand with his feet at this point: his body fits there, no fluid is in it and some part of his footprint rests on a solid floor. */
    private static boolean stands(CompanionEntity owner, ServerLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + SETTLED, z);
        if (!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        if (level.getBlockCollisions(owner, owner.getBoundingBox().move(x - owner.getX(), y - owner.getY(), z - owner.getZ()).deflate(SETTLED)).iterator().hasNext()) return false;
        if (!harmless(level, feet)) return false;
        double half = owner.getBbWidth() / 2 - EDGE;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos under = new BlockPos(Mth.floor(x + sx * half), feet.getY() - 1, Mth.floor(z + sz * half));
                if (!level.hasChunkAt(under) || level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) return true;
            }
        }
        return false;
    }

    /** Nothing about the cell (his footprint and the cells round it) burns, pricks or poisons him: no lava under, beside or in it, no fire, magma, berry bush, wither rose, powder snow or cactus. */
    private static boolean harmless(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) if (level.getFluidState(feet.offset(dx, dy, dz)).is(FluidTags.LAVA)) return false;
            if (level.getBlockState(feet.offset(dx, -1, dz)).is(Blocks.MAGMA_BLOCK)) return false;
            for (int dy = 0; dy <= 1; dy++) if (hurts(level.getBlockState(feet.offset(dx, dy, dz)))) return false;
        }
        return true;
    }

    private static boolean hurts(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.CACTUS);
    }
}
