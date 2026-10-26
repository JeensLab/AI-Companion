package dev.aicompanion.entity;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;

public final class CompanionHostiles {
    private CompanionHostiles() { }
    /**
     * A hostile mob: every Monster, and also the slimes, magma cubes, phantoms, ghasts and shulkers, which are hostile without
     * being Monsters (game screenshot 27.10: «убей слаймов» beside two slimes — «моих добивающих ударов: 0»).
     */
    public static boolean hostile(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.Mob && entity instanceof net.minecraft.world.entity.monster.Enemy;
    }
    public static boolean observed(net.minecraft.world.entity.LivingEntity observer, net.minecraft.world.entity.LivingEntity monster, double radius) {
        return monster.isAlive() && observer.distanceToSqr(monster) <= radius * radius && observer.hasLineOfSight(monster);
    }
    public static void join(Creeper creeper) {
        // Keep vanilla fuse/explosion rules, choosing the nearest eligible player OR companion.
        // A lower-priority companion-only goal would never run while an owner was also nearby.
        creeper.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(creeper, net.minecraft.world.entity.LivingEntity.class, 10,
                true, false, target -> target instanceof CompanionEntity || target instanceof net.minecraft.world.entity.player.Player));
    }
}
