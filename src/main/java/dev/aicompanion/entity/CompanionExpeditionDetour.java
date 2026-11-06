package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The next advance of the staircase when the straight step is not the best one: after a refusal of that step in an earlier
 * attempt he turns at once, and where the step only lacks its floor he turns only if the turn needs nothing placed.
 */
final class CompanionExpeditionDetour {
    private CompanionExpeditionDetour() { }

    /** The step to take from {@code stand}, or null when the straight step is blocked or refused and there is no turn: the stairs end here. */
    static CompanionExpeditionStep next(CompanionWork w, CompanionExpeditionDig dig, ServerLevel level, BlockPos stand, Direction dir, boolean mayTurn) {
        CompanionShaftPlan.Avoid avoid = w.mine.avoid();
        boolean refused = avoid.refused().test(stand, dir);
        CompanionShaftPlan.Kind kind = CompanionShaftPlan.assessStep(level, stand, dir, w.mine.seals(), avoid).kind();
        boolean shut = refused || kind == CompanionShaftPlan.Kind.BLOCKED;
        Direction side = mayTurn && (shut || kind == CompanionShaftPlan.Kind.FLOOR) ? CompanionShaftPlan.chooseTurn(level, stand, dir, w.mine.seals(), avoid) : null;
        if (side != null && (refused || kind != CompanionShaftPlan.Kind.BLOCKED && CompanionShaftPlan.turnCost(level, stand, side, w.mine.seals(), avoid) == 0)) {
            return CompanionExpeditionStep.landing(w, dig, stand, side);
        }
        return shut && side == null ? null : CompanionExpeditionStep.stair(w, dig, stand, dir);
    }
}
