package dev.aicompanion.entity;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Smelts handed over to their furnaces: the companion is free meanwhile and collects the batch when it is done. One per companion, saved with him. */
public final class CompanionFurnaceBatches {
    public enum Status { WAITING, READY, LOST }
    /** {@code nearest}: the open furnace closest to the companion in his dimension, or null; null with {@code here} true means none is left. */
    public record State(Status status, int ready, int total, BlockPos nearest, boolean orphan, boolean here) { }
    /** A batch as the queue sees it: item ids and the note that names where the leftovers are. */
    public record Summary(long id, String input, String output, String note, boolean orphan) { }

    /** The snapshot names this many batches (and furnaces per batch); the rest is only counted. */
    private static final int AI_MAX_LISTED = 3;
    /** Less than this left on the clock and standing by is cheaper than a trip back. */
    static final int PARK_MIN_TICKS = 400;
    /** Walking pace for the deadline of a collection: generous, a trip back is no race. */
    private static final int COLLECT_TICKS_PER_BLOCK = 8;
    private static final int REFUEL_ALLOWANCE_TICKS = 20 * 180;
    /** A collection that ends with product still in the furnaces this many times is given up: the leftovers are named and the furnaces freed. */
    private static final int MAX_COLLECT_FAILURES = 3;

    static final class Batch {
        final long id;
        final ResourceKey<Level> dimension;
        final CompanionSmelting.Recipe recipe;
        final int requestedCount, smeltCount;
        final boolean smeltAll, mixedMeat, furnacesShort;
        final int mixedCooked, mixedKinds;
        final Set<Item> mixedInputs;
        final List<CompanionWork.Lot> lots;
        final Set<CompanionWork.Lot> settled = new HashSet<>();
        Status status = Status.WAITING;
        boolean orphan;
        /** Taken up by the companion himself (it was an orphan when the collection began): the end of that collection is his to judge, not a plan's. */
        boolean ownCollect;
        int failures;
        /** What the runner reads for the parked work, however many orders came after it. */
        CompanionActionResult parked;

        Batch(long id, ResourceKey<Level> dimension, CompanionWork w, List<CompanionWork.Lot> lots) {
            this.id = id; this.dimension = dimension; this.recipe = w.smelt; this.lots = lots;
            requestedCount = w.requestedCount; smeltCount = w.smeltCount; smeltAll = w.smeltAll; mixedMeat = w.mixedMeat;
            furnacesShort = w.furnacesShort; mixedCooked = w.mixedCooked; mixedKinds = w.mixedKinds; mixedInputs = new HashSet<>(w.mixedInputs);
        }
        Batch(long id, ResourceKey<Level> dimension, CompanionSmelting.Recipe recipe, int requestedCount, int smeltCount, boolean smeltAll, boolean mixedMeat,
              boolean furnacesShort, int mixedCooked, int mixedKinds, Set<Item> mixedInputs, List<CompanionWork.Lot> lots) {
            this.id = id; this.dimension = dimension; this.recipe = recipe; this.lots = lots;
            this.requestedCount = requestedCount; this.smeltCount = smeltCount; this.smeltAll = smeltAll; this.mixedMeat = mixedMeat;
            this.furnacesShort = furnacesShort; this.mixedCooked = mixedCooked; this.mixedKinds = mixedKinds; this.mixedInputs = mixedInputs;
        }
        Status evaluate() {
            List<CompanionWork.Lot> live = lots.stream().filter(lot -> lot.loaded && !lot.emptied).toList();
            if (live.stream().allMatch(lot -> lot.gaveUp)) return Status.LOST;
            return live.stream().allMatch(lot -> lot.gaveUp || settled.contains(lot)) ? Status.READY : Status.WAITING;
        }
    }

    private static final String STATUS_PREFIX = "Жарится «";
    /** What a save may bring back: more is a damaged or hand-edited file, and every lot holds a chunk ticket. */
    public static final int MAX_BATCHES = 32;
    public static final int MAX_LOTS = 16;

