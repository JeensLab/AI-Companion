package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The staircase ends in a dead end: he walks back up his own stairs, twice as far each time, to the first stand where a side
 * step down is possible, closes the way behind him with a block from the bag and turns off there. The record is cut only
 * when this is done, so an interrupted backtrack is chosen again from the full record.
 */
final class CompanionExpeditionBacktrack {
    enum Result { WORKING, DONE, STUCK }

    private enum Phase { WALK, PLUG }

    static final int MAX_BACKTRACKS = 8;
    private static final int PLUG_TRIES = 60;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionWalk walk;
    private Phase phase = Phase.WALK;
    /** Index in the record of the stand he goes back to; -1 is the entrance. */
    private int at;
    private Direction side;
    private BlockPos target, plugCell, lowerStand;
    private int tries;

    CompanionExpeditionBacktrack(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.owner = w.owner;
        this.walk = new CompanionExpeditionWalk(dig);
    }

    /** The stand he went back to, and the side he turns off to there. */
    BlockPos target() { return target; }

    Direction side() { return side; }

    /** Picks the stand to go back to; false when there is none (or the mine has used its backtracks), then the mine starts again. */
    boolean begin(ServerLevel level) {
        reset();
        CompanionMineSite mine = w.mine;
        List<BlockPos> stands = mine.stands();
        if (!mine.exists() || stands.isEmpty() || mine.backtracks() >= MAX_BACKTRACKS) return false;
        int jump = 1 << mine.backtracks();
        for (int i = stands.size() - 1 - jump; i >= -1; i--) {
            BlockPos stand = pos(mine, i);
            if (mine.shunned(stand)) continue;
            Direction turn = sideAt(level, mine, i);
            if (turn == null) continue;
            at = i;
            side = turn;
            target = stand;
            lowerStand = pos(mine, i + 1);
            plugCell = lowerStand.above();
            List<BlockPos> way = new ArrayList<>();
            List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
            if (!tunnels.isEmpty()) {
                CompanionMineSite.Tunnel last = tunnels.get(tunnels.size() - 1);
                for (int k = last.length(); k >= 1; k--) way.add(mine.foot().relative(last.direction(), k));
            }
            for (int j = stands.size() - 1; j >= i; j--) way.add(pos(mine, j));

            walk.set(way, 0);
            mine.backtracked();
            return true;
        }
        return false;
    }

    Result tick(ServerLevel level) {
        if (phase == Phase.WALK) {
            switch (walk.tick()) {
                case WORKING -> { return Result.WORKING; }
                case STUCK -> { return Result.STUCK; }
                case DONE -> phase = Phase.PLUG;
            }
        }
        return plug(level);
    }

    /** One tick of closing {@code cell} from the stand beside it; true when done. A plug that cannot be placed does not stop him. */
    boolean seal(ServerLevel level, BlockPos cell) {
        // A torch he hung on the stair wall may stand in the cell: it comes out like a plant, as in a cave plug.
        if (!CompanionSeal.replaceable(level, cell) && CompanionShaftPlan.open(level, cell)) { level.destroyBlock(cell, true, owner); return false; }
        CompanionSeal.Result result = CompanionSeal.place(owner, cell);
        if (result.placed()) w.mine.sealed(cell);
        else if (result != CompanionSeal.Result.NO_BLOCK && ++tries <= PLUG_TRIES) return false;
        tries = 0;
        return true;
    }

    /** The way he came from is closed from the stand above it; the cell is forbidden either way. */
    private Result plug(ServerLevel level) {
        if (!seal(level, plugCell)) return Result.WORKING;
        Direction stairs = toward(target, lowerStand);

        if (stairs != null) w.mine.refuse(target, stairs);
        w.mine.forbid(plugCell);
        w.mine.truncate(at + 1);
        return Result.DONE;
    }

    void reset() {
        walk.clear();
        phase = Phase.WALK;
        at = tries = 0;
        side = null;
        target = plugCell = lowerStand = null;
    }

    /** The cheapest side step off the stand that is not the stairs themselves; null when none needs less than a fallback turn. */
    private static Direction sideAt(ServerLevel level, CompanionMineSite mine, int i) {
        BlockPos stand = pos(mine, i);
        Direction down = toward(stand, pos(mine, i + 1)), up = i < 0 ? null : toward(stand, pos(mine, i - 1));
        Direction best = null;
        int bestCost = CompanionShaftPlan.FALLBACK_TURN;
        for (Direction turn : Direction.Plane.HORIZONTAL) {
            if (turn == down || turn == up) continue;
            int cost = CompanionShaftPlan.turnCost(level, stand, turn, mine.seals(), mine.avoid());
            if (cost < bestCost) { best = turn; bestCost = cost; }
        }
        return best;
    }

    private static BlockPos pos(CompanionMineSite mine, int i) { return i < 0 ? mine.entrance() : mine.stands().get(i); }

    /** The horizontal direction from one stand to a neighbour one; null for a stand straight above or below. */
    private static Direction toward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return dx == 0 && dz == 0 ? null : Direction.getNearest(dx, 0, dz);
    }
}
