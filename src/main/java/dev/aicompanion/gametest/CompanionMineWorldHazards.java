package dev.aicompanion.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;

import java.util.Comparator;
import java.util.List;

/** Development only. Puts a hazard into Oleg's own mine and tells whether it really came close to him. */
final class CompanionMineWorldHazards {
    static final int LIQUID_NEAR = 3, HOSTILE_NEAR = 8, SPAWN_MIN = 3, SPAWN_MAX = 9;
    private static final List<EntityType<? extends Mob>> PACK = List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON);

    private CompanionMineWorldHazards() { }

    static boolean liquidNear(ServerLevel level, BlockPos at, boolean lava) {
        var tag = lava ? FluidTags.LAVA : FluidTags.WATER;
        for (BlockPos cell : BlockPos.betweenClosed(at.offset(-LIQUID_NEAR, -LIQUID_NEAR, -LIQUID_NEAR), at.offset(LIQUID_NEAR, LIQUID_NEAR, LIQUID_NEAR))) {
            if (level.hasChunkAt(cell) && level.getFluidState(cell).is(tag)) return true;
        }
        return false;
    }

    static boolean hostileNear(ServerLevel level, LivingEntity who) {
        return !level.getEntitiesOfClass(Monster.class, who.getBoundingBox().inflate(HOSTILE_NEAR), Monster::isAlive).isEmpty();
    }

    /** Real hostile mobs on the air cells (his stairs, up and down the line) a few blocks from him; false when the mine has no such cells yet. */
    static boolean spawnPack(ServerLevel level, LivingEntity who, List<BlockPos> cells) {
        List<BlockPos> free = cells.stream()
                .filter(cell -> cell.distSqr(who.blockPosition()) >= SPAWN_MIN * SPAWN_MIN && cell.distSqr(who.blockPosition()) <= SPAWN_MAX * SPAWN_MAX)
                .filter(cell -> level.getBlockState(cell).isAir() && level.getBlockState(cell.above()).isAir() && level.getBlockState(cell.below()).isSolid())
                .sorted(Comparator.comparingDouble(cell -> cell.distSqr(who.blockPosition())))
                .toList();
        if (free.isEmpty()) return false;
        for (int i = 0; i < PACK.size(); i++) {
            Mob mob = PACK.get(i).spawn(level, free.get(i % free.size()), MobSpawnType.COMMAND);
            if (mob == null) continue;
            mob.setPersistenceRequired();
            mob.setTarget(who);
        }
        return true;
    }
}
