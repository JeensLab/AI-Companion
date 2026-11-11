package dev.aicompanion.client;

import java.util.List;

/**
 * The model's answer. {@code repeat} is how many times the whole plan is walked: a walking route over markers may ask
 * for it («патрулируй от дома до дачи 100 раз»), and the queue expands the written steps before running them, so cancel,
 * progress and per-step reporting stay as they are. Any other plan, and a plan rewritten by
 * {@link CompanionPlanRequirements}, carries 1.
 */
public record CompanionReply(String text, List<PlannedAction> actions, int repeat) {
    public CompanionReply {
        if (repeat < 1 || repeat > CompanionPlanCodec.MAX_REPEAT) throw new IllegalArgumentException("Repeat out of range: " + repeat);
        if (repeat > 1 && actions.stream().anyMatch(step -> !"go_to_place".equals(step.type())))
            throw new IllegalArgumentException("A repeat is only a walking route: " + actions);
    }

    public CompanionReply(String text, List<PlannedAction> actions) {
        this(text, actions, 1);
    }
}
