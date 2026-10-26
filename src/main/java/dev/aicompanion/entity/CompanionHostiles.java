package dev.aicompanion.entity;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;

public final class CompanionHostiles {
    private CompanionHostiles() { }
    public static boolean observed(net.minecraft.world.entity.LivingEntity observer, net.minecraft.world.entity.monster.Monster monster, double radius) {
        return monster.isAlive() && observer.distanceToSqr(monster) <= radius * radius && observer.hasLineOfSight(monster);
    }
    public static void join(Creeper creeper) {
        // Keep vanilla fuse/explosion rules, choosing the nearest eligible player OR companion.
        // A lower-priority companion-only goal would never run while an owner was also nearby.
        creeper.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(creeper, net.minecraft.world.entity.LivingEntity.class, 10,
                true, false, target -> target instanceof CompanionEntity || target instanceof net.minecraft.world.entity.player.Player));
    }
}
