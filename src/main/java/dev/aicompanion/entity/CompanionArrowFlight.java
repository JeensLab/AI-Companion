package dev.aicompanion.entity;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Where to send a full-draw arrow so it comes down into a still target, as a player learns to lob one: the arrow is flown here tick by
 * tick as the game flies it (AbstractArrow.tick: a step, then a hundredth of its speed gone, then 0.05 down), and a way that meets a
 * block first does not count. A crystal on top of a wide pillar is hidden from below; only an arrow sent high over the edge gets there.
 */
public final class CompanionArrowFlight {
    /** A full draw of a player's bow (BowItem); the game's drag and gravity on an arrow in the air. */
    static final double SPEED = 3, DRAG = .99, GRAVITY = .05;
    /** The pitches tried, from a shot a little down to almost straight up, flattest first. */
    private static final double LOWEST = -30, HIGHEST = 85, STEP = 1;
    private static final int LONGEST_FLIGHT = 200;
    /** The game finds an arrow's hit in the target's box widened by this (ProjectileUtil.getEntityHitResult). */
    private static final double HIT_MARGIN = .3;

    /** The point to send the arrow toward from {@code from} (the way, as Projectile.shoot takes it), and the points it passes. */
    public record Flight(Vec3 aim, List<Vec3> path) { }

    private CompanionArrowFlight() { }

    /**
     * The flight from {@code from} (where the arrow starts: the archer's eyes, a tenth lower) into {@code target}, or null when no pitch
     * gets an arrow there clear of blocks. Of the pitches that hit, the middle one of the flattest run: the bow's spread then still lands.
     */
    public static Flight toward(Level level, Vec3 from, AABB target) {
        Vec3 centre = target.getCenter();
        double dx = centre.x - from.x, dz = centre.z - from.z, flat = Math.sqrt(dx * dx + dz * dz);
        double ux = flat < 1e-6 ? 0 : dx / flat, uz = flat < 1e-6 ? 0 : dz / flat;
        AABB box = target.inflate(HIT_MARGIN);
        double first = Double.NaN, last = Double.NaN;
        for (double pitch = LOWEST; pitch <= HIGHEST; pitch += STEP) {
            if (fly(level, from, way(ux, uz, pitch), box, centre.y, flat) != null) {
                if (Double.isNaN(first)) first = pitch;
                last = pitch;
            } else if (!Double.isNaN(first)) break;
        }
        if (Double.isNaN(first)) return null;
        Vec3 way = way(ux, uz, (first + last) / 2);
        List<Vec3> path = fly(level, from, way, box, centre.y, flat);
        if (path == null) return null;
        return new Flight(from.add(way.scale(16)), path);
    }

    private static Vec3 way(double ux, double uz, double pitch) {
        double radians = Math.toRadians(pitch), cos = Math.cos(radians);
        return new Vec3(ux * cos, Math.sin(radians), uz * cos);
    }

    /** The points the arrow passes up to the target, or null when it meets a block first or never gets there. */
    private static List<Vec3> fly(Level level, Vec3 from, Vec3 way, AABB box, double targetY, double flat) {
        List<Vec3> path = new ArrayList<>();
        path.add(from);
        Vec3 at = from, speed = way.scale(SPEED);
        for (int tick = 0; tick < LONGEST_FLIGHT; tick++) {
            Vec3 next = at.add(speed);
            boolean hits = box.clip(at, next).isPresent() || box.contains(next);
            Vec3 end = hits ? box.clip(at, next).orElse(next) : next;
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))) return null;
            if (level.clip(new ClipContext(at, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType()
                    != HitResult.Type.MISS) return null;
            path.add(end);
            if (hits) return path;
            at = next;
            speed = speed.scale(DRAG).subtract(0, GRAVITY, 0);
            double gone = Math.sqrt((at.x - from.x) * (at.x - from.x) + (at.z - from.z) * (at.z - from.z));
            // Past the target and below it, or falling well below it: this pitch is spent.
            if (gone > flat + 4 || speed.y < 0 && at.y < Math.min(targetY, from.y) - 8) return null;
        }
        return null;
    }
}
