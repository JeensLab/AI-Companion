package dev.aicompanion.entity;

import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.entity.CompanionAgendaData.Goal;
import dev.aicompanion.entity.CompanionMilestones.Kind;
import dev.aicompanion.entity.CompanionMilestones.Milestone;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** The waits of a goal's mine step for food and game, and the iron the ore search could not reach (moved out of {@link CompanionAgenda}). */
final class CompanionAgendaWaits {
    /** A retreat that ends in a rest he cannot take (hurt, nothing to eat) is waited out this many times, a minute each, before it counts as a failure of the mine step. */
    private static final int GAME_WAITS = 4, HUNGRY_WAITS = 6, HUNGRY_WAIT_TICKS = 20 * 60;
    /** What the hunt reports when no game of the kind is in range at all (CompanionHunting.finishHunt). */
    private static final String NO_GAME = "больше не нашёл";

    private CompanionAgendaWaits() { }

    /**
     * The top-up stopped before the mine for lack of food, and the descent is not allowed without it. A hunt that found no game at all
     * changes nothing by waiting: with full health the goal stops at once and asks for food; a hurt one (or a hunt that saw game it could not
     * take) waits a minute and hunts again, a few times, before a failure counts.
     */
    static boolean forGame(CompanionAgendaData data, UUID owner, Goal goal, Milestone milestone, CompanionEntity c, CompanionPlanRunner.Outcome outcome, Throwable error) {
        if (milestone.kind() != Kind.EXPEDITION || error != null || outcome == null || goal.hungryWaits >= GAME_WAITS
                || outcome.skipped().stream().noneMatch(step -> "dig_for_diamonds".equals(step.type())) || !milestone.foodShort(CompanionFacts.of(c))) return false;
        boolean hurt = c.getHealth() < c.getMaxHealth();
        if (outcome.report().contains(NO_GAME) && !hurt) {
            // Diamonds already in the bag (a death on the way up took the bread) are not held back behind the food: they go to the player, and the goal ends with them.
            if (CompanionFacts.of(c).carried(Items.DIAMOND) > 0) { goal.handOverShort = true; data.setDirty(); return true; }
            CompanionAgenda.block(data, owner, goal, c, "нет еды, а животных поблизости нет. Принеси еды, и я пойду вниз.");
            return true;
        }
        if (goal.hungryWaits++ == 0) c.autonomySay("Еды нет, а без неё вниз не пойду: подожду и поищу еду снова.");
        goal.waitUntil = c.level().getGameTime() + HUNGRY_WAIT_TICKS;
        goal.reason = (hurt ? CompanionExpeditionRetreat.NO_FOOD : "нет еды для спуска") + ": " + CompanionAgenda.reason(outcome.report());
        data.setDirty();
        return true;
    }

    /**
     * A mine step that ended in a rest he could not take (hurt, no food, a stomach too empty to mend) is no failure of the mine: a stop spent on it
     * is gone for a wound that only food heals. The goal waits a minute and tries again, a few times, and says why once.
     */
    static boolean forFood(CompanionAgendaData data, Goal goal, Milestone milestone, CompanionEntity c, CompanionPlanRunner.Outcome outcome, Throwable error) {
        if (milestone.kind() != Kind.EXPEDITION || error != null || outcome == null || goal.hungryWaits >= HUNGRY_WAITS || !outcome.report().contains(CompanionExpeditionRetreat.NO_FOOD)) return false;
        if (goal.hungryWaits++ == 0) c.autonomySay("Еды нет, а рана сама не заживает: подожду и поищу еду снова.");
        goal.waitUntil = c.level().getGameTime() + HUNGRY_WAIT_TICKS;
        goal.reason = CompanionAgenda.reason(outcome.report());
        data.setDirty();
        return true;
    }

    /** The ore search came back empty for iron: none seen within its depth, or seen but not safely reachable from here. */
    static boolean ironBeyondReach(String report) {
        return report.contains("«железная руда»") && (report.contains("блоков вниз.") || report.contains("не нашёл безопасного хода"));
    }
}
