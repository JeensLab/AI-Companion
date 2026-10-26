package dev.aicompanion.client;

/** A validated, allowlisted action proposed by the model. */
public record PlannedAction(String type, int seconds, int count, String item, String slot, String location) {
    /** smelt_items without a number: everything of that raw material carried when the step starts (at most a stack). */
    public static final int ALL_CARRIED = dev.aicompanion.entity.CompanionWork.ALL_CARRIED;
    public PlannedAction(String type, int seconds, int count, String item, String slot) {
        this(type, seconds, count, item, slot, "near_player");
    }
    public PlannedAction(String type, int seconds, int count, String item) {
        this(type, seconds, count, item, "auto");
    }
    public PlannedAction(String type, int seconds, int count) {
        this(type, seconds, count, "", "auto");
    }
}
