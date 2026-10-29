package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * One advance of the mine: a staircase step, the landing of a forced turn or four cells of a tunnel. It checks the hazards once
 * (sealing liquid first), opens the cells it can reach from where he starts, walks on and opens the rest from the new stand.
 */
final class CompanionExpeditionStep {
    enum Kind { STAIR, LANDING, TUNNEL }

    enum Result { WORKING, DONE, BLOCKED, NO_TOOL, FULL }

    private static final int MAX_SEALS = 24, SEAL_TRIES = 20;

    final Kind kind;
    final BlockPos from, to;
    /** The side of a landing, the direction of the stairs after it. */
    final Direction turn;
    private final CompanionWork w;
    private final CompanionExpeditionDig dig;
    private final List<BlockPos> cells, floor;
    /** Cells before this index are opened from the old stand, the rest from the new one. */
    private final int stepAt;
    private int index, tries, seals;
    private boolean safe, moved;

    CompanionExpeditionStep(CompanionWork w, CompanionExpeditionDig dig, Kind kind, BlockPos from, BlockPos to, Direction turn,
                            List<BlockPos> cells, List<BlockPos> floor, int stepAt) {
        this.w = w; this.dig = dig; this.kind = kind; this.from = from; this.to = to; this.turn = turn;
        this.cells = cells; this.floor = floor; this.stepAt = stepAt;
    }

    static CompanionExpeditionStep stair(CompanionWork w, CompanionExpeditionDig dig, BlockPos stand, Direction d) {
        BlockPos next = CompanionShaftPlan.nextStand(stand, d);
        List<BlockPos> cells = CompanionShaftPlan.advanceCells(stand, d);
        return new CompanionExpeditionStep(w, dig, Kind.STAIR, stand, next, d, cells, List.of(next.below()), cells.size());
    }

    static CompanionExpeditionStep landing(CompanionWork w, CompanionExpeditionDig dig, BlockPos stand, Direction side) {
        BlockPos landing = CompanionShaftPlan.landing(stand, side);
        List<BlockPos> cells = CompanionShaftPlan.column(landing);
        return new CompanionExpeditionStep(w, dig, Kind.LANDING, stand, landing, side, cells, List.of(landing.below()), cells.size());
    }

    static CompanionExpeditionStep tunnel(CompanionWork w, CompanionExpeditionDig dig, BlockPos head, Direction t) {
        return new CompanionExpeditionStep(w, dig, Kind.TUNNEL, head, head.relative(t), t, CompanionShaftPlan.tunnelCells(head, t),
                CompanionShaftPlan.tunnelFloor(head, t), 2);
    }

    Result tick(ServerLevel level) {
        if (!safe) return check(level);
        if (index < stepAt) return open(level, stepAt);
        if (!moved) {
            if (!dig.walkTo(to)) return dig.walkStuck() ? Result.BLOCKED : Result.WORKING;
            moved = true;
        }
        if (index < cells.size()) return open(level, cells.size());
        dig.flush();
        return Result.DONE;
    }

    private Result check(ServerLevel level) {
        if (!dig.walkTo(from)) return dig.walkStuck() ? Result.BLOCKED : Result.WORKING;
        CompanionShaftPlan.Hazard hazard = CompanionShaftPlan.assess(level, from, cells, floor, w.mine.seals());
        switch (hazard.kind()) {
            case OK -> safe = true;
            case BLOCKED -> { return Result.BLOCKED; }
            case SEAL -> {
                CompanionSeal.Result placed = CompanionSeal.place(w.owner, hazard.cell());
                if (placed.placed()) { w.mine.sealed(hazard.cell()); tries = 0; seals++; }
                else if (placed == CompanionSeal.Result.NO_BLOCK || ++tries > SEAL_TRIES) return Result.BLOCKED;
                if (seals > MAX_SEALS) return Result.BLOCKED;
            }
        }
        return Result.WORKING;
    }

    private Result open(ServerLevel level, int limit) {
        if (!dig.walkTo(moved ? to : from)) return dig.walkStuck() ? Result.BLOCKED : Result.WORKING;
        switch (dig.dig(level, cells.get(index))) {
            case DONE -> { if (++index == limit) dig.flush(); }
            case NO_TOOL -> { return Result.NO_TOOL; }
            case FULL -> { return Result.FULL; }
            case STUCK -> { return Result.BLOCKED; }
            case WORKING -> { }
        }
        return Result.WORKING;
    }
}
