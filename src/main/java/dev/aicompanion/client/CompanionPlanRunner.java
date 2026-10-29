package dev.aicompanion.client;

import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionActionResult.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
    /** Collections in a row that parked again (a mixed smelt cooks one kind after the other) before the plan gives the batch up. */
    private static final int MAX_REPARKS = 8;
    /** In a row, steps of one obtain_item after which the bag layout is unchanged: the second one gives the part up. */
    private static final int NO_GAIN_LIMIT = 2;
    /** Steps run for one obtain_item, replans included: twice the longest chain the planner gives (CompanionObtain.MAX_STEPS). */
    public static final int MAX_OBTAIN_STEPS = 32;
    /** Orders that the server rejects while another work order is still active. */
    public static final Set<String> WORK = Set.of("gather_wood", "gather_dirt", "gather_stone", "store_items", "take_items",
            "build_house", "craft_item", "place_block", "give_items", "give_wood", "smelt_items", "mine_ore", "hunt_animals");
    private static final Set<String> GATHER = Set.of("gather_wood", "gather_dirt", "gather_stone");
    /** Steps that may run while a parked batch cooks, unless they produce its input (CompanionParkHold decides that). */
    private static final Set<String> RUN_AHEAD = Set.of("gather_wood", "gather_dirt", "gather_stone", "mine_ore", "hunt_animals");

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
        /** True when the queue lets this plan's long smelt park; a port without a queue never parks, so everything stands at the furnace as before. */
        default boolean parkable() { return false; }
        /** The plan owns the parked batch from now on, even if it is cancelled before it ever waits for it. */
        default void own(long batchId) { }
        /** Completes when the batch is ready to collect and the plan holds the queue slot again; fails with CancellationException if cancelled meanwhile. */
        default CompletableFuture<Void> park(long batchId) { return CompletableFuture.completedFuture(null); }
        /** The batch is collected (the collection was accepted): nothing is left for the queue to release or orphan. */
        default void collected(long batchId) { }
        /** True when the plan gave the slot away at a step boundary (it is back on the slot when this completes). */
        default CompletableFuture<Boolean> checkpoint() { return CompletableFuture.completedFuture(false); }
    }
    /** steps end with verify_item, so the order succeeds only by the bag. */
    public record Expansion(List<PlannedAction> steps, String refusal) { }
    /** busy: an earlier order is still finishing. progress: physical pickups for gather steps, -1 if not measured. */
    public record State(boolean busy, int progress) { }
    /** started: the server accepted the order, so the result comes from real work. */
    public record Attempt(CompanionActionResult result, boolean started) { }
    public enum Decision { CONTINUE, RETRY, STOP, PARK }
    public record Verdict(Decision decision, PlannedAction retry, String reason) { }
    public record Outcome(String report, List<PlannedAction> skipped, int recoveries) { }

    private CompanionPlanRunner() { }

    public static Verdict decide(PlannedAction action, Attempt attempt, State before, State after, List<PlannedAction> later,
                                 boolean retried, int recoveries, boolean cancelled) {
        CompanionActionResult result = attempt.result();
        if (cancelled || result.status() == Status.CANCELLED) return stop("cancelled");
        if (result.status() == Status.PARKED) return new Verdict(Decision.PARK, null, "parked");
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
        // The first step that needs the product of the parked batch, or the end of the plan, waits for it and collects it.
        if (settleFirst(run, index, action))
            return settle(run, false).thenCompose(goesOn -> goesOn ? step(run, index, action, retried) : CompletableFuture.completedFuture(index));
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
            run.obtain = action; run.obtainEnd = index + expansion.steps().size(); run.obtainSteps = 0; run.noGain = 0; run.noGainFailure = null;
            return step(run, index, run.plan.get(index), false);
        });
        // A step boundary of the plan itself: a cooked batch of another request that is near is collected now (CompanionServerPlanPort.checkpoint).
        if (checkpointOpen(run, index, action, retried))
            return port.checkpoint().exceptionally(error -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                if (cause instanceof CancellationException) return false;
                throw error instanceof CompletionException wrapped ? wrapped : new CompletionException(error);
            }).thenCompose(yielded -> port.cancelled() ? step(run, index, action, retried) : dispatch(run, index, action, retried));
        return dispatch(run, index, action, retried);
    }

    /**
     * No hand-over inside an obtain chain, on a retry, or where the step depends on a placement: a placed bench or furnace is used from
     * where it was made (craft_item walks to a bench only within 16 blocks), for the whole run of crafts or smelts after it, and
     * verify_item must count the bag as the plan left it.
     */
    private static boolean checkpointOpen(Run run, int index, PlannedAction action, boolean retried) {
        return index > 0 && !retried && !(run.obtain != null && index < run.obtainEnd)
                && !"verify_item".equals(action.type()) && !usesPlacement(run.plan, index, action);
    }

    /** The step right after a place_block, or a craft or smelt that continues a run of them started by one. */
    private static boolean usesPlacement(List<PlannedAction> plan, int index, PlannedAction action) {
        if ("place_block".equals(plan.get(index - 1).type())) return true;
        if (!isBenchStep(action)) return false;
        int before = index - 1;
        while (before >= 0 && isBenchStep(plan.get(before))) before--;
        return before >= 0 && "place_block".equals(plan.get(before).type());
    }

    private static boolean isBenchStep(PlannedAction action) {
        return "craft_item".equals(action.type()) || "smelt_items".equals(action.type());
    }

    private static CompletableFuture<Integer> dispatch(Run run, int index, PlannedAction action, boolean retried) {
        Port port = run.port;
        int number = index + 1;
        // Pillar descent, loot pickup and similar tails of the previous step end before the next order.
        boolean settle = WORK.contains(action.type()) && (index > 0 || retried);
        return (settle ? idle(port, action, IDLE_WAIT_SECONDS) : port.state(action)).thenCompose(before -> {
            CompanionDiagnostics.event(port.trace(), "step_started", "step", number, "action", action, "attempt", retried ? 2 : 1);
            return port.run(action).thenCompose(attempt -> after(run, index, action, retried, before, attempt));
        });
    }

    /** Judges the attempt of a step: a parked one is first collected, and only its final outcome is reported and decided on. */
    private static CompletableFuture<Integer> after(Run run, int index, PlannedAction action, boolean retried, State before, Attempt attempt) {
        Port port = run.port;
        int number = index + 1;
        CompanionActionResult parked = attempt.result();
        if (parked.status() == Status.PARKED && parked.evidence() != null) port.own(parked.evidence().workId());
        return port.state(action).thenCompose(now -> {
            List<PlannedAction> later = run.plan.subList(index + 1, run.plan.size());
            Verdict verdict = decide(action, attempt, before, now, later, retried, run.recoveries, port.cancelled());
            if (verdict.decision() == Decision.PARK) {
                PlannedAction next = index + 1 < run.plan.size() ? run.plan.get(index + 1) : null;
                boolean inChain = run.obtain != null && index < run.obtainEnd;
                // An obtain_item chain collects at once: the replan must see the bag with the product, or it orders the ore again.
                if (!inChain && port.parkable() && run.pending == null && parked.evidence() != null && runsAhead(next, parked.evidence().item())) {
                    int slot = run.reports.size();
                    run.reports.add(null);
                    run.pending = new Pending(attempt, action, before, slot, index);
                    CompanionDiagnostics.event(port.trace(), "run_ahead", "step", number, "parked", action, "next", next);
                    return step(run, index + 1, next, false);
                }
                return collectLoop(run, attempt, 0).thenCompose(collected -> after(run, index, action, retried, before, collected));
            }
            CompanionActionResult result = attempt.result();
            boolean inObtain = run.obtain != null && index < run.obtainEnd;
            if (inObtain) run.obtainSteps++;
            List<PlannedAction> layout = inObtain ? List.copyOf(run.plan.subList(index, Math.min(run.obtainEnd, run.plan.size()))) : List.of();
            CompanionDiagnostics.event(port.trace(), "step_result", "step", number, "action", action, "success", result.success(),
                    "continuePlan", verdict.decision() == Decision.CONTINUE, "decision", verdict.decision(), "reason", verdict.reason(),
                    "status", result.status(), "message", result.message(), "progressBefore", before.progress(), "progressAfter", now.progress(),
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
            // A blocked step of an obtain chain gives up only this part; the rest of the plan goes on.
            if (inObtain && "blocked".equals(verdict.reason())) return giveUpObtain(run, index, result.message());
            // A step of an obtain chain that did not succeed is planned around, even a partial gather the plan would go on after:
            // the stop comes only if the new plan is the same as before the step (nothing was gained).
            if (inObtain && !result.success() && verdict.decision() != Decision.RETRY && !"cancelled".equals(verdict.reason())) {
                run.replan = true; run.before = layout; run.lastFailed = true; run.lastMessage = result.message();
                return step(run, index + 1, null, false);
            }
            return switch (verdict.decision()) {
                case CONTINUE -> {
                    run.reports.add(result.message());
                    if (inObtain) {
                        if (index + 1 < run.obtainEnd) { run.replan = true; run.before = layout; run.lastFailed = false; run.lastMessage = result.message(); }
                        else run.obtain = null;
                    }
                    yield step(run, index + 1, index + 1 < run.plan.size() ? run.plan.get(index + 1) : null, false);
                }
                case RETRY -> {
                    run.recoveries++;
                    run.reports.add("Повтор шага: " + retryNote(verdict));
                    yield step(run, index, verdict.retry(), true);
                }
                case STOP -> {
                    boolean cancelledStop = "cancelled".equals(verdict.reason());
                    // A failed step that ran ahead must not drop the batch: it is collected first and the stop line stays the last one.
                    CompletableFuture<Boolean> settled = run.pending != null && !cancelledStop && !port.cancelled()
                            ? settle(run, true) : CompletableFuture.completedFuture(true);
                    yield settled.thenApply(unused -> {
                        // Stopped by something else than the plan's own cancel: nobody collects the batch for this report, so it names it.
                        if (run.pending != null && cancelledStop && !port.cancelled()) leftInFurnace(run);
                        run.cancelled |= cancelledStop;
                        run.reports.add("План остановлен: " + result.message());
                        return index + 1;
                    });
                }
                case PARK -> throw new IllegalStateException("A parked attempt is collected before it is judged.");
            };
        });
    }

    private static void leftInFurnace(Run run) {
        Pending pending = run.pending;
        run.pending = null;
        String placed = pending.parked.result().message();
        int cut = placed.indexOf(". ");
        run.reports.set(pending.slot, (cut < 0 ? placed : placed.substring(0, cut + 1)) + " Продукт остался в печи.");
    }

    private static boolean runsAhead(PlannedAction step, String batchInput) {
        return step != null && RUN_AHEAD.contains(step.type())
                && !CompanionParkHold.held(List.of(step), List.of(new CompanionParkHold.Parked(batchInput, "")));
    }

    private static boolean settleFirst(Run run, int index, PlannedAction action) {
        if (run.pending == null || run.port.cancelled()) return false;
        if (index >= run.plan.size() && !run.replan) return true;
        return action == null || !runsAhead(action, run.pending.parked.result().evidence().item());
    }

    /**
     * Waits for the batch parked by an earlier step, collects it and writes that step's report into the slot reserved for it.
     * Completes with false when the collection stopped the plan; {@code planStopped} keeps the stop line to the one the caller adds.
     */
    private static CompletableFuture<Boolean> settle(Run run, boolean planStopped) {
        Pending pending = run.pending;
        run.pending = null;
        Port port = run.port;
        return collectLoop(run, pending.parked, 0).thenCompose(collected -> port.state(pending.action).thenApply(now -> {
            CompanionActionResult result = collected.result();
            Verdict verdict = decide(pending.action, collected, pending.before, now, List.of(), false, run.recoveries, port.cancelled());
            boolean goesOn = verdict.decision() == Decision.CONTINUE;
            CompanionDiagnostics.event(port.trace(), "step_result", "step", pending.index + 1, "action", pending.action, "success", result.success(),
                    "continuePlan", goesOn, "decision", verdict.decision(), "reason", verdict.reason(), "status", result.status(),
                    "message", result.message(), "progressBefore", pending.before.progress(), "progressAfter", now.progress(), "completionCheck", "engine_result");
            run.reports.set(pending.slot, goesOn || planStopped ? result.message() : "План остановлен: " + result.message());
            if (!goesOn && "cancelled".equals(verdict.reason())) run.cancelled = true;
            return goesOn;
        }));
    }

    /**
     * Hands the parked batch to the queue and collects it when it is ready. A collection that parks again (the next kind of a
     * mixed smelt) is collected in turn; the batch stays with the task whenever the collection fails.
     */
    private static CompletableFuture<Attempt> collectLoop(Run run, Attempt parked, int reparks) {
        Port port = run.port;
        long batch = parked.result().evidence() == null ? 0 : parked.result().evidence().workId();
        if (reparks >= MAX_REPARKS || batch <= 0)
            return CompletableFuture.completedFuture(new Attempt(CompanionActionResult.failed("Не удалось забрать плавку из печи."), true));
        PlannedAction collect = new PlannedAction("collect_smelt", 0, 1, String.valueOf(batch));
        return port.park(batch).thenCompose(unused -> idle(port, collect, IDLE_WAIT_SECONDS))
                .thenCompose(unused -> port.run(collect))
                .thenCompose(attempt -> attempt.started() ? CompletableFuture.completedFuture(attempt)
                        : idle(port, collect, IDLE_WAIT_SECONDS).thenCompose(unused -> port.run(collect)))
                .thenCompose(attempt -> {
                    // started is true even for a refusal, or the whole smelt_items step would be repeated as «companion busy».
                    if (!attempt.started()) return CompletableFuture.completedFuture(new Attempt(attempt.result(), true));
                    // A cancelled collection stays with the task: its end releases the batch and names the furnaces.
                    if (attempt.result().status() == Status.CANCELLED) return CompletableFuture.completedFuture(new Attempt(attempt.result(), true));
                    port.collected(batch);
                    if (attempt.result().status() != Status.PARKED) return CompletableFuture.completedFuture(new Attempt(attempt.result(), true));
                    if (attempt.result().evidence() != null) port.own(attempt.result().evidence().workId());
                    return collectLoop(run, attempt, reparks + 1);
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof CancellationException) return new Attempt(CompanionActionResult.cancelled("План отменён."), true);
                    throw error instanceof CompletionException wrapped ? wrapped : new CompletionException(error);
                });
    }

    /** Plans the running obtain_item again from the bag as it is now and puts the new steps in place of the unfinished ones. */
    private static CompletableFuture<Integer> replan(Run run, int index) {
        Port port = run.port;
        run.replan = false;
        List<PlannedAction> before = run.before;
        boolean failed = run.lastFailed;
        String message = run.lastMessage;
        run.before = null;
        return port.expand(run.obtain).thenCompose(expansion -> {
            CompanionDiagnostics.event(port.trace(), "obtain_replanned", "step", index + 1, "action", run.obtain, "steps", expansion.steps(), "refusal", expansion.refusal());
            if (expansion.refusal() != null) return giveUpObtain(run, index, expansion.refusal());
            // The same steps as before the one that just ran: it gained nothing, success or not. One such step may be chance (a cow that
            // dropped no leather); the same twice in a row is given up, with the failure's own message or, if both succeeded, a reason of ours.
            if (before != null && before.equals(expansion.steps())) {
                if (failed) run.noGainFailure = message;
                if (++run.noGain >= NO_GAIN_LIMIT) return giveUpObtain(run, index, run.noGainFailure != null ? run.noGainFailure : "Два раза подряд ничего не прибавилось.");
            } else { run.noGain = 0; run.noGainFailure = null; }
            if (run.obtainSteps >= MAX_OBTAIN_STEPS)
                return giveUpObtain(run, index, failed ? message : "слишком много шагов ради одного предмета.");
            run.plan.subList(Math.min(index, run.plan.size()), Math.min(run.obtainEnd, run.plan.size())).clear();
            run.plan.addAll(index, expansion.steps());
            run.obtainEnd = index + expansion.steps().size();
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
        boolean earlier = !run.dropped.isEmpty();
        run.dropped.add(obtain);
        run.dropped.addAll(same);
        boolean goesOn = from < run.plan.size();
        String name = obtain.item() == null ? "" : "«" + CompanionDialogue.itemName(obtain.item()) + "»: ";
        // The last part of a plan that gave parts up before is named too (the stop line follows with the same reason, said once by CompanionDialogue).
        if (!goesOn && earlier) run.reports.add(CompanionDialogue.PARTIAL_PREFIX + name + reason);
        run.reports.add((goesOn ? CompanionDialogue.PARTIAL_PREFIX + name : "План остановлен: ") + reason);
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

    /** A smelt step that parked: its attempt, and the report slot kept so the report stays in the order of the plan. */
    private record Pending(Attempt parked, PlannedAction action, State before, int slot, int index) { }

    private static final class Run {
        final List<PlannedAction> plan;
        final Port port;
        final List<String> reports = new ArrayList<>();
        /** Given-up obtain_item orders and the later steps skipped with them. */
        final List<PlannedAction> dropped = new ArrayList<>();
        int recoveries;
        boolean cancelled;
        /** The obtain_item being run, where its unfinished steps end in the plan, and what the replan after the last step must know. */
        PlannedAction obtain;
        /** The unfinished steps before the step that ran last, whether it failed, and its message. */
        List<PlannedAction> before;
        boolean lastFailed;
        String lastMessage;
        int obtainEnd, obtainSteps, noGain;
        /** The last failure message among the no-gain steps in a row, if any of them failed. */
        String noGainFailure;
        boolean replan;
        /** The parked batch that is still cooking while the next independent steps run. */
        Pending pending;
        Run(List<PlannedAction> plan, Port port) { this.plan = new ArrayList<>(plan); this.port = port; }

        Outcome outcome(int stoppedAt) {
            List<PlannedAction> skipped = new ArrayList<>(plan.subList(Math.min(stoppedAt, plan.size()), plan.size()));
            skipped.addAll(dropped);
            if (cancelled) skipped.clear();
            List<String> lines = new ArrayList<>(reports.stream().filter(Objects::nonNull).toList());
            if (!skipped.isEmpty()) CompanionDiagnostics.event(port.trace(), "step_skipped", "actions", skipped, "reason", "previous_step_failed");
            if (!skipped.isEmpty()) lines.add(CompanionDialogue.SKIPPED_PREFIX + CompanionDialogue.describeAll(skipped));
            return new Outcome(String.join("\n", lines), List.copyOf(skipped), recoveries);
        }
    }
}
