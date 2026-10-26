package dev.aicompanion.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Priorities reorder requests, never the recipe prerequisites within a request. */
public final class CompanionTaskQueue {
    public enum Status { QUEUED, RUNNING, CANCELLING, COMPLETED, BLOCKED, CANCELLED }
    public record View(long id, int priority, Status status, List<PlannedAction> steps, int step, String report) { }
    public record Result(String report, boolean success) { }
    public final class Task {
        private final long id;
        private final List<PlannedAction> original;
        private List<PlannedAction> steps;
        private final Function<Task, CompletableFuture<Result>> runner;
        private final CompletableFuture<String> completion = new CompletableFuture<>();
        private int priority = 1, step = -1;
        private Status status = Status.QUEUED;
        private String report = "";
        private volatile boolean cancelled;
        private boolean hidden;
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
    }
    private final List<Task> waiting = new ArrayList<>(), history = new ArrayList<>();
    private Task active;
    private long sequence;

    public synchronized boolean contains(List<PlannedAction> actions) {
        return active != null && !active.cancelled && active.original.equals(actions)
                || waiting.stream().anyMatch(task -> task.original.equals(actions));
    }
    public synchronized Task submit(List<PlannedAction> steps, Function<Task, CompletableFuture<Result>> runner) {
        Task task = new Task(++sequence, steps, runner);
        waiting.add(task); pump(); return task;
    }
    private void pump() {
        if (active != null || waiting.isEmpty()) return;
        waiting.sort(Comparator.comparingInt((Task task) -> -task.priority).thenComparingLong(task -> task.id));
        Task task = waiting.removeFirst(); active = task; task.status = Status.RUNNING;
        CompletableFuture<Result> run;
        try { run = task.runner.apply(task); }
        catch (Exception error) { run = CompletableFuture.failedFuture(error); }
        run.whenComplete((result, error) -> finish(task, result, error));
    }
    private synchronized void finish(Task task, Result result, Throwable error) {
        task.status = task.cancelled ? Status.CANCELLED : error != null || !result.success() ? Status.BLOCKED : Status.COMPLETED;
        task.report = task.cancelled ? "План отменён игроком." : error != null ? "Не выполнено: " + error.getMessage() : result.report();
        if (!task.hidden) remember(task);
        active = null;
        if (error != null && !task.cancelled) task.completion.completeExceptionally(error);
        else task.completion.complete(task.report);
        pump();
    }
    private void remember(Task task) { history.addFirst(task); while (history.size() > 12) history.removeLast(); }
    public synchronized boolean cancel(long id) {
        if (active != null && active.id == id) {
            if (!active.cancelled) { active.cancelled = true; active.status = Status.CANCELLING; active.stop.run(); }
            return true;
        }
        for (Task task : List.copyOf(waiting)) if (task.id == id) {
            waiting.remove(task); task.cancelled = true; task.status = Status.CANCELLED; task.report = "План отменён игроком.";
            remember(task); task.completion.complete(task.report); return true;
        }
        return false;
    }
    public synchronized boolean priority(long id, int priority) {
        for (Task task : waiting) if (task.id == id) { task.priority = Math.clamp(priority, 0, 2); return true; }
        return false;
    }
    public synchronized void cancelAll(boolean clearHistory) {
        for (Task task : List.copyOf(waiting)) cancel(task.id);
        if (active != null) cancel(active.id);
        if (clearHistory) { history.clear(); if (active != null) active.hidden = true; }
    }
    public synchronized List<View> snapshot() {
        List<View> result = new ArrayList<>();
        if (active != null && !active.cancelled) result.add(view(active));
        waiting.stream().sorted(Comparator.comparingInt((Task task) -> -task.priority).thenComparingLong(task -> task.id))
                .map(this::view).forEach(result::add);
        if (active != null && active.cancelled && !active.hidden) result.add(view(active));
        history.stream().map(this::view).forEach(result::add);
        return List.copyOf(result);
    }
    private View view(Task task) { return new View(task.id, task.priority, task.status, task.steps, task.step, task.report); }
}
