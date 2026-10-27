package dev.aicompanion.client;

import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionActionResult.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Runs a validated plan step by step. A failed step is repeated at most once, only on server
 * evidence (the companion was still finishing earlier work, or a timeout came after real
 * progress), and at most {@link #MAX_RECOVERIES} times per plan. Nothing new is invented here:
 * a repeat is the same allowlisted action, possibly with the remaining count.
 * No client classes, so GameTests drive the same logic as the game.
 */
public final class CompanionPlanRunner {
    public static final int MAX_RECOVERIES = 2;
    public static final int IDLE_WAIT_SECONDS = 20;
    /** Steps run for one obtain_item, replans included: twice the longest chain the planner gives (CompanionObtain.MAX_STEPS). */
    public static final int MAX_OBTAIN_STEPS = 32;
    /** Orders that the server rejects while another work order is still active. */
    public static final Set<String> WORK = Set.of("gather_wood", "gather_dirt", "gather_stone", "store_items", "take_items",
            "build_house", "craft_item", "place_block", "give_items", "give_wood", "smelt_items", "mine_ore", "hunt_animals");
    private static final Set<String> GATHER = Set.of("gather_wood", "gather_dirt", "gather_stone");

    public interface Port {
        String trace();
        boolean cancelled();
        /** Companion state between steps, read on the server thread. */
        CompletableFuture<State> state(PlannedAction action);
        /** Dispatches the action and completes with its verified result, not with the acknowledgement. */
        CompletableFuture<Attempt> run(PlannedAction action);
        CompletableFuture<Void> pause(int seconds);
        default void progress(List<PlannedAction> plan, int index) { }
        /** How many of the item he carries now. */
        default CompletableFuture<Integer> carried(String item) { return CompletableFuture.completedFuture(0); }
        /** obtain_item turned into the steps that get the item, or the reason it cannot be got. */
        default CompletableFuture<Expansion> expand(PlannedAction action) {
            return CompletableFuture.completedFuture(new Expansion(List.of(), "Не умею добывать этот предмет."));
        }
    }
    /** steps end with verify_item, so the order succeeds only by the bag. */
    public record Expansion(List<PlannedAction> steps, String refusal) { }
    /** busy: an earlier order is still finishing. progress: physical pickups for gather steps, -1 if not measured. */
    public record State(boolean busy, int progress) { }
    /** started: the server accepted the order, so the result comes from real work. */
    public record Attempt(CompanionActionResult result, boolean started) { }
    public enum Decision { CONTINUE, RETRY, STOP }
    public record Verdict(Decision decision, PlannedAction retry, String reason) { }
    public record Outcome(String report, List<PlannedAction> skipped, int recoveries) { }

    private CompanionPlanRunner() { }

    public static Verdict decide(PlannedAction action, Attempt attempt, State before, State after, List<PlannedAction> later,
                                 boolean retried, int recoveries, boolean cancelled) {
        CompanionActionResult result = attempt.result();
        if (cancelled || result.status() == Status.CANCELLED) return stop("cancelled");
        if (result.success()) return new Verdict(Decision.CONTINUE, null, "verified");
        if (CompanionDialogue.continueAfterPartialGather(action, later, false, result.message())) return new Verdict(Decision.CONTINUE, null, "partial_gather_feeds_craft");
        if (result.status() == Status.BLOCKED) return stop("blocked");
        int gained = before.progress() >= 0 && after.progress() >= 0 ? after.progress() - before.progress() : -1;
        if (result.status() == Status.TIMEOUT && GATHER.contains(action.type()) && gained >= action.count())
            return new Verdict(Decision.CONTINUE, null, "target_collected_before_timeout");
        if (retried) return stop("step_already_repeated");
        if (recoveries >= MAX_RECOVERIES) return stop("plan_recovery_budget_spent");
        if (!attempt.started() && after.busy() && WORK.contains(action.type())) return new Verdict(Decision.RETRY, action, "companion_busy");
        if (result.status() == Status.TIMEOUT && GATHER.contains(action.type())) {
            if (gained <= 0) return stop("timeout_without_progress");
            return new Verdict(Decision.RETRY, new PlannedAction(action.type(), action.seconds(), action.count() - gained,
                    action.item(), action.slot(), action.location()), "timeout_after_progress");
        }
        return stop("failed");
    }

    public static CompletableFuture<Outcome> run(List<PlannedAction> plan, Port port) {
        Run run = new Run(plan, port);
        if (plan.isEmpty()) return CompletableFuture.completedFuture(run.outcome(0));
        return step(run, 0, plan.getFirst(), false).thenApply(run::outcome);
    }

    private static CompletableFuture<Integer> step(Run run, int index, PlannedAction action, boolean retried) {
        if (index >= run.plan.size() && !run.replan) return CompletableFuture.completedFuture(index);
        Port port = run.port;
        int number = index + 1;
        port.progress(List.copyOf(run.plan), index);
        if (port.cancelled()) {
            CompanionDiagnostics.event(port.trace(), "step_cancelled", "step", number, "reason", "generation_changed");
            run.reports.add("План отменён командой остановки.");
            run.cancelled = true;
            return CompletableFuture.completedFuture(index);
        }
        if (run.replan) return replan(run, index);
        // «Добудь X»: the chain of real steps is planned now, from the bag as it is after the steps before (CompanionObtain).
        // After each of its steps the chain is planned again (replan), so steps that became needless drop out and a shortage is dug up.
        if ("obtain_item".equals(action.type())) return port.expand(action).thenCompose(expansion -> {
            CompanionDiagnostics.event(port.trace(), "obtain_planned", "step", number, "action", action, "steps", expansion.steps(), "refusal", expansion.refusal());
            if (expansion.refusal() != null) {
                run.obtain = action; run.obtainEnd = index + 1;
                return giveUpObtain(run, index, expansion.refusal());
            }
            run.plan.remove(index);
            run.plan.addAll(index, expansion.steps());
            run.obtain = action; run.obtainEnd = index + expansion.steps().size(); run.obtainSteps = 0;
            return step(run, index, run.plan.get(index), false);
        });
        // Pillar descent, loot pickup and similar tails of the previous step end before the next order.
        boolean settle = WORK.contains(action.type()) && (index > 0 || retried);
        return (settle ? idle(port, action, IDLE_WAIT_SECONDS) : port.state(action)).thenCompose(before -> {
            CompanionDiagnostics.event(port.trace(), "step_started", "step", number, "action", action, "attempt", retried ? 2 : 1);
            return port.run(action).thenCompose(attempt -> port.state(action).thenCompose(after -> {
                List<PlannedAction> later = run.plan.subList(index + 1, run.plan.size());
                Verdict verdict = decide(action, attempt, before, after, later, retried, run.recoveries, port.cancelled());
                CompanionActionResult result = attempt.result();
                boolean inObtain = run.obtain != null && index < run.obtainEnd;
                if (inObtain) run.obtainSteps++;
                CompanionDiagnostics.event(port.trace(), "step_result", "step", number, "action", action, "success", result.success(),
                        "continuePlan", verdict.decision() == Decision.CONTINUE, "decision", verdict.decision(), "reason", verdict.reason(),
                        "status", result.status(), "message", result.message(), "progressBefore", before.progress(), "progressAfter", after.progress(),
                        "completionCheck", List.of("follow", "protect", "wander").contains(action.type()) ? "mode_acknowledgement_only" : "engine_result");
                // A craft refused at once for lack of materials becomes «get it»: "craft N" is N new ones, "get" counts all in the bag.
                if (!inObtain && !attempt.started() && result.status() == Status.SHORTAGE && "craft_item".equals(action.type())) {
                    return port.carried(action.item()).thenCompose(have -> {
                        PlannedAction obtain = new PlannedAction("obtain_item", 0, have + action.count(), action.item());
                        CompanionDiagnostics.event(port.trace(), "craft_to_obtain", "step", number, "action", action, "obtain", obtain);
                        run.plan.set(index, obtain);
                        return step(run, index, obtain, false);
                    });
                }
                // A step of an obtain chain that did not succeed is planned around, even a partial gather the plan would go on after:
                // the stop comes only if the new plan starts with the same step.
                if (inObtain && !result.success() && verdict.decision() != Decision.RETRY
                        && !"cancelled".equals(verdict.reason()) && !"blocked".equals(verdict.reason())) {
                    run.replan = true; run.failed = action; run.failMessage = result.message();
                    return step(run, index + 1, null, false);
                }
                return switch (verdict.decision()) {
                    case CONTINUE -> {
                        run.reports.add(result.message());
                        if (inObtain) { if (index + 1 < run.obtainEnd) run.replan = true; else run.obtain = null; }
                        yield step(run, index + 1, index + 1 < run.plan.size() ? run.plan.get(index + 1) : null, false);
                    }
                    case RETRY -> {
                        run.recoveries++;
                        run.reports.add("Повтор шага: " + retryNote(verdict));
                        yield step(run, index, verdict.retry(), true);
                    }
                    case STOP -> {
                        run.cancelled = "cancelled".equals(verdict.reason());
                        run.reports.add("План остановлен: " + result.message());
                        yield CompletableFuture.completedFuture(index + 1);
                    }
                };
            }));
        });
    }

    /** Plans the running obtain_item again from the bag as it is now and puts the new steps in place of the unfinished ones. */
    private static CompletableFuture<Integer> replan(Run run, int index) {
        Port port = run.port;
        run.replan = false;
        PlannedAction failed = run.failed;
        String failMessage = run.failMessage;
        run.failed = null;
        return port.expand(run.obtain).thenCompose(expansion -> {
            CompanionDiagnostics.event(port.trace(), "obtain_replanned", "step", index + 1, "action", run.obtain, "steps", expansion.steps(), "refusal", expansion.refusal());
            if (expansion.refusal() != null) return giveUpObtain(run, index, expansion.refusal());
            if (run.obtainSteps >= MAX_OBTAIN_STEPS)
                return giveUpObtain(run, index, failed != null ? failMessage : "слишком много шагов ради одного предмета.");
            run.plan.subList(Math.min(index, run.plan.size()), Math.min(run.obtainEnd, run.plan.size())).clear();
            run.plan.addAll(index, expansion.steps());
            run.obtainEnd = index + expansion.steps().size();
            if (failed != null && failed.equals(expansion.steps().getFirst())) return giveUpObtain(run, index, failMessage);
            return step(run, index, run.plan.get(index), false);
        });
    }

    /**
     * The running obtain_item is given up: its unfinished steps and the later steps of the plan that name the same item are dropped
     * (reported as not done), the rest of the plan goes on. With nothing left to run it is the old whole-plan stop.
     */
    private static CompletableFuture<Integer> giveUpObtain(Run run, int index, String reason) {
        PlannedAction obtain = run.obtain;
        run.obtain = null;
        int from = Math.min(index, run.plan.size());
        run.plan.subList(from, Math.min(run.obtainEnd, run.plan.size())).clear();
        List<PlannedAction> same = new ArrayList<>();
        for (int i = run.plan.size() - 1; i >= from; i--)
            if (obtain.item() != null && obtain.item().equals(run.plan.get(i).item())) same.addFirst(run.plan.remove(i));
        run.dropped.add(obtain);
        run.dropped.addAll(same);
        boolean goesOn = from < run.plan.size();
        run.reports.add((goesOn ? CompanionDialogue.PARTIAL_PREFIX : "План остановлен: ") + reason);
        return step(run, from, goesOn ? run.plan.get(from) : null, false);
    }

    private static CompletableFuture<State> idle(Port port, PlannedAction action, int seconds) {
        return port.state(action).thenCompose(state -> !state.busy() || seconds <= 0 || port.cancelled()
                ? CompletableFuture.completedFuture(state)
                : port.pause(1).thenCompose(unused -> idle(port, action, seconds - 1)));
    }

    private static String retryNote(Verdict verdict) {
        return "companion_busy".equals(verdict.reason()) ? "дождался конца предыдущей работы и начал снова."
                : "не успел вовремя, добираю оставшиеся " + verdict.retry().count() + ".";
    }

    private static Verdict stop(String reason) { return new Verdict(Decision.STOP, null, reason); }

    private static final class Run {
        final List<PlannedAction> plan;
        final Port port;
        final List<String> reports = new ArrayList<>();
        /** Given-up obtain_item orders and the later steps skipped with them. */
        final List<PlannedAction> dropped = new ArrayList<>();
        int recoveries;
        boolean cancelled;
        /** The obtain_item being run, where its unfinished steps end in the plan, and what the replan after the last step must know. */
        PlannedAction obtain, failed;
        String failMessage;
        int obtainEnd, obtainSteps;
        boolean replan;
        Run(List<PlannedAction> plan, Port port) { this.plan = new ArrayList<>(plan); this.port = port; }

        Outcome outcome(int stoppedAt) {
            List<PlannedAction> skipped = new ArrayList<>(plan.subList(Math.min(stoppedAt, plan.size()), plan.size()));
            skipped.addAll(dropped);
            if (cancelled) skipped.clear();
            List<String> lines = new ArrayList<>(reports);
            if (!skipped.isEmpty()) CompanionDiagnostics.event(port.trace(), "step_skipped", "actions", skipped, "reason", "previous_step_failed");
            if (!skipped.isEmpty()) lines.add(CompanionDialogue.SKIPPED_PREFIX + CompanionDialogue.describeAll(skipped));
            return new Outcome(String.join("\n", lines), List.copyOf(skipped), recoveries);
        }
    }
}
