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
            // The three buttons of a long goal act on that goal only, as the phrases do; a screen that shows an older goal changes
            // nothing. A screen without a long goal keeps the old meaning of these buttons: the pickaxe order.
            case "cancel_goal" -> {
                if (shown.agenda() != null) CompanionAgenda.cancelShown(player, c, shown.agenda().goal());
                else cancelPickaxes(c, shown);
            }
            case "resume_goal" -> {
                if (shown.agenda() == null) resumePickaxes(c, shown);
                else if (live(c, shown) instanceof CompanionAgendaView.View now && now.canResume()) {
                    // «Продолжай» also ends a deed of his own and lifts his hold, as the phrase does.
                    c.autonomy().playerOrder("goal_resume");
                    CompanionAgenda.resume(player, c);
                }
            }
            case "defer_goal" -> { if (live(c, shown) instanceof CompanionAgendaView.View now && now.canDefer()) CompanionAgenda.defer(player, c); }
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
    /** The goal as it is now, if it is still the one the screen shows: the buttons act on what is true, not on what was drawn. */
    private static CompanionAgendaView.View live(CompanionEntity c, Snapshot shown) {
        var now = shown.agenda() == null ? null : CompanionAgendaView.of(c);
        return now != null && now.goal().equals(shown.agenda().goal()) ? now : null;
    }
    private static boolean pickaxesShown(CompanionEntity c, Snapshot shown) {
        return shown.goal() != null && shown.goal().equals(c.goals().id());
    }
    private static void cancelPickaxes(CompanionEntity c, Snapshot shown) { if (pickaxesShown(c, shown)) c.goals().cancel(); }
    private static void resumePickaxes(CompanionEntity c, Snapshot shown) {
        if (!pickaxesShown(c, shown)) return;
        c.autonomy().playerOrder("goal_resume");
        c.goals().resume();
    }
}
