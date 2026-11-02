package dev.aicompanion.client;

import dev.aicompanion.client.CompanionPlanRunner.Attempt;
import dev.aicompanion.client.CompanionPlanRunner.State;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAutonomy;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Executes plan steps on the server thread and waits for the result of their own workId. Shared by the client and GameTests. */
public final class CompanionServerPlanPort implements CompanionPlanRunner.Port {
    public static final int WORK_LIMIT_SECONDS = 660;
    public static final int GATHER_LIMIT_SECONDS = 185;
    private static final Set<String> TRACKED_WORK = Set.of("gather_wood", "gather_dirt", "gather_loose", "gather_stone", "store_items", "take_items", "sort_chests",
            "build_house", "craft_item", "place_block", "come", "hunt_mobs", "give_items", "give_wood", "smelt_items", "mine_ore", "hunt_animals", "collect_smelt", "build_pen", "breed_animals", "farm_crops", "harvest_crops", "walk_heading");
    private static final Set<String> PARKABLE_WORK = Set.of("smelt_items", "collect_smelt");
    private static final Queue<Sleeper> SLEEPERS = new ConcurrentLinkedQueue<>();
    private final MinecraftServer server;
    private final UUID playerId;
    private final String trace;
    private final BooleanSupplier cancelled;
    private final java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress;
    private final CompanionTaskQueue.Task task;
    private final CompanionParkLink link;
    private long ownedWorkId;
    /** Server tick at which the companion was first missing in a row; -1 while he is there. Written by the queue's tick only. */
    private long missingSince = -1;
    /** Server tick at which the companion was first seen halted in a row; -1 while he is not. Written by the queue's tick only. */
    private long haltedSince = -1;
    /** Whether a step was already dispatched: a «wait» after it is a pause inside the plan. Server thread only. */
    private boolean dispatched;
    /** Whether the request's first move already told the agenda (CompanionAgenda.requestStarted). Server thread only. */
    private boolean began;
    /** A long goal's own steps (CompanionAgenda): his own work, not orders of the player, and its smelts park as his own batches. */
    private final boolean goal;
    /** The plan as the runner has it now: the class of the whole request decides how it interrupts a long goal. */
    private volatile List<PlannedAction> plan = List.of();

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled) {
        this(server, playerId, trace, cancelled, (plan, index) -> { });
    }

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled,
            java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress) {
        this(server, playerId, trace, cancelled, progress, null, null);
    }

    /** With a queue task the plan's long smelt parks: it gives the slot to other requests and takes it back when the batch is ready. */
    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled,
            java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress, CompanionTaskQueue.Task task, CompanionParkLink link) {
        this(server, playerId, trace, cancelled, progress, task, link, false);
    }

    private CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled,
            java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress, CompanionTaskQueue.Task task, CompanionParkLink link, boolean goal) {
        this.server = server; this.playerId = playerId; this.trace = trace; this.cancelled = cancelled;
        this.progress = progress; this.task = task; this.link = link; this.goal = goal;
    }

    /** The port of a long goal's run: called on the server thread by CompanionAgenda, which owns the cancel. */
    public static CompanionServerPlanPort forGoal(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled) {
        return new CompanionServerPlanPort(server, playerId, trace, cancelled, (plan, index) -> { }, null, null, true);
    }

    /** The work this port started last; the goal stops it on an interrupt only while it is still the active one. */
    public long ownedWorkId() { return ownedWorkId; }

    @Override public void progress(java.util.List<PlannedAction> plan, int index) { this.plan = List.copyOf(plan); progress.accept(plan, index); }

    /** Ordered on the same server executor as dispatch, restricted to this request's own work ID. */
    public void cancelWork() {
        if (!server.isRunning()) return;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var companion = player == null ? null : CompanionEvents.getOwnedCompanion(player);
            if (companion != null && ownedWorkId > 0 && companion.activeWorkId() == ownedWorkId) companion.stopGathering();
        });
    }

    @Override public boolean parkable() { return task != null || goal; }
    @Override public void own(long batchId) { if (task != null) task.own(batchId); }
    @Override public CompletableFuture<Void> park(long batchId) {
        if (goal) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Цель прервана."));
        return task == null ? CompletableFuture.completedFuture(null) : task.await(batchId, () -> collectable(batchId));
    }

    private void markParkable(CompanionEntity companion, long workId) {
        if (goal) companion.markDeedParkable(workId); else companion.markParkable(workId);
    }
    @Override public void collected(long batchId) { if (task != null) task.collected(batchId); }

    /**
     * Server thread (the queue's tick): the batch is cooked and the companion can walk to its furnaces. A vanished batch is ready too:
     * the collection reports it. A companion who is held or stopped is not sent anywhere; one who stays missing for long is, so the
     * collection says why it cannot happen instead of the task waiting for ever.
     */
    private boolean collectable(long batchId) {
        var player = server.getPlayerList().getPlayer(playerId);
        CompanionEntity companion = player == null ? null : CompanionEvents.getOwnedCompanion(player);
        if (companion == null) {
            if (missingSince < 0) missingSince = server.getTickCount();
            return server.getTickCount() - missingSince >= CompanionParkLink.MISSING_COMPANION_TICKS;
        }
        missingSince = -1;
        if (companion.autonomy().halted()) {
            // A «стой» that did not cancel this request (a server command, a later plan) holds the collection back, but not for ever.
            if (haltedSince < 0) haltedSince = server.getTickCount();
            if (server.getTickCount() - haltedSince < (link == null ? CompanionParkLink.MISSING_COMPANION_TICKS : link.haltedCapTicks())) return false;
        } else haltedSince = -1;
        var state = companion.batchState(batchId);
        return state == null || state.status() != CompanionFurnaceBatches.Status.WAITING && state.here();
    }

    /** The vertical reach of a visit that is worth a detour between two steps. */
    private static final int NEAR_VERTICAL = 8;

    /**
     * A cooked batch is worth fetching between two steps of another request: it is in the companion's dimension and its nearest open
     * furnace is within the leash horizontally and {@value #NEAR_VERTICAL} blocks vertically. Anything else waits for the request to end.
     */
    public static boolean worthAVisit(CompanionFurnaceBatches.State state, BlockPos from) {
        if (state == null || state.status() == CompanionFurnaceBatches.Status.WAITING || !state.here() || state.nearest() == null) return false;
        BlockPos furnace = state.nearest();
        return Math.abs(furnace.getX() - from.getX()) <= CompanionAutonomy.LEASH && Math.abs(furnace.getZ() - from.getZ()) <= CompanionAutonomy.LEASH
                && Math.abs(furnace.getY() - from.getY()) <= NEAR_VERTICAL;
    }

    /**
     * Between two steps: gives the slot to a cooked batch of another request when its furnace is near, or back to the request that
     * lent it. Not while the companion is still finishing the last step (a pillar descent, a loot pickup).
     */
    @Override public CompletableFuture<Boolean> checkpoint() {
        if (task == null) return CompletableFuture.completedFuture(false);
        List<Long> ready = task.readyBatches();
        boolean borrowed = task.borrowed();
        if (ready.isEmpty() && !borrowed) return CompletableFuture.completedFuture(false);
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null || companion.busyForOrders()) return Optional.<Set<Long>>empty();
            Set<Long> near = ready.stream().filter(batch -> worthAVisit(companion.batchState(batch), companion.blockPosition())).collect(Collectors.toSet());
            return borrowed || !near.isEmpty() ? Optional.of(near) : Optional.<Set<Long>>empty();
        }).thenCompose(near -> {
            if (near.isEmpty()) return CompletableFuture.completedFuture(false);
            CompanionDiagnostics.event(trace, "slot_yielded", "borrowed", borrowed, "ready", ready, "near", near.get());
            return task.yieldSlot(near.get()).thenApply(unused -> true);
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
            boolean stoppedBefore = before != null && before.autonomy().playerStopped();
            if (!goal) dev.aicompanion.entity.CompanionAgenda.request(playerId, plan.stream().map(PlannedAction::type).toList());
            CompanionActionResult result;
            try {
                result = goal
                        ? CompanionEvents.executeGoal(player, action.type(), action.item(), action.count(), action.slot(), trace, action.location())
                        : CompanionEvents.executeAction(player, action.type(), action.item(), action.count(), action.slot(), trace, action.location());
            } finally {
                if (!goal) dev.aicompanion.entity.CompanionAgenda.request(playerId, List.of());
            }
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (dispatched && "wait".equals(action.type()) && companion != null) companion.autonomy().clearHalt(before != null && stoppedBefore);
            dispatched = true;
            long work = companion == null ? 0 : companion.activeWorkId();
            if (result.success() && work != previousWork) ownedWorkId = work;
            if (parkable() && work > 0 && result.success() && work != previousWork && PARKABLE_WORK.contains(action.type())) markParkable(companion, work);
            return new Dispatch(result, result.success() && work != previousWork ? work : 0, player.level().getGameTime());
        }).thenCompose(dispatch -> {
            CompanionActionResult result = dispatch.result();
            if (!result.success()) return CompletableFuture.completedFuture(new Attempt(result, false));
            CompletableFuture<CompanionActionResult> verified;
            if (dispatch.workId() > 0 && TRACKED_WORK.contains(action.type())) verified = waitForWork(dispatch.workId(), dispatch.tick(), limitSeconds(action));
            else if ("gather_wood".equals(action.type())) verified = waitForGathering(dispatch.workId(), dispatch.tick());
            else if ("wait".equals(action.type())) verified = waitSeconds(action.seconds(), result);
            else verified = CompletableFuture.completedFuture(result);
            // The bag note of a sorting this order ended came with the acceptance; the player hears the outcome (review round 3, finding 1).
            boolean note = result.message().contains(CompanionDialogue.BAG_NOTE);
            return verified.thenApply(done -> new Attempt(!note || done.message().contains(CompanionDialogue.BAG_NOTE) ? done
                    : new CompanionActionResult(done.success(), done.message() + " " + CompanionDialogue.BAG_NOTE, done.status(), done.evidence()), true));
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

    @Override public CompletableFuture<Integer> carried(String item) {
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            return companion == null ? 0 : companion.carriedCount(item);
        });
    }

    /** Pauses count server ticks: they stop while singleplayer is paused and follow the GameTest tick rate. */
    @Override public CompletableFuture<Void> pause(int seconds) {
        lease();
        CompletableFuture<Void> wake = new CompletableFuture<>();
        if (!server.isRunning()) wake.completeExceptionally(new IllegalStateException("Мир закрыт, поручение остановлено."));
        else SLEEPERS.add(new Sleeper(server, server.getTickCount() + seconds * 20, wake));
        return wake;
    }

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer current = event.getServer();
        if (current.getTickCount() % 20 == 0) CompanionParkLink.tickAll(current);
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
        CompanionParkLink.stop(stopping);
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
        // A safety net over the server's own deadline of a collection (CompanionFurnaceBatches.collectDeadline): the cooking left, a refuel, the walk.
        if ("collect_smelt".equals(action.type())) return WORK_LIMIT_SECONDS + 10 * 64 + 120 + 180;
        // A walk until «стоп» ends on the server by its own bound (CompanionCourse.LIMIT_SECONDS).
        if ("walk_heading".equals(action.type())) return dev.aicompanion.entity.CompanionCourse.LIMIT_SECONDS + 120;
        // Iron tools and gold or diamonds without an iron pickaxe: ore, furnace and bench in one order, each stage with its
        // own time on the server (review 22.10, М5); the client waits for all of them.
        var kind = "mine_ore".equals(action.type()) ? dev.aicompanion.entity.CompanionOre.Kind.of(action.item()) : null;
        boolean ironChain = "craft_item".equals(action.type()) && dev.aicompanion.entity.CompanionCraftCosts.ingotCost(action.item()) > 0
                || kind != null && kind.pickaxe == net.minecraft.world.item.Items.IRON_PICKAXE;
        return ironChain ? WORK_LIMIT_SECONDS + 900 : WORK_LIMIT_SECONDS;
    }

    /** Stops the companion only while the active work is still {@code workId} (0: the legacy gathering): a late timeout never cuts a newer order of the player or a goal's step. */
    public static boolean stopOwnWork(CompanionEntity companion, long workId) {
        if (companion.activeWorkId() != workId) return false;
        companion.stopGathering();
        return true;
    }

    // Limits count game ticks, not wall seconds: a paused singleplayer game must not time out real work.
    private CompletableFuture<CompanionActionResult> waitForWork(long workId, long startTick, int limitSeconds) {
        if (cancelled()) return parkable() ? parkedBeforeCancel(workId) : CompletableFuture.completedFuture(CompanionActionResult.cancelled("План отменён."));
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null) return CompanionActionResult.failed("Компаньон выгружен из мира.");
            if (parkable() && companion.activeWorkId() == workId) markParkable(companion, workId);
            CompanionActionResult result = companion.workResult(workId);
            if (result != null) {
                if (result.status() == CompanionActionResult.Status.PARKED) parkedSeen(result);
                return result;
            }
            if (player.level().getGameTime() - startTick >= limitSeconds * 20L) {
                companion.diagnosticSnapshot("step_timeout");
                stopOwnWork(companion, workId);
                return CompanionActionResult.timeout("Остановил работу по тайм-ауту." + companion.takeSortNote());
            }
            return (CompanionActionResult) null;
        }).thenCompose(result -> result == null ? pause(1).thenCompose(unused -> waitForWork(workId, startTick, limitSeconds))
                : CompletableFuture.completedFuture(result));
    }

    /** Server thread: the queue owns the parked batch at once, even if this poll's answer never arrives, and its note is in the snapshot before any cancel releases it. */
    private void parkedSeen(CompanionActionResult parked) {
        if (parked.evidence() != null) own(parked.evidence().workId());
        if (link != null) link.publish(server);
    }

    /**
     * A cancel can land right after the work parked its batch, or while it collected one: the result is still read once. A parked one
     * must not become nobody's; a collection cancelled on its way tells where the product stayed.
     */
    private CompletableFuture<CompanionActionResult> parkedBeforeCancel(long workId) {
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            CompanionActionResult result = companion == null ? null : companion.workResult(workId);
            if (result != null && result.status() == CompanionActionResult.Status.PARKED) parkedSeen(result);
            return result != null && (result.status() == CompanionActionResult.Status.PARKED || result.status() == CompanionActionResult.Status.CANCELLED)
                    ? result : CompanionActionResult.cancelled("План отменён.");
        }).exceptionally(error -> CompanionActionResult.cancelled("План отменён."));
    }

    private CompletableFuture<CompanionActionResult> waitForGathering(long workId, long startTick) {
        if (cancelled()) return CompletableFuture.completedFuture(CompanionActionResult.cancelled("План отменён."));
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null) return new GatherState(false, CompanionActionResult.failed("Компаньон выгружен из мира."));
            if (player.level().getGameTime() - startTick >= GATHER_LIMIT_SECONDS * 20L && companion.isGatheringTask()) {
                companion.diagnosticSnapshot("step_timeout");
                stopOwnWork(companion, workId);
                return new GatherState(false, CompanionActionResult.timeout("Остановил работу по тайм-ауту."));
            }
            return new GatherState(companion.isGatheringTask(),
                    new CompanionActionResult(companion.didLastTaskSucceed(), companion.getLastTaskResult()));
        }).thenCompose(state -> state.active() ? pause(1).thenCompose(unused -> waitForGathering(workId, startTick))
                : CompletableFuture.completedFuture(state.result()));
    }

    private CompletableFuture<CompanionActionResult> waitSeconds(int remaining, CompanionActionResult result) {
        if (cancelled()) return CompletableFuture.completedFuture(CompanionActionResult.cancelled("План отменён."));
        if (remaining <= 0) return CompletableFuture.completedFuture(result);
        return pause(1).thenCompose(unused -> waitSeconds(remaining - 1, result));
    }

    /**
     * Every move of the player's request (a step, a poll, a pause between steps, a «wait N») renews the lease that keeps a long
     * goal from stepping in between; the lease outlives the request by a few seconds, which also spans the gap to the next request.
     */
    private void lease() { if (!goal) dev.aicompanion.entity.CompanionAgenda.lease(server, playerId); }

    /** The first move of a player's request: a long goal's step stops now, not only at the first order, which can come after a busy poll or a pause. */
    private void begin(ServerPlayer player) {
        if (began || goal || plan.isEmpty()) return;
        began = true;
        if (cancelled()) return;
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        if (companion == null) return;
        try {
            dev.aicompanion.entity.CompanionAgenda.requestStarted(player, companion, plan.stream().map(PlannedAction::type).toList());
        } catch (RuntimeException exception) {
            // The goal's bookkeeping must never fail the first step of the player's own request.
            dev.aicompanion.AICompanionMod.LOGGER.warn("The long goal could not be paused for the player's request", exception);
        }
    }

    private <T> CompletableFuture<T> onServer(Function<ServerPlayer, T> action) {
        lease();
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!server.isRunning()) {
            result.completeExceptionally(new IllegalStateException("Для действий компаньона нужен открытый одиночный мир."));
            return result;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Игрок вышел из мира.");
                begin(player);
                result.complete(action.apply(player));
            } catch (Exception exception) { result.completeExceptionally(exception); }
        });
        return result.orTimeout(15, TimeUnit.SECONDS);
    }

    private record Sleeper(MinecraftServer server, int wakeTick, CompletableFuture<Void> wake) { }
    private record Dispatch(CompanionActionResult result, long workId, long tick) { }
    private record GatherState(boolean active, CompanionActionResult result) { }
}
