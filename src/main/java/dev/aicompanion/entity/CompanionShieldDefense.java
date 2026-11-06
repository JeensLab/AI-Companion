package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * The shield against blows and shots, as a player holds it: not only a creeper's blast — a zombie's blow, a skeleton's arrow and the
 * like. With a shield in the left hand it is up while his target, or any mob set on him, is within reach of a blow; while one that
 * shoots (a bow, crossbow or trident in hand, or a blaze, ghast, shulker or breeze) is within its shooting range in sight; and while a
 * shot not of the player or a companion flies at him. Vanilla blocks only what comes from where the head faces, so the head turns to
 * the nearest of these. A lit creeper is {@link CompanionShieldGuard}'s; one set on him is {@link CompanionCreeperGuard}'s to step away from.
 */
final class CompanionShieldDefense {
    /** A baby zombie or a vindicator closes a quarter block a tick: from 5 blocks the shield is up and turned before its first blow. */
    private static final double REACH = 5, SHOT_RANGE = 16, SHOT_MISS = 1.5, STILL = 1.0E-4;
    /** Degrees a tick: a whole turn fits in the 5 ticks a raised shield takes before it blocks. */
    private static final float TURN = 60;

    private final CompanionEntity c;

    CompanionShieldDefense(CompanionEntity c) { this.c = c; }

    void tick() {
        if (c.inventoryViewers != 0 || !c.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK) || c.creeperGuard.shielding()
                || !(c.level() instanceof ServerLevel level)) return;
        Entity danger = c.archery.aiming() ? null : danger(level);
        if (danger == null) {
            if (c.isUsingItem() && c.getUsedItemHand() == InteractionHand.OFF_HAND) c.stopUsingItem();
            return;
        }
        c.getLookControl().setLookAt(danger, TURN, TURN);
        if (!c.isUsingItem()) c.startUsingItem(InteractionHand.OFF_HAND);
    }

    private Entity danger(ServerLevel level) {
        LivingEntity target = c.getTarget();
        Entity nearest = target != null && target.isAlive() && threatens(target) && c.hasLineOfSight(target) ? target : null;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, c.getBoundingBox().inflate(SHOT_RANGE),
                mob -> mob.getTarget() == c && mob.isAlive() && !(mob instanceof Creeper) && threatens(mob) && c.hasLineOfSight(mob)))
            nearest = nearer(nearest, mob);
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, c.getBoundingBox().inflate(SHOT_RANGE), this::comesAtHim))
            nearest = nearer(nearest, shot);
        return nearest;
    }

    private boolean threatens(LivingEntity mob) {
        double distance = c.distanceToSqr(mob);
        return distance < REACH * REACH || shoots(mob) && distance < SHOT_RANGE * SHOT_RANGE;
    }

    private static boolean shoots(LivingEntity mob) {
        return mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem || mob.getMainHandItem().is(Items.TRIDENT)
                || mob instanceof Blaze || mob instanceof Ghast || mob instanceof Shulker || mob instanceof Breeze;
    }

    /**
     * A shot in flight that passes within SHOT_MISS of him: where it comes level with him is read along the last tick's move, its height
     * there with its fall, as an arrow is aimed high and drops onto him. The move, not the motion: an arrow stuck in the ground keeps the
     * motion it hit with, but does not move; a shot not yet ticked has no move to read.
     */
    private boolean comesAtHim(Projectile shot) {
        if (!shot.isAlive() || shot.tickCount == 0 || ours(shot.getOwner())) return false;
        Vec3 move = shot.position().subtract(shot.xo, shot.yo, shot.zo);
        double flat = move.x * move.x + move.z * move.z;
        if (flat < STILL) return false;
        double dx = c.getX() - shot.getX(), dz = c.getZ() - shot.getZ();
        double ticks = (dx * move.x + dz * move.z) / flat;
        if (ticks <= 0) return false;
        double sideX = dx - move.x * ticks, sideZ = dz - move.z * ticks;
        double height = shot.getY() + move.y * ticks - shot.getGravity() * ticks * ticks / 2;
        double half = c.getBbHeight() / 2;
        return sideX * sideX + sideZ * sideZ < SHOT_MISS * SHOT_MISS && Math.abs(height - (c.getY() + half)) < SHOT_MISS + half;
    }

    private static boolean ours(Entity owner) { return owner instanceof Player || owner instanceof CompanionEntity; }

    private Entity nearer(Entity nearest, Entity other) {
        return nearest == null || c.distanceToSqr(other) < c.distanceToSqr(nearest) ? other : nearest;
    }
}
