package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Geometry of a player's diamond mine: one straight diagonal staircase, a forced 90 degree turn only where the way is blocked,
 * and 2x2 tunnels at diamond depth. The cell methods are pure; the entrance chooser and the hazard classifier read the world.
 * Cells are named by the stand they are dug from: a stand is the feet cell, a column is feet, feet+1, feet+2.
 */
public final class CompanionShaftPlan {
    public static final int FEET_ABOVE_BOTTOM = 6, DEEPEST_FEET_Y = -58;
    public static final int MAX_TUNNEL_CELLS = 64;
    /** Eye height of a standing companion above his feet cell's floor, for judging reach from a stand he is not on yet. */
    private static final double EYE = 1.62;
    private static final int WATER_AROUND = 6, SURFACE_SCAN_UP = 8, SURFACE_SCAN_DOWN = 24;

    private CompanionShaftPlan() { }

    public enum Kind { OK, SEAL, FLOOR, BLOCKED }

    /** What stands before opening a cell: nothing, a liquid cell to seal first, a floor cell to lay, or no way through. {@code cell} is null unless SEAL or FLOOR. */
    public record Hazard(Kind kind, BlockPos cell) {
        public static final Hazard OK = new Hazard(Kind.OK, null), BLOCKED = new Hazard(Kind.BLOCKED, null);
        public static Hazard seal(BlockPos cell) { return new Hazard(Kind.SEAL, cell); }
        public static Hazard floor(BlockPos cell) { return new Hazard(Kind.FLOOR, cell); }
    }

    /** Cells the mine must not open again and moves (a stand and a direction) that already failed; consulted only by the overloads that take it. */
    public record Avoid(Set<BlockPos> forbidden, BiPredicate<BlockPos, Direction> refused, Predicate<BlockPos> shunned) {
        public static final Avoid NONE = new Avoid(Set.of(), (stand, d) -> false);

        public Avoid(Set<BlockPos> forbidden, BiPredicate<BlockPos, Direction> refused) { this(forbidden, refused, cell -> false); }
    }

    /** Cost added to a turn whose first step along is blocked: the landing is reachable, but it only moves the dead end aside. */
    static final int FALLBACK_TURN = 100;
    public static final int NO_TURN = Integer.MAX_VALUE;

    /** Feet height of the tunnel floor: six above the world bottom, never deeper than the diamond layer at -58. */
    public static int targetFeetY(int minBuildHeight) { return Math.max(minBuildHeight + FEET_ABOVE_BOTTOM, DEEPEST_FEET_Y); }

    public static Direction left(Direction d) { return d.getCounterClockWise(); }

    public static Direction right(Direction d) { return d.getClockWise(); }

    /** Where tunnels are tried, in order: on along the staircase, to the left, to the right, back. */
    public static List<Direction> tunnelOrder(Direction stair) {
        return List.of(stair, left(stair), right(stair), stair.getOpposite());
    }

    /** The k-th stand of a straight run: one block on and one down per step. */
    public static BlockPos stand(BlockPos origin, Direction d, int k) {
        return origin.relative(d, k).below(k);
    }

    /** The stands of a straight run of the given length, origin first. */
    public static List<BlockPos> stands(BlockPos origin, Direction d, int steps) {
        List<BlockPos> stands = new ArrayList<>(steps + 1);
        for (int k = 0; k <= steps; k++) stands.add(stand(origin, d, k));
        return stands;
    }

    /** Steps from a feet cell down to the target feet height. */
    public static int stepsTo(int feetY, int targetFeetY) { return Math.max(0, feetY - targetFeetY); }

    /** The three open cells of a stand: feet, head, above the head. */
    public static List<BlockPos> column(BlockPos stand) {
        return List.of(stand, stand.above(), stand.above(2));
    }

