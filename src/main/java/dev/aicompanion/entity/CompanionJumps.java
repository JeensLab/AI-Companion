package dev.aicompanion.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Walks the jumps and ladder climbs of the companion's own route (CompanionMoves.jump). A jump is two nodes of the route more than a block apart (no
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

    /** The speed modifier on and onto a ladder (of the walk, which is .2158 blocks a tick). */
    private static final double SLOW = 1, OFF_WALL = .15;

    private final Mob mob;
    private boolean sprints, climbing;

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

    /** On a ladder or vine, or the next node is one: not at a sprint (it carried him past the ladder, off it). Read by the stroke. */
    boolean climbs() { return climbing; }

    /**
     * On a ladder or vine with the next node above him: press the jump control. The game climbs a living thing that is jumping (or
     * pressed against a wall) up 0.2 a tick, and a vine has no collision to press against. Going down needs nothing: a climber falls
     * at most 0.15 a tick.
     */
    void climb(Path path) {
        climbing = false;
        if (path == null || path.isDone()) return;
        Node next = path.getNextNode();
        climbing = mob.onClimbable() || mob.level().getBlockState(new net.minecraft.core.BlockPos(next.x, next.y, next.z)).is(net.minecraft.tags.BlockTags.CLIMBABLE);
        // Off the ground he glides on whatever speed he left it with (a climber's horizontal speed is held to .15, the air takes .91 of it
        // a tick): walked slowly onto a ladder he stays in its cell, run onto it he is a block past.
        var move = mob.getMoveControl();
        double x = move.getWantedX(), z = move.getWantedZ();
        var at = mob.level().getBlockState(mob.blockPosition());
        // On a ladder in the column of the next node, he holds off its plate (the cell's wall side, a hair from the centre): pressed
        // to it he would climb on his own (a collision with a wall climbs), up the ladder he is meant to go down.
        if (mob.onClimbable() && at.getBlock() instanceof net.minecraft.world.level.block.LadderBlock && next.x == mob.getBlockX() && next.z == mob.getBlockZ()) {
            Direction open = at.getValue(net.minecraft.world.level.block.LadderBlock.FACING);
            x = next.x + .5 + open.getStepX() * OFF_WALL; z = next.z + .5 + open.getStepZ() * OFF_WALL;
        }
        if (climbing) move.setWantedPosition(x, move.getWantedY(), z, SLOW);
        if (!mob.onClimbable()) return;
        if (!path.isDone() && path.getNextNode().y > mob.getY()) mob.getJumpControl().jump();
    }
}
