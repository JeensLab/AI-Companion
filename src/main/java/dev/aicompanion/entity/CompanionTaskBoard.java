package dev.aicompanion.entity;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Immutable snapshots only; no client reads of a live server entity. */
public final class CompanionTaskBoard {
    private CompanionTaskBoard() { }
    public record Snapshot(UUID companion, String name, String activity, String detail, long work,
                           UUID goal, CompanionGoals.State goalState, String goalStatus, String goalNext,
                           boolean autonomous, boolean autonomyHeld, String autonomyTask) { }
    public static Snapshot read(ServerPlayer player) {
        var c = CompanionEvents.getOwnedCompanion(player);
        if (c == null) return null;
        return new Snapshot(c.getUUID(), c.getName().getString(), c.taskActivity(), c.getLastTaskResult(), c.activeWorkId(),
                c.goals().id(), c.goals().state(), c.goals().status(), c.goals().nextTask(),
                c.autonomy().enabled(), c.autonomy().held(), c.autonomy().taskPreview());
    }
    public static void control(ServerPlayer player, Snapshot shown, String command) {
        var c = CompanionEvents.getOwnedCompanion(player);
        if (c == null || shown == null || !c.getUUID().equals(shown.companion())) return;
        switch (command) {
            case "cancel_goal" -> { if (shown.goal() != null && shown.goal().equals(c.goals().id())) c.goals().cancel(); }
            case "resume_goal" -> { if (shown.goal() != null && shown.goal().equals(c.goals().id())) c.goals().resume(); }
            case "cancel_work" -> {
                if (shown.work() <= 0 || c.activeWorkId() != shown.work()) return;
                if (c.goals().ownsWork(shown.work())) c.goals().cancel();
                else if (c.autonomy().running()) c.autonomy().setEnabled(false);
                else c.stopGathering();
            }
            case "cancel_autonomy" -> { if (shown.autonomous()) c.autonomy().setEnabled(false); }
            default -> { }
        }
    }
}
