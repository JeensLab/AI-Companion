package dev.aicompanion.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Walks the jumps of the companion's own route (CompanionMoves.jump). A jump is two nodes of the route more than a block apart (no
 * other move leaves a gap), so it needs no mark on the nodes and survives the route being cut or rebuilt. He walks to the edge as
 * the game's path following takes him, then presses the jump control, and the game's physics carries him over, as a player's jump
 * does: nothing is set but the key. A jump that needs the run of a sprint gets it through {@link #sprints()}, which the stroke
 * of CompanionSwimming reads (the one place that sets sprinting).
 */
final class CompanionJumps {
    /** How far past the centre of the last cell he jumps: the edge is at .5, and his feet reach .3 more over it. */
    private static final double EDGE = .4;
    /** A jump of this many blocks and more between two nodes is run up to at a sprint (a walking jump clears a block less). */
    private static final int RUN_FROM = 3;

    private final Mob mob;
    private boolean sprints;

    CompanionJumps(Mob mob) { this.mob = mob; }

    /** A jump is next on the route (or the one after): run up to it. */
    boolean sprints() { return sprints; }

    /** One tick, after the game's path following has aimed him at the next node. */
    void tick(Path path) {
        sprints = false;
        if (path == null || path.isDone()) return;
        int next = path.getNextNodeIndex();
        for (int landing = Math.max(1, next); landing <= next + 1 && landing < path.getNodeCount(); landing++) {
            Node from = path.getNode(landing - 1), to = path.getNode(landing);
            int dx = to.x - from.x, dz = to.z - from.z, width = Math.max(Math.abs(dx), Math.abs(dz));
            if (width < 2 || from.y != to.y) continue;
            sprints = width >= RUN_FROM;
            // At the edge cell, heading for the landing: how far over its centre he is, along the jump.
            double along = (mob.getX() - from.x - .5) * Integer.signum(dx) + (mob.getZ() - from.z - .5) * Integer.signum(dz);
            if (landing == next && mob.onGround() && along >= EDGE) mob.getJumpControl().jump();
            return;
        }
    }
}
