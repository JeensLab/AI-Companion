package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.client.CompanionParkHold.Parked;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches.Summary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Joins the request queue to the companion's furnace batches on the server. The queue lives on whichever thread asks, so
 * everything it needs from the world (the hold rule, the note of a released batch) comes from a snapshot that only the server
 * thread writes; what it does to the world (release, orphan) is handed to the server thread. No client classes: GameTests use
 * this link exactly as the game does.
 */
public final class CompanionParkLink implements CompanionTaskQueue.BatchOwner {
    private record Binding(MinecraftServer server, UUID playerId) { }
    /** How long a companion who is not in the world keeps his batches held and his collection waiting: five minutes of server ticks. */
    public static final int MISSING_COMPANION_TICKS = 20 * 60 * 5;
    private static final Set<String> RECIPE_BOUND = Set.of("craft_item", "obtain_item");
    private static final Set<CompanionParkLink> LINKS = ConcurrentHashMap.newKeySet();

    private final CompanionTaskQueue queue = new CompanionTaskQueue(this::held, this);
    private volatile Binding binding = new Binding(null, null);
    private volatile List<Parked> parked = List.of();
    /** Where the leftovers of each batch are, as of the last publish; kept past the batch itself, for the release of a collection that was cancelled. */
    private final Map<Long, String> notes = new ConcurrentHashMap<>();
    /** Server tick at which the companion was first missing in a row; -1 while he is there. Server thread only. */
    private long missingSince = -1;
    /** Crafted item to the items its recipes consume, from the recipe data; null until the first parked batch needs it. */
    private volatile Map<String, Set<String>> recipes;
    /** How long a «стой» that did not cancel the request keeps its collection waiting. */
    private volatile int haltedCapTicks = MISSING_COMPANION_TICKS;

    public CompanionParkLink() { LINKS.add(this); }

    int haltedCapTicks() { return haltedCapTicks; }
    /** For GameTests: the cap of a halt on a collection, much shorter than the game's. */
    public void setHaltedCapTicks(int ticks) { haltedCapTicks = ticks; }

    private record Late(int dueTick, Consumer<CompanionEntity> action) { }
    private final java.util.Queue<Late> late = new ConcurrentLinkedQueue<>();
    private volatile int settleDelayTicks;
    /** For GameTests: the queue's release, orphan and collected calls reach the companion this many server ticks late, as when a cancel and the end of its task are far apart. */
    public void setSettleDelayTicks(int ticks) { settleDelayTicks = ticks; }

    /** Where the requests run; called with every new request. Either part may be null when no world is open. */
    public void bind(MinecraftServer server, UUID playerId) { binding = new Binding(server, playerId); }

    public boolean contains(List<PlannedAction> actions) { return queue.contains(actions); }
    public List<CompanionTaskQueue.View> tasks() { return queue.snapshot(); }
    public boolean cancel(long id) { return queue.cancel(id); }
    public boolean priority(long id, int priority) { return queue.priority(id, priority); }
    public void cancelAll(boolean clearHistory) { queue.cancelAll(clearHistory, false); }
    /** {@code byPlayer}: the player's cancel, which releases the smelts the tasks held; otherwise they are orphaned. */
    public void cancelAll(boolean clearHistory, boolean byPlayer) { queue.cancelAll(clearHistory, byPlayer); }

    /** Takes the plan into the queue; the future completes with the report. {@code stale} says the plan was invalidated from outside. */
    public CompletableFuture<String> submit(List<PlannedAction> actions, String trace, BooleanSupplier stale) {
        return queue.submit(actions, task -> {
            Binding bound = binding;
            if (bound.server() == null || bound.playerId() == null)
                return CompletableFuture.failedFuture(new IllegalStateException("Для действий компаньона нужен открытый одиночный мир."));
            long started = System.nanoTime();
            CompanionServerPlanPort port = new CompanionServerPlanPort(bound.server(), bound.playerId(), trace,
                    () -> task.cancelled() || stale.getAsBoolean(), task::progress, task, this);
            task.onCancel(port::cancelWork);
            return CompanionPlanRunner.run(actions, port).thenApply(outcome -> {
                CompanionDiagnostics.event(trace, "plan_outcome", "recoveries", outcome.recoveries(), "skipped", outcome.skipped());
                return new CompanionTaskQueue.Result(outcome.report(), !outcome.report().contains("План остановлен:") && !outcome.report().contains(CompanionDialogue.PARTIAL_PREFIX));
            }).whenComplete((report, error) -> {
                if (error != null) CompanionDiagnostics.failure(trace, "plan_execution", error);
                CompanionDiagnostics.event(trace, "plan_finished", "elapsedMs", (System.nanoTime() - started) / 1_000_000,
                        "generationCurrent", !stale.getAsBoolean(), "report", report, "exception", error != null);
            });
        }).completion();
    }

