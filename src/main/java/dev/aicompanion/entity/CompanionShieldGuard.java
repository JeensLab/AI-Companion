package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/**
 * The shield against a lit fuse, for {@link CompanionCreeperGuard}: with a shield in the left hand he does not run from a creeper whose
 * fuse is lit within the range at which it keeps burning (vanilla's fuse goes out beyond 7 blocks). He turns head and body to it, which is
 * where a shield blocks (vanilla compares the blast's direction with the head's), raises the shield at once and stands until the creeper
 * blows or its fuse goes out. A blast fully blocked costs the shield its wear ({@link CompanionEquipment#wearShield}), never his health.
 * <p>
 * Two lit fuses are held off with the one shield only while both lie in the half-plane in front of the nearer; otherwise he retreats as without it.
 */
final class CompanionShieldGuard {
    private static final double RANGE = 7;

    private final CompanionEntity owner;
    private boolean holding;

    CompanionShieldGuard(CompanionEntity owner) { this.owner = owner; }

    /** The shield is up for a fuse; the ordinary lowering of an idle shield ({@link CompanionShieldDefense}) must leave it alone. */
    boolean holding() { return holding; }

    /** The nearest lit creeper is in range, and every other lit one in sight lies in front of it: the shield is the answer, not the retreat. */
    boolean covers(ServerLevel level, Creeper nearest) {
        if (!ready() || !CompanionCreeperGuard.lit(nearest) || owner.distanceTo(nearest) > RANGE) return false;
        Vec3 ahead = flat(nearest);
        return level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(CompanionCreeperGuard.NEAR),
                other -> other != nearest && other.isAlive() && !other.isNoAi() && CompanionCreeperGuard.lit(other) && owner.hasLineOfSight(other))
                .stream().allMatch(other -> flat(other).dot(ahead) > 0);
    }

    /** One tick of standing behind the shield; false when it no longer answers (the fuse is out, the creeper gone, another lit one behind). */
    boolean hold(ServerLevel level, Creeper nearest) {
        if (!covers(level, nearest)) { holding = false; return false; }
        holding = true;
        owner.getNavigation().stop();
        float yaw = (float) (Mth.atan2(nearest.getZ() - owner.getZ(), nearest.getX() - owner.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        owner.setYRot(yaw);
        owner.setYBodyRot(yaw);
        owner.setYHeadRot(yaw);
        owner.getLookControl().setLookAt(nearest, 30, 30);
        if (!owner.isUsingItem()) owner.startUsingItem(InteractionHand.OFF_HAND);
        return true;
    }

    void release() { holding = false; }

    private boolean ready() {
        return owner.inventoryViewers == 0 && owner.getOffhandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)
                && !owner.archery.aiming() && (!owner.isUsingItem() || owner.getUsedItemHand() == InteractionHand.OFF_HAND);
    }

    private Vec3 flat(Creeper creeper) { return new Vec3(creeper.getX() - owner.getX(), 0, creeper.getZ() - owner.getZ()); }
}
