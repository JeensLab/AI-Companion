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
    enum Result { WORKING, DONE, STUCK }

    private final CompanionExpeditionDig dig;
    private List<BlockPos> cells = List.of();
    private int index;

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

    Result tick() {
        if (done()) return Result.DONE;
        // A cell with nothing firm under it cannot be entered, and his record can hold one whose floor is gone: the walk goes on past it, never leaving out the last.
        while (index < cells.size() - 1 && !dig.floored(cells.get(index))) index++;
        if (dig.reach(cells.get(index))) index++;
        else if (dig.walkStuck()) {
            dig.diagnoseStuck(cells.get(index), index, cells.size());
            return Result.STUCK;
        }
        return done() ? Result.DONE : Result.WORKING;
    }

    void clear() { set(List.of(), 0); }
}
