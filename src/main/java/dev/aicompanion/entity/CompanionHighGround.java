package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;

import java.util.EnumSet;
import java.util.List;

/**
 * A crowd after him is not met on its own ground, as a player does not: he lays two blocks from his bag under his feet and strikes down
 * from the column, where the arm of a zombie does not reach him and his blade reaches it. In the open he keeps out of reach between two
 * swings anyway; in a corner or a passage there is nowhere to step back to, and each of three zombies lands its blow (run ct78 20261031
 * mobs). Probes of 23.11, five zombies and a stone sword: a corner 12–15 blows taken, his own tunnel 14–18 and a death in four, a column
 * none. The column is the one of {@link CompanionPillar}: real blocks, a jump for each, taken down and back into the bag when the crowd is
 * dead or gone. Where it cannot stand (no blocks, a low roof, no floor) he fights as before. A creeper is still for the guard, at a
 * higher priority.
 */
final class CompanionHighGround extends Goal {
    /** How many of them after him are a crowd, and how many when he is already hurt; how high he stands. */
    private static final int CROWD = 3, HURT_CROWD = 2, HEIGHT = 2;
    /** How far he counts them. */
    private static final double LOOKOUT = 8;
    /** How long with nobody about before he comes down, and how long after a column that did not stand before the next one is tried. */
    private static final int CALM_TICKS = 40, RETRY_TICKS = 200;
    /** How often they are counted, and how long he stands with none of them in his reach (behind a fence, across a gap) before he comes down. */
    private static final int COUNT_EVERY = 5, PATIENCE = 20 * 30, AFTER_PATIENCE = 20 * 60;

    private final CompanionEntity c;
    private boolean raised, over;
    private int calm, idle, retryAt;

    CompanionHighGround(CompanionEntity c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        if (c.tickCount < retryAt || c.tickCount % COUNT_EVERY != 0 || c.inventoryViewers != 0 || !c.onGround() || c.temporaryPillar.isActive() || c.temporaryPillar.isRecovering()
                || !(c.level() instanceof ServerLevel level)) return false;
        int after = 0;
        for (Monster foe : c.combat.footwork.foes(level, LOOKOUT)) if (foe.getTarget() == c) after++;
        return after >= (c.getHealth() <= CompanionCaveSense.HEALTH_WARY ? HURT_CROWD : CROWD);
    }

    /** An order of the player (stop, follow) takes the column down at once: that descent is the recovery goal's turn. */
    @Override public boolean canContinueToUse() { return !over && !c.temporaryPillar.isRecovering(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        raised = over = false;
        calm = idle = 0;
        // Before the block for the column is in his hand: the tool of the work is what the fight gives back.
        c.work.expedition.fight.fightBegan();
        c.getNavigation().stop();
        if (c.hasActiveWorkOrder()) c.workStatus("Их много: встаю на два блока, сверху меня не достанут.");
    }

    @Override public void tick() {
        if (!(c.level() instanceof ServerLevel level)) return;
        c.work.expedition.fight.fightGoing();
        if (!raised) {
            CompanionPillar.Outcome outcome = c.temporaryPillar.tickUp(HEIGHT);
            if (outcome == CompanionPillar.Outcome.FAILED) { retryAt = c.tickCount + RETRY_TICKS; over = true; }
            if (outcome != CompanionPillar.Outcome.READY) return;
            raised = true;
        }
        c.getNavigation().stop();
        // Thrown off it (an arrow, a blast): the fight below is the melee goal's turn again, and the column is taken down after it.
        if (c.onGround() && !c.temporaryPillar.onTop()) { over = true; return; }
        List<Monster> foes = c.combat.footwork.foes(level, LOOKOUT);
        if (foes.isEmpty()) { over = ++calm > CALM_TICKS; return; }
        calm = 0;
        c.selectWeaponForCombat();
        Monster nearest = foes.get(0);
        for (Monster foe : foes) if (c.distanceToSqr(foe) < c.distanceToSqr(nearest)) nearest = foe;
        c.setTarget(nearest);
        c.getLookControl().setLookAt(nearest);
        c.combat.strike(nearest);
        if (c.isWithinMeleeAttackRange(nearest)) idle = 0;
        else if (++idle > PATIENCE) { retryAt = c.tickCount + AFTER_PATIENCE; over = true; }
    }

    @Override public void stop() {
        if (c.temporaryPillar.isActive() && !c.temporaryPillar.isRecovering()) c.temporaryPillar.requestCleanup();
    }
}
