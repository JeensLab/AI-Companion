package dev.aicompanion.client;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Observable request queue; the server owns physical actions and results. */
public final class CompanionActionExecutor {
    private static final CompanionTaskQueue tasks = new CompanionTaskQueue();
    private static final AtomicLong planGeneration = new AtomicLong();
    private CompanionActionExecutor() { }
    public static long currentPlanGeneration() { return planGeneration.get(); }
    public static List<CompanionTaskQueue.View> tasks() { return tasks.snapshot(); }
    public static boolean cancelTask(long id) { return tasks.cancel(id); }
    public static boolean setPriority(long id, int priority) { return tasks.priority(id, priority); }

    public static CompletableFuture<String> execute(List<PlannedAction> actions, long generation) {
        return execute(actions, generation, CompanionDiagnostics.newTrace());
    }
    public static synchronized CompletableFuture<String> execute(List<PlannedAction> actions, long generation, String trace) {
        if (actions.isEmpty()) return CompletableFuture.completedFuture("");
        if (generation != planGeneration.get()) return CompletableFuture.completedFuture("План отменён игроком.");
        if (tasks.contains(actions)) {
            CompanionDiagnostics.event(trace, "duplicate_plan_rejected", "actions", actions, "generation", generation);
            return CompletableFuture.completedFuture("Такое поручение уже выполняется или стоит в очереди. Повторно не запускаю.");
        }
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        var playerId = client.player == null ? null : client.player.getUUID();
        CompanionDiagnostics.event(trace, "plan_queued", "generation", generation, "actions", actions);
        return tasks.submit(actions, task -> {
            if (server == null || playerId == null)
                return CompletableFuture.failedFuture(new IllegalStateException("Для действий компаньона нужен открытый одиночный мир."));
            long started = System.nanoTime();
            CompanionServerPlanPort port = new CompanionServerPlanPort(server, playerId, trace,
                    () -> task.cancelled() || generation != planGeneration.get(), task::progress, CompanionSchematicPlacement::choose);
            task.onCancel(port::cancelWork);
            return CompanionPlanRunner.run(actions, port).thenApply(outcome -> {
                CompanionDiagnostics.event(trace, "plan_outcome", "recoveries", outcome.recoveries(), "skipped", outcome.skipped());
                return new CompanionTaskQueue.Result(outcome.report(), !outcome.report().contains("План остановлен:"));
            }).whenComplete((report, error) -> {
                if (error != null) CompanionDiagnostics.failure(trace, "plan_execution", error);
                CompanionDiagnostics.event(trace, "plan_finished", "elapsedMs", (System.nanoTime() - started) / 1_000_000,
                        "generationCurrent", generation == planGeneration.get(), "report", report, "exception", error != null);
            });
        }).completion();
    }
    public static CompletableFuture<String> execute(List<PlannedAction> actions) { return execute(actions, currentPlanGeneration()); }
    public static void cancelPendingPlans() { cancelPendingPlans("", "world_or_command_change"); }
    public static synchronized void cancelPendingPlans(String trace, String reason) {
        planGeneration.incrementAndGet();
        tasks.cancelAll(reason.equals("world_changed"));
        CompanionDiagnostics.event(trace, "plans_invalidated", "generation", planGeneration.get(), "reason", reason);
    }
}
