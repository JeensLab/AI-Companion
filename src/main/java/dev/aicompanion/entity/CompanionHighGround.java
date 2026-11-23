package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.EnumSet;
import java.util.List;

/**
 * A crowd after him is not met on its own ground, as a player does not: he lays two blocks from his bag under his feet and strikes down
 * from the column, where the arm of a zombie does not reach him and his blade reaches it. In the open he keeps out of reach between two
 * swings anyway; in a corner or a passage there is nowhere to step back to, and each of three zombies lands its blow (run ct78 20261031
 * mobs). Probes of 23.11, five zombies and a stone sword: a corner 12–15 blows taken, his own tunnel 14–18 and a death in four, a column
 * none. The column is the one of {@link CompanionPillar}: real blocks, a jump for each, taken down and back into the bag when the crowd is
 * dead or gone. Where it cannot stand (no blocks, a low roof, no floor) the goal does not start and he fights as before. A creeper is still
 * for the guard, at a higher priority; under a guard order he stays on the ground, where the player is, and so he does with a skeleton
 * shooting at him: a column is a stand for its arrows.
 * <p>
 * The column does not hold him: a mob that reaches him on it (from a ledge beside it, a spider up its side), an arrow or a blast that
 * throws him off, the guard that runs him from a fuse, all give the fight back to the melee goal, and the column is left standing. Its
 * blocks are taken back (the descent of {@link CompanionPillar} walks him to it) only from near by, with nobody after him and no retreat
 * of his mine under way; from afar, or after {@link #LEFT_PATIENCE}, they stay where they are, like any block a player leaves behind,
 * and nothing waits for them.
 */
final class CompanionHighGround extends Goal {
    private enum Phase { RAISE, STAND }

    /**
     * How many of them after him are a crowd, and how many when he is already hurt; how high he stands: three blocks where the roof lets
     * him, as a player does (a zombie that jumps at the column does not reach him there), else two, where a jump may land a blow now and then.
     */
    private static final int CROWD = 3, HURT_CROWD = 2, TALL = 3, LOW = 2;
    /** A mob that struck him on the column from this much above its foot stands where it reaches him: a ledge beside it, a spider up its side. */
    private static final double REACHES_FROM = .9;
    /** How far from the middle of the top block he lets a blow move him before he steps back to it, and the pace of that step. */
    private static final double OFF_CENTRE_SQR = .2 * .2, CENTRE_SPEED = .3;
    /** How far he counts them, and how near one presses on him so that his blow must throw it back before a block goes under his feet. */
    private static final double LOOKOUT = 10, TOUCH_SQR = 1.5 * 1.5;
    /** How far from a column left standing he still comes back for its blocks. */
    private static final double COME_BACK_SQR = 8 * 8;
    /** How far a mob with a bow that is after him keeps him on the ground. */
    private static final double ARCHERS = 20;
    /** How long with nobody about before he comes down, and how long after a column that did not stand before the next one is tried. */
    private static final int CALM_TICKS = 40, RETRY_TICKS = 200;
    /** How often they are counted, and how long he stands with none of them in his reach (behind a fence, across a gap) before he comes down. */
    private static final int COUNT_EVERY = 5, PATIENCE = 20 * 30, AFTER_PATIENCE = 20 * 60;
    /** How long a column left standing is his at all. */
    private static final int LEFT_PATIENCE = 20 * 30;

    private final CompanionEntity c;
    private Phase phase = Phase.RAISE;
    /** The goal is over; the column comes down now (nobody about), or is left standing. */
    private boolean over, takeDown;
    private int calm, idle, retryAt, hurtAt, height;
    /** His own tick at which the column was left standing. */
    private int leftAt;

    CompanionHighGround(CompanionEntity c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        if (c.tickCount % COUNT_EVERY != 0 || c.inventoryViewers != 0 || !c.onGround() || c.temporaryPillar.isRecovering() || !(c.level() instanceof ServerLevel level)) return false;
        if (c.temporaryPillar.leftStanding()) { settle(level); return false; }
        // With no blade on a task he walks away from a monster (CompanionCombat.StepOffGoal): a column would only hold him among them.
        if (c.tickCount < retryAt || c.temporaryPillar.isActive() || CompanionGuardWatch.watching(c) || c.combat.unarmedOnTask() || shotAt(level)) return false;
        int after = 0;
        for (Monster foe : c.combat.footwork.foes(level, LOOKOUT)) if (foe.getTarget() == c) after++;
        if (after < (c.getHealth() <= CompanionCaveSense.HEALTH_WARY ? HURT_CROWD : CROWD)) return false;
        height = c.temporaryPillar.canStandUp(TALL) ? TALL : LOW;
        return height == TALL || c.temporaryPillar.canStandUp(LOW);
    }

    /**
     * What becomes of the column left standing: taken down once nobody is after him, his mine is not being left, and it is near. From
     * afar, or when the wait has been long, its blocks are left for good: a walk back through the mobs, or against his own retreat, costs
     * more than they do.
     */
    private void settle(ServerLevel level) {
        BlockPos foot = c.temporaryPillar.foot();
        boolean far = foot == null || c.distanceToSqr(foot.getX() + .5, foot.getY(), foot.getZ() + .5) > COME_BACK_SQR;
        if (c.tickCount - leftAt > LEFT_PATIENCE) c.temporaryPillar.abandon();
        else if (c.work.expedition.retreating() || sought(level)) return;
        else if (far) c.temporaryPillar.abandon();
        else c.temporaryPillar.requestCleanup();
    }

