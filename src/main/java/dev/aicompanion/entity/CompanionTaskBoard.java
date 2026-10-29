package dev.aicompanion.entity;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Immutable snapshots only; no client reads of a live server entity. */
public final class CompanionTaskBoard {
    private CompanionTaskBoard() { }
    public record Snapshot(UUID companion, String name, String activity, String detail, long work,
                           UUID goal, CompanionGoals.State goalState, String goalStatus, String goalNext,
                           boolean autonomous, boolean autonomyHeld, String autonomyTask, CompanionAgendaView.View agenda) { }
    public static Snapshot read(ServerPlayer player) {
        var c = CompanionEvents.getOwnedCompanion(player);
        if (c == null) return null;
        return new Snapshot(c.getUUID(), c.getName().getString(), c.taskActivity(), c.getLastTaskResult(), c.activeWorkId(),
                c.goals().id(), c.goals().state(), c.goals().status(), c.goals().nextTask(),
                c.autonomy().enabled(), c.autonomy().held(), c.autonomy().taskPreview(), CompanionAgendaView.of(c));
    }
    public static void control(ServerPlayer player, Snapshot shown, String command) {
        var c = CompanionEvents.getOwnedCompanion(player);
        if (c == null || shown == null || !c.getUUID().equals(shown.companion())) return;
        switch (command) {
            // The three buttons of a long goal act as the phrases do; a screen that shows an older goal changes nothing.
            case "cancel_goal" -> {
                if (agendaShown(c, shown) && CompanionAgenda.cancel(player, c) != null) return;
                cancelPickaxes(c, shown);
            }
            case "resume_goal" -> {
                // «Продолжай» also ends a deed of his own and lifts his hold, as the phrase does.
                if (agendaShown(c, shown) || pickaxesShown(c, shown)) c.autonomy().playerOrder("goal_resume");
                if (agendaShown(c, shown) && CompanionAgenda.resume(player, c) != null) return;
                resumePickaxes(c, shown);
            }
            case "defer_goal" -> { if (agendaShown(c, shown) && shown.agenda().canDefer()) CompanionAgenda.defer(player, c); }
            // The row of the pickaxe order: its buttons never touch a long goal that waits beside it.
            case "cancel_pickaxes" -> cancelPickaxes(c, shown);
            case "resume_pickaxes" -> resumePickaxes(c, shown);
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
    private static boolean agendaShown(CompanionEntity c, Snapshot shown) {
        return shown.agenda() != null && shown.agenda().goal().equals(CompanionAgendaView.target(c));
    }
    private static boolean pickaxesShown(CompanionEntity c, Snapshot shown) {
        return shown.goal() != null && shown.goal().equals(c.goals().id());
    }
    private static void cancelPickaxes(CompanionEntity c, Snapshot shown) { if (pickaxesShown(c, shown)) c.goals().cancel(); }
    private static void resumePickaxes(CompanionEntity c, Snapshot shown) { if (pickaxesShown(c, shown)) c.goals().resume(); }
}
