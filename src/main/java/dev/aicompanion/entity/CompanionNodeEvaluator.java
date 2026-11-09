package dev.aicompanion.entity;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** The game's judgement of cells (type, floor, collision) that the companion's own search (CompanionSearch, CompanionMoves) builds on. */
final class CompanionNodeEvaluator extends WalkNodeEvaluator {
    /** The dragon's breath in the region searched, as walls: never stepped into, waited out where there is no way round. */
    private java.util.List<net.minecraft.world.entity.AreaEffectCloud> breath = java.util.List.of();
    /** But for a cloud he is caught by already: a way out of it, through it, is a way (CompanionDragonBreath walks him out). */
    void keepOffBreath(java.util.List<net.minecraft.world.entity.AreaEffectCloud> clouds) {
        breath = clouds.stream().filter(cloud -> !CompanionDragonBreath.near(cloud, mob.getX(), mob.getY(), mob.getZ(), CompanionDragonBreath.MARGIN)).toList();
    }
    // What the companion's own search (CompanionSearch) asks of the game's judgement of cells; a cell is one Node for the search.
    net.minecraft.world.level.pathfinder.PathType typeAt(int x, int y, int z) { return getCachedPathType(x, y, z); }
    /** The mob's malus for the cell's type; by lava that stands still (and nothing else hot) the game's malus of «fire near» is cut to a wall's. */
    float malusAt(net.minecraft.world.level.pathfinder.PathType type, int x, int y, int z) {
        if (!breath.isEmpty() && CompanionDragonBreath.near(breath, x + .5, y, z + .5, CompanionDragonBreath.WALL)) return -1;
        float malus = mob.getPathfindingMalus(type);
        boolean settled = type == net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE && malus > CompanionLava.SETTLED_MALUS
                && CompanionLava.onlySettledLavaNear(mob.level(), x, y, z, pos -> getPathTypeFromState(mob.level(), pos));
        return settled ? CompanionLava.SETTLED_MALUS : malus;
    }
    /**
     * Afloat the game starts a route at the top of the water column he is in. Under a ceiling that cell has no room for his head and
     * no move leaves it, so in a flooded tunnel no route began (and none led back from the work): it starts at his own cell then.
     */
    @Override
    public Node getStart() {
        Node start = super.getStart();
        if (start == null || start.costMalus >= 0 || !mob.isInWater() || !canFloat()) return start;
        var at = mob.blockPosition();
        var type = typeAt(at.getX(), at.getY(), at.getZ());
        float malus = mob.getPathfindingMalus(type);
        return malus >= 0 ? mark(at.getX(), at.getY(), at.getZ(), type, malus) : start;
    }
    double floorAt(int x, int y, int z) { return getFloorLevel(new net.minecraft.core.BlockPos(x, y, z)); }
    double floorAt(net.minecraft.core.BlockPos pos) { return getFloorLevel(pos); }
    /**
     * A ladder, vine or other block a living thing climbs in (the game's own tag), but not scaffolding: a mob never sneaks, so it
     * stands on scaffolding and could climb up the player's but never down it again.
     */
    boolean climbableAt(int x, int y, int z) {
        var state = currentContext.level().getBlockState(new net.minecraft.core.BlockPos(x, y, z));
        return state.is(net.minecraft.tags.BlockTags.CLIMBABLE) && !state.is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
    }
    boolean collides(net.minecraft.world.phys.AABB box) { return !currentContext.level().noCollision(mob, box); }
    /**
     * The water straight up from (x, y, z) ends in air within {@code blocks}, and none of it is a bubble column that drags down (over
     * magma): a way up to breathe at its top, not a pocket of water under rock (review 08.11) nor deeper than his breath.
     */
    boolean airUp(int x, int y, int z, int blocks) {
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i <= blocks; i++, pos.move(0, 1, 0)) {
            var state = currentContext.level().getBlockState(pos);
            if (!state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) return state.isAir();
            if (state.hasProperty(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN)
                    && state.getValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN)) return false;
        }
        return false;
    }
    Node mark(int x, int y, int z, net.minecraft.world.level.pathfinder.PathType type, float malus) {
        Node node = getNode(x, y, z);
        node.type = type;
        node.costMalus = Math.max(node.costMalus, malus);
        return node;
    }
}
