package dev.aicompanion.entity;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** The game's judgement of cells (type, floor, collision) that the companion's own search (CompanionSearch, CompanionMoves) builds on. */
final class CompanionNodeEvaluator extends WalkNodeEvaluator {
    // What the companion's own search (CompanionSearch) asks of the game's judgement of cells; a cell is one Node for the search.
    net.minecraft.world.level.pathfinder.PathType typeAt(int x, int y, int z) { return getCachedPathType(x, y, z); }
    double floorAt(int x, int y, int z) { return getFloorLevel(new net.minecraft.core.BlockPos(x, y, z)); }
    double floorAt(net.minecraft.core.BlockPos pos) { return getFloorLevel(pos); }
    boolean collides(net.minecraft.world.phys.AABB box) { return !currentContext.level().noCollision(mob, box); }
    Node mark(int x, int y, int z, net.minecraft.world.level.pathfinder.PathType type, float malus) {
        Node node = getNode(x, y, z);
        node.type = type;
        node.costMalus = Math.max(node.costMalus, malus);
        return node;
    }
}
