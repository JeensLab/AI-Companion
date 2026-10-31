package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;

import java.util.List;

/**
 * A creeper in sight underground, nearer than {@link #NEAR}: he goes back up his own way for {@link #BACK} cells, waits a moment
 * for it to lose interest or wander off, and goes on. The fight is not his (see the calm in the cave episode), distance is.
 * After the wait the creeper is left alone for a while, so one that stays in the stairs does not keep him going up and down.
 */
final class CompanionExpeditionCreeper {
    private enum Phase { IDLE, BACK, WAIT }

    static final double NEAR = 6;
    static final int BACK = 6, WAIT = 60, COOLDOWN = 20 * 30, LOOK_EVERY = 5;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionWalk walk;
    private Phase phase = Phase.IDLE;
    private long until, quietUntil;

    CompanionExpeditionCreeper(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.owner = w.owner;
        this.walk = new CompanionExpeditionWalk(dig);
    }

    /** True while this tick is his: he is stepping back or waiting. */
    boolean tick(ServerLevel level) {
        long now = level.getGameTime();
        switch (phase) {
            case BACK -> {
                if (walk.tick() != CompanionExpeditionWalk.Result.WORKING) { phase = Phase.WAIT; until = now + WAIT; }
                return true;
            }
            case WAIT -> {
                if (now < until) return true;
                phase = Phase.IDLE;
                quietUntil = now + COOLDOWN;
                return false;
            }
            default -> {
                if (now < quietUntil || now % LOOK_EVERY != 0 || !w.mine.exists() || !seen(level)) return false;
                List<BlockPos> way = CompanionExpeditionClimb.wayUp(w.mine);
                int at = CompanionExpeditionClimb.nearest(way, owner.blockPosition());
                if (at < 0) return false;
                walk.set(way.subList(0, Math.min(at + BACK, way.size() - 1) + 1), at);
                phase = Phase.BACK;
                owner.workStatus("Рядом крипер, отхожу назад по лестнице.");
                return true;
            }
        }
    }

    private boolean seen(ServerLevel level) {
        return !level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(NEAR),
                creeper -> creeper.isAlive() && creeper.distanceTo(owner) < NEAR && owner.hasLineOfSight(creeper)).isEmpty();
    }

    void reset() {
        walk.clear();
        phase = Phase.IDLE;
        until = quietUntil = 0;
    }
}
