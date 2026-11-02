package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;

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

    CompanionExpeditionWalk(CompanionExpeditionDig dig) { this.dig = dig; }

    void set(List<BlockPos> path, int start) {
        cells = path;
        index = start;
    }

    /** The cells of a tunnel off the stairs' foot, from the foot out to its end or from its end back to the foot; the walk starts at the cell nearest to {@code here}. */
    void inTunnel(BlockPos foot, CompanionMineSite.Tunnel tunnel, boolean outward, BlockPos here) {
        List<BlockPos> path = new ArrayList<>(tunnel.length() + 1);
        for (int k = 0; k <= tunnel.length(); k++) path.add(foot.relative(tunnel.direction(), outward ? k : tunnel.length() - k));
        int start = 0;
        for (int i = 1; i < path.size(); i++) if (path.get(i).distSqr(here) < path.get(start).distSqr(here)) start = i;
        set(path, outward ? Math.max(1, start) : start);
    }

    boolean done() { return index >= cells.size(); }

    void bridging(boolean on) {
        bridging = on;
        laying = null;
    }

    Result tick() {
        if (done()) return Result.DONE;
        ledBack();
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
        if (dig.distSqr(cells.get(index)) <= LOST) return;
        for (int i = 0; i < cells.size(); i++) if (dig.distSqr(cells.get(i)) < dig.distSqr(cells.get(index))) index = i;
    }

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
        bridging(false);
    }
}