    /** A monster that sees him is after him. */
    private boolean sought(ServerLevel level) {
        return !level.getEntitiesOfClass(Monster.class, c.getBoundingBox().inflate(LOOKOUT), foe -> foe.isAlive() && !foe.isNoAi() && foe.getTarget() == c && c.hasLineOfSight(foe)).isEmpty();
    }

    /** A mob with a bow is after him and sees him: on a column he would stand still for its arrows, on the ground he closes in on it or raises the shield. */
    private boolean shotAt(ServerLevel level) {
        return !level.getEntitiesOfClass(Monster.class, c.getBoundingBox().inflate(ARCHERS), foe -> foe.isAlive() && !foe.isNoAi() && foe.getTarget() == c
                && foe instanceof RangedAttackMob && foe.getMainHandItem().getItem() instanceof ProjectileWeaponItem && c.hasLineOfSight(foe)).isEmpty();
    }

    /** An order of the player (stop, follow) takes the column down at once: that descent is the turn of the recovery goal. */
    @Override public boolean canContinueToUse() { return !over && !c.temporaryPillar.isRecovering(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        over = takeDown = false;
        calm = idle = 0;
        phase = Phase.RAISE;
        c.getNavigation().stop();
        // Before the block for the column is in his hand: the tool of the work is what the fight gives back.
        c.work.expedition.fight.fightBegan();
        if (c.hasActiveWorkOrder()) c.workStatus("Их много: встаю на столбик из своих блоков, сверху меня не достанут.");
    }

    @Override public void tick() {
        if (!(c.level() instanceof ServerLevel level)) return;
        List<Monster> foes = c.combat.footwork.foes(level, LOOKOUT);
        Monster nearest = null;
        for (Monster foe : foes) if (nearest == null || c.distanceToSqr(foe) < c.distanceToSqr(nearest)) nearest = foe;
        switch (phase) {
            case RAISE -> raise(nearest);
            case STAND -> stand(nearest);
        }
    }

    private void raise(Monster nearest) {
        c.work.expedition.fight.fightGoing();
        // A mob pressed against him stands in the cell the block must take: his blow throws it back, and the jump follows.
        if (nearest != null && c.distanceToSqr(nearest) < TOUCH_SQR && c.combat.swingReady() && c.onGround()) {
            c.selectWeaponForCombat();
            c.getLookControl().setLookAt(nearest);
            c.combat.strike(nearest);
        }
        CompanionPillar.Outcome outcome = c.temporaryPillar.tickUp(height);
        if (outcome == CompanionPillar.Outcome.FAILED) { retryAt = c.tickCount + RETRY_TICKS; over = true; }
        if (outcome != CompanionPillar.Outcome.READY) return;
        phase = Phase.STAND;
        hurtAt = c.getLastHurtByMobTimestamp();
    }

    private void stand(Monster nearest) {
        c.work.expedition.fight.fightGoing();
        c.getNavigation().stop();
        BlockPos foot = c.temporaryPillar.foot();
        // Thrown off it (a blast), shot on it, or struck on it by a mob that stands where it reaches him: the fight is the turn of the melee
        // goal again. A zombie that jumps at the column lands a blow now and then (in the air, from the floor): far fewer than on the ground.
        boolean reached = false;
        if (c.getLastHurtByMobTimestamp() != hurtAt) {
            hurtAt = c.getLastHurtByMobTimestamp();
            reached = foot != null && c.getLastHurtByMob() instanceof Monster by && (by instanceof RangedAttackMob || by.onGround() && by.getY() >= foot.getY() + REACHES_FROM);
        }
        if (reached || c.onGround() && !c.temporaryPillar.onTop()) { retryAt = c.tickCount + RETRY_TICKS; over = true; return; }
        if (c.onGround() && foot != null && c.distanceToSqr(foot.getX() + .5, c.getY(), foot.getZ() + .5) > OFF_CENTRE_SQR) c.getMoveControl().setWantedPosition(foot.getX() + .5, c.getY(), foot.getZ() + .5, CENTRE_SPEED);
        if (nearest == null) { over = takeDown = ++calm > CALM_TICKS; return; }
        calm = 0;
        c.selectWeaponForCombat();
        c.setTarget(nearest);
        c.getLookControl().setLookAt(nearest);
        c.combat.strike(nearest);
        if (c.isWithinMeleeAttackRange(nearest)) idle = 0;
        else if (++idle > PATIENCE) { retryAt = c.tickCount + AFTER_PATIENCE; over = takeDown = true; }
    }

    @Override public void stop() {
        c.getNavigation().stop();
        CompanionPillar pillar = c.temporaryPillar;
        if (!pillar.isActive() || pillar.isRecovering()) return;
        // With no support laid there is nothing to come back for: the cleanup only forgets the column.
        if (takeDown || !pillar.hasSupports()) { pillar.requestCleanup(); return; }
        if (!pillar.leftStanding()) { pillar.leaveStanding(); leftAt = c.tickCount; }
    }
}