    /**
     * Cells opened to step from a stand onwards and down, in the order they are broken: the one ahead at the feet's height
     * first, the one below it, the one at head height last (it is seen only once the others are open, and breaking it last
     * keeps the walk off the first drop). The block under the stand is never among them.
     */
    public static List<BlockPos> advanceCells(BlockPos stand, Direction d) {
        BlockPos ahead = stand.relative(d);
        return List.of(ahead, ahead.below(), ahead.above());
    }

    /** The stand the advance leads to. */
    public static BlockPos nextStand(BlockPos stand, Direction d) { return stand.relative(d).below(); }

    /** Cells of a flat landing beside a stand, the start of a run in a new direction; the landing's column is the same as a stand's. */
    public static BlockPos landing(BlockPos stand, Direction turn) { return stand.relative(turn); }

    /** The four cells opened per advance of a 2x2 tunnel: lane 0 on the stand line, lane 1 on its right; feet then head, lane 0 first. */
    public static List<BlockPos> tunnelCells(BlockPos stand, Direction t) {
        BlockPos lane0 = stand.relative(t), lane1 = lane0.relative(right(t));
        return List.of(lane0, lane0.above(), lane1, lane1.above());
    }

    /** The floor cells under the two lanes of a tunnel advance. */
    public static List<BlockPos> tunnelFloor(BlockPos stand, Direction t) {
        BlockPos lane0 = stand.relative(t);
        return List.of(lane0.below(), lane0.relative(right(t)).below());
    }

    /** Nothing to break and nobody to burn: an open cell has no collision shape and no fluid. */
    public static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** All three cells of a stand are open. */
    public static boolean columnOpen(ServerLevel level, BlockPos stand) {
        for (BlockPos cell : column(stand)) if (!level.hasChunkAt(cell) || !open(level, cell)) return false;
        return true;
    }

