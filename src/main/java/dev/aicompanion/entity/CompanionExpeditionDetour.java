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

    static CompanionExpeditionStep next(CompanionWork w, CompanionExpeditionDig dig, ServerLevel level, BlockPos stand, Direction dir, boolean mayTurn) {
        if (mayTurn) {
            CompanionShaftPlan.Avoid avoid = w.mine.avoid();
            boolean refused = avoid.refused().test(stand, dir);
            if (refused || CompanionShaftPlan.assessStep(level, stand, dir, w.mine.seals(), avoid).kind() == CompanionShaftPlan.Kind.FLOOR) {
                Direction side = CompanionShaftPlan.chooseTurn(level, stand, dir, w.mine.seals(), avoid);
                if (side != null && (refused || CompanionShaftPlan.turnCost(level, stand, side, w.mine.seals(), avoid) == 0)) {
                    return CompanionExpeditionStep.landing(w, dig, stand, side);
                }
            }
        }
        return CompanionExpeditionStep.stair(w, dig, stand, dir);
    }
}
