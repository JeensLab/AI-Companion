package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionAgendaData.Goal;
import dev.aicompanion.entity.CompanionMilestones.Kind;
import dev.aicompanion.entity.CompanionMilestones.Milestone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Long goals («сделай себе железную броню», «убей дракона»): one active and one deferred per player, kept in
 * {@link CompanionAgendaData}. Once a second the next unmet milestone is chosen from the facts and its short plan runs through
 * the ordinary {@link CompanionPlanRunner} on a goal port, with the same repeats, replanning and honest stops as a request
 * of the player; its steps are his own work, never orders of the player.
 * Reflexes come first (a fight pauses the goal), then the player: any request of his interrupts the goal, and a lease keeps
 * the goal out from between the steps of his plan. His own deeds come only when neither is there. Server thread only, except
 * {@link #lease}, which the player's port calls from its own thread (a concurrent map).
 */
public final class CompanionAgenda {
    public enum State {
        ACTIVE, INTERRUPTED, HELD, PAUSED_DANGER, BLOCKED, COMPLETED, CANCELLED;
        public boolean outstanding() { return this != COMPLETED && this != CANCELLED; }
        /** The goal works or is about to; held or blocked it leaves his own deeds and orphan batches free. */
        public boolean suppressesDeeds() { return this == ACTIVE || this == INTERRUPTED || this == PAUSED_DANGER; }
    }

    static final int MAX_ATTEMPTS = 12, MAX_FAILURES = 3, MAX_DEATHS = 2, FALLBACK_FAILURES = 2;
    private static final int SAFE_TICKS = 100, LEASE_TICKS = 100;
    /** A fight or a fire that does not let go for this long stops the goal: waiting longer would not change anything. */
    private static final int PAUSE_LIMIT_TICKS = 20 * 60;
    /** Working time without a single milestone met: the goal stops and says so rather than going round in circles. */
    private static final long STALL_TICKS = 20L * 60 * 60;
    /**
     * A goal that waits for the player's batch or his furnace says so once, and stops after a wait that long: the usual wait clears
     * by itself within minutes, so only a stuck one reaches the limit.
     */
    private static final long IDLE_SAY_TICKS = 20L * 60 * 5, IDLE_BLOCK_TICKS = 20L * 60 * 30;
    /** The things of a dead companion are searched this far round his death place, and fetched from no farther than a trip he can make. */
    private static final int RECOVER_RADIUS = 8, RECOVER_RANGE = 96, LAVA_RADIUS = 2, VOID_MARGIN = 4;
    private static final String COLLECT = "collect";
    /** Requests that leave the goal as it is: goals themselves, the bag check, the mode of his own deeds. */
    private static final Set<String> NEUTRAL = Set.of("pursue_goal", "verify_item", "autonomy");
    /** A request of only these (follow, stay, guard…) holds the goal until the player says to go on. */
    public static final Set<String> MODES = Set.of("follow", "wait", "come", "protect", "stop_protect", "stop", "wander");
    private static final Map<UUID, Integer> LEASES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    /** A run of one milestone's plan, bound to the companion entity that started it: another entity means a death or a reload. */
    private static final class Run {
        final UUID goal, companion;
        final int entity;
        final String milestone;
        /** What the milestone looked like when the run began; null for the collection of a batch. */
        final List<Integer> progress;
        volatile boolean cancelled;
        CompanionServerPlanPort port;
        Run(UUID goal, UUID companion, int entity, String milestone, List<Integer> progress) {
            this.goal = goal; this.companion = companion; this.entity = entity; this.milestone = milestone; this.progress = progress;
        }
    }

    private CompanionAgenda() { }

    // ---- The player's side ----

    public static CompanionActionResult accept(ServerPlayer player, CompanionEntity c, String id, int tier) {
        var book = CompanionPlaybooks.of(id, tier);
        if (book == null) return CompanionActionResult.failed("Такой большой цели я не знаю.");
        if (c.goals().outstanding()) {
            // A «стоп» leaves the pickaxe order paused for good: without this hint the player has no way out of the refusal.
            String way = c.goals().state() == CompanionGoals.State.PAUSED_PLAYER ? " Скажи «отмени цель», если кирки больше не нужны." : "";
            return CompanionActionResult.blocked("Сначала закончу заказ на кирки, потом возьмусь за «" + book.title() + "»." + way);
        }
        UUID owner = player.getUUID();
        CompanionAgendaData data = CompanionAgendaData.get(player.server);
        Goal current = data.active(owner);
        if (current != null && current.state.outstanding() && current.playbook.equals(book.id()) && current.tier == book.tier())
            return current.state == State.HELD || current.state == State.BLOCKED ? resume(player, c, false) : CompanionActionResult.ok(statusText(data, owner, c));
        String note = "";
        if (current != null && current.state.outstanding()) {
            cancelRun(owner, c);
            current.state = State.HELD; current.reason = "Отложена ради новой цели.";
            Goal dropped = data.deferred(owner);
            data.setDeferred(owner, current);
            note = " Цель «" + current.book().title() + "» отложил" + (dropped == null ? "" : ", а про «" + dropped.book().title() + "» больше не помню") + ".";
        }
        Goal goal = new Goal(UUID.randomUUID(), book.id(), book.tier());
        goal.companion = c.getUUID();
        data.setActive(owner, goal);
        c.noteDeed("Взял долгую цель: " + book.title() + ".", true);
        CompanionDiagnostics.event(c.diagnosticTrace(), "goal_accepted", "goal", book.id(), "tier", book.tier(), "id", goal.id);
        return CompanionActionResult.accepted(book.summary(CompanionFacts.of(c), false) + note);
    }

    /**
     * Any order of the player that is not about goals: the goal stops at once. A request with real work interrupts it (it comes
     * back by itself when the request is over); a request of only modes, or one of the stop, follow, stay or guard commands, holds
     * it until «продолжай». The class comes from the whole request when the queue's port told it, else from the action.
     */
    public static void playerOrder(ServerPlayer player, CompanionEntity c, String action) {
        UUID owner = player.getUUID();
        Boolean requestHolds = REQUESTS.remove(owner);
        if (NEUTRAL.contains(action) || action.startsWith("goal_")) return;
        lease(player.server, owner);
        interrupt(player.server, owner, c, requestHolds != null ? requestHolds : MODES.contains(action), action);
    }

    /** The queue's port made its first call, before any order reached the server: the goal's step must not run on beside the request. */
    public static void requestStarted(ServerPlayer player, CompanionEntity c, List<String> types) {
        Boolean holds = holds(types);
        if (holds != null) interrupt(player.server, player.getUUID(), c, holds, "request");
    }

    private static void interrupt(MinecraftServer server, UUID owner, CompanionEntity c, boolean holds, String by) {
        CompanionAgendaData data = CompanionAgendaData.get(server);
        Goal goal = data.active(owner);
        if (goal == null || !goal.state.suppressesDeeds()) return;
        cancelRun(owner, c);
        State next = holds ? State.HELD : State.INTERRUPTED;
        if (goal.state == next) return;
        goal.state = next;
        goal.reason = holds ? "Жду: скажи «продолжай», и вернусь к цели." : "";
        data.setDirty();
        CompanionDiagnostics.event(c.diagnosticTrace(), "goal_interrupted", "goal", goal.playbook, "by", by, "state", next);
    }

    /** The queue's port, on the server thread right before it dispatches a step: the class of the whole request. */
    public static void request(UUID owner, List<String> types) {
        Boolean holds = holds(types);
        if (holds == null) REQUESTS.remove(owner); else REQUESTS.put(owner, holds);
    }

    /** True: only modes; false: some real work; null: nothing that touches the goal. */
    static Boolean holds(List<String> types) {
        boolean mode = false;
        for (String type : types) {
            if (NEUTRAL.contains(type) || type.startsWith("goal_")) continue;
            if (!MODES.contains(type)) return false;
            mode = true;
        }
        return mode ? Boolean.TRUE : null;
    }

    /**
     * The player's request is running: the goal takes no step until this lapses. Renewed by every move of the request. A parked smelt
     * makes no moves, so the lease lapses and the goal may work while that batch cooks, on purpose: a cooked batch stops new goal steps
     * (readyForQueue) and the player's collection cancels a step that runs, so nothing is lost.
     */
    public static void lease(MinecraftServer server, UUID owner) {
        if (server != null && owner != null) LEASES.put(owner, server.getTickCount() + LEASE_TICKS);
    }

    private static boolean leased(MinecraftServer server, UUID owner) {
        Integer until = LEASES.get(owner);
        return until != null && server.getTickCount() < until;
    }

    /** «Продолжай»: the held, interrupted or blocked goal goes on, or the deferred one comes back. Null: no long goal, or the pickaxe order resumes first. */
    public static CompanionActionResult resume(ServerPlayer player, CompanionEntity c) { return resume(player, c, true); }

    /** {@code explicit}: the player said «продолжай»; a goal the model asks for again must not get fresh attempts from that. */
    private static CompanionActionResult resume(ServerPlayer player, CompanionEntity c, boolean explicit) {
        UUID owner = player.getUUID();
        CompanionAgendaData data = CompanionAgendaData.get(player.server);
        Goal goal = data.active(owner);
        boolean fromDeferred = goal == null || !goal.state.outstanding();
        Goal candidate = fromDeferred ? data.deferred(owner) : goal;
        // An outstanding pickaxe order owns «продолжай»: the long goal stays as it is until that order ends.
        if (candidate == null || c.goals().outstanding()) return null;
        if (fromDeferred) {
            data.setDeferred(owner, null);
            data.setActive(owner, candidate);
        }
        goal = candidate;
        String title = goal.book().title();
        if (goal.state == State.ACTIVE || goal.state == State.PAUSED_DANGER) return CompanionActionResult.ok(statusText(data, owner, c));
        // An explicit «продолжай» is a fresh start, whatever state the goal was in (a deferred one comes back HELD even if it was blocked):
        // the player has seen the reason and says to go on.
        if (explicit) {
            Milestone next = pick(goal, CompanionFacts.of(c), c);
            if (next != null) { int[] counter = goal.counter(next.key()); counter[0] = 0; counter[1] = 0; }
        }
        if (explicit || goal.state == State.BLOCKED) {
            goal.counters.values().forEach(counter -> counter[1] = 0);
            goal.workTicks = 0; goal.deaths = 0;
        }
        goal.state = State.ACTIVE; goal.reason = ""; goal.pausedTicks = 0; goal.idleTicks = 0; goal.idleSaid = false;
        data.setDirty();
        return CompanionActionResult.accepted("Продолжаю: " + title + ".");
    }

    /**
     * «Отложи цель»: the long goal steps aside into the one deferred slot, or the pickaxe order is parked. A second goal is never
     * pushed out of that slot by this: the player says «отмени цель» if the first one is not needed.
     */
    public static CompanionActionResult defer(ServerPlayer player, CompanionEntity c) {
        UUID owner = player.getUUID();
        CompanionAgendaData data = CompanionAgendaData.get(player.server);
        Goal goal = data.active(owner), deferred = data.deferred(owner);
        if (goal != null && goal.state.outstanding()) {
            if (deferred != null)
                return CompanionActionResult.failed("Уже отложена цель «" + deferred.book().title() + "»; вторую отложить не могу. Скажи «отмени цель», если эта не нужна.");
            cancelRun(owner, c);
            goal.state = State.HELD; goal.reason = "Отложена по твоей просьбе.";
            data.setDeferred(owner, goal);
            data.setActive(owner, null);
            c.noteDeed("Отложил долгую цель: " + goal.book().title() + ".", true);
            return CompanionActionResult.ok("Отложил цель «" + goal.book().title() + "». Скажи «продолжай», когда вернуться к ней.");
        }
        if (!c.goals().outstanding()) return CompanionActionResult.failed("Нет цели, которую можно отложить.");
        c.goals().playerOrder("stop");
        return CompanionActionResult.ok("Отложил заказ на кирки. Скажи «продолжай работу», когда вернуться к нему.");
    }

    /** «Отмени цель»: only an explicit cancel ends a long goal; with none working, the deferred one goes. Null: no long goal to cancel. */
    public static CompanionActionResult cancel(ServerPlayer player, CompanionEntity c) {
        UUID owner = player.getUUID();
        CompanionAgendaData data = CompanionAgendaData.get(player.server);
        Goal goal = data.active(owner);
        if (goal == null || !goal.state.outstanding()) {
            Goal deferred = data.deferred(owner);
            // A working long goal is cancelled first; with only a deferred one left, an outstanding pickaxe order takes the cancel, as it takes «продолжай».
            if (deferred == null || c.goals().outstanding()) return null;
            return dropDeferred(data, owner, c, deferred);
        }
        cancelRun(owner, c);
        goal.state = State.CANCELLED; goal.reason = "Отменено игроком.";
        data.setDirty();
        c.noteDeed("Отменил долгую цель: " + goal.book().title() + ".", true);
        Goal deferred = data.deferred(owner);
        return CompanionActionResult.ok("Отменил цель «" + goal.book().title() + "»."
                + (deferred == null ? "" : " Отложенная «" + deferred.book().title() + "» ждёт: скажи «продолжай»."));
    }

    /** The screen's «Отменить цель» on the row of goal {@code id}: that goal ends, even beside an outstanding pickaxe order. Null: it is no longer his. */
    public static CompanionActionResult cancelShown(ServerPlayer player, CompanionEntity c, UUID id) {
        UUID owner = player.getUUID();
        CompanionAgendaData data = CompanionAgendaData.get(player.server);
        Goal goal = data.active(owner), deferred = data.deferred(owner);
        if (goal != null && goal.state.outstanding()) return goal.id.equals(id) ? cancel(player, c) : null;
        return deferred != null && deferred.id.equals(id) ? dropDeferred(data, owner, c, deferred) : null;
    }

    private static CompanionActionResult dropDeferred(CompanionAgendaData data, UUID owner, CompanionEntity c, Goal deferred) {
        data.setDeferred(owner, null);
        c.noteDeed("Отменил отложенную долгую цель: " + deferred.book().title() + ".", true);
        return CompanionActionResult.ok("Отменил отложенную цель «" + deferred.book().title() + "».");
    }

    /** What the goal is about, for «что делаешь» and the model. Null when the pickaxe order is the one to answer. */
    public static CompanionActionResult status(ServerPlayer player, CompanionEntity c) {
        UUID owner = player.getUUID();
        CompanionAgendaData data = CompanionAgendaData.get(player.server);
        Goal goal = data.active(owner);
        if (goal == null && data.deferred(owner) == null) return null;
        if (c.goals().outstanding() && (goal == null || !goal.state.suppressesDeeds())) return null;
        return CompanionActionResult.ok(statusText(data, owner, c));
    }

    /** The long goal works right now, so the pickaxe order waits; a held or blocked one does not stand in its way. */
    public static boolean working(ServerPlayer player) {
        Goal goal = CompanionAgendaData.get(player.server).active(player.getUUID());
        return goal != null && goal.state.suppressesDeeds();
    }

    /** His own deeds and the collection of orphans wait while the goal works; read on the server thread. */
    public static boolean suppressesDeeds(CompanionEntity c) {
        if (c.getServer() == null || c.getOwnerId() == null) return false;
        Goal goal = CompanionAgendaData.get(c.getServer()).active(c.getOwnerId());
        return goal != null && goal.state.suppressesDeeds();
    }

    public static String statusText(CompanionAgendaData data, UUID owner, CompanionEntity c) {
        Goal goal = data.active(owner), deferred = data.deferred(owner);
        if (goal == null) return deferred == null ? "Долгой цели нет." : "Отложена цель «" + deferred.book().title() + "».";
        var book = goal.book();
        CompanionFacts facts = CompanionFacts.of(c);
        Milestone next = next(goal, facts);
        String title = "«" + book.title() + "»";
        StringBuilder text = new StringBuilder(switch (goal.state) {
            case ACTIVE -> "Цель " + title + ". Сейчас: " + (next == null ? "проверяю" : next.label(goal.fallback)) + ".";
            case INTERRUPTED -> "Цель " + title + ": отвлёкся на твою просьбу, потом сам вернусь к ней.";
            case HELD -> "Цель " + title + " на паузе: скажи «продолжай».";
            case PAUSED_DANGER -> "Цель " + title + ": сначала разберусь с опасностью.";
            case BLOCKED -> "Цель " + title + " стоит: " + goal.reason;
            case COMPLETED -> "Цель " + title + " выполнена.";
            case CANCELLED -> "Цель " + title + " отменена.";
        });
        if (goal.state.outstanding()) {
            List<String> done = new java.util.ArrayList<>(), later = new java.util.ArrayList<>(), cannot = new java.util.ArrayList<>();
            for (Milestone milestone : book.milestones()) {
                if (milestone.satisfied(facts, goal.fallback, book.milestones())) done.add(milestone.label(goal.fallback));
                else if (!milestone.supported() || !cannot.isEmpty()) cannot.add(milestone.label(goal.fallback));
                else if (next == null || !milestone.key().equals(next.key())) later.add(milestone.label(goal.fallback));
            }
            if (!done.isEmpty()) text.append(" Сделано: ").append(String.join(", ", done)).append('.');
            if (!later.isEmpty()) text.append(" Дальше: ").append(String.join(", ", later)).append('.');
            if (!cannot.isEmpty()) text.append(" Пока не умею: ").append(String.join(", ", cannot)).append('.');
        }
        if (deferred != null) text.append(" Отложена цель «").append(deferred.book().title()).append("».");
        return text.toString();
    }

    // ---- The engine ----

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        CompanionAgendaData data = CompanionAgendaData.get(server);
        for (UUID owner : data.owners()) {
            Goal goal = data.active(owner);
            if (goal == null || !goal.state.suppressesDeeds()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            CompanionEntity c = player == null ? null : CompanionEvents.getOwnedCompanion(player);
            if (c == null || !c.isAlive()) continue;
            try { step(server, data, owner, goal, player, c); }
            catch (RuntimeException error) { AICompanionMod.LOGGER.warn("Long goal tick failed: {}", error.toString()); }
        }
    }

    /** The world closes: runs end without an outcome (a neutral end), and nothing of this world leaks into the next. */
    public static void stop() {
        RUNS.values().forEach(run -> run.cancelled = true);
        RUNS.clear(); LEASES.clear(); REQUESTS.clear();
    }

    private static void step(MinecraftServer server, CompanionAgendaData data, UUID owner, Goal goal, ServerPlayer player, CompanionEntity c) {
        Run run = RUNS.get(owner);
        if (goal.companion != null && !goal.companion.equals(c.getUUID())) {
            // He came back after a death: the same goal, a new companion who carries nothing. Not a failure of the milestone.
            goal.deaths++; goal.companion = c.getUUID();
            cancelRun(owner, null); run = null;
            data.setDirty();
            CompanionDiagnostics.event(c.diagnosticTrace(), "goal_companion_died", "goal", goal.playbook, "deaths", goal.deaths);
            if (goal.deaths > MAX_DEATHS) { block(data, owner, goal, c, "я погиб уже " + goal.deaths + " раза, пока шёл к цели. Слишком опасно, нужна твоя помощь."); return; }
        }
        if (goal.companion == null) { goal.companion = c.getUUID(); data.setDirty(); }
        // The same companion loaded again (a reload, a chunk): the run waits on the work of an entity that is gone.
        if (run != null && run.entity != c.getId()) { cancelRun(owner, null); run = null; }
        if (danger(c, player)) {
            if (goal.state != State.PAUSED_DANGER) {
                var enemy = c.getTarget();
                cancelRun(owner, c);
                goal.state = State.PAUSED_DANGER;
                c.setTarget(enemy);
                goal.pausedTicks = 0;
                data.setDirty();
            } else goal.pausedTicks += 20;
            goal.safeTicks = 0;
            if (goal.pausedTicks >= PAUSE_LIMIT_TICKS) blockDanger(data, owner, goal, c);
            return;
        }
        if (goal.state == State.PAUSED_DANGER) {
            goal.safeTicks += 20; goal.pausedTicks += 20;
            if (goal.pausedTicks >= PAUSE_LIMIT_TICKS) { blockDanger(data, owner, goal, c); return; }
            if (goal.safeTicks < SAFE_TICKS) return;
            goal.state = State.ACTIVE; goal.pausedTicks = 0;
            data.setDirty();
        }
        if (run != null) { goal.workTicks += 20; return; }
        if (leased(server, owner)) return;
        if (!free(c)) { waiting(data, owner, goal, c, "занят другим делом"); return; }
        if (c.batches().readyForQueue()) { waiting(data, owner, goal, c, "жду готовую партию из печи по твоей просьбе"); return; }
        if (goal.state == State.INTERRUPTED) {
            goal.state = State.ACTIVE;
            data.setDirty();
            Milestone next = next(goal, CompanionFacts.of(c));
            c.autonomySay("Возвращаюсь к цели" + (next == null ? "." : ": " + next.label(goal.fallback) + "."));
        }
        reconcile(server, data, owner, goal, c);
    }

    /** A boundary of the goal's steps: first a cooked batch nobody waits for, then the first unmet milestone from the facts. */
    private static void reconcile(MinecraftServer server, CompanionAgendaData data, UUID owner, Goal goal, CompanionEntity c) {
        ServerLevel level = c.level() instanceof ServerLevel here ? here : null;
        if (level != null && recover(data, goal, c, level)) return;
        long orphan = level == null ? -1 : c.batches().readyOrphan(level);
        if (orphan > 0) { start(server, owner, goal, c, COLLECT, null, List.of(new PlannedAction("collect_smelt", 0, 1, String.valueOf(orphan)))); return; }
        var book = goal.book();
        List<Milestone> all = book.milestones();
        CompanionFacts facts = CompanionFacts.of(c);
        int done = (int) all.stream().filter(milestone -> milestone.satisfied(facts, goal.fallback, all)).count();
        if (done != goal.done) {
            if (done > goal.done) { goal.workTicks = 0; goal.idleTicks = 0; goal.idleSaid = false; }
            goal.done = done;
            data.setDirty();
        }
        Milestone next = next(goal, facts), last = all.getLast();
        if (next == null || last.kind() == Kind.DRAGON && last.satisfied(facts, goal.fallback, all)) { complete(data, owner, goal, c); return; }
        if (!next.supported()) {
            List<String> cannot = all.subList(all.indexOf(next), all.size()).stream()
                    .filter(milestone -> !milestone.satisfied(facts, goal.fallback, all)).map(milestone -> milestone.label(goal.fallback)).toList();
            block(data, owner, goal, c, "дальше пока не умею: " + String.join(", ", cannot) + ".");
            return;
        }
        Milestone pick = pick(goal, facts, c);
        if (pick == null) { waiting(data, owner, goal, c, "жду, пока в печи доготовится партия"); return; }
        int[] counter = goal.counter(pick.key());
        if (counter[0] >= MAX_ATTEMPTS) { block(data, owner, goal, c, pick.label(goal.fallback) + " — попытки исчерпаны. " + goal.reason); return; }
        if (goal.workTicks >= STALL_TICKS) { block(data, owner, goal, c, "давно нет продвижения: " + pick.label(goal.fallback) + "."); return; }
        List<PlannedAction> plan = pick.method(facts, goal.fallback, all).stream()
                .map(step -> new PlannedAction(step.type(), 0, step.count(), step.item(), step.slot())).toList();
        if (!pick.key().equals(goal.announced)) {
            if (!goal.announced.isEmpty()) c.autonomySay("Дальше: " + pick.label(goal.fallback) + ".");
            goal.announced = pick.key();
        }
        data.setDirty();
        start(server, owner, goal, c, pick.key(), pick.progress(facts, goal.fallback, all), plan);
    }

    /** What his own furnaces are cooking is not made again: the first unmet milestone that does not wait for it, null when there is none. */
    private static Milestone pick(Goal goal, CompanionFacts facts, CompanionEntity c) {
        List<Milestone> all = goal.book().milestones();
        List<Item> cooking = c.level() instanceof ServerLevel level ? c.batches().cookingOutputs(level) : List.of();
        for (Milestone milestone : all) {
            if (milestone.satisfied(facts, goal.fallback, all)) continue;
            if (!milestone.supported()) return null;
            if (cooking.stream().anyMatch(output -> milestone.dependsOn(output, facts, goal.fallback))) continue;
            return milestone;
        }
        return null;
    }

    /** One second of a goal that cannot step: the player hears of it after five minutes, and after half an hour the goal stops and says so. */
    private static void waiting(CompanionAgendaData data, UUID owner, Goal goal, CompanionEntity c, String why) {
        goal.idleTicks += 20;
        if (goal.idleTicks >= IDLE_BLOCK_TICKS) { block(data, owner, goal, c, "давно жду: " + why + "."); return; }
        if (goal.idleTicks < IDLE_SAY_TICKS || goal.idleSaid) return;
        goal.idleSaid = true;
        c.autonomySay("Цель «" + goal.book().title() + "» пока ждёт: " + why + ".");
    }

    private static Milestone next(Goal goal, CompanionFacts facts) {
        List<Milestone> all = goal.book().milestones();
        for (Milestone milestone : all) if (!milestone.satisfied(facts, goal.fallback, all)) return milestone;
        return null;
    }

    private static void start(MinecraftServer server, UUID owner, Goal goal, CompanionEntity c, String milestone, List<Integer> progress,
                              List<PlannedAction> plan) {
        Run run = new Run(goal.id, c.getUUID(), c.getId(), milestone, progress);
        goal.idleTicks = 0; goal.idleSaid = false;
        String trace = CompanionDiagnostics.newTrace();
        run.port = CompanionServerPlanPort.forGoal(server, owner, trace, () -> run.cancelled || !server.isRunning());
        RUNS.put(owner, run);
        CompanionDiagnostics.event(trace, "goal_step_started", "goal", goal.playbook, "milestone", milestone, "plan", plan);
        CompanionPlanRunner.run(plan, run.port).whenComplete((outcome, error) -> {
            if (server.isRunning()) server.execute(() -> finished(server, owner, run, outcome, error));
        });
    }

    /**
     * The run is over; only a refusal of the mechanics counts against the milestone. A cancel, a death, a reload, a closed world
     * or a player who left are neutral: nothing is counted and the next second reconciles from the facts.
     */
    private static void finished(MinecraftServer server, UUID owner, Run run, CompanionPlanRunner.Outcome outcome, Throwable error) {
        if (RUNS.get(owner) == run) RUNS.remove(owner);
        CompanionAgendaData data = CompanionAgendaData.get(server);
        Goal goal = data.active(owner);
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        CompanionEntity c = player == null ? null : CompanionEvents.getOwnedCompanion(player);
        // Only a cancel is neutral among exceptions: a step that throws every time would otherwise start again every second.
        Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
        // A fight that ended the step before the next second paused the goal counts no more than the pause itself.
        boolean neutral = run.cancelled || cause instanceof java.util.concurrent.CancellationException || error == null && (outcome == null || outcome.cancelled())
                || goal == null || !goal.id.equals(run.goal) || goal.state != State.ACTIVE || c == null || !c.getUUID().equals(run.companion)
                || danger(c, player);
        CompanionDiagnostics.event(run.port.trace(), "goal_step_finished", "milestone", run.milestone, "neutral", neutral,
                "report", outcome == null ? "" : outcome.report(), "error", error == null ? "" : error.toString());
        if (neutral || COLLECT.equals(run.milestone)) return;
        var book = goal.book();
        Milestone milestone = book.milestone(run.milestone);
        if (milestone == null) return;
        int[] counter = goal.counter(milestone.key());
        counter[0]++;
        CompanionFacts facts = CompanionFacts.of(c);
        if (milestone.satisfied(facts, goal.fallback, book.milestones())) {
            counter[0] = 0; counter[1] = 0; goal.reason = "";
            goal.record(milestone.key(), run.port.ownedWorkId(), c.level().getGameTime(), c.getUUID());
            data.setDirty();
            return;
        }
        String why = error != null ? "сбой механики, подробности в журнале диагностики." : reason(outcome.report());
        goal.reason = why;
        data.setDirty();
        // A step that moved the bag (ingots smelted, ore mined, meat hunted) is progress, not a failure.
        boolean progressed = run.progress != null && !run.progress.equals(milestone.progress(facts, goal.fallback, book.milestones()));
        if (progressed) { counter[1] = 0; return; }
        counter[1]++;
        if (milestone.concernsDiamonds(goal.fallback) && !goal.fallback && hasFreeSlot(c)) {
            // One empty search does not end an honest hunt for diamonds; the second one in a row does.
            if (counter[1] < FALLBACK_FAILURES) return;
            goal.fallback = true; counter[1] = 0;
            c.autonomySay("Алмазов добыть не вышло: " + why + " Сделаю снаряжение из железа.");
            return;
        }
        if (counter[1] >= MAX_FAILURES) block(data, owner, goal, c, milestone.label(goal.fallback) + ": " + why);
    }

    private static boolean hasFreeSlot(CompanionEntity c) {
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return true;
        return false;
    }

    /** The last line of the report that says why, without the runner's prefixes. */
    private static String reason(String report) {
        String why = "";
        for (String line : report.split("\n")) {
            if (line.isBlank() || line.startsWith(CompanionDialogue.SKIPPED_PREFIX)) continue;
            why = line.replace("План остановлен: ", "").replace(CompanionDialogue.PARTIAL_PREFIX, "").strip();
        }
        return why.isEmpty() ? "не получилось." : why;
    }

    private static void blockDanger(CompanionAgendaData data, UUID owner, Goal goal, CompanionEntity c) {
        block(data, owner, goal, c, "опасность не отступает больше минуты, нужна твоя помощь.");
    }

    private static void block(CompanionAgendaData data, UUID owner, Goal goal, CompanionEntity c, String why) {
        cancelRun(owner, c);
        goal.state = State.BLOCKED; goal.reason = why;
        data.setDirty();
        String title = goal.book().title();
        c.noteDeed("Долгая цель «" + title + "» стоит: " + why, false);
        c.autonomySay("С целью «" + title + "» загвоздка: " + why + " Скажи «продолжай», когда можно будет.");
        CompanionDiagnostics.event(c.diagnosticTrace(), "goal_blocked", "goal", goal.playbook, "reason", why);
    }

    private static void complete(CompanionAgendaData data, UUID owner, Goal goal, CompanionEntity c) {
        goal.state = State.COMPLETED; goal.reason = "";
        data.setDirty();
        String title = goal.book().title();
        Goal deferred = data.deferred(owner);
        c.noteDeed("Долгая цель выполнена: " + title + ".", true);
        c.autonomySay("Готово: " + title + "." + (deferred == null ? "" : " Вернуться к цели «" + deferred.book().title() + "»? Скажи «продолжай»."));
        CompanionDiagnostics.event(c.diagnosticTrace(), "goal_completed", "goal", goal.playbook);
    }

    /** The companion died with things on him (they fell tagged as the owner's): the goal remembers where, for the one try to fetch them. */
    public static void died(MinecraftServer server, UUID owner, ResourceKey<Level> dimension, BlockPos place) {
        CompanionAgendaData data = CompanionAgendaData.get(server);
        Goal goal = data.active(owner);
        if (goal == null || !goal.state.outstanding()) return;
        goal.deathPlace = place.immutable(); goal.deathDimension = dimension;
        data.setDirty();
    }

    /**
     * A boundary of the goal's steps after a death. One try per death place, so neither a reload nor a second death can make the goal
     * wait for them: his things within reach on a safe spot go on his pickup list (he walks to them and takes them up like his own
     * harvest; nothing is made anew), anything else is said once, with the place. True: the pickup begins and the goal waits for it.
     */
    private static boolean recover(CompanionAgendaData data, Goal goal, CompanionEntity c, ServerLevel level) {
        BlockPos place = goal.deathPlace;
        if (place == null) return false;
        UUID owner = c.getOwnerId();
        String why = !level.dimension().equals(goal.deathDimension) ? "это другое измерение"
                : !level.isLoaded(place) ? "туда не дойти, место не загружено"
                : c.blockPosition().distSqr(place) > RECOVER_RANGE * RECOVER_RANGE ? "до них слишком далеко"
                : unsafe(level, place) ? "там лава или пустота" : null;
        goal.deathPlace = null; goal.deathDimension = null;
        data.setDirty();
        List<ItemEntity> mine = why != null ? List.of() : level.getEntitiesOfClass(ItemEntity.class, new AABB(place).inflate(RECOVER_RADIUS),
                item -> item.isAlive() && item.getPersistentData().hasUUID(CompanionBag.BELONGINGS)
                        && item.getPersistentData().getUUID(CompanionBag.BELONGINGS).equals(owner));
        CompanionDiagnostics.event(c.diagnosticTrace(), "goal_belongings", "goal", goal.playbook, "place", place.toShortString(), "found", mine.size(), "why", why == null ? "" : why);
        if (mine.isEmpty()) {
            c.autonomy().report("Мои вещи остались там, где я погиб (" + place.toShortString() + "): " + (why == null ? "я их там уже не нашёл" : why) + ".");
            return false;
        }
        c.loot.claim(mine);
        c.autonomy().report("Иду за своими вещами, что выпали на " + place.toShortString() + ".");
        return true;
    }

    private static boolean unsafe(ServerLevel level, BlockPos place) {
        if (place.getY() < level.getMinBuildHeight() + VOID_MARGIN) return true;
        for (BlockPos pos : BlockPos.betweenClosed(place.offset(-LAVA_RADIUS, -LAVA_RADIUS, -LAVA_RADIUS), place.offset(LAVA_RADIUS, 1, LAVA_RADIUS)))
            if (level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.LAVA)) return true;
        return false;
    }

    /** Ends the current run; its own work stops only if it is still the active one, so a newer order is never cut. */
    private static void cancelRun(UUID owner, CompanionEntity c) {
        Run run = RUNS.remove(owner);
        if (run == null) return;
        run.cancelled = true;
        long work = run.port == null ? 0 : run.port.ownedWorkId();
        if (c != null && work > 0 && c.activeWorkId() == work) c.stopGathering();
    }

    private static boolean free(CompanionEntity c) {
        return !c.hasActiveWorkOrder() && !c.isGatheringTask() && !c.hasPendingLoot() && c.canWorkNow() && !c.isSelfRescuing()
                && c.actionAnimation() == 0 && !c.autonomy().running();
    }

    private static boolean danger(CompanionEntity c, ServerPlayer player) {
        return c.getTarget() instanceof Enemy && c.getTarget().isAlive() || c.isOnFire() || c.isInLava() || c.getHealth() <= 6 && CompanionEating.hasFood(c) || player.hurtTime > 0;
    }

    /** For GameTests: the goal of the player as the engine sees it. */
    public static Goal goal(MinecraftServer server, UUID owner) { return CompanionAgendaData.get(server).active(owner); }

    /** For GameTests: whether a step of the goal is running for the player. */
    public static boolean running(UUID owner) { return RUNS.containsKey(owner); }
}
