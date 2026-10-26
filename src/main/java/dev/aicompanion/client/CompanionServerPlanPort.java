package dev.aicompanion.client;

import dev.aicompanion.client.CompanionPlanRunner.Attempt;
import dev.aicompanion.client.CompanionPlanRunner.State;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.schematic.CompanionSchematic;
import dev.aicompanion.CompanionDiagnostics;
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
            "build_house", "build_schematic", "craft_item", "place_block", "come", "hunt_mobs", "give_items", "give_wood", "smelt_items", "mine_ore", "hunt_animals");
    /** build_house items: the next round of an order already running, and «дострой» of the saved house without placing it again. */
    public static final String HOUSE_ROUND = "continue", HOUSE_RESUME = "resume";

    /** Where the schematic goes: chosen by the player in the game (CompanionSchematicPlacement); GameTests pass their own. */
    public interface Placer {
        CompletableFuture<Choice> choose(String hint, CompanionSchematic.Placement current, BooleanSupplier cancelled);
        Placer NONE = (hint, current, cancelled) -> CompletableFuture.completedFuture(new Choice(null, "Место для схемы выбирается в игре."));
    }
    public record Choice(CompanionSchematic.Placement placement, String refusal) { }
    private static final Queue<Sleeper> SLEEPERS = new ConcurrentLinkedQueue<>();
    private final MinecraftServer server;
    private final UUID playerId;
    private final String trace;
    private final BooleanSupplier cancelled;
    private final java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress;
    private final Placer placer;
    private long ownedWorkId;

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled) {
        this(server, playerId, trace, cancelled, (plan, index) -> { });
    }

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled,
            java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress) {
        this(server, playerId, trace, cancelled, progress, Placer.NONE);
    }

    public CompanionServerPlanPort(MinecraftServer server, UUID playerId, String trace, BooleanSupplier cancelled,
            java.util.function.BiConsumer<java.util.List<PlannedAction>, Integer> progress, Placer placer) {
        this.server = server; this.playerId = playerId; this.trace = trace; this.cancelled = cancelled;
        this.progress = progress; this.placer = placer;
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

    @Override public CompletableFuture<CompanionPlanRunner.Expansion> house(PlannedAction action) {
        if (HOUSE_ROUND.equals(action.item())) return onServer(this::round);
        boolean resume = HOUSE_RESUME.equals(action.item());
        return onServer(player -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            if (companion == null) return new HouseStart(null, false, "Компаньон выгружен из мира.");
            var unfinished = companion.unfinishedHouse();
            // «Дострой дом»: the saved house as it stands; a new «построй» is placed by the player, an unfinished one offered first.
            return new HouseStart(unfinished, resume && unfinished != null, null);
        }).thenCompose(start -> {
            if (start.refusal() != null) return CompletableFuture.completedFuture(new CompanionPlanRunner.Expansion(java.util.List.of(), start.refusal()));
            CompletableFuture<Choice> choice = start.resume() ? CompletableFuture.completedFuture(new Choice(start.current(), null))
                    : placer.choose(resume ? "" : action.item(), start.current(), this::cancelled);
            return choice.thenCompose(chosen -> {
                if (chosen.refusal() != null) return CompletableFuture.completedFuture(new CompanionPlanRunner.Expansion(java.util.List.of(), chosen.refusal()));
                if (cancelled()) return CompletableFuture.completedFuture(new CompanionPlanRunner.Expansion(java.util.List.of(), "План отменён."));
                return onServer(player -> {
                    CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
                    if (companion == null) return new CompanionPlanRunner.Expansion(java.util.List.of(), "Компаньон выгружен из мира.");
                    CompanionActionResult placed = companion.chooseHouse(chosen.placement());
                    CompanionDiagnostics.event(trace, "house_placed", "placement", chosen.placement(), "success", placed.success(), "message", placed.message());
                    if (!placed.success()) return new CompanionPlanRunner.Expansion(java.util.List.of(), placed.message());
                    say(player, companion, placed.message());
                    return round(player);
                });
            });
        });
    }

    /** The next round of the house order, from the world and the bag as they are now. */
    private CompanionPlanRunner.Expansion round(ServerPlayer player) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        if (companion == null) return new CompanionPlanRunner.Expansion(java.util.List.of(), "Компаньон выгружен из мира.");
        var round = companion.houseRound();
        CompanionDiagnostics.event(trace, "house_round", "needs", round.needs(), "batch", round.batch(), "done", round.done(), "refusal", round.refusal(), "note", round.note());
        if (round.note() != null && !round.note().isBlank()) say(player, companion, round.note());
        if (round.refusal() != null) return new CompanionPlanRunner.Expansion(java.util.List.of(), round.refusal());
        if (round.done()) return new CompanionPlanRunner.Expansion(java.util.List.of(new PlannedAction("verify_house", 0, 1)), null);
        java.util.List<PlannedAction> steps = new java.util.ArrayList<>();
        for (var need : round.needs()) steps.add(new PlannedAction("obtain_item", 0, need.count(), need.item()));
        steps.add(new PlannedAction("build_schematic", 0, round.batch()));
        steps.add(new PlannedAction("build_house", 0, 1, HOUSE_ROUND));
        return new CompanionPlanRunner.Expansion(java.util.List.copyOf(steps), null);
    }

    /** Said in chat at once, not only in the final report: where the house goes, which wood replaces which. */
    private static void say(ServerPlayer player, CompanionEntity companion, String text) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(companion.getName().getString() + ": " + text)
                .withStyle(net.minecraft.ChatFormatting.AQUA));
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
        // A batch of a house: the server gives it four seconds a block on top of the usual time, the client waits a little longer.
        if ("build_schematic".equals(action.type())) return WORK_LIMIT_SECONDS + 4 * Math.max(1, action.count()) + 60;
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
    private record HouseStart(CompanionSchematic.Placement current, boolean resume, String refusal) { }
    private record Dispatch(CompanionActionResult result, long workId, long tick) { }
    private record GatherState(boolean active, CompanionActionResult result) { }
}