    private final CompanionEntity owner;
    private final Map<Long, Batch> batches = new LinkedHashMap<>();
    private long parkableId = -1;
    /** The parkable work is a deed of his own: its batch has no queue behind it, so it is his from the moment it parks. */
    private boolean parkableIsDeed;
    /** The batch whose lots the collecting work holds right now; it is not in {@code batches} meanwhile but is still saved and given back. */
    private Batch inFlight;
    /** What a batch lost on reading has to tell the player, said once he is there to hear it. */
    private final List<String> lostNotes = new ArrayList<>();
    /** Whether the request queue holds a request, running or waiting its turn: he does not go for orphans then. Read on the server thread. */
    private BooleanSupplier queueBusy = () -> false;

    CompanionFurnaceBatches(CompanionEntity owner) { this.owner = owner; }

    public void queueBusy(BooleanSupplier busy) { queueBusy = busy; }

    /** Only a work marked by whoever manages the orders may park; a direct order keeps standing at its furnaces. */
    void markParkable(long workId) { parkableId = workId; parkableIsDeed = false; }

    /** A deed of his own may park too; the batch it leaves is an orphan, collected by the same path as any other. */
    void markDeedParkable(long workId) { parkableId = workId; parkableIsDeed = true; }

    /** Hands a long wait to a batch: true when the work was parked and has nothing more to do. */
    boolean parkIfLong(CompanionWork w, ServerLevel level) {
        if (w.ironPrep || parkableId != w.taskId) return false;
        List<CompanionWork.Lot> open = w.lots.stream().filter(CompanionWork.Lot::open).toList();
        long remaining = 0;
        for (CompanionWork.Lot lot : open) {
            AbstractFurnaceBlockEntity oven = CompanionFurnaceReader.oven(level, lot);
            if (oven != null) remaining = Math.max(remaining, (long) CompanionFurnaceReader.left(w.smelt, oven) * w.smelt.cookTicks());
        }
        if (remaining <= PARK_MIN_TICKS) return false;
        Batch batch = new Batch(w.taskId, level.dimension(), w, new ArrayList<>(w.lots.stream().filter(lot -> lot.loaded).toList()));
        batch.orphan = parkableIsDeed;
        batches.put(batch.id, batch);
        inFlight = null;
        refresh(batch, level);
        CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_parked", "entity", owner.getUUID(), "batch", batch.id,
                "furnaces", open.size(), "remainingTicks", remaining);
        String name = CompanionWork.itemName(w.smelt.input());
        int loaded = open.stream().mapToInt(lot -> lot.count).sum();
        String message = "Заложил " + loaded + " «" + name + "» в " + places(open, "печь", "печи") + ". Жарится дольше 20 секунд, вернусь за продуктом, когда всё будет готово.";
        batch.parked = CompanionActionResult.parked(message, new CompanionActionResult.Evidence(w.taskId, "smelt_items",
                BuiltInRegistries.ITEM.getKey(w.smelt.input()).toString(), w.smeltCount, null, null, null));
        w.park(batch.parked);
        return true;
    }

    /**
     * Some batch of his, parked or being collected, holds items the predicate accepts: another smelt of the same kind would only want a
     * second furnace. Only a batch that can still progress here counts: one in another dimension, in a chunk that does not tick or
     * stalled in its furnaces would hold the deed back for good. A cooked batch waiting to be collected counts however long it waited:
     * a stalled reading is no reason to start another smelt beside it.
     */
    boolean smelting(ServerLevel level, Predicate<Item> input) {
        List<Batch> all = new ArrayList<>(batches.values());
        if (inFlight != null) all.add(inFlight);
        return all.stream().filter(batch -> canProgress(batch, level))
                .anyMatch(batch -> input.test(batch.recipe.input()) || batch.mixedInputs.stream().anyMatch(input));
    }

    private static boolean canProgress(Batch batch, ServerLevel level) {
        return batch.dimension == level.dimension() && (batch.status == Status.READY || batch.lots.stream().anyMatch(lot -> lot.open()
                && CompanionFurnaceReader.ticking(level, lot) && !CompanionFurnaceReader.stalled(level, batch.recipe, lot)));
    }

    /**
     * A cooked batch of his own waits here and he is free for it: true while a new deed would only come before it. The furnaces are read
     * afresh, since the once-a-second reading may come after the decision for the same second.
     */
    boolean ownReadyWaiting(ServerLevel level) {
        if (queueBusy.getAsBoolean()) return false;
        for (Batch batch : List.copyOf(batches.values())) {
            if (!batch.orphan || batch.dimension != level.dimension() || batch.failures >= MAX_COLLECT_FAILURES) continue;
            refresh(batch, level);
            // A refused collection (its deed on a break) lets the deeds run: the batch is tried again when the break is over.
            if (batch.status == Status.READY) return owner.autonomy().collectOrphan(batch.id);
        }
        return false;
    }

    private static String places(List<CompanionWork.Lot> lots, String one, String many) {
        List<String> coords = lots.stream().map(lot -> lot.pos.toShortString()).toList();
        return (coords.size() == 1 ? one : many) + " на " + String.join("; ", coords);
    }

    /** Once a second: reads the furnaces of loaded chunks; the ones that are not ticking keep their clock still. */
    void tick(ServerLevel here) {
        for (Batch batch : List.copyOf(batches.values())) {
            ServerLevel level = here.getServer().getLevel(batch.dimension);
            if (level != null) refresh(batch, level);
        }
        announce();
        if (!lostNotes.isEmpty() && owner.getOwnerPlayer() != null) owner.autonomy().report(lostNotes.removeFirst());
        collectOrphans(here);
    }

    /**
     * A batch nobody waits for is his own business: with no order, no work, no deed and no «стоп» he goes for the cooked ones as a deed of his
     * own (never through an order, which would lift the «стоп»). A cooked one in another dimension waits until he is there; one whose
     * dimension is gone, whose furnaces are lost or that he gave up on is released wherever it lies.
     */
    private void collectOrphans(ServerLevel here) {
        if (owner.hasActiveWorkOrder() || owner.goals().outstanding() || queueBusy.getAsBoolean()
                || owner.autonomy().running() || owner.autonomy().playerStopped()) return;
        boolean goalWorks = CompanionAgenda.suppressesDeeds(owner);
        for (Batch batch : List.copyOf(batches.values())) {
            if (!batch.orphan) continue;
            boolean gone = here.getServer().getLevel(batch.dimension) == null;
            if (gone || batch.failures >= MAX_COLLECT_FAILURES || batch.status == Status.LOST) {
                String name = CompanionWork.itemName(batch.recipe.result().getItem());
                String note = release(batch.id);
                String why = gone ? "Печи с «" + name + "» остались в измерении, которого больше нет."
                        : batch.status == Status.LOST ? "Плавка «" + name + "» не удалась: печей с продуктом не осталось."
                        : "Не вышло забрать «" + name + "» из печей.";
                owner.autonomy().report(why + (note.isBlank() ? "" : " " + note.strip()));
                return;
            }
            if (!goalWorks && batch.dimension == here.dimension() && batch.status == Status.READY && owner.autonomy().collectOrphan(batch.id)) return;
        }
    }

    /**
     * With nothing else to do he follows the player; the status line says what is cooking. It replaces only an empty result, its own earlier
     * text or the park message, never the result of other work (a plan may still be reading it), and it goes when no batch is left to report.
     */
    private void announce() {
        if (owner.hasActiveWorkOrder() || owner.autonomy().running()) return;
        String current = owner.getLastTaskResult();
        Batch live = batches.values().stream().filter(batch -> !batch.orphan).findFirst().orElse(null);
        if (live == null) {
            if (current.startsWith(STATUS_PREFIX)) owner.workStatus("");
            return;
        }
        boolean ours = current.isEmpty() || current.startsWith(STATUS_PREFIX)
                || batches.values().stream().anyMatch(batch -> batch.parked != null && current.equals(batch.parked.message()));
        if (!ours) return;
        int ready = live.lots.stream().mapToInt(lot -> lot.ready).sum(), total = live.lots.stream().mapToInt(lot -> lot.count).sum();
        String text = STATUS_PREFIX + CompanionWork.itemName(live.recipe.result().getItem()) + "»: готово " + ready + " из " + total + ".";
        if (!text.equals(current)) owner.workStatus(text);
    }

    private void refresh(Batch batch, ServerLevel level) {
        for (CompanionWork.Lot lot : batch.lots) {
            if (!lot.loaded || lot.emptied) continue;
            if (lot.gaveUp) { batch.settled.add(lot); continue; }
            if (!CompanionFurnaceReader.ticking(level, lot)) { lot.progressTick = level.getGameTime(); continue; }
            if (CompanionFurnaceReader.oven(level, lot) == null) { lot.gaveUp = true; batch.settled.add(lot); continue; }
            CompanionFurnaceReader.watch(level, batch.recipe, lot);
            if (CompanionFurnaceReader.needsHands(level, batch.recipe, lot)) batch.settled.add(lot); else batch.settled.remove(lot);
        }
        Status now = batch.evaluate();
        if (now == batch.status) return;
        batch.status = now;
        if (now == Status.READY) CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_ready", "entity", owner.getUUID(), "batch", batch.id);
        if (now == Status.LOST) CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_lost", "entity", owner.getUUID(), "batch", batch.id);
    }

    /** A cooked orphan here: a long goal collects it at the boundary of its steps, before anything else. -1 when there is none. */
    long readyOrphan(ServerLevel level) {
        for (Batch batch : List.copyOf(batches.values())) {
            if (!batch.orphan || batch.dimension != level.dimension() || batch.failures >= MAX_COLLECT_FAILURES) continue;
            refresh(batch, level);
            if (batch.status == Status.READY) return batch.id;
        }
        return -1;
    }

    /** What his own batches still cooking here will give: a long goal does not make that item itself meanwhile. */
    List<Item> cookingOutputs(ServerLevel level) {
        return batches.values().stream().filter(batch -> batch.orphan && batch.status == Status.WAITING && canProgress(batch, level))
                .map(batch -> batch.recipe.result().getItem()).toList();
    }

    /** A cooked batch of a request in the queue: the request takes him back for it, so a long goal starts no step meanwhile. */
    boolean readyForQueue() {
        return queueBusy.getAsBoolean() && batches.values().stream().anyMatch(batch -> !batch.orphan && batch.status == Status.READY && batch.dimension == owner.level().dimension());
    }

    /** The park report of the work {@code id} while its batch lives; null otherwise. */
    CompanionActionResult parkedResult(long id) {
        Batch batch = batches.get(id);
        return batch == null ? null : batch.parked;
    }

    public State state(long id) {
        Batch batch = batches.get(id);
        if (batch == null) return null;
        int ready = batch.lots.stream().mapToInt(lot -> lot.ready).sum(), total = batch.lots.stream().mapToInt(lot -> lot.count).sum();
        boolean here = owner.level().dimension() == batch.dimension;
        BlockPos nearest = !here ? null
                : batch.lots.stream().filter(CompanionWork.Lot::open).map(lot -> lot.pos)
                        .min(Comparator.comparingDouble(pos -> owner.distanceToSqr(pos.getCenter()))).orElse(null);
        return new State(batch.status, ready, total, nearest, batch.orphan, here);
    }

    public List<Summary> summaries() {
        return batches.values().stream().map(batch -> new Summary(batch.id, BuiltInRegistries.ITEM.getKey(batch.recipe.input()).toString(),
                BuiltInRegistries.ITEM.getKey(batch.recipe.result().getItem()).toString(), CompanionFurnaceReader.note(batch.lots), batch.orphan)).toList();
    }

    /** One short line for the model's snapshot: what each batch holds, where it stands and how much is ready; empty when no batch is left. */
    public String forAi() {
        List<Batch> all = new ArrayList<>(batches.values());
        if (inFlight != null) all.add(inFlight);
        if (all.isEmpty()) return "";
        String more = all.size() > AI_MAX_LISTED ? "; ещё партий: " + (all.size() - AI_MAX_LISTED) : "";
        return "в печах: " + all.stream().limit(AI_MAX_LISTED).map(this::lineForAi).collect(java.util.stream.Collectors.joining("; ")) + more;
    }

    private String lineForAi(Batch batch) {
        int ready = batch.lots.stream().mapToInt(lot -> lot.ready).sum(), total = batch.lots.stream().mapToInt(lot -> lot.count).sum();
        String input = batch.mixedMeat ? "разное сырое мясо" : CompanionWork.itemName(batch.recipe.input());
        List<String> at = batch.lots.stream().filter(CompanionWork.Lot::open).limit(AI_MAX_LISTED)
                .map(lot -> lot.pos.getX() + "," + lot.pos.getY() + "," + lot.pos.getZ()).toList();
        String where = batch.dimension != owner.level().dimension() ? " в другом измерении" : at.isEmpty() ? "" : " у " + String.join(" ", at);
        String state = batch.status == Status.READY ? "ждёт сбора" : batch.status == Status.LOST ? "печей с продуктом не осталось" : "ещё жарится";
        return total + " " + input + where + " — готово " + ready + " из " + total + " (" + state + ")";
    }

    /** A furnace of a batch is spoken for: no other smelt loads into it. */
    boolean reserved(ServerLevel level, BlockPos pos) {
        return batches.values().stream().anyMatch(batch -> batch.dimension == level.dimension() && batch.lots.stream().anyMatch(lot -> lot.open() && lot.pos.equals(pos)));
    }

    /** Gives the batch up; its product stays where it is. Returns where the leftovers are, for the report. */
    public String release(long id) {
        Batch batch = batches.remove(id);
        if (batch == null && inFlight != null && inFlight.id == id) { batch = inFlight; inFlight = null; }
        if (batch == null) return "";
        CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_released", "entity", owner.getUUID(), "batch", id);
        return CompanionFurnaceReader.note(batch.lots);
    }

    /** A reload makes every batch an orphan; the ones a queue task still awaits are that task's again, so he does not collect them from under it. */
    public void adopt(Set<Long> owned) {
        for (Batch batch : batches.values()) if (batch.orphan && owned.contains(batch.id)) batch.orphan = false;
    }

    public void orphan(long id) {
        Batch batch = batches.get(id);
        if (batch == null && inFlight != null && inFlight.id == id) batch = inFlight;
        if (batch == null) return;
        batch.orphan = true;
        CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_orphaned", "entity", owner.getUUID(), "batch", id);
    }

    /**
     * The queue's task is done with a batch it collected and judged: a plan's own collection that ended without the product (it timed out, it
     * failed) already told the player and named the furnaces, so the batch is not his to retry. An orphan, or one he is collecting, stays.
     */
    public void collectEnded(long id) {
        Batch batch = batches.get(id);
        if (batch == null || batch.orphan) return;
        release(id);
    }

    /**
     * The collecting work ends with product still in the furnaces. His own collection goes back as an orphan with a failure counted, finished
     * or interrupted. A plan's collection that finished has told its result: the batch is released. One that was interrupted stays the queue's
     * until the queue settles it: the player's cancel releases it, any other end orphans it (a reload or his death orphans it anyway).
     */
    void reclaim(boolean finished) {
        Batch batch = inFlight;
        inFlight = null;
        if (batch == null || batch.lots.stream().noneMatch(CompanionWork.Lot::open)) return;
        if (batch.ownCollect) {
            batch.orphan = true;
            batch.failures++;
        } else if (finished) {
            CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_released", "entity", owner.getUUID(), "batch", batch.id, "reason", "collection_finished");
            return;
        }
        batches.put(batch.id, batch);
        CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_returned", "entity", owner.getUUID(), "batch", batch.id, "orphan", batch.orphan, "finished", finished);
    }

    /** The collection was refused at the start (it said why): a batch that keeps being refused is given up, not retried every second. */
    void collectRefused(long id) {
        Batch batch = batches.get(id);
        if (batch != null) batch.failures++;
    }

    /** The batches, those in the hands of the collecting work too, for the save of the companion and for what he remembers after his death. */
    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        batches.values().forEach(batch -> list.add(CompanionFurnaceBatchNbt.save(batch)));
        if (inFlight != null && inFlight.lots.stream().anyMatch(CompanionWork.Lot::open)) list.add(CompanionFurnaceBatchNbt.save(inFlight));
        if (!list.isEmpty()) tag.put(CompanionFurnaceBatchNbt.KEY, list);
    }

    /** Every batch read is an orphan; one whose recipe the world lost is told to the player instead. An older save has no batches. */
    void load(CompoundTag tag) {
        batches.clear(); lostNotes.clear(); inFlight = null;
        if (!(owner.level() instanceof ServerLevel level)) return;
        ListTag saves = tag.getList(CompanionFurnaceBatchNbt.KEY, Tag.TAG_COMPOUND);
        for (Tag entry : saves.subList(0, Math.min(saves.size(), MAX_BATCHES))) {
            CompoundTag saved = (CompoundTag) entry;
            Batch batch = CompanionFurnaceBatchNbt.load(saved, level);
            if (batch == null) {
                lostNotes.add(CompanionFurnaceBatchNbt.lostNote(saved));
                CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_lost", "entity", owner.getUUID(), "batch", saved.getLong("Id"), "reason", "unreadable");
                continue;
            }
            batches.put(batch.id, batch);
            CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_batch_loaded", "entity", owner.getUUID(), "batch", batch.id, "lots", batch.lots.size());
        }
    }

    /** Chunks of the open furnaces, parked or in the work that collects them: they must keep ticking while the companion is away or on his way. */
    Set<Long> chunks(ServerLevel level) {
        Set<Long> chunks = new HashSet<>();
        for (Batch batch : batches.values()) if (batch.dimension == level.dimension())
            for (CompanionWork.Lot lot : batch.lots) if (lot.open()) chunks.add(ChunkPos.asLong(lot.pos));
        if (owner.level() == level) for (CompanionWork.Lot lot : owner.work.lots) if (lot.open()) chunks.add(ChunkPos.asLong(lot.pos));
        return chunks;
    }

    /** Why a collection cannot even begin; null when it can. */
    String collectRefusal(String item) {
        Batch batch = find(item);
        if (batch == null) return "Партии плавки нет: её уже забрали или отпустили.";
        if (owner.level().dimension() != batch.dimension) return "Печи этой партии в другом измерении, отсюда за ними не сходить.";
        return null;
    }

    private Batch find(String item) {
        try { return batches.get(Long.parseLong(item == null ? "" : item.trim())); } catch (NumberFormatException e) { return null; }
    }

    /** At least what a standing smelt gets per furnace (the cooking left plus the service allowance), and on top of that the walk there and a refuel. */
    private long collectDeadline(Batch batch, ServerLevel level, long current) {
        long cooking = 0, walking = 0;
        for (CompanionWork.Lot lot : batch.lots) {
            if (!lot.open()) continue;
            AbstractFurnaceBlockEntity oven = CompanionFurnaceReader.oven(level, lot);
            if (oven != null) cooking = Math.max(cooking, (long) CompanionFurnaceReader.left(batch.recipe, oven) * batch.recipe.cookTicks());
            walking += (long) (Math.sqrt(owner.distanceToSqr(lot.pos.getCenter())) * COLLECT_TICKS_PER_BLOCK);
        }
        return Math.max(current, level.getGameTime() + cooking + CompanionFurnaces.SERVICE_ALLOWANCE_TICKS + walking + REFUEL_ALLOWANCE_TICKS);
    }

    /** Turns the batch back into the smelt it was: same recipe, lots and counts, so the report is the one a standing smelt would give. */
    String restore(String item, CompanionWork w, ServerLevel level) {
        Batch batch = find(item);
        batches.remove(batch.id);
        inFlight = batch;
        batch.ownCollect = batch.orphan;
        w.smelt = batch.recipe; w.smeltCount = batch.smeltCount; w.requestedCount = batch.requestedCount;
        w.smeltAll = batch.smeltAll; w.mixedMeat = batch.mixedMeat; w.mixedCooked = batch.mixedCooked; w.mixedKinds = batch.mixedKinds;
        w.mixedInputs.clear(); w.mixedInputs.addAll(batch.mixedInputs);
        w.furnacesShort = batch.furnacesShort;
        w.requestedItem = BuiltInRegistries.ITEM.getKey(batch.recipe.input()).toString();
        w.deadline = collectDeadline(batch, level, w.deadline);
        w.lots.addAll(batch.lots);
        CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_collected", "entity", owner.getUUID(), "batch", batch.id, "status", batch.status);
        List<CompanionWork.Lot> open = batch.lots.stream().filter(CompanionWork.Lot::open).toList();
        String name = CompanionWork.itemName(batch.recipe.result().getItem());
        if (open.isEmpty()) return "Схожу посмотреть, что осталось от плавки «" + name + "».";
        return batch.status == Status.READY ? "Готово, схожу заберу «" + name + "» из " + places(open, "печи", "печей") + "."
                : "Схожу к " + places(open, "печи", "печам") + ", заберу «" + name + "», что успело.";
    }
}
