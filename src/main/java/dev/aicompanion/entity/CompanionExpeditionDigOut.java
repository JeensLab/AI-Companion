package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Set;

/**
 * The last restart of a fruitless chain, one past the cap: where he stands if a side is free (the cap alone stopped him), else, walled in
 * by his own cave plugs or by refusals, after he breaks the floor under him, a real break per cell, until the ring is above him and a side is free.
 */
final class CompanionExpeditionDigOut {
    enum Result { WORKING, DONE, STUCK }

    private static final int MAX_DEPTH = 3, PATIENCE = 20 * 60;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private int depth, ticks;
    private BlockPos opened, at;
    private Direction side;

    CompanionExpeditionDigOut(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.owner = w.owner;
        this.dig = dig;
    }

    BlockPos at() { return at; }

    Direction side() { return side; }

    /**
     * True when the last restart is still his: then {@link #tick} goes on to it, and digs the floor until a side is free, as many cells as it may.
     * A pit refused on every side, with no plug of his own beside him, needs it as much as a plugged one.
     */
    boolean begin() {
        reset();
        return CompanionExpeditionRecord.mayDigOut(w.mine);
    }

    Result tick(ServerLevel level) {
        if (++ticks > PATIENCE) return Result.STUCK;
        if (!owner.onGround()) return Result.WORKING;
        BlockPos here = owner.blockPosition();
        if (opened != null) {
            if (here.getY() > opened.getY()) return Result.WORKING;
            opened = null;
        }
        side = CompanionExpeditionRecord.digOutSide(level, w.mine, here);
        if (side != null) { at = here; return Result.DONE; }
        if (depth >= MAX_DEPTH) return Result.STUCK;
        BlockPos cell = here.below();
        // The caves he fled from do not shut the rock under him (a restart forgets the ones beside him as well), and a restart's own entrance
        // cell is the one he stands in: only the floor of stairs that still stand is kept.
        boolean carries = !w.mine.stands().isEmpty() && w.mine.carriesStand(cell);
        CompanionShaftPlan.Avoid open = new CompanionShaftPlan.Avoid(Set.of(), w.mine.avoid().refused(), other -> false);
        if (w.mine.forbidden(cell) || carries || CompanionShaftPlan.assess(level, here, List.of(cell), List.of(cell.below()), w.mine.seals(), open, false).kind() != CompanionShaftPlan.Kind.OK) return Result.STUCK;
        // Over the middle of the cell, or the floor beside it holds him up when the cell below is open.
        if (!dig.walkTo(here)) return Result.WORKING;
        return switch (dig.dig(level, cell)) {
            case WORKING -> Result.WORKING;
            case DONE -> { depth++; opened = cell; yield Result.WORKING; }
            default -> Result.STUCK;
        };
    }

    void reset() {
        depth = ticks = 0;
        opened = at = null;
        side = null;
    }
}
