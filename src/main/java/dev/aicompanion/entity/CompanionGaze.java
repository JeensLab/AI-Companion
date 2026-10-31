package dev.aicompanion.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where his eyes go when nothing else claims them. The owner asked (29.10): while his mood is good Oleg looks the player in the
 * eye and turns his head to him, and when it is bad he avoids eye and head contact. The mood is already held server-side as the
 * bond (CompanionBond.Tier), so this rule is a pure function of the bond score: no world access, and it is tested without a game
 * (CompanionGazeTests).
 */
public final class CompanionGaze {
    /** His idle gaze: at the player, past his shoulder, or nowhere in particular. */
    public enum Eyes { AT_PLAYER, PAST_PLAYER, DOWN }

    /** How far to the side of the player the wary gaze lands, in blocks. */
    static final double PAST_OFFSET = 2.5D;
    /** How far ahead the eyes rest on the ground when he avoids the player's. */
    static final double AVOID_DISTANCE = 2.0D;

    private CompanionGaze() {
    }

    /** Acquainted and warmer: the eyes are on the player. Wary: past him. Offended: away. */
    public static Eyes eyesFor(int bondScore) {
        return switch (CompanionBond.Tier.of(bondScore)) {
            case OFFENDED -> Eyes.DOWN;
            case WARY -> Eyes.PAST_PLAYER;
            default -> Eyes.AT_PLAYER;
        };
    }

    public static boolean looksAtPlayer(int bondScore) { return eyesFor(bondScore) == Eyes.AT_PLAYER; }

    /**
     * Whether he turns his eyes to the player when spoken to, or touched: wary still meets the eyes on demand, only an offended
     * companion keeps them away (the owner asked for this, 29.10).
     */
    public static boolean meetsEyes(int bondScore) { return CompanionBond.Tier.of(bondScore) != CompanionBond.Tier.OFFENDED; }

    /** The yaw from one spot to another in the game's degrees: 0 is south, 90 is west. */
    public static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    /** A spot beside the player at his eye height: the gaze that does not meet his eyes goes there. */
    public static Vec3 past(LivingEntity from, LivingEntity to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) return to.getEyePosition();
        return new Vec3(to.getX() - dz / length * PAST_OFFSET, to.getEyeY(), to.getZ() + dx / length * PAST_OFFSET);
    }

    /** A point on the ground ahead of him: where the eyes rest when he does not meet the player's. */
    public static Vec3 groundAhead(LivingEntity from) {
        Vec3 ahead = Vec3.directionFromRotation(0.0F, from.getYRot()).scale(AVOID_DISTANCE);
        return new Vec3(from.getX() + ahead.x, from.getY() - .5D, from.getZ() + ahead.z);
    }
}
