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
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * «Патрулируй от дома до дачи 100 раз»: the count is a plan-level repeat beside the written steps. The codec takes it
 * only for a walking route over markers, the queue expands the legs in order, the player hears the route and its count,
 * and a real patrol walks every leg — with «стоп» cancelling the rest and an unknown name ending it there, once.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPlanRepeatTests {

    // ---- the codec: a repeat belongs to a walking route ----

    @GameTest(template = "empty")
    public static void theCodecTakesARepeatOnlyForAWalkingRoute(GameTestHelper h) {
        String route = "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}";
        var reply = CompanionPlanCodec.decode(toolCall(route));
        h.assertTrue(reply.actions().size() == 2 && reply.repeat() == 50, "The repeat was lost: " + reply);
        h.assertTrue(reply.text().contains("50") && reply.text().contains("«дом»") && reply.text().contains("«дача»"),
                "The player does not hear the count and the route: " + reply.text());
        // Without the field the plan runs once, as it did before.
        var once = CompanionPlanCodec.decode(toolCall("{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дача\"}]}"));
        h.assertTrue(once.repeat() == 1 && once.actions().size() == 1, "A plan without repeat changed: " + once);
        // repeat 1 beside another action is the same as no repeat at all.
        var mixed = CompanionPlanCodec.decode(toolCall("{\"steps\":[{\"action\":\"store_items\",\"item\":\"resources\"}],\"repeat\":1}"));
        h.assertTrue(mixed.repeat() == 1 && "store_items".equals(mixed.actions().getFirst().type()), "repeat 1 refused another action: " + mixed);
        // Any other action in the plan, a count out of range or a fractional one spoils it whole.
        String walk = "{\"action\":\"go_to_place\",\"item\":\"дом\"}";
        for (String bad : List.of(
                "{\"steps\":[" + walk + ",{\"action\":\"store_items\",\"item\":\"resources\"}],\"repeat\":5}",
                "{\"steps\":[" + walk + "],\"repeat\":0}",
                "{\"steps\":[" + walk + "],\"repeat\":101}",
                "{\"steps\":[" + walk + "],\"repeat\":2.5}")) {
            boolean refused = false;
            try { CompanionPlanCodec.decode(toolCall(bad)); } catch (RuntimeException expected) { refused = true; }
            h.assertTrue(refused, "Accepted: " + bad);
        }
        // The 12-step cap is on the written steps, not on the walks the repeat adds; 13 steps with a repeat are refused.
        StringBuilder many = new StringBuilder("{\"steps\":[");
        for (int step = 0; step < CompanionPlanCodec.MAX_STEPS + 1; step++) many.append(step == 0 ? "" : ",").append(walk);
        many.append("],\"repeat\":2}");
        boolean refused = false;
        try { CompanionPlanCodec.decode(toolCall(many.toString())); } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused, "13 written steps with a repeat were accepted: " + many);
        // The fallback text carries the repeat beside the steps just as well.
        JsonObject fallbackText = new JsonObject();
        fallbackText.addProperty("content", "<|DSML|invoke name=\"queue_companion_plan\">"
                + "<|DSML|parameter name=\"steps\" string=\"false\">[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}]</|DSML|parameter>"
                + "<|DSML|parameter name=\"repeat\" string=\"false\">5</|DSML|parameter></|DSML|invoke>");
        var fallback = CompanionPlanCodec.decode(fallbackText);
        h.assertTrue(fallback.repeat() == 5 && fallback.actions().size() == 2, "The fallback text lost the repeat: " + fallback);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theRepeatExpandsIntoTheQueuedStepsInOrder(GameTestHelper h) {
        var written = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}"));
        var queued = CompanionPlanCodec.repeated(written, 50);
        h.assertTrue(queued.size() == 100, "The route was not repeated: " + queued.size());
        h.assertTrue(queued.get(0).item().equals("дом") && queued.get(1).item().equals("дача")
                && queued.get(2).item().equals("дом") && queued.get(99).item().equals("дача"),
                "The legs are out of order: " + queued.subList(0, 4) + " … " + queued.getLast());
        h.assertTrue(queued.stream().allMatch(step -> "go_to_place".equals(step.type())), "A step of another kind was queued");
        h.assertTrue(CompanionPlanCodec.repeated(written, 1).equals(written), "One repeat changed the plan");
        h.succeed();
    }

    /** «Патрулируй дом→дача→дом 2 раза»: the spoken route keeps every leg in order, and the count in the player's word. */
    @GameTest(template = "empty")
    public static void theSpokenRouteKeepsEveryLegAndItsNumeral(GameTestHelper h) {
        var two = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"},"
                        + "{\"action\":\"go_to_place\",\"item\":\"дом\"}],\"repeat\":2}"));
        h.assertTrue(two.text().contains("Маршрут «дом» → «дача» → «дом» — 2 раза."),
                "The return leg or the count is not said: " + two.text());
        var five = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":5}"));
        h.assertTrue(five.text().contains("— 5 раз."), "The count is not said in the player's word: " + five.text());
        h.assertTrue(!five.text().contains("5 раза") && !two.text().contains("2 раз."), "A wrong numeral: " + two.text() + " / " + five.text());
        // «11 раз», «21 раз», «22 раза»: the teens and the ones decide the word, not the last digit alone.
        h.assertTrue("раз".equals(CompanionDialogue.times(11)) && "раз".equals(CompanionDialogue.times(21))
                && "раза".equals(CompanionDialogue.times(22)) && "раза".equals(CompanionDialogue.times(4)),
                "The numeral does not follow the count");
        h.succeed();
    }

    /**
     * «Поставь метки и патрулируй дом→дача 50 раз»: a placement wording that changes nothing returns the decoded plan as it
     * was, and one that adds a step still keeps the count for the walk.
     */
    @GameTest(template = "empty")
    public static void aRewrittenPlanKeepsTheRepeatItWasDecodedWith(GameTestHelper h) {
        var decoded = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}"));
        var unchanged = CompanionPlanRequirements.apply("поставь метки и патрулируй дом и дачу 50 раз", decoded);
        h.assertTrue(unchanged.repeat() == 50 && unchanged.actions().equals(decoded.actions()),
                "The rewritten plan lost its repeat: " + unchanged);
        h.assertTrue(unchanged.text().contains("50"), "The spoken count disappeared: " + unchanged.text());
        var added = CompanionPlanRequirements.apply("поставь сундук и патрулируй дом и дачу 50 раз", decoded);
        h.assertTrue(added.repeat() == 50 && added.actions().stream().anyMatch(step -> "place_block".equals(step.type())),
                "The added chest dropped the count: " + added);
        h.assertTrue(added.text().contains("— 50 раз."), "The count is not said with the added step: " + added.text());
        h.succeed();
    }

    /** Two invokes in one answer: one and the same repeat is the plan's, two different counts spoil it. */
    @GameTest(template = "empty")
    public static void twoInvokesMayNotDisagreeOnTheRepeat(GameTestHelper h) {
        String route = "[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}]";
        String same = invoke(route, "5") + invoke(route, "5");
        JsonObject acceptedText = new JsonObject();
        acceptedText.addProperty("content", same);
        var accepted = CompanionPlanCodec.decode(acceptedText);
        h.assertTrue(accepted.repeat() == 5 && accepted.actions().size() == 4, "Two agreeing invokes were refused: " + accepted);

        String different = invoke(route, "5") + invoke(route, "2");
        JsonObject refusedText = new JsonObject();
        refusedText.addProperty("content", different);
        boolean refused = false;
        try { CompanionPlanCodec.decode(refusedText); } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused, "Two invokes with different counts were accepted: " + different);

        // A repeat parameter of another invoke is not the plan's.
        JsonObject otherText = new JsonObject();
        otherText.addProperty("content", "<|DSML|invoke name=\"queue_companion_plan\">"
                + "<|DSML|parameter name=\"steps\" string=\"false\">" + route + "</|DSML|parameter></|DSML|invoke>"
                + "<|DSML|invoke name=\"remember_note\">"
                + "<|DSML|parameter name=\"repeat\" string=\"false\">9</|DSML|parameter></|DSML|invoke>");
        var other = CompanionPlanCodec.decode(otherText);
        h.assertTrue(other.repeat() == 1 && other.actions().size() == 2, "Another invoke's repeat became the plan's: " + other);
        h.succeed();
    }

    /** «Запомни это место как дача и патрулируй дом→дача 50 раз»: remembering once, then the route fifty times. */
    @GameTest(template = "empty")
    public static void aRememberedPlaceBesideARepeatRunsOnce(GameTestHelper h) {
        var reply = CompanionPlanCodec.decode(toolCall(
                "{\"steps\":[{\"action\":\"remember_place\",\"item\":\"дача\"},{\"action\":\"go_to_place\",\"item\":\"дом\"},"
                        + "{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":50}"));
        h.assertTrue(reply.repeat() == 50 && reply.actions().size() == 3, "The remembered place spoiled the plan: " + reply);
        var queued = CompanionPlanCodec.repeated(reply.actions(), reply.repeat());
        h.assertTrue(queued.size() == 101, "The remembered place was repeated: " + queued.size());
        h.assertTrue("remember_place".equals(queued.getFirst().type()) && "дача".equals(queued.getFirst().item()),
                "Remembering is not first: " + queued.getFirst());
        h.assertTrue(queued.subList(1, queued.size()).stream().allMatch(step -> "go_to_place".equals(step.type())),
                "A non-walk step went into the route: " + queued.subList(0, 4));
        h.assertTrue("дом".equals(queued.get(1).item()) && "дача".equals(queued.get(2).item())
                && "дом".equals(queued.get(3).item()) && "дача".equals(queued.get(100).item()),
                "The route is out of order: " + queued.subList(0, 4) + " … " + queued.getLast());
        h.assertTrue(reply.text().contains("Запомнил это место как «дача»") && reply.text().contains("— 50 раз."),
                "The player does not hear the place and the count: " + reply.text());
        h.succeed();
    }

    /** A patrol's report names the same arrival every lap: the model history keeps each distinct line once, with its count. */
    @GameTest(template = "empty")
    public static void theHistoryCollapsesRepeatedLegLines(GameTestHelper h) {
        StringBuilder report = new StringBuilder();
        for (int lap = 0; lap < 50; lap++)
            report.append("Пришёл к метке «дом»: 14, 65, 8.\n").append("Пришёл к метке «дача»: 20, 65, 6.\n");
        String collapsed = OpenRouterClient.collapsedReport(report.toString());
        h.assertTrue(collapsed.lines().count() == 2, "Identical legs were not collapsed: " + collapsed);
        h.assertTrue(collapsed.contains("Пришёл к метке «дом»: 14, 65, 8. (×50)")
                && collapsed.contains("Пришёл к метке «дача»: 20, 65, 6. (×50)"), "The count of the legs is lost: " + collapsed);
        OpenRouterClient.resetSession();
        // A reply first: a history with a system prompt would read the config, which a gametest server has not loaded.
        OpenRouterClient.recordReply(OpenRouterClient.generation(), "Ладно, иду.");
        OpenRouterClient.recordActionResult(report.toString());
        String stored = OpenRouterClient.historySnapshot().getLast().get("content").getAsString();
        h.assertTrue(stored.length() < 200 && stored.contains("(×50)"), "The raw report reached the history: " + stored);
        OpenRouterClient.resetSession();
        h.succeed();
    }

    private static String invoke(String steps, String repeat) {
        return "<|DSML|invoke name=\"queue_companion_plan\">"
                + "<|DSML|parameter name=\"steps\" string=\"false\">" + steps + "</|DSML|parameter>"
                + "<|DSML|parameter name=\"repeat\" string=\"false\">" + repeat + "</|DSML|parameter></|DSML|invoke>";
    }

    // ---- the patrol: one plan, several walks ----

    /**
     * «Патрулируй от дома до дачи и обратно 2 раза»: three written legs and repeat 2 become six real walks, and he visits
     * both markers — home four times, the dacha twice — by the normal path, one leg after the other.
     */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void aPatrolWithRepeatWalksEveryLegItWasGiven(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        BlockPos home = h.absolutePos(new BlockPos(14, 1, 8));
        BlockPos dacha = h.absolutePos(new BlockPos(20, 1, 6));
        store.add(c.getOwnerId(), "дом", h.getLevel().dimension(), home, 1);
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), dacha, 2);
        var written = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"},{\"action\":\"go_to_place\",\"item\":\"дом\"}],\"repeat\":2}"));
        h.assertTrue(written.size() == 3, "The codec expanded the written steps: " + written);
        var queued = CompanionPlanCodec.repeated(written, 2);
        h.assertTrue(queued.size() == 6, "The patrol was not expanded: " + queued.size());
        var plan = CompanionPlanRunner.run(queued,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-patrol", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The patrol is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            String report = outcome.report();
            h.assertTrue(arrivals(report) == 6, "The six legs were not all walked: " + report);
            h.assertTrue(arrivals(report, "дом") == 4 && arrivals(report, "дача") == 2, "The route was not the one written: " + report);
            h.assertTrue(outcome.skipped().isEmpty() && !report.contains("Не выполнено"), "A leg was skipped: " + report);
            h.assertTrue(report.lastIndexOf("Пришёл к метке «дом»") > report.lastIndexOf("Пришёл к метке «дача»"),
                    "The patrol did not end at home: " + report);
        });
    }

    /** «Стоп» mid-patrol: the leg in hand ends, the rest of the six are not walked, and nothing is listed as not done. */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void aStopMidPatrolCancelsTheRest(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        var store = CompanionMarkers.get(h.getLevel().getServer());
        store.add(c.getOwnerId(), "дом", h.getLevel().dimension(), h.absolutePos(new BlockPos(14, 1, 8)), 1);
        store.add(c.getOwnerId(), "дача", h.getLevel().dimension(), h.absolutePos(new BlockPos(20, 1, 6)), 2);
        var queued = CompanionPlanCodec.repeated(CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"дом\"},{\"action\":\"go_to_place\",\"item\":\"дача\"},{\"action\":\"go_to_place\",\"item\":\"дом\"}],\"repeat\":2}")), 2);
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        int[] legs = {0};
        boolean[] working = {false};
        h.onEachTick(() -> {
            boolean now = c.isWorking() || c.isGatheringTask();
            if (working[0] && !now) legs[0]++;
            working[0] = now;
            if (legs[0] >= 2) stop.set(true);
        });
        var plan = CompanionPlanRunner.run(queued,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-patrol-stop", stop::get));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The patrol is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.cancelled(), "The stop did not cancel the patrol: " + outcome.report());
            h.assertTrue(outcome.skipped().isEmpty(), "A cancelled patrol listed legs as not done: " + outcome.skipped());
            h.assertTrue(outcome.report().contains("План остановлен:"), "The stop is not said: " + outcome.report());
            h.assertTrue(arrivals(outcome.report()) < 6, "The patrol walked on after the stop: " + outcome.report());
            h.assertTrue(legs[0] >= 2, "The stop came before the patrol walked: " + legs[0]);
        });
    }

    /** A patrol whose first leg names no known marker: it fails there, the other five legs are not walked, nothing is retried. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void anUnknownMarkerOnTheFirstLegEndsThePatrolWithoutRepeating(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        CompanionMarkers.get(h.getLevel().getServer()).add(c.getOwnerId(), "дача", h.getLevel().dimension(), c.blockPosition().offset(6, 0, 0), 1);
        var queued = CompanionPlanCodec.repeated(CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "{\"steps\":[{\"action\":\"go_to_place\",\"item\":\"озеро\"},{\"action\":\"go_to_place\",\"item\":\"дача\"}],\"repeat\":2}")), 2);
        h.assertTrue(queued.size() == 4, "The patrol was not expanded: " + queued.size());
        var plan = CompanionPlanRunner.run(queued,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-patrol-unknown", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The patrol is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            String report = outcome.report();
            h.assertTrue(report.contains("Не знаю метки «озеро»"), "The unknown name is not said: " + report);
            h.assertTrue(outcome.recoveries() == 0 && !report.contains("Повтор шага"), "The first leg was repeated: " + report);
            h.assertTrue(outcome.skipped().size() == 3, "The legs after the failed one are not listed: " + outcome.skipped());
            h.assertTrue(arrivals(report, "дача") == 0, "He walked to the dacha after the first leg failed: " + report);
            h.assertTrue(c.workPhase().isEmpty(), "A walk started for the unknown name: " + c.workPhase());
        });
    }

    // ---- fixtures ----

    /** One tool call of the model's answer, as the provider hands it over: the plan JSON is the arguments string. */
    private static JsonObject toolCall(String arguments) {
        JsonObject function = new JsonObject();
        function.addProperty("name", "queue_companion_plan");
        function.addProperty("arguments", arguments);
        JsonObject call = new JsonObject();
        call.add("function", function);
        JsonArray calls = new JsonArray();
        calls.add(call);
        JsonObject message = new JsonObject();
        message.addProperty("content", "");
        message.add("tool_calls", calls);
        return message;
    }

    /** The arrivals at one marker, or at any marker, in a plan's report. */
    private static int arrivals(String report) {
        return (int) report.lines().filter(line -> line.contains("Пришёл к метке «")).count();
    }

    private static int arrivals(String report, String name) {
        return (int) report.lines().filter(line -> line.contains("Пришёл к метке «" + name + "»")).count();
    }

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setFollowing(false);
        // The world's store outlives a test: this owner starts with no markers of his own.
        var store = CompanionMarkers.get(h.getLevel().getServer());
        for (var marker : store.all(owner.getUUID())) store.remove(owner.getUUID(), marker.id());
        return companion;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}
