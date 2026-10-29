package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;

import java.util.List;

/**
 * One advance of the mine: a staircase step, the landing of a forced turn or four cells of a tunnel. It checks the hazards once
 * (sealing liquid first), opens the cells it can reach from where he starts, walks on and opens the rest from the new stand.
 */
final class CompanionExpeditionStep {
    enum Kind { STAIR, LANDING, TUNNEL }

    enum Result { WORKING, DONE, BLOCKED, NO_TOOL, FULL }

    private static final int MAX_SEALS = 24, SEAL_TRIES = 20;
    private static final String WALK = "застрял на ходу: не смог дойти до нужной клетки";

    final Kind kind;
    final BlockPos from, to;
    /** The side of a landing, the direction of the stairs after it. */
    final Direction turn;
    private final CompanionWork w;
    private final CompanionExpeditionDig dig;
    private final List<BlockPos> cells, floor;
    /** Cells before this index are opened from the old stand, the rest from the new one. */
    private final int stepAt;
    /** Why the advance was blocked, short, for the report. */
    String why = "";
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

    /** The cells this advance opens. */
    List<BlockPos> cells() { return cells; }

    Result tick(ServerLevel level) {
        if (!safe) return check(level);
        if (index < stepAt) return open(level, stepAt);
        if (!moved) {
            if (!dig.walkTo(to)) return dig.walkStuck() ? fail(WALK) : Result.WORKING;
            moved = true;
        }
        if (index < cells.size()) return open(level, cells.size());
        dig.flush();
        return Result.DONE;
    }

    private Result check(ServerLevel level) {
        if (!dig.walkTo(from)) return dig.walkStuck() ? fail(WALK) : Result.WORKING;
        CompanionShaftPlan.Hazard hazard = CompanionShaftPlan.assess(level, from, cells, floor, w.mine.seals());
        switch (hazard.kind()) {
            case OK -> safe = true;
            case BLOCKED -> { return fail(diagnose(level)); }
            case SEAL -> {
                CompanionSeal.Result placed = CompanionSeal.place(w.owner, hazard.cell());
                if (placed.placed()) { w.mine.sealed(hazard.cell()); tries = 0; seals++; }
                else if (placed == CompanionSeal.Result.NO_BLOCK) return fail("жидкость надо заделать, а блоков для этого в сумке нет");
                else if (++tries > SEAL_TRIES) return fail("не смог заделать жидкость: не дотянулся или клетка занята");
                if (seals > MAX_SEALS) return fail("жидкости слишком много: заделок уже больше " + MAX_SEALS);
            }
        }
        return Result.WORKING;
    }

    private Result open(ServerLevel level, int limit) {
        if (!dig.walkTo(moved ? to : from)) return dig.walkStuck() ? Result.BLOCKED : Result.WORKING;
        switch (dig.dig(level, cells.get(index))) {
            case DONE -> { if (++index == limit) dig.flush(); }
            case NO_TOOL -> { return Result.NO_TOOL; }
            case NO_HAND -> { return fail("не смог взять кирку в руку"); }
            case FULL -> { return Result.FULL; }
            case STUCK -> { return fail("клетку не удалось разобрать: не вижу её, не дотягиваюсь или в ней жидкость"); }
            case WORKING -> { }
        }
        return Result.WORKING;
    }

    private Result fail(String reason) {
        why = reason;
        return Result.BLOCKED;
    }

    /** The first cause that makes a cell of this advance impassable, in the order the hazard check looks at them. */
    private String diagnose(ServerLevel level) {
        for (BlockPos cell : cells) {
            if (CompanionShaftPlan.classify(level, from, cell, w.mine.seals()).kind() != CompanionShaftPlan.Kind.BLOCKED) continue;
            if (!level.hasChunkAt(cell) || !level.getWorldBorder().isWithinBounds(cell) || level.isOutsideBuildHeight(cell)) return "дальше граница мира или незагруженная земля";
            if (liquidAround(level, cell)) return "рядом жидкость, до которой не дотянуться, чтобы заделать";
            if (!CompanionShaftPlan.open(level, cell) && !w.mine.seals().contains(cell) && !CompanionOre.tunnelable(level.getBlockState(cell))) return "на пути коренная порода или неподходящий блок";
            if (level.getBlockEntity(cell) != null || level.getBlockState(cell.above()).getBlock() instanceof FallingBlock) {
                return level.getBlockEntity(cell) != null ? "на пути сундук или другой хозяйственный блок" : "над клеткой песок или гравий, он завалит ход";
            }
            return "рядом постройка";
        }
        return "под новым местом неустойчивый пол";
    }

    private static boolean liquidAround(ServerLevel level, BlockPos cell) {
        if (!level.getFluidState(cell).isEmpty()) return true;
        for (Direction side : Direction.values()) if (!level.getFluidState(cell.relative(side)).isEmpty()) return true;
        return false;
    }
}
