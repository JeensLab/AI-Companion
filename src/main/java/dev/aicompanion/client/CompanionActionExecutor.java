package dev.aicompanion.client;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Observable request queue; the server owns physical actions and results. */
public final class CompanionActionExecutor {
    private static final CompanionParkLink link = new CompanionParkLink();
    private static final AtomicLong planGeneration = new AtomicLong();
    private CompanionActionExecutor() { }
    public static long currentPlanGeneration() { return planGeneration.get(); }
    public static List<CompanionTaskQueue.View> tasks() { return link.tasks(); }
    public static boolean cancelTask(long id) { return link.cancel(id); }
    public static boolean setPriority(long id, int priority) { return link.priority(id, priority); }

    public static CompletableFuture<String> execute(List<PlannedAction> actions, long generation) {
        return execute(actions, generation, CompanionDiagnostics.newTrace());
    }
    public static synchronized CompletableFuture<String> execute(List<PlannedAction> actions, long generation, String trace) {
        if (actions.isEmpty()) return CompletableFuture.completedFuture("");
        if (generation != planGeneration.get()) return CompletableFuture.completedFuture("План отменён игроком.");
        if (link.contains(actions)) {
            CompanionDiagnostics.event(trace, "duplicate_plan_rejected", "actions", actions, "generation", generation);
            return CompletableFuture.completedFuture("Такое поручение уже выполняется или стоит в очереди. Повторно не запускаю.");
        }
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        var playerId = client.player == null ? null : client.player.getUUID();
        CompanionDiagnostics.event(trace, "plan_queued", "generation", generation, "actions", actions);
        link.bind(server, playerId);
        return link.submit(actions, trace, () -> generation != planGeneration.get());
    }
    public static CompletableFuture<String> execute(List<PlannedAction> actions) { return execute(actions, currentPlanGeneration()); }
    public static void cancelPendingPlans() { cancelPendingPlans("", "world_or_command_change"); }
    public static synchronized void cancelPendingPlans(String trace, String reason) {
        planGeneration.incrementAndGet();
        link.cancelAll(reason.equals("world_changed"), reason.equals(CompanionLocalCommands.PLAYER_CANCEL));
        CompanionDiagnostics.event(trace, "plans_invalidated", "generation", planGeneration.get(), "reason", reason);
    }
}
