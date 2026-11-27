package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What {@link CompanionCreeperGuard} does with blocks and with his own passage, and the swim off a creeper; it keeps no state of its own.
 * <p>
 * Under rock a lit fuse he cannot run from is walled out: two blocks from the bag on the next step towards the creeper, which stands while
 * its fuse burns. On his own stairs or tunnel the way between him and a creeper is shut a few stands off, before any fuse. In his mine such
 * a block is one of its temporary blocks, taken up again when the walk reaches it ({@link CompanionExpeditionDig}); in a cave outside his
 * mine it stays where it was set, as a player's would. In water, which has no floor for a way out, he swims straight off the creeper.
 */
final class CompanionCreeperWall {
    private static final int SHUT_STANDS = 3, ON_ROUTE_SQR = 2, ROUTE_CREEPER_SQR = 4, PASSAGE_RANGE = 24;
    /** How far ahead of him the point lies that he swims for, straight off a lit creeper in water. */
    private static final double SWIM_OFF = 4;
    /** The search for a creeper's walk to him: the blocks round it, and the nodes, of a vanilla creeper's own search (follow range 16). */
    private static final int SEARCH_SPAN = 16, SEARCH_NODES = 256;
    /** A walk that ends this near his stand is near enough: the fuse is lit at three blocks, and he steps about his stand. */
    private static final double LIGHTS_WITHIN = 4;

    final CompanionEntity c;

    CompanionCreeperWall(CompanionEntity c) { this.c = c; }

    /** His own way up, from the depth to the entrance; empty with no mine or out of its dimension. */
    List<BlockPos> way(ServerLevel level) {
        CompanionMineSite mine = c.work.mine;
        return mine.exists() && level.dimension() == Level.OVERWORLD ? CompanionMineSite.wayUp(mine) : List.of();
    }

    /** The cell of his way he stands at under the ground, or -1: on the surface and off his way the guard does as anywhere else. */
    int at(ServerLevel level, List<BlockPos> way) {
        return level.canSeeSky(c.blockPosition()) ? -1 : indexOf(way, c.blockPosition(), ON_ROUTE_SQR);
    }

    /**
     * Sets the wall: blocks from the bag at his feet and head on the next step towards the creeper, which stands while its fuse burns. True while
     * the wall goes up or stands; false where there is no block, or the creeper, a wall or the reach stand in the way of it, or he is in the open
     * (nobody takes a wall out there).
     */
    boolean raise(ServerLevel level, Creeper creeper) {
        if (CompanionSeal.available(c) <= 0 || level.canSeeSky(c.blockPosition())) return false;
        boolean shut = false;
        for (BlockPos cell : wallCells(level, creeper)) {
            CompanionSeal.Result result = CompanionSeal.place(c, cell);
            if (result == CompanionSeal.Result.PLACED) { c.work.mine.temporary(cell); shut = true; }
            else if (result == CompanionSeal.Result.NO_BLOCK) return false;
            else if (!CompanionSeal.replaceable(level, cell)) shut = true;
        }
        return shut;
    }

    private List<BlockPos> wallCells(ServerLevel level, Creeper creeper) {
        Vec3 towards = creeper.position().subtract(c.position());
        Direction side = Math.abs(towards.x) > Math.abs(towards.z) ? (towards.x > 0 ? Direction.EAST : Direction.WEST) : (towards.z > 0 ? Direction.SOUTH : Direction.NORTH);
        for (int rise : new int[]{0, 1, -1}) {
            BlockPos feet = c.blockPosition().relative(side).above(rise);
            if (CompanionSeal.replaceable(level, feet) && CompanionSeal.replaceable(level, feet.above())) return List.of(feet, feet.above());
        }
        return List.of();
    }

    /**
     * Where he swims or wades to, straight off the creeper, or null out of water or with rock or lava ahead: a push into those is worse than
     * a blow. A few blocks halve the blast; a blow throws the creeper back by less than one (run r314159-d3/retreat: in a lake, three blows,
     * the fourth too late, dead at sixteen).
     */
    Vec3 swimOff(ServerLevel level, Creeper creeper) {
        Vec3 away = c.position().subtract(creeper.position()).multiply(1, 0, 1);
        if (!c.isInWater() || away.lengthSqr() <= 1.0E-4) return null;
        Vec3 way = away.normalize();
        BlockPos ahead = BlockPos.containing(c.position().add(way));
        if (!level.noCollision(c, c.getBoundingBox().move(way)) || CompanionLava.at(level, ahead) || CompanionLava.at(level, ahead.above())) return null;
        return c.position().add(way.scale(SWIM_OFF));
    }

    /**
     * The creeper can walk to his stand, or near enough to light its fuse at him, by its own rules of walking (a search of his own: its
     * navigation, and with it where it is headed, is left alone). One in a pit or a cave below with no way up is no reason to act: a block
     * set against it the walk takes up again with no creeper near it, and a step away from it he walks back from, both for ever.
     */
    boolean walks(ServerLevel level, Creeper creeper, BlockPos stand) {
        BlockPos from = creeper.blockPosition();
        var region = new PathNavigationRegion(level, from.offset(-SEARCH_SPAN, -SEARCH_SPAN, -SEARCH_SPAN), from.offset(SEARCH_SPAN, SEARCH_SPAN, SEARCH_SPAN));
        NodeEvaluator its = creeper.getNavigation().getNodeEvaluator();
        WalkNodeEvaluator walker = new WalkNodeEvaluator();
        walker.setCanFloat(its.canFloat());
        walker.setCanPassDoors(its.canPassDoors());
        walker.setCanOpenDoors(its.canOpenDoors());
        Path path = new PathFinder(walker, SEARCH_NODES).findPath(region, creeper, Set.of(stand), SEARCH_SPAN, 1, 1);
        return path != null && path.getEndNode() != null
                && (path.canReach() || Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceTo(Vec3.atBottomCenterOf(stand)) < LIGHTS_WITHIN);
    }

