package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Priorities reorder requests, never the recipe prerequisites within a request. A running task may park itself as
 * WAITING (a furnace batch is cooking): it gives up the slot, and {@link #tick()} resumes it once its batch is ready.
 * The queue touches no world state; the batch rules arrive as injected hooks.
 */
public final class CompanionTaskQueue {
    public enum Status { QUEUED, RUNNING, CANCELLING, COMPLETED, BLOCKED, CANCELLED, WAITING }
    public record View(long id, int priority, Status status, List<PlannedAction> steps, int step, String report) { }
    public record Result(String report, boolean success) { }
    /**
     * What happens to a parked batch when its owning task ends. Called outside the queue lock. A batch is settled at most
     * once by the queue, but the implementation must still be idempotent and ignore unknown or already collected ids:
     * {@link Task#collected(long)} may race with the physical pickup, so call it before that pickup completes. A failing
     * call is only logged, so the implementation has to keep its own books consistent.
     */
    public interface BatchOwner {
        /** The player cancelled: the product stays in the furnace. Returns a note naming the place (may be empty). */
        String release(long batchId);
        /** Any other end: nobody asked to drop it, so the companion collects it himself. */
        void orphan(long batchId);
        /** The task collected the batch and judged the result: whatever is left in the furnaces is not retried. */
        default void collected(long batchId) { }
    }
    private static final BatchOwner NO_OWNER = new BatchOwner() {
        @Override public String release(long batchId) { return ""; }
        @Override public void orphan(long batchId) { }
    };

    public final class Task {
        private final long id;
        private final List<PlannedAction> original;
        private List<PlannedAction> steps;
        private final Function<Task, CompletableFuture<Result>> runner;
        private final CompletableFuture<String> completion = new CompletableFuture<>();
        private final Set<Long> batches = new LinkedHashSet<>();
        private int priority = 1, step = -1;
        private Status status = Status.QUEUED;
        private String report = "";
        private volatile boolean cancelled;
        private boolean hidden, finished, byPlayer = true, ready;
        /** The task that gave up the slot for this one and gets it back at this one's next boundary; null when none did. */
        private Task lender;
        private long awaited;
        private Set<Long> eligible = Set.of();
        private BooleanSupplier readiness;
        private CompletableFuture<Void> resume;
        private Runnable stop = () -> { };
        private Task(long id, List<PlannedAction> steps, Function<Task, CompletableFuture<Result>> runner) {
            this.id = id; this.original = List.copyOf(steps); this.steps = original; this.runner = runner;
        }
        public boolean cancelled() { return cancelled; }
        public CompletableFuture<String> completion() { return completion; }
        public void onCancel(Runnable stop) {
            synchronized (CompanionTaskQueue.this) { this.stop = stop; if (cancelled) stop.run(); }
        }
        public void progress(List<PlannedAction> plan, int index) {
            synchronized (CompanionTaskQueue.this) { steps = List.copyOf(plan); step = index; }
        }
        /**
         * Parks the running task as WAITING and frees the slot; the task owns {@code batchId} from now on. The future
         * completes (outside the lock, with the task RUNNING again) after {@code ready} turned true in a {@link #tick()}
         * and the slot was free; it fails with {@link CancellationException} if the task is cancelled meanwhile.
         * {@code ready} runs only in {@code tick()}, never under the queue lock; if it throws, the task ends BLOCKED and
         * frees the slot. The batch is owned from the call on, even when the call fails: the task's end (or, for a task
         * that already ended, this call) settles it.
         */
        public CompletableFuture<Void> await(long batchId, BooleanSupplier ready) {
            CompletableFuture<Void> resumed = new CompletableFuture<>();
            boolean ended;
            synchronized (CompanionTaskQueue.this) {
                ended = finished;
                if (!ended) {
                    batches.add(batchId);
                    if (cancelled) return CompletableFuture.failedFuture(new CancellationException("План отменён."));
                    if (active != this) return CompletableFuture.failedFuture(new IllegalStateException("Ждать может только выполняющаяся задача."));
                    readiness = ready; this.ready = false; resume = resumed; awaited = batchId; lender = null;
                    status = Status.WAITING; active = null; awaiting.add(this);
                }
            }
            if (ended) {
                disposeBatches(this, List.of(batchId));
                return CompletableFuture.failedFuture(cancelled ? new CancellationException("План отменён.")
                        : new IllegalStateException("Ждать может только выполняющаяся задача."));
            }
            pump();
            return resumed;
        }
        /** The batches of other tasks that are cooked and waiting for the slot, as of the last {@link #tick()}. */
        public List<Long> readyBatches() {
            synchronized (CompanionTaskQueue.this) { return awaiting.stream().filter(task -> task != this && task.ready).map(task -> task.awaited).toList(); }
        }
        /**
         * True when the task runs on a slot that another task gave up for it: it gives the slot back at its next boundary.
         * Not when the lender was cancelled meanwhile and no other task waits for the slot: nobody is left to give it back to.
         */
        public boolean borrowed() {
            synchronized (CompanionTaskQueue.this) { return lender != null && !yielded.isEmpty(); }
        }
        /**
         * The running task gives its slot to a cooked batch of another task (or back to the task that lent it) at a step boundary.
         * While it waits for the slot only the cooked tasks that own one of the {@code eligibleBatches} (the near ones) may take it:
         * any other ready task stays behind this one. The future completes, outside the lock with the task RUNNING again, when the slot
         * comes back, before any queued task gets it; it fails with {@link CancellationException} if the task is cancelled meanwhile.
         */
        public CompletableFuture<Void> yieldSlot(Set<Long> eligibleBatches) {
            CompletableFuture<Void> resumed = new CompletableFuture<>();
            synchronized (CompanionTaskQueue.this) {
                if (cancelled) return CompletableFuture.failedFuture(new CancellationException("План отменён."));
                if (finished || active != this) return CompletableFuture.failedFuture(new IllegalStateException("Уступить может только выполняющаяся задача."));
                resume = resumed; lender = null; eligible = Set.copyOf(eligibleBatches);
                status = Status.WAITING; active = null; yielded.add(this);
            }
            pump();
            return resumed;
        }
        /**
         * The task owns a parked batch from now on, before it ever awaits it (a cancelled step may never get that far). The task's
         * end settles it; for a task that already ended, this call does.
         */
        public void own(long batchId) {
            boolean ended;
            synchronized (CompanionTaskQueue.this) { ended = finished; if (!ended) batches.add(batchId); }
            if (ended) disposeBatches(this, List.of(batchId));
        }
        /** The task finished collecting the batch itself: nothing is left to release or orphan. */
        public void collected(long batchId) {
            boolean owned;
            synchronized (CompanionTaskQueue.this) { owned = batches.remove(batchId); }
            if (owned) owner.collected(batchId);
        }
    }
    private static final Comparator<Task> ORDER = Comparator.comparingInt((Task task) -> -task.priority).thenComparingLong(task -> task.id);
    private final List<Task> waiting = new ArrayList<>(), awaiting = new ArrayList<>(), yielded = new ArrayList<>(), history = new ArrayList<>();
    private final Predicate<List<PlannedAction>> held;
    private final BatchOwner owner;
    private Task active;
    private long sequence;

    public CompanionTaskQueue() { this(steps -> false, NO_OWNER); }
    /**
     * {@code held} says a queued task's steps must wait for a parked batch. It runs under the lock on every pump, on both the
     * client and the server thread: read an immutable or volatile snapshot, never a live registry. A throwing {@code held}
     * counts as "not held".
     */
    public CompanionTaskQueue(Predicate<List<PlannedAction>> held, BatchOwner owner) { this.held = held; this.owner = owner; }

    public synchronized boolean contains(List<PlannedAction> actions) {
        return active != null && !active.cancelled && active.original.equals(actions)
                || waiting.stream().anyMatch(task -> task.original.equals(actions))
                || awaiting.stream().anyMatch(task -> task.original.equals(actions))
                || yielded.stream().anyMatch(task -> task.original.equals(actions));
    }
    public Task submit(List<PlannedAction> steps, Function<Task, CompletableFuture<Result>> runner) {
        Task task;
        synchronized (this) { task = new Task(++sequence, steps, runner); waiting.add(task); }
        pump();
        return task;
    }
    /** Re-checks the readiness of waiting tasks (the server calls it about once a second), then hands out a free slot. */
    public void tick() {
        List<Probe> probes;
        synchronized (this) { probes = awaiting.stream().filter(task -> !task.ready).map(task -> new Probe(task, task.readiness, task.resume)).toList(); }
        for (Probe probe : probes) {
            boolean isReady;
            try { isReady = probe.readiness().getAsBoolean(); }
            catch (RuntimeException error) { fail(probe, error); continue; }
            if (isReady) synchronized (this) { if (current(probe)) probe.task().ready = true; }
        }
        pump();
    }
    /** One await cycle of a task: a result computed for an older cycle must not mark a re-awaiting task ready. */
    private record Probe(Task task, BooleanSupplier readiness, CompletableFuture<Void> resume) { }
    private boolean current(Probe probe) {
        Task task = probe.task();
        return task.status == Status.WAITING && task.readiness == probe.readiness() && task.resume == probe.resume();
    }
    /** A readiness check that throws cannot be retried: the queue ends the task itself instead of leaving a zombie on the slot. */
    private void fail(Probe probe, Throwable failure) {
        synchronized (this) {
            if (!current(probe) || !awaiting.remove(probe.task())) return;
        }
        probe.resume().completeExceptionally(failure);
        finish(probe.task(), null, failure);
    }
    /**
     * The one scheduler: a cooked waiting task first (it borrows the slot when a task that gave it up is still waiting, and then only
     * a task the last giver named as eligible may), then the task that gave its slot up, longest waiting first, then the first queued
     * task that is not held. Starts outside the lock.
     */
    private void pump() {
        Runnable start;
        synchronized (this) { start = next(); }
        if (start != null) start.run();
    }
    private Runnable next() {
        if (active != null) return null;
        Set<Long> eligible = yielded.isEmpty() ? null : yielded.getLast().eligible;
        Task resumed = awaiting.stream().filter(task -> task.ready && (eligible == null || eligible.contains(task.awaited))).min(ORDER).orElse(null);
        if (resumed != null) {
            awaiting.remove(resumed); active = resumed; resumed.status = Status.RUNNING; resumed.lender = yielded.isEmpty() ? null : yielded.getFirst();
            return () -> resumed.resume.complete(null);
        }
        if (!yielded.isEmpty()) {
            Task back = yielded.removeFirst();
            active = back; back.status = Status.RUNNING;
            return () -> back.resume.complete(null);
        }
        Task task = waiting.stream().filter(candidate -> !isHeld(candidate)).min(ORDER).orElse(null);
        if (task == null) return null;
        waiting.remove(task); active = task; task.status = Status.RUNNING;
        return () -> {
            CompletableFuture<Result> run;
            try { run = task.runner.apply(task); }
            catch (Exception error) { run = CompletableFuture.failedFuture(error); }
            run.whenComplete((result, error) -> finish(task, result, error));
        };
    }
    private boolean isHeld(Task candidate) {
        try { return held.test(candidate.steps); }
        catch (RuntimeException error) {
            AICompanionMod.LOGGER.warn("Park hold rule failed, the task is not held: {}", error.getMessage());
            return false;
        }
    }
    private void finish(Task task, Result result, Throwable error) {
        List<Long> owned;
        synchronized (this) {
            if (task.finished) return;
            task.finished = true;
            task.status = task.cancelled ? Status.CANCELLED : error != null || !result.success() ? Status.BLOCKED : Status.COMPLETED;
            task.report = task.cancelled ? "План отменён игроком." : error != null ? "Не выполнено: " + error.getMessage() : result.report();
            if (!task.hidden) remember(task);
            if (active == task) active = null;
            awaiting.remove(task);
            owned = List.copyOf(task.batches); task.batches.clear();
        }
        disposeBatches(task, owned);
        if (error != null && !task.cancelled) task.completion.completeExceptionally(error);
        else task.completion.complete(task.report);
        pump();
    }
    /** Every end of a task settles the batches it owned: the player's cancel releases them, anything else orphans them. */
    private void disposeBatches(Task task, List<Long> owned) {
        boolean release = task.cancelled && task.byPlayer;
        StringBuilder notes = new StringBuilder();
        for (long batch : owned) {
            try {
                if (!release) { owner.orphan(batch); continue; }
                String note = owner.release(batch);
                if (note != null && !note.isBlank()) notes.append(' ').append(note.strip());
            } catch (RuntimeException error) {
                AICompanionMod.LOGGER.warn("Furnace batch {} could not be settled: {}", batch, error.getMessage());
            }
        }
        if (!notes.isEmpty()) synchronized (this) { task.report += notes; }
    }
    private void remember(Task task) { history.addFirst(task); while (history.size() > 12) history.removeLast(); }
    /** A request is running or waits for its turn: parked ones, which wait for their furnaces and not for him, do not count. */
    public synchronized boolean busy() { return active != null || !waiting.isEmpty() || !yielded.isEmpty(); }
    public boolean cancel(long id) { return cancel(id, true); }
    private boolean cancel(long id, boolean byPlayer) {
        Task parked = null, dropped = null;
        synchronized (this) {
            if (active != null && active.id == id) {
                if (!active.cancelled) { active.cancelled = true; active.byPlayer = byPlayer; active.status = Status.CANCELLING; active.stop.run(); }
                return true;
            }
            for (Task task : List.copyOf(waiting)) if (task.id == id) {
                waiting.remove(task); task.cancelled = true; task.finished = true; task.status = Status.CANCELLED; task.report = "План отменён игроком.";
                remember(task); dropped = task; break;
            }
            if (dropped == null) for (Task task : Stream.concat(awaiting.stream(), yielded.stream()).toList()) if (task.id == id) {
                awaiting.remove(task); yielded.remove(task); parked = task;
                task.cancelled = true; task.byPlayer = byPlayer; task.finished = true; task.status = Status.CANCELLED; task.report = "План отменён игроком.";
                if (!task.hidden) remember(task);
            }
        }
        if (dropped != null) { dropped.completion.complete(dropped.report); return true; }
        if (parked == null) return false;
        List<Long> owned;
        synchronized (this) { owned = List.copyOf(parked.batches); parked.batches.clear(); }
        parked.stop.run();
        disposeBatches(parked, owned);
        parked.resume.completeExceptionally(new CancellationException("План отменён."));
        parked.completion.complete(parked.report);
        pump();
        return true;
    }
    public synchronized boolean priority(long id, int priority) {
        for (Task task : waiting) if (task.id == id) { task.priority = Math.clamp(priority, 0, 2); return true; }
        for (Task task : awaiting) if (task.id == id) { task.priority = Math.clamp(priority, 0, 2); return true; }
        for (Task task : yielded) if (task.id == id) { task.priority = Math.clamp(priority, 0, 2); return true; }
        return false;
    }
    public void cancelAll(boolean clearHistory) { cancelAll(clearHistory, false); }
    public void cancelAll(boolean clearHistory, boolean byPlayer) {
        List<Long> queued;
        synchronized (this) { queued = new ArrayList<>(); waiting.forEach(task -> queued.add(task.id)); awaiting.forEach(task -> queued.add(task.id)); yielded.forEach(task -> queued.add(task.id)); }
        for (long id : queued) cancel(id, byPlayer);
        Long running;
        synchronized (this) { running = active == null ? null : active.id; }
        if (running != null) cancel(running, byPlayer);
        if (!clearHistory) return;
        synchronized (this) { history.clear(); if (active != null) active.hidden = true; }
    }
    /** Ids of the batches the tasks still own; a copy. */
    public synchronized Set<Long> ownedBatches() {
        Set<Long> owned = new HashSet<>();
        if (active != null) owned.addAll(active.batches);
        awaiting.forEach(task -> owned.addAll(task.batches));
        yielded.forEach(task -> owned.addAll(task.batches));
        return owned;
    }
    public synchronized List<View> snapshot() {
        List<View> result = new ArrayList<>();
        if (active != null && !active.cancelled) result.add(view(active));
        awaiting.stream().sorted(ORDER).map(this::view).forEach(result::add);
        yielded.stream().map(this::view).forEach(result::add);
        waiting.stream().sorted(ORDER).map(this::view).forEach(result::add);
        if (active != null && active.cancelled && !active.hidden) result.add(view(active));
        history.stream().map(this::view).forEach(result::add);
        return List.copyOf(result);
    }
    private View view(Task task) { return new View(task.id, task.priority, task.status, task.steps, task.step, task.report); }
}
