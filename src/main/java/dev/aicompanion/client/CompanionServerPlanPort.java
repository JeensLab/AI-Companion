package dev.aicompanion.client;

import dev.aicompanion.client.CompanionPlanRunner.Attempt;
import dev.aicompanion.client.CompanionPlanRunner.State;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Executes plan steps on the server thread and waits for the result of their own workId. Shared by the client and GameTests. */
public final class CompanionServerPlanPort implements CompanionPlanRunner.Port {
    public static final int WORK_LIMIT_SECONDS = 660;
    public static final int GATHER_LIMIT_SECONDS = 185;
    private static final Set<String> TRACKED_WORK = Set.of("gather_wood", "gather_dirt", "gather_stone", "store_items", "take_items",
            "build_house", "craft_item", "place_block", "come", "hunt_mobs", "give_items", "give_wood", "smelt_items", "mine_ore", "hunt_animals");
    private static final Queue<Sleeper> SLEEPERS = new ConcurrentLinkedQueue<>();
    private final MinecraftServer server;
    private final UUID playerId;
    private final String trace;
    private final BooleanSupplier cancelled;
    private final java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress;
    private long ownedWorkId;

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled) {
        this(server, playerId, trace, cancelled, (plan, index) -> { });
    }

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled,
            java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress) {
        this.server = server; this.playerId = playerId; this.trace = trace; this.cancelled = cancelled;
        this.progress = progress;
    }

    @Override public void progress(java.util.List<PlannedAction> plan, int index) { progress.accept(plan, index); }

    /** Ordered on the same server executor as dispatch, restricted to this request's own work ID. */
    public void cancelWork() {
        if (!server.isRunning()) return;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var companion = player == null ? null : CompanionEvents.getOwnedCompanion(player);
            if (companion != null && ownedWorkId > 0 && companion.activeWorkId() == ownedWorkId) companion.stopGathering();
        });
    }

    @Override public String trace() { return trace; }
    @Override public boolean cancelled() { return cancelled.getAsBoolean(); }

    @Override public CompletableFuture<State> state(PlannedAction action) {
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            return companion == null ? new State(false, -1)
                    : new State(companion.busyForOrders(), companion.planProgress(action.type()));
        });
    }

    @Override public CompletableFuture<Attempt> run(PlannedAction action) {
        return onServer(player -> {
            if (cancelled()) return new Dispatch(CompanionActionResult.cancelled("План отменён."), 0, 0);
            CompanionEntity before = CompanionEvents.getOwnedCompanion(player);
            long previousWork = before == null ? 0 : before.activeWorkId();
            CompanionActionResult result = CompanionEvents.executeAction(player, action.type(), action.item(), action.count(), action.slot(), trace, action.location());
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            long work = companion == null ? 0 : companion.activeWorkId();
            if (result.success() && work != previousWork) ownedWorkId = work;
            return new Dispatch(result, result.success() && work != previousWork ? work : 0, player.level().getGameTime());
        }).thenCompose(dispatch -> {
            CompanionActionResult result = dispatch.result();
            if (!result.success()) return CompletableFuture.completedFuture(new Attempt(result, false));
            CompletableFuture<CompanionActionResult> verified;
            if (dispatch.workId() > 0 && TRACKED_WORK.contains(action.type())) verified = waitForWork(dispatch.workId(), dispatch.tick(), limitSeconds(action));
            else if ("gather_wood".equals(action.type())) verified = waitForGathering(dispatch.tick());
            else if ("wait".equals(action.type())) verified = waitSeconds(action.seconds(), result);
            else verified = CompletableFuture.completedFuture(result);
            return verified.thenApply(done -> new Attempt(done, true));
        });
    }

    @Override public CompletableFuture<CompanionPlanRunner.Expansion> expand(PlannedAction action) {
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null) return new CompanionPlanRunner.Expansion(java.util.List.of(), "Компаньон выгружен из мира.");
            var plan = companion.obtainPlan(action.item(), action.count());
            if (plan.refused()) return new CompanionPlanRunner.Expansion(java.util.List.of(), plan.refusal());
            java.util.List<PlannedAction> steps = new java.util.ArrayList<>();
            for (var step : plan.steps()) steps.add(new PlannedAction(step.type(), 0, step.count(), step.item()));
            steps.add(new PlannedAction("verify_item", 0, action.count(), action.item()));
            return new CompanionPlanRunner.Expansion(java.util.List.copyOf(steps), null);
        });
    }

    /** Pauses count server ticks: they stop while singleplayer is paused and follow the GameTest tick rate. */
    @Override public CompletableFuture<Void> pause(int seconds) {
        CompletableFuture<Void> wake = new CompletableFuture<>();
        if (!server.isRunning()) wake.completeExceptionally(new IllegalStateException("Мир закрыт, поручение остановлено."));
        else SLEEPERS.add(new Sleeper(server, server.getTickCount() + seconds * 20, wake));
        return wake;
    }

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer current = event.getServer();
        for (var iterator = SLEEPERS.iterator(); iterator.hasNext(); ) {
            Sleeper sleeper = iterator.next();
            if (sleeper.server() == current ? current.getTickCount() < sleeper.wakeTick() : sleeper.server().isRunning()) continue;
            iterator.remove();
            if (sleeper.server() == current) sleeper.wake().complete(null);
            else sleeper.wake().completeExceptionally(new IllegalStateException("Мир закрыт, поручение остановлено."));
        }
    }

    /** A plan waiting on a closed world must fail, or it would block the plan queue of the next world. */
    public static void stop(MinecraftServer stopping) {
        for (var iterator = SLEEPERS.iterator(); iterator.hasNext(); ) {
            Sleeper sleeper = iterator.next();
            if (sleeper.server() != stopping) continue;
            iterator.remove();
            sleeper.wake().completeExceptionally(new IllegalStateException("Мир закрыт, поручение остановлено."));
        }
    }

    /** Smelting waits for the recipe time on top of the preparation: ten seconds an item, so the server ends it first. */
    public static int limitSeconds(PlannedAction action) {
        int items = action.count() == PlannedAction.ALL_CARRIED ? 64 : action.count();
        if ("smelt_items".equals(action.type())) return WORK_LIMIT_SECONDS + 10 * items + 120;
        // Iron tools and gold or diamonds without an iron pickaxe: ore, furnace and bench in one order, each stage with its
        // own time on the server (review 22.10, М5); the client waits for all of them.
        var kind = "mine_ore".equals(action.type()) ? dev.aicompanion.entity.CompanionOre.Kind.of(action.item()) : null;
        boolean ironChain = "craft_item".equals(action.type()) && dev.aicompanion.entity.CompanionWork.ingotCost(action.item()) > 0
                || kind != null && kind.pickaxe == net.minecraft.world.item.Items.IRON_PICKAXE;
        return ironChain ? WORK_LIMIT_SECONDS + 900 : WORK_LIMIT_SECONDS;
    }

    // Limits count game ticks, not wall seconds: a paused singleplayer game must not time out real work.
    private CompletableFuture<CompanionActionResult> waitForWork(long workId, long startTick, int limitSeconds) {
        if (cancelled()) return CompletableFuture.completedFuture(CompanionActionResult.cancelled("План отменён."));
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null) return CompanionActionResult.failed("Компаньон выгружен из мира.");
            CompanionActionResult result = companion.workResult(workId);
            if (result != null) return result;
            if (player.level().getGameTime() - startTick >= limitSeconds * 20L) {
                companion.diagnosticSnapshot("step_timeout"); companion.stopGathering();
                return CompanionActionResult.timeout("Остановил работу по тайм-ауту.");
            }
            return (CompanionActionResult) null;
        }).thenCompose(result -> result == null ? pause(1).thenCompose(unused -> waitForWork(workId, startTick, limitSeconds))
                : CompletableFuture.completedFuture(result));
    }

    private CompletableFuture<CompanionActionResult> waitForGathering(long startTick) {
        if (cancelled()) return CompletableFuture.completedFuture(CompanionActionResult.cancelled("План отменён."));
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null) return new GatherState(false, CompanionActionResult.failed("Компаньон выгружен из мира."));
            if (player.level().getGameTime() - startTick >= GATHER_LIMIT_SECONDS * 20L && companion.isGatheringTask()) {
                companion.diagnosticSnapshot("step_timeout");
                companion.stopGathering();
                return new GatherState(false, CompanionActionResult.timeout("Остановил работу по тайм-ауту."));
            }
            return new GatherState(companion.isGatheringTask(),
                    new CompanionActionResult(companion.didLastTaskSucceed(), companion.getLastTaskResult()));
        }).thenCompose(state -> state.active() ? pause(1).thenCompose(unused -> waitForGathering(startTick))
                : CompletableFuture.completedFuture(state.result()));
    }

    private CompletableFuture<CompanionActionResult> waitSeconds(int remaining, CompanionActionResult result) {
        if (cancelled()) return CompletableFuture.completedFuture(CompanionActionResult.cancelled("План отменён."));
        if (remaining <= 0) return CompletableFuture.completedFuture(result);
        return pause(1).thenCompose(unused -> waitSeconds(remaining - 1, result));
    }

    private <T> CompletableFuture<T> onServer(Function<ServerPlayer, T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!server.isRunning()) {
            result.completeExceptionally(new IllegalStateException("Для действий компаньона нужен открытый одиночный мир."));
            return result;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Игрок вышел из мира.");
                result.complete(action.apply(player));
            } catch (Exception exception) { result.completeExceptionally(exception); }
        });
        return result.orTimeout(15, TimeUnit.SECONDS);
    }

    private record Sleeper(MinecraftServer server, int wakeTick, CompletableFuture<Void> wake) { }
    private record Dispatch(CompanionActionResult result, long workId, long tick) { }
    private record GatherState(boolean active, CompanionActionResult result) { }
}
