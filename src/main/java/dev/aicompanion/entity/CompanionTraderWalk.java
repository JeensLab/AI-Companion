package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The walk to one trader, shared by the round of scouting and by a trade: far off in legs, then to a cell within a hand's reach of
 * him with a clear line, over a fence or through the gap of a trading hall's cell; given up when he gets no closer for a while.
 */
final class CompanionTraderWalk {
    /** From his eye to the trader's box: a hand's reach. */
    static final double REACH = 3.0;
    /** Without getting closer for this long the trader is given up (a wall, a fence all round, no way). */
    private static final int STUCK_TICKS = 200;

    enum Step { WALKING, GAVE_UP }

    private final CompanionWork w;
    private long progressAt, nextPath;
    private double best;
    private Path path;

    CompanionTraderWalk(CompanionWork w) { this.w = w; }

    /** A new trader to walk to. */
    void begin(long now) { best = Double.MAX_VALUE; progressAt = now; nextPath = 0; path = null; }

    /** Forgets the walk and stops him: the trader is done with or given up. */
    void end() { path = null; w.route.legsEnd(false); w.route.stop(); }

    boolean inReach(AbstractVillager trader) {
        return trader.getBoundingBox().distanceToSqr(w.owner.getEyePosition()) <= REACH * REACH && w.owner.hasLineOfSight(trader);
    }

    Step approach(ServerLevel level, AbstractVillager trader, long now) {
        double distance = trader.getBoundingBox().distanceToSqr(w.owner.getEyePosition());
        if (distance + .05 < best) { best = distance; progressAt = now; }
        if (now - progressAt > STUCK_TICKS) return Step.GAVE_UP;
        // Far off: the single route walks him in legs to a few blocks from the trader; the last steps are chosen for the hand's reach.
        if (trader.distanceToSqr(w.owner) > 6 * 6) {
            w.owner.workStatus("Иду к торговцу.");
            switch (w.route.legs(trader.blockPosition(), 3)) {
                case WALKING -> { }
                case NEAR -> w.route.legsEnd(true);
                case FAILED -> { return Step.GAVE_UP; }
            }
            return Step.WALKING;
        }
        if (now >= nextPath && (path == null || w.route.legOver())) {
            path = standPath(level, trader);
            nextPath = now + 10;
            if (path == null || !path.canReach()) { path = null; w.owner.workStatus("Не могу подойти к торговцу."); return Step.WALKING; }
        }
        if (path == null) return Step.WALKING;
        w.owner.workStatus("Подхожу к торговцу.");
        w.route.walk(path);
        return Step.WALKING;
    }

    /** Cells within a hand's reach of the trader with a clear line to him, over a fence too. */
    private Path standPath(ServerLevel level, AbstractVillager trader) {
        Set<BlockPos> feet = new HashSet<>();
        BlockPos at = trader.blockPosition();
        for (int dy = -2; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos pos = at.offset(dx, dy, dz);
            if (!CompanionMiningNavigation.standable(w.owner, pos)) continue;
            Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, w.owner.getEyeHeight(), 0);
            if (trader.getBoundingBox().distanceToSqr(eye) > (REACH - .2) * (REACH - .2)) continue;
            if (level.clip(new ClipContext(eye, trader.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w.owner)).getType() != HitResult.Type.MISS) continue;
            feet.add(pos);
        }
        return feet.isEmpty() ? null : w.route.toCells(feet);
    }
}
