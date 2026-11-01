package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Set;

/**
 * He is walled in by his own cave plugs and no side is left to begin stairs to: he breaks the floor under him, a real break per cell,
 * until the ring of plugs is above him and a side is free, and the mine begins again from that cell.
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

    /** The cell he starts again in, and the side the stairs go to there. */
    BlockPos at() { return at; }

    Direction side() { return side; }

    /** True when he is plugged in with a restart to spare: then {@link #tick} digs. */
    boolean begin(ServerLevel level) {
        reset();
        BlockPos here = CompanionExpeditionRecord.standingCell(level, owner.blockPosition());
        return CompanionExpeditionRecord.mayDigOut(w.mine) && CompanionExpeditionRecord.pluggedIn(w.mine, here);
    }

    Result tick(ServerLevel level) {
        if (++ticks > PATIENCE) return Result.STUCK;
        if (!owner.onGround()) return Result.WORKING;
        BlockPos here = owner.blockPosition();
        if (opened != null) {
            if (here.getY() > opened.getY()) return Result.WORKING;
            opened = null;
        }
        if (depth > 0) {
            side = CompanionExpeditionRecord.digOutSide(level, w.mine, here);
            if (side != null) { at = here; return Result.DONE; }
        }
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
