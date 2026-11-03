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
    /** True when the advance failed for a reason that will not change by itself (a hazard, a plug, no floor to lay), not a stuck walk or a missing tool. */
    boolean lasting;
    /** True when the advance failed because he could not walk to the cell it needed: the stairs may be lost from under him. */
    boolean lost;
    private int index, tries, seals;
    private boolean safe, moved;
    /** Set when the new stand has no floor: it is laid after the cells are open and before he walks onto it. */
    private CompanionExpeditionFloor laying;

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

    /** A plug in this cell strands the advance: he must walk through it (the second lane of a tunnel is beside the walk). */
    boolean needs(BlockPos cell) { return kind != Kind.TUNNEL || cells.indexOf(cell) < 2; }

    Result tick(ServerLevel level) {
        if (!safe) return check(level);
        if (index < stepAt) return open(level, stepAt);
        if (!moved) {
            if (laying != null) {
                Result laid = layFloor(level);
                if (laid != null) return laid;
            }
            if (!dig.reach(to)) return dig.walkStuck() ? lose() : Result.WORKING;
            moved = true;
        }
        if (index < cells.size()) return open(level, cells.size());
        dig.flush();
        return Result.DONE;
    }

    private Result check(ServerLevel level) {
        if (!dig.reach(from)) return dig.walkStuck() ? lose() : Result.WORKING;
        CompanionShaftPlan.Hazard hazard = CompanionShaftPlan.assess(level, from, cells, floor, w.mine.seals(), w.mine.avoid(), kind != Kind.TUNNEL);
        switch (hazard.kind()) {
            case OK -> safe = true;
            case FLOOR -> { laying = new CompanionExpeditionFloor(hazard.cell()); safe = true; }
            case BLOCKED -> { return refuse(diagnose(level)); }
            case SEAL -> { return seal(hazard.cell()); }
        }
        return Result.WORKING;
    }

    private Result seal(BlockPos liquid) {
        CompanionSeal.Result placed = CompanionSeal.place(w.owner, liquid);
        if (placed.placed()) { w.mine.sealed(liquid); tries = 0; seals++; }
        else if (placed == CompanionSeal.Result.NO_BLOCK) return fail("жидкость надо заделать, а блоков для этого в сумке нет");
        else if (++tries > SEAL_TRIES) return refuse("не смог заделать жидкость: не дотянулся или клетка занята");
        if (seals > MAX_SEALS) return refuse("жидкости слишком много: заделок уже больше " + MAX_SEALS);
        return Result.WORKING;
    }

    /** Null once the floor holds and the walk may go on; a hazard-like failure ends the advance, a missing block is only reported. */
    private Result layFloor(ServerLevel level) {
        switch (laying.lay(w.owner, level)) {
            case PLACED -> { w.mine.sealed(laying.cell); return Result.WORKING; }
            case LAID -> { laying = null; return null; }
            case NO_BLOCK -> { return fail("пол под новой ступенью надо уложить, а блоков для этого в сумке нет"); }
            case FAILED -> { return refuse("не смог уложить пол под новой ступенью: не дотянулся или клетка занята"); }
            default -> { return Result.WORKING; }
        }
    }

    private Result open(ServerLevel level, int limit) {
        BlockPos stand = moved ? to : from;
        if (!dig.reach(stand)) return dig.walkStuck() ? lose() : Result.WORKING;
        // Digging opens sources the check did not see: liquid that has flowed in since is sealed before the cell is broken.
        CompanionShaftPlan.Hazard flowed = CompanionShaftPlan.classify(level, stand, cells.get(index), w.mine.seals(), w.mine.avoid(), null);
        if (flowed.kind() == CompanionShaftPlan.Kind.SEAL) return seal(flowed.cell());
        switch (dig.dig(level, cells.get(index))) {
            case DONE -> { if (++index == limit) dig.flush(); }
            case NO_TOOL -> { return Result.NO_TOOL; }
            case NO_HAND -> { return fail("не смог взять кирку в руку"); }
            case FULL -> { return Result.FULL; }
            case STUCK -> {
                dig.diagnoseDig(cells.get(index));
                return fail("клетку не удалось разобрать: не вижу её, не дотягиваюсь или в ней жидкость");
            }
            case WORKING -> { }
        }
        return Result.WORKING;
    }

    private Result lose() {
        lost = true;
        return fail(WALK);
    }

    private Result refuse(String reason) {
        lasting = true;
        return fail(reason);
    }

    private Result fail(String reason) {
        why = reason;
        return Result.BLOCKED;
    }

    /** The first cause that makes a cell of this advance impassable, in the order the hazard check looks at them. */
    private String diagnose(ServerLevel level) {
        for (BlockPos cell : cells) {
            if (CompanionShaftPlan.classify(level, from, cell, w.mine.seals(), w.mine.avoid(), null).kind() != CompanionShaftPlan.Kind.BLOCKED) continue;
            if (w.mine.forbidden(cell)) return "в этой клетке моя пробка: там пещера";
            if (w.mine.carriesStand(cell)) return "в этой клетке пол моей же ступени";
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
