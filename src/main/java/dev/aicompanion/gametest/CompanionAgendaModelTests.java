package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The model's side of long goals: what the codec lets through as a goal, and what the player hears when a big goal is taken. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaModelTests {
    @AfterBatch(batch = "agenda_model_dragon") public static void afterDragon(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Only a catalog id with a level of 1 or 2 is a goal, and a goal is the whole plan: anything else is invalid and nothing runs. */
    @GameTest(template = "tall_tree", batch = "agenda_model_codec", timeoutTicks = 100)
    public static void pursueGoalDecodesOnlyCatalogIdsAsASingleStep(GameTestHelper h) {
        var tiered = CompanionPlanCodec.parsePlan(JsonParser.parseString(plan(goal("gear_up", ",\"count\":2"))));
        h.assertTrue(tiered.equals(List.of(new PlannedAction("pursue_goal", 0, 2, "gear_up", "auto", "near_player"))), "Tier 2 goal decoded as " + tiered);
        for (String id : CompanionPlaybooks.IDS) {
            var steps = CompanionPlanCodec.parsePlan(JsonParser.parseString("{\"steps\":" + plan(goal(id, "")) + "}"));
            h.assertTrue(steps.equals(List.of(new PlannedAction("pursue_goal", 0, 1, id, "auto", "near_player"))), "Goal " + id + " decoded as " + steps);
        }
        String follow = "{\"action\":\"follow\"}";
        String[] refused = {plan(goal("no_such_goal", "")), plan(goal("minecraft:iron_ingot", "")), plan(goal("", "")), plan("{\"action\":\"pursue_goal\"}"),
                plan(goal("gear_up", ",\"count\":3")), plan(goal("gear_up", ",\"count\":0")), plan(goal("gear_up", ",\"count\":-1")),
                plan(goal("gear_up", ""), follow), plan(follow, goal("gear_up", "")), plan(goal("gear_up", ""), goal("prepare_nether", "")),
                plan("{\"action\":\"goal_cancel\"}"), plan("{\"action\":\"goal_defer\"}"), plan("{\"action\":\"goal_resume\"}"), plan("{\"action\":\"goal_status\"}")};
        for (String json : refused) h.assertTrue(rejects(() -> CompanionPlanCodec.parsePlan(JsonParser.parseString(json))), "A plan was decoded: " + json);

        var one = CompanionPlanCodec.decode(message(plan(goal("gear_up", ""))));
        h.assertTrue(one.actions().size() == 1 && one.actions().getFirst().type().equals("pursue_goal"), "The tool call lost its goal: " + one.actions());
        h.assertTrue(!one.text().isBlank() && !one.text().equals("Окей."), "The goal has no word of its own to be acknowledged with: " + one.text());
        h.assertTrue(CompanionDialogue.describe(one.actions().getFirst()).contains("железное снаряжение"), "The task list does not name the goal: " + CompanionDialogue.describe(one.actions().getFirst()));
        h.assertTrue(rejects(() -> CompanionPlanCodec.decode(message(plan(goal("gear_up", "")), plan(follow)))), "A goal beside the steps of another tool call was decoded");
        h.assertTrue(rejects(() -> CompanionPlanCodec.decode(message(plan(goal("gear_up", "")), plan(goal("gear_up", ""))))), "Two goals in two tool calls were decoded");
        h.assertTrue(CompanionPlanRequirements.apply("сделай железную броню и поставь сундук", one).actions().equals(one.actions()), "The request's wording added steps to the goal");
        h.succeed();
    }

    /**
     * A plan with the dragon goal given through the queue is accepted, and the player hears what he will do and what he cannot yet;
     * the goal lives on the server, so the queue task closes on the acceptance.
     */
    @GameTest(template = "tall_tree", batch = "agenda_model_dragon", timeoutTicks = 600)
    public static void dragonGoalAcceptsPrefixAndNamesMissingSkills(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        var server = h.getLevel().getServer();
        UUID owner = p.getUUID();
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(plan(goal("defeat_ender_dragon", ""))));
        List<CompanionActionResult> results = new ArrayList<>();
        var queue = new CompanionTaskQueue();
        queue.submit(plan, task -> {
            var port = recording(new CompanionServerPlanPort(server, owner, "agenda-model-dragon", task::cancelled), results);
            return CompanionPlanRunner.run(plan, port).thenApply(outcome -> new CompanionTaskQueue.Result(outcome.report(), true));
        });
        h.onEachTick(queue::tick);
        h.succeedWhen(() -> {
            var views = queue.snapshot();
            h.assertTrue(views.size() == 1 && views.getFirst().status() == CompanionTaskQueue.Status.COMPLETED, "The task did not close on the acceptance: " + views);
            h.assertTrue(results.size() == 1 && results.getFirst().status() == CompanionActionResult.Status.ACCEPTED, "The goal was not accepted: " + results);
            String said = results.getFirst().message();
            h.assertTrue(said.contains("Сделаю:") && said.contains("Начинаю."), "He does not say what he will do: " + said);
            int cannot = said.indexOf("Дальше пока не умею:");
            h.assertTrue(cannot > 0, "He does not say what he cannot yet: " + said);
            String spoken = CompanionDialogue.summarize(plan, views.getFirst().report());
            var book = CompanionPlaybooks.of("defeat_ender_dragon", 1);
            int missing = 0;
            for (var milestone : book.milestones()) {
                if (milestone.supported()) continue;
                missing++;
                h.assertTrue(said.indexOf(milestone.label(false), cannot) > 0, "«" + milestone.label(false) + "» is not among the missing skills: " + said);
                h.assertTrue(spoken.contains(milestone.label(false)), "The spoken summary lost «" + milestone.label(false) + "»: " + spoken);
            }
            h.assertTrue(missing > 0, "The dragon goal has no missing skill to name");
            var goal = CompanionAgenda.goal(server, owner);
            h.assertTrue(goal != null && goal.playbook().equals("defeat_ender_dragon") && goal.state().outstanding(), "The agenda does not hold the goal: " + goal);
            h.assertTrue(CompanionEvents.executeAction(p, "goal_cancel", "", 1).success(), "The goal was not cancelled at the end");
        });
    }

    private static String goal(String item, String more) { return "{\"action\":\"pursue_goal\",\"item\":\"" + item + "\"" + more + "}"; }

    private static String plan(String... steps) { return "[" + String.join(",", steps) + "]"; }

    /** A model message with one queue_companion_plan tool call per plan. */
    private static JsonObject message(String... plans) {
        JsonArray calls = new JsonArray();
        for (String steps : plans) {
            JsonObject function = new JsonObject();
            function.addProperty("name", "queue_companion_plan");
            function.addProperty("arguments", "{\"steps\":" + steps + "}");
            JsonObject call = new JsonObject();
            call.add("function", function);
            calls.add(call);
        }
        JsonObject message = new JsonObject();
        message.addProperty("content", "Берусь.");
        message.add("tool_calls", calls);
        return message;
    }

    private static boolean rejects(Runnable decode) {
        try { decode.run(); return false; }
        catch (IllegalStateException invalid) { return true; }
    }

    /** The port as the queue runs it, with every result of its dispatches kept: the runner reports only text. */
    @SuppressWarnings("unchecked")
    private static CompanionPlanRunner.Port recording(CompanionPlanRunner.Port port, List<CompanionActionResult> results) {
        return (CompanionPlanRunner.Port) Proxy.newProxyInstance(CompanionPlanRunner.Port.class.getClassLoader(), new Class<?>[] {CompanionPlanRunner.Port.class},
                (proxy, method, args) -> {
                    try {
                        Object out = method.invoke(port, args);
                        if (!method.getName().equals("run")) return out;
                        return ((CompletableFuture<CompanionPlanRunner.Attempt>) out).thenApply(attempt -> { results.add(attempt.result()); return attempt; });
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        return p;
    }
}