    /**
     * Where the shaft starts: the nearest surface cell to {@code from} (within {@code radius}) with solid natural ground,
     * no building or chest beside it and no water around; the direction is the first of the four whose first three columns
     * are free of liquid and crafted blocks, so the stair does not start towards a lake or a house. Null when none fits.
     */
    public static Entrance chooseEntrance(ServerLevel level, BlockPos from, int radius) {
        for (int r = 0; r <= radius; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) for (int dz = -r; dz <= r; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                BlockPos column = from.offset(dx, 0, dz);
                if (!level.hasChunkAt(column)) continue;
                BlockPos feet = topOpenCell(level, column);
                Entrance entrance = feet == null ? null : entranceAt(level, feet);
                if (entrance != null) return entrance;
            }
        }
        return null;
    }

    public record Entrance(BlockPos feet, Direction direction) { }

    /** The first open cell with something under it, looking down from a few blocks above the column's given height. */
    private static BlockPos topOpenCell(ServerLevel level, BlockPos column) {
        for (int y = column.getY() + SURFACE_SCAN_UP; y > column.getY() - SURFACE_SCAN_DOWN; y--) {
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            if (level.isOutsideBuildHeight(feet.below()) || !level.hasChunkAt(feet)) return null;
            if (!open(level, feet.below())) return open(level, feet) ? feet : null;
        }
        return null;
    }

    private static Entrance entranceAt(ServerLevel level, BlockPos feet) {
        if (!groundSolid(level, feet) || !columnOpen(level, feet) || nearBuilding(level, feet) || waterNear(level, feet)) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) if (directionFree(level, feet, d)) return new Entrance(feet, d);
        return null;
    }

    private static boolean groundSolid(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.hasChunkAt(floor) && level.getFluidState(floor).isEmpty() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && CompanionOre.tunnelable(level.getBlockState(floor));
    }

    private static boolean nearBuilding(ServerLevel level, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
            if (!level.hasChunkAt(pos)) return true;
            if (built(level, pos)) return true;
        }
        return false;
    }

    /** A crafted block or a chest/furnace; the companion's own torches are not a building. */
    private static boolean built(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return false;
        return CompanionBuildings.crafted(state) || level.getBlockEntity(pos) != null;
    }

    private static boolean builtBeside(ServerLevel level, BlockPos cell) {
        for (Direction side : Direction.values()) if (built(level, cell.relative(side))) return true;
        return false;
    }

    private static boolean waterNear(ServerLevel level, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-WATER_AROUND, -2, -WATER_AROUND), feet.offset(WATER_AROUND, 2, WATER_AROUND))) {
            if (level.hasChunkAt(pos) && !level.getFluidState(pos).isEmpty()) return true;
        }
        return false;
    }

    private static boolean directionFree(ServerLevel level, BlockPos feet, Direction d) {
        for (int k = 1; k <= 3; k++) {
            BlockPos next = stand(feet, d, k);
            for (BlockPos cell : column(next)) {
                if (!level.hasChunkAt(cell) || !level.getFluidState(cell).isEmpty() || built(level, cell)) return false;
            }
        }
        return true;
    }

    /**
     * What must happen before a cell is broken. Liquid in the cell or beside it is sealed first when it can be reached
     * from the stand (in reach, nothing in the way), otherwise the way is blocked. A falling block above, a building,
     * bedrock, a chest or an unloaded chunk block it. Cells already open and cells of the own mine are fine.
     */
    public static Hazard classify(ServerLevel level, BlockPos stand, BlockPos cell, Set<BlockPos> own) {
        return classify(level, stand, cell, own, Avoid.NONE, null);
    }

    /** As above; a forbidden cell is blocked, and hidden liquid exactly at {@code laid} (a floor cell to lay) is left to the floor laying. */
    static Hazard classify(ServerLevel level, BlockPos stand, BlockPos cell, Set<BlockPos> own, Avoid avoid, BlockPos laid) {
        if (avoid.forbidden().contains(cell) || avoid.shunned().test(cell)) return Hazard.BLOCKED;
        if (!level.hasChunkAt(cell) || !level.getWorldBorder().isWithinBounds(cell) || level.isOutsideBuildHeight(cell)) return Hazard.BLOCKED;
        BlockPos liquid = null;
        for (BlockPos pos : around(cell)) {
            if (!level.hasChunkAt(pos)) return Hazard.BLOCKED;
            if (level.getFluidState(pos).isEmpty()) continue;
            if (!sealable(level, stand, pos)) {
                if (pos.equals(laid)) continue;
                return Hazard.BLOCKED;
            }
            if (liquid == null) liquid = pos;
        }
        BlockState state = level.getBlockState(cell);
        boolean mine = own.contains(cell);
        if (!open(level, cell) && !mine && !CompanionOre.tunnelable(state)) return Hazard.BLOCKED;
        if (level.getBlockEntity(cell) != null || !mine && builtBeside(level, cell)) return Hazard.BLOCKED;
        BlockPos above = cell.above();
        if (level.hasChunkAt(above) && level.getBlockState(above).getBlock() instanceof FallingBlock) return Hazard.BLOCKED;
        return liquid == null ? Hazard.OK : Hazard.seal(liquid);
    }

    /** The worst hazard of the cells of one advance (staircase step or tunnel advance) and of the floor of its new stand. */
    public static Hazard assess(ServerLevel level, BlockPos stand, List<BlockPos> cells, List<BlockPos> floor, Set<BlockPos> own) {
        return assess(level, stand, cells, floor, own, Avoid.NONE, false);
    }

    /** As above; with {@code laysFloor} a single floor cell that is air, replaceable or liquid is a FLOOR hazard, not a block. */
    static Hazard assess(ServerLevel level, BlockPos stand, List<BlockPos> cells, List<BlockPos> floor, Set<BlockPos> own, Avoid avoid, boolean laysFloor) {
        BlockPos laid = laysFloor && floor.size() == 1 ? floor.getFirst() : null;
        Hazard seal = Hazard.OK;
        for (BlockPos cell : cells) {
            Hazard hazard = classify(level, stand, cell, own, avoid, laid);
            if (hazard.kind() == Kind.BLOCKED) return hazard;
            if (hazard.kind() == Kind.SEAL && seal.kind() == Kind.OK) seal = hazard;
        }
        if (seal.kind() == Kind.SEAL) return seal;
        for (BlockPos under : floor) {
            if (!level.hasChunkAt(under)) return Hazard.BLOCKED;
            BlockState state = level.getBlockState(under);
            if (state.isFaceSturdy(level, under, Direction.UP)) continue;
            return under.equals(laid) && (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) ? Hazard.floor(under) : Hazard.BLOCKED;
        }
        return Hazard.OK;
    }

    public static Hazard assessStep(ServerLevel level, BlockPos stand, Direction d, Set<BlockPos> own) {
        return assess(level, stand, advanceCells(stand, d), List.of(nextStand(stand, d).below()), own);
    }

    /** A staircase step that may lack its floor: the answer is FLOOR (lay it) instead of BLOCKED. */
    public static Hazard assessStep(ServerLevel level, BlockPos stand, Direction d, Set<BlockPos> own, Avoid avoid) {
        return assess(level, stand, advanceCells(stand, d), List.of(nextStand(stand, d).below()), own, avoid, true);
    }

    public static Hazard assessTunnel(ServerLevel level, BlockPos stand, Direction t, Set<BlockPos> own) {
        return assess(level, stand, tunnelCells(stand, t), tunnelFloor(stand, t), own);
    }

    /** The side of a forced turn: of the two perpendicular directions the cheaper by {@link #turnCost}, the left on a tie; null when both are out. */
    public static Direction chooseTurn(ServerLevel level, BlockPos stand, Direction d, Set<BlockPos> own) {
        return chooseTurn(level, stand, d, own, Avoid.NONE);
    }

    /** As above, skipping the moves {@code avoid} refuses; a floor still to lay costs like a seal. */
    public static Direction chooseTurn(ServerLevel level, BlockPos stand, Direction d, Set<BlockPos> own, Avoid avoid) {
        Direction best = null;
        int bestCost = NO_TURN;
        for (Direction side : List.of(left(d), right(d))) {
            int cost = turnCost(level, stand, side, own, avoid);
            if (cost < bestCost) { best = side; bestCost = cost; }
        }
        return best;
    }

    /**
     * What a turn to {@code side} needs before the walk goes on: 0 when its landing and first step are free, one per seal or floor
     * to place, plus a large penalty when the step along is blocked. {@link #NO_TURN} when the landing is blocked or the move was refused.
     */
    public static int turnCost(ServerLevel level, BlockPos stand, Direction side, Set<BlockPos> own, Avoid avoid) {
        if (avoid.refused().test(stand, side)) return NO_TURN;
        BlockPos landing = landing(stand, side);
        Hazard onto = assess(level, stand, column(landing), List.of(landing.below()), own, avoid, true);
        if (onto.kind() == Kind.BLOCKED) return NO_TURN;
        Hazard along = assessStep(level, landing, side, own, avoid);
        return (onto.kind() == Kind.OK ? 0 : 1) + (along.kind() == Kind.BLOCKED ? FALLBACK_TURN : along.kind() == Kind.OK ? 0 : 1);
    }

    /** A liquid cell can be sealed from a stand when it is within 4.5 of the eye and nothing but air lies between. */
    static boolean sealable(ServerLevel level, BlockPos stand, BlockPos liquid) {
        Vec3 eye = new Vec3(stand.getX() + .5, stand.getY() + EYE, stand.getZ() + .5);
        return clearTo(level, eye, liquid);
    }

    static boolean clearTo(ServerLevel level, Vec3 eye, BlockPos cell) {
        Vec3 centre = cell.getCenter();
        if (eye.distanceTo(centre) > CompanionSeal.REACH) return false;
        var hit = level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(cell);
    }

    private static List<BlockPos> around(BlockPos cell) {
        List<BlockPos> cells = new ArrayList<>(7);
        cells.add(cell);
        for (Direction side : Direction.values()) cells.add(cell.relative(side));
        return cells;
    }
}