    /** The head-height cells of the stand a few steps off on the creeper's side, far enough that its fuse is not lit; empty when that is shut already or out of reach. */
    List<BlockPos> shutCells(List<BlockPos> way, int at, Creeper creeper) {
        int side = sideOf(way, at, creeper), creeperAt = indexOf(way, creeper.blockPosition(), ROUTE_CREEPER_SQR);
        ServerLevel level = (ServerLevel) c.level();
        for (int k = SHUT_STANDS; k >= 1; k--) {
            int index = at + side * k;
            if (index < 0 || index >= way.size() || creeperAt >= 0 && Math.abs(creeperAt - at) <= k) continue;
            List<BlockPos> open = new ArrayList<>();
            for (BlockPos cell : headRow(way, index)) if (CompanionSeal.replaceable(level, cell)) open.add(cell);
            if (open.isEmpty()) return List.of();
            if (open.stream().allMatch(cell -> CompanionSeal.reachable(c, cell))) return open;
        }
        return List.of();
    }

    /**
     * One tick of setting the blocks of {@link #shutCells}: the cells still open after it, or null with no block in the bag. Standing half in a
     * cell, on the edge of his stand where the guard stopped his walk, he first steps back onto the middle of the stand.
     */
    List<BlockPos> place(List<BlockPos> cells, BlockPos stand, double speed) {
        List<BlockPos> left = new ArrayList<>(cells);
        for (BlockPos cell : cells) {
            CompanionSeal.Result result = CompanionSeal.place(c, cell);
            if (result == CompanionSeal.Result.PLACED) {
                c.work.mine.temporary(cell);
                left.remove(cell);
            } else if (result == CompanionSeal.Result.NO_BLOCK) {
                return null;
            } else if (c.getBoundingBox().intersects(new AABB(cell))) {
                Vec3 back = Vec3.atBottomCenterOf(stand);
                CompanionCreeperGuard.faceTowards(c, back);
                c.getMoveControl().setWantedPosition(back.x, back.y, back.z, speed);
            }
        }
        return left;
    }

    /** The cell above the stand, and in a 2x2 tunnel the one beside it too: a creeper is nearly two blocks tall and cannot pass under them. */
    private List<BlockPos> headRow(List<BlockPos> way, int index) {
        BlockPos cell = way.get(index);
        List<CompanionMineSite.Tunnel> tunnels = c.work.mine.tunnels();
        if (!tunnels.isEmpty() && index < tunnels.get(tunnels.size() - 1).length()) {
            BlockPos beside = cell.relative(CompanionShaftPlan.right(tunnels.get(tunnels.size() - 1).direction()));
            return List.of(cell.above(), beside.above());
        }
        return List.of(cell.above());
    }

    /** +1 when the creeper is towards the entrance, -1 when it is towards the depth; one off the route is at the end of it that it is nearer to (a cave opens onto the depth end). */
    private static int sideOf(List<BlockPos> way, int at, Creeper creeper) {
        int creeperAt = indexOf(way, creeper.blockPosition(), ROUTE_CREEPER_SQR);
        if (creeperAt >= 0 && creeperAt != at) return Integer.signum(creeperAt - at);
        double depth = creeper.distanceToSqr(Vec3.atCenterOf(way.get(0))), entrance = creeper.distanceToSqr(Vec3.atCenterOf(way.get(way.size() - 1)));
        return entrance < depth ? 1 : -1;
    }

    /** On his route, away from the creeper's side for a few cells; null where the route ends. */
    Vec3 alongRoute(List<BlockPos> way, int at, Creeper creeper, int cells) {
        int side = sideOf(way, at, creeper);
        int target = Math.max(0, Math.min(way.size() - 1, at - side * cells));
        return target == at ? null : Vec3.atBottomCenterOf(way.get(target));
    }

    /** Off the route: the nearest cell of his own passage that is farther from the creeper than he is; null with none. */
    Vec3 passageFrom(List<BlockPos> way, Creeper creeper) {
        double mine = creeper.distanceToSqr(c);
        BlockPos best = null;
        double bestDistance = PASSAGE_RANGE * PASSAGE_RANGE;
        for (BlockPos cell : way) {
            double distance = c.distanceToSqr(Vec3.atBottomCenterOf(cell));
            if (distance < bestDistance && creeper.distanceToSqr(Vec3.atBottomCenterOf(cell)) > mine) { best = cell; bestDistance = distance; }
        }
        return best == null ? null : Vec3.atBottomCenterOf(best);
    }

    /** The cells of his passage about him: places to step away to besides those round him. */
    List<Vec3> passage(List<BlockPos> way) {
        List<Vec3> spots = new ArrayList<>();
        for (BlockPos cell : way) {
            Vec3 spot = Vec3.atBottomCenterOf(cell);
            if (c.distanceToSqr(spot) <= PASSAGE_RANGE * PASSAGE_RANGE) spots.add(spot);
        }
        return spots;
    }

    private static int indexOf(List<BlockPos> way, BlockPos here, int within) {
        int best = -1;
        double bestDistance = within + 1;
        for (int i = 0; i < way.size(); i++) {
            double distance = way.get(i).distSqr(here);
            if (distance <= within && distance < bestDistance) { best = i; bestDistance = distance; }
        }
        return best;
    }
}