    /** A queued request waits while its first step needs a product that is still cooking. Reads the snapshot only: it runs under the queue lock. */
    private boolean held(List<PlannedAction> steps) {
        List<Parked> now = parked;
        Map<String, Set<String>> known = recipes;
        if (steps.isEmpty() || now.isEmpty()) return false;
        // The recipes are read within a second of the first batch; until then a craft may need the product, so it waits.
        if (known == null) return RECIPE_BOUND.contains(steps.getFirst().type()) || CompanionParkHold.held(steps, now);
        return CompanionParkHold.held(steps, now, item -> known.getOrDefault(item, Set.of()));
    }

    /**
     * Server thread: puts the batches of the companion into the snapshot. Orphans are nobody's business here, so they hold nothing.
     * A companion who is not found (unloaded, far, dead) leaves the last snapshot in place for a while: his batches still exist.
     */
    public void publish(MinecraftServer current) {
        Binding bound = binding;
        if (bound.server() != current) return;
        CompanionEntity companion = companion(current, bound.playerId());
        for (Late next = late.peek(); next != null && next.dueTick() <= current.getTickCount(); next = late.peek()) {
            late.poll();
            if (companion != null) next.action().accept(companion);
        }
        if (companion != null) companion.batches().queueBusy(queue::busy);
        if (companion == null) {
            if (missingSince < 0) missingSince = current.getTickCount();
            if (current.getTickCount() - missingSince >= MISSING_COMPANION_TICKS) parked = List.of();
            return;
        }
        missingSince = -1;
        companion.batches().adopt(queue.ownedBatches());
        List<Summary> batches = companion.batches().summaries();
        batches.forEach(batch -> notes.put(batch.id(), batch.note()));
        Set<Long> live = queue.ownedBatches();
        batches.forEach(batch -> live.add(batch.id()));
        notes.keySet().retainAll(live);
        parked = batches.stream().filter(batch -> !batch.orphan()).map(batch -> new Parked(batch.input(), batch.output())).toList();
        if (!parked.isEmpty() && recipes == null) recipes = recipesOf(current);
    }

    /** Once a second on the server thread: refresh every link of this server, then let its queue resume what is ready. */
    public static void tickAll(MinecraftServer current) {
        for (CompanionParkLink link : LINKS) {
            if (link.binding.server() != current) continue;
            try { link.publish(current); link.queue.tick(); }
            catch (RuntimeException error) { AICompanionMod.LOGGER.warn("Plan queue tick failed: {}", error.getMessage()); }
        }
    }

    /** The world is closing: the next one must not hold requests for batches of this one. */
    public static void stop(MinecraftServer stopping) {
        for (CompanionParkLink link : LINKS) {
            if (link.binding.server() != stopping) continue;
            link.parked = List.of();
            link.notes.clear();
            link.recipes = null;
            link.binding = new Binding(null, null);
        }
    }

    /** For GameTests: leaves the tick list and gives up whatever the queue still holds. */
    public void close() {
        LINKS.remove(this);
        queue.cancelAll(false);
    }

    /** The player cancelled the owner: the product stays in the furnace; the note comes from the last publish, so it is there even if the batch is already collected. */
    @Override public String release(long batchId) {
        String note = notes.remove(batchId);
        settle(companion -> companion.batches().release(batchId));
        return note == null ? "" : note;
    }

    @Override public void orphan(long batchId) { settle(companion -> companion.batches().orphan(batchId)); }
    @Override public void collected(long batchId) { settle(companion -> companion.batches().collectEnded(batchId)); }

    private void settle(Consumer<CompanionEntity> action) {
        Binding bound = binding;
        int delay = settleDelayTicks;
        if (delay > 0 && bound.server() != null) late.add(new Late(bound.server().getTickCount() + delay, action));
        else onServer(action);
    }

    private void onServer(Consumer<CompanionEntity> action) {
        Binding bound = binding;
        MinecraftServer server = bound.server();
        if (server == null || !server.isRunning()) return;
        server.execute(() -> {
            CompanionEntity companion = companion(server, bound.playerId());
            if (companion != null) action.accept(companion);
        });
    }

    private static CompanionEntity companion(MinecraftServer server, UUID playerId) {
        var player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        return player == null ? null : CompanionEvents.getOwnedCompanion(player);
    }

    private static Map<String, Set<String>> recipesOf(MinecraftServer current) {
        Map<String, Set<String>> result = new HashMap<>();
        for (var holder : current.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            var recipe = holder.value();
            ItemStack product = recipe.getResultItem(current.registryAccess());
            if (product.isEmpty()) continue;
            Set<String> needed = result.computeIfAbsent(id(product), key -> new HashSet<>());
            for (Ingredient ingredient : recipe.getIngredients()) for (ItemStack option : ingredient.getItems()) needed.add(id(option));
        }
        result.replaceAll((item, needed) -> Set.copyOf(needed));
        return Map.copyOf(result);
    }

    private static String id(ItemStack stack) { return CompanionParkHold.id(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); }
}
