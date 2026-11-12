package dev.aicompanion.client;

import java.util.List;

/**
 * The model's answer. {@code repeat} is how many times the walking route is walked: a route over markers may ask
 * for it («патрулируй от дома до дачи 100 раз»), and the queue expands the go_to_place steps before running them, so
 * cancel, progress and per-step reporting stay as they are. A step beside the route — a remembered place, or one the
 * request's wording added — runs once. A plan rewritten by {@link CompanionPlanRequirements} keeps the repeat it was
 * decoded with.
 */
public record CompanionReply(String text, List<PlannedAction> actions, int repeat) {
    public CompanionReply {
        if (repeat < 1 || repeat > CompanionPlanCodec.MAX_REPEAT) throw new IllegalArgumentException("Repeat out of range: " + repeat);
        // The repeat covers the walk itself; without a go_to_place step there is nothing to walk again.
        if (repeat > 1 && actions.stream().noneMatch(step -> "go_to_place".equals(step.type())))
            throw new IllegalArgumentException("A repeat needs a walking route: " + actions);
    }

    public CompanionReply(String text, List<PlannedAction> actions) {
        this(text, actions, 1);
    }
}
