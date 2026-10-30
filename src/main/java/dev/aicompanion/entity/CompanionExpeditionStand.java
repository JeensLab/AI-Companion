package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Where he stands to plug a cave cell: a cell of his own mine from which the cell can be reached and seen. */
final class CompanionExpeditionStand {
    private CompanionExpeditionStand() { }

    /** An open cell of {@code mine} from which the target can be reached and seen; away from a creeper in {@code air} if there is one. Null when there is none yet. */
    static BlockPos standFor(CompanionEntity owner, ServerLevel level, Set<BlockPos> mine, Set<BlockPos> air, BlockPos target) {
        Vec3 creeper = creeperAt(owner, level, air);
        BlockPos best = null;
        double bestKey = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-3, -3, -3), target.offset(3, 3, 3))) {
            if (!mine.contains(p) || !CompanionShaftPlan.open(level, p) || !CompanionShaftPlan.open(level, p.above())) continue;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
            if (!CompanionShaftPlan.clearTo(level, new Vec3(p.getX() + .5, p.getY() + 1.62, p.getZ() + .5), target)) continue;
            double key = creeper == null ? owner.distanceToSqr(Vec3.atBottomCenterOf(p)) : -creeper.distanceToSqr(Vec3.atBottomCenterOf(p));
            if (key < bestKey) { best = p.immutable(); bestKey = key; }
        }
        return best;
    }

    private static Vec3 creeperAt(CompanionEntity owner, ServerLevel level, Set<BlockPos> air) {
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(CompanionCaveSense.WATCH_RADIUS))) {
            if (creeper.isAlive() && air.contains(creeper.blockPosition())) return creeper.position();
        }
        return null;
    }
}
