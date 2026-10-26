package dev.aicompanion.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The game's walking search, with one thing a player does that it does not: out of the water onto a bank one block over the
 * water's surface (marathon 23.10, arena «берег»: the island rose a block over the sea, and no route led onto it from the water,
 * so the trees there were «out of reach» four blocks of swimming away). A swimmer at the edge jumps out; in the game the water
 * gives the body the lift for it (LivingEntity.travel, horizontal collision in water). Nothing else of the search changes.
 */
final class CompanionNodeEvaluator extends WalkNodeEvaluator {
    /** The search climbs a cell at a time by calling itself: the one extra cell is given once, not at every cell of the climb. */
    private boolean lifting;

    @Override
    protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
        Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
        if (lifting || (node != null && node.costMalus >= 0) || pathType != PathType.WATER || verticalDeltaLimit < 1) return node;
        // From the surface of the water a jump reaches half a block higher than from the ground, and two cells up are tried —
        // no more (marathon 23.10: given at every cell of the climb, the lift led the route onto a wall three blocks over the
        // water, and he jumped at it in the channel for good).
        lifting = true;
        try {
            Node out = super.findAcceptedNode(x, y, z, verticalDeltaLimit + 1, nodeFloorLevel + .5, direction, pathType);
            return out != null && out.costMalus >= 0 && out.y > y ? out : node;
        } finally {
            lifting = false;
        }
    }
}
