package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of his own cells walked one by one on the move control: a tunnel or a staircase he dug is a corridor with a known
 * floor, not terrain for a path search (a search stops within two blocks of its goal, which left him short of a cell three
 * blocks down his own tunnel).
 */
final class CompanionExpeditionWalk {
    enum Result { WORKING, DONE, STUCK, NO_BLOCK }

    /** How far (squared) from the cell he is to reach he counts as led off the walk. */
    private static final double LOST = 16;

    private final CompanionExpeditionDig dig;
    private List<BlockPos> cells = List.of();
    private int index;
    /** Cells with no floor are not skipped but given one, from the cell before. */
    private boolean bridging;
    private CompanionExpeditionFloor laying;

    /** Water or lava beside the cells is sealed while walking, as before a swing; at most this many blocks per walk. */
    private static final int MAX_SEALS = 12;

    private CompanionWork sealing;
    private int sealed;

    CompanionExpeditionWalk(CompanionExpeditionDig dig) { this.dig = dig; }

    /** The walk seals liquid that has flowed into his own tunnel, from the bag, before it goes on. */
    CompanionExpeditionWalk sealingLiquid(CompanionWork w) {
        sealing = w;
        return this;
    }

    /** Blocks set against liquid so far: a counter the stall watchdog sees move. */
    int sealed() { return sealed; }

    void set(List<BlockPos> path, int start) {
        cells = path;
        index = start;
    }

    /** The cells of a tunnel off the stairs' foot, from the foot out to its end or from its end back to the foot; the walk starts at the cell nearest to {@code here}. */
    void inTunnel(BlockPos foot, CompanionMineSite.Tunnel tunnel, boolean outward, BlockPos here) {
        List<BlockPos> path = new ArrayList<>(tunnel.length() + 1);
        for (int k = 0; k <= tunnel.length(); k++) path.add(foot.relative(tunnel.direction(), outward ? k : tunnel.length() - k));
        int start = CompanionExpeditionRecord.nearest(path, here, Double.POSITIVE_INFINITY);
        set(path, outward ? Math.max(1, start) : start);
    }

    boolean done() { return index >= cells.size(); }

    int index() { return index; }

    void bridging(boolean on) {
        bridging = on;
        laying = null;
    }

    Result tick() {
        if (done()) return Result.DONE;
        ledBack();
        if (sealLiquid()) return Result.WORKING;
        // A cell with nothing firm under it cannot be entered, and his record can hold one whose floor is gone: the walk goes on past it, never leaving out the last.
        if (bridging) {
            Result floor = lay(cells.get(index));
            if (floor != null) return floor;
        } else while (index < cells.size() - 1 && !dig.floored(cells.get(index))) index++;
        if (dig.takingUpOnHisWay(cells.get(index))) return Result.WORKING;
        if (dig.reach(cells.get(index))) index++;
        else if (dig.walkStuck()) {
            dig.diagnoseStuck(cells.get(index), index, cells.size());
            return Result.STUCK;
        }
        return done() ? Result.DONE : Result.WORKING;
    }

    /** A creeper or a fight may have led him off the cells: the walk goes on from the one nearest to him, not from the one it left, which can be a long push away. */
    private void ledBack() {
        // A target past cells with no floor lies further than LOST while he still stands by the cell before it: that is the gap to bridge, not a push off the walk.
        if (dig.distSqr(cells.get(index)) <= LOST || index > 0 && dig.distSqr(cells.get(index - 1)) <= LOST) return;
        for (int i = 0; i < cells.size(); i++) if (dig.distSqr(cells.get(i)) < dig.distSqr(cells.get(index))) index = i;
    }

    /** True for the tick a block is set against liquid beside the next cell or where he stands; a liquid he cannot reach or seal is left to the walk and the stall watchdog. */
    private boolean sealLiquid() {
        if (sealing == null || sealed >= MAX_SEALS || !(sealing.owner.level() instanceof ServerLevel level)) return false;
        BlockPos liquid = liquidBeside(level, sealing.owner.blockPosition(), cells.get(index));
        if (liquid == null || !CompanionSeal.plug(sealing.owner, liquid).placed()) return false;
        sealing.mine.sealed(liquid);
        sealed++;
        return true;
    }

    /**
     * A source first, else flowing liquid, around the feet or the head of the cell ahead or of where he stands, outside the path itself (a block there would
     * shut it) and in reach: a spring in the wall is seen by its open face, as its centre lies behind the rock beside his head or feet.
     */
    private BlockPos liquidBeside(ServerLevel level, BlockPos here, BlockPos ahead) {
        BlockPos flowing = null;
        for (BlockPos base : new BlockPos[] {ahead, ahead.above(), here, here.above()}) {
            for (Direction side : Direction.values()) {
                BlockPos pos = base.relative(side);
                if (!level.hasChunkAt(pos) || level.getFluidState(pos).isEmpty() || onPath(pos) || pos.equals(here) || pos.equals(here.above())) continue;
                if (!CompanionSeal.inReach(sealing.owner, pos)) continue;
                if (level.getFluidState(pos).isSource()) return pos;
                if (flowing == null) flowing = pos;
            }
        }
        return flowing;
    }

    /** The foot or the head of a cell of the walk or of any tunnel he dug: a block set there would shut a way he walks next (the walk from the foot to a new tunnel passes the mouths of the old ones). */
    private boolean onPath(BlockPos pos) { return cells.contains(pos) || cells.contains(pos.below()) || sealing.mine.inTunnel(pos); }

    /** Null once the cell has a floor; the floor goes down a block at a time. */
    private Result lay(BlockPos cell) {
        if (dig.floored(cell)) { laying = null; return null; }
        if (laying == null) laying = new CompanionExpeditionFloor(cell.below());
        switch (dig.lay(laying)) {
            case LAID -> { laying = null; return null; }
            case NO_BLOCK -> { return Result.NO_BLOCK; }
            case FAILED -> { return Result.STUCK; }
            default -> { return Result.WORKING; }
        }
    }

    void clear() {
        set(List.of(), 0);
        sealed = 0;
        bridging(false);
    }
}
