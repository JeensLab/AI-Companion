package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Толик's chat without the network: a phrase goes the way it goes in the game. A short order is a local command
 * (CompanionLocalCommands); anything else gets the plan a model would send for it (given here as the tool call's steps), which is
 * decoded by CompanionPlanCodec, amended by CompanionPlanRequirements and run by CompanionPlanRunner through
 * CompanionServerPlanPort. Plans queue one after another, and «стоп», «иди за мной» or a plan that starts with a priority order
 * cancel the ones before it, as CompanionActionExecutor does.
 */
final class MarathonChat {
    private final GameTestHelper h;
    private CompanionEntity oleg;
    private final AtomicLong generation = new AtomicLong();
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);
    /** Every request: what was said, the plan that ran, and the report once it is done. */
    final List<Said> said = new ArrayList<>();

    record Said(String text, List<PlannedAction> plan, CompletableFuture<CompanionPlanRunner.Outcome> outcome) {
        boolean done() { return outcome.isDone(); }
        String report() { return !outcome.isDone() ? "(running)" : outcome.isCompletedExceptionally() ? "(failed: " + outcome.handle((o, e) -> e).join() + ")" : outcome.join().report(); }
    }

    MarathonChat(GameTestHelper h, CompanionEntity oleg) { this.h = h; this.oleg = oleg; }

    /** A short order: the local command it is in the game. Fails the test if the phrase would go to the model. */
    Said say(String text) {
        String command = CompanionLocalCommands.route(text, "Олег");
        h.assertTrue(command != null, "«" + text + "» is no local command; give the model's plan");
        if (CompanionLocalCommands.cancelsPlans(command)) generation.incrementAndGet();
        return run(text, List.of(CompanionLocalCommands.action(command)));
    }

    /** A request for the model, with the steps it would send in queue_companion_plan. */
    Said say(String text, String modelSteps) {
        h.assertTrue(CompanionLocalCommands.route(text, "Олег") == null, "«" + text + "» is a local command in the game");
        List<PlannedAction> plan = CompanionPlanRequirements.apply(text, CompanionPlanCodec.decode(toolCall(modelSteps))).actions();
        if (CompanionLocalCommands.priorityOrder(plan)) generation.incrementAndGet();
        return run(text, plan);
    }

    private Said run(String text, List<PlannedAction> plan) {
        long mine = generation.get();
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), oleg.getOwnerId(), "marathon-" + said.size(), () -> mine != generation.get());
        CompletableFuture<CompanionPlanRunner.Outcome> outcome = queue.handle((unused, error) -> null).thenCompose(unused -> CompanionPlanRunner.run(plan, port));
        queue = outcome.handle((unused, error) -> null);
        Said entry = new Said(text, plan, outcome);
        said.add(entry);
        return entry;
    }

    /** The world closed: every plan of it stops, as CompanionServerPlanPort.stop and a new generation do in the game. */
    void worldClosed() { generation.incrementAndGet(); }
    void watch(CompanionEntity reloaded) { oleg = reloaded; }

    boolean idle() { return said.stream().allMatch(Said::done); }

    String history() {
        StringBuilder b = new StringBuilder();
        for (Said entry : said) b.append("«").append(entry.text()).append("» ").append(entry.plan()).append(" → ").append(entry.report().replace('\n', ' ')).append(" ;; ");
        return b.toString();
    }

    private static JsonObject toolCall(String steps) {
        JsonObject function = new JsonObject();
        function.addProperty("name", "queue_companion_plan");
        function.addProperty("arguments", "{\"steps\":" + steps + "}");
        JsonObject call = new JsonObject();
        call.add("function", function);
        JsonArray calls = new JsonArray();
        calls.add(call);
        JsonObject message = new JsonObject();
        message.addProperty("content", "Сейчас сделаю.");
        message.add("tool_calls", calls);
        return message;
    }
}
