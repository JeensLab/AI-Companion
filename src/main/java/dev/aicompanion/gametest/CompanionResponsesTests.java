package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.OpenAiResponses;
import dev.aicompanion.client.OpenRouterClient;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionResponsesTests {
    @GameTest(template = "empty")
    public static void directOpenAiUsesResponses(GameTestHelper h) {
        for (String address : new String[]{"https://api.openai.com/v1", "https://api.openai.com/v1/",
                "https://api.openai.com/v1/responses", "https://api.openai.com/v1/chat/completions"})
            h.assertTrue(OpenRouterClient.endpoint(address).toString().equals("https://api.openai.com/v1/responses"),
                    "Direct OpenAI did not use Responses: " + address);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void otherProvidersKeepChatCompletions(GameTestHelper h) {
        for (String base : new String[]{"https://openrouter.ai/api/v1", "http://localhost:11434/v1",
                "https://api.openai.com.example/v1", "https://api.openai.com@other.example/v1"})
            h.assertTrue(OpenRouterClient.endpoint(base).getPath().endsWith("/chat/completions"), "Changed another provider");
        h.assertTrue(OpenRouterClient.endpoint("https://API.OPENAI.COM/v1").getPath().equals("/v1/responses"), "Host case matters");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bothModelsUseNativeFunctionSchema(GameTestHelper h) {
        for (String model : new String[]{"gpt-6-luna", "gpt-6.1-terra"}) {
            JsonObject chat = chat(model, 2048); JsonArray tools = new JsonArray(); tools.add(OpenRouterClient.planTool());
            chat.add("tools", tools); chat.addProperty("tool_choice", "required"); chat.addProperty("parallel_tool_calls", false);
            JsonObject sent = OpenAiResponses.request(chat);
            JsonObject tool = sent.getAsJsonArray("tools").get(0).getAsJsonObject();
            h.assertTrue(sent.getAsJsonObject("reasoning").get("effort").getAsString().equals("none"), "Wrong reasoning effort");
            h.assertTrue(!tool.has("function") && tool.get("name").getAsString().equals("queue_companion_plan")
                    && !tool.get("strict").getAsBoolean(), "Not a native optional-field function schema");
            h.assertTrue(tool.get("parameters").equals(OpenRouterClient.planTool().getAsJsonObject("function").get("parameters")), "Changed plan schema");
            h.assertTrue(sent.get("tool_choice").getAsString().equals("required") && !sent.get("parallel_tool_calls").getAsBoolean(), "Changed tool policy");
            h.assertTrue(chat.has("messages") && !chat.has("input") && !tools.get(0).getAsJsonObject().has("strict"), "Mutated source request");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void plainAndAmbientRequestsKeepTheirBudgetsAndHistory(GameTestHelper h) {
        for (int budget : new int[]{600, 2048, 777}) {
            JsonObject chat = chat("gpt-6-luna", budget); JsonObject sent = OpenAiResponses.request(chat);
            h.assertTrue(sent.get("max_output_tokens").getAsInt() == budget && !sent.get("store").getAsBoolean(), "Wrong budget or cloud storage");
            h.assertTrue(sent.get("input").equals(chat.get("messages")), "Lost text history or current context");
            for (String absent : new String[]{"messages", "max_tokens", "max_completion_tokens", "provider", "previous_response_id", "tools"})
                h.assertTrue(!sent.has(absent), "Unexpected Responses field: " + absent);
            sent.getAsJsonArray("input").get(0).getAsJsonObject().addProperty("content", "changed");
            h.assertTrue(chat.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString().equals("rules"), "Shared mutable history");
        }
        h.assertTrue(!OpenAiResponses.request(chat("other-model", 600)).has("reasoning"), "Forced unsupported effort on another model");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void textAndUsageAreDecodedWithoutReasoning(GameTestHelper h) {
        JsonObject response = response();
        response.getAsJsonArray("output").add(json("{\"type\":\"reasoning\",\"summary\":[]}"));
        response.getAsJsonArray("output").add(json("{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"Привет!\"},{\"type\":\"output_text\",\"text\":\"Как дела?\"}]}"));
        response.add("usage", json("{\"output_tokens\":12,\"output_tokens_details\":{\"reasoning_tokens\":0}}"));
        JsonObject decoded = OpenAiResponses.completion(response);
        h.assertTrue(OpenRouterClient.completionText(decoded).equals("Привет!\nКак дела?"), "Lost text parts");
        h.assertTrue(decoded.get("model").getAsString().equals("gpt-6-luna")
                && decoded.getAsJsonObject("usage").getAsJsonObject("completion_tokens_details").get("reasoning_tokens").getAsInt() == 0, "Lost diagnostics");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeCallsReachTheExistingPlanValidator(GameTestHelper h) {
        JsonObject response = response();
        response.getAsJsonArray("output").add(call("queue_companion_plan", "{\"steps\":[{\"action\":\"come\"}]}"));
        response.getAsJsonArray("output").add(call("queue_companion_plan", "{\"steps\":[{\"action\":\"wait\"}]}"));
        var reply = CompanionPlanCodec.decode(message(response));
        h.assertTrue(reply.actions().size() == 2 && reply.actions().get(0).type().equals("come")
                && reply.actions().get(1).type().equals("wait") && !reply.text().isBlank(), "Lost tool-only ordered plan");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidLaterCallsRejectTheWholePlan(GameTestHelper h) {
        for (JsonObject bad : new JsonObject[]{call("unknown", "{\"steps\":[{\"action\":\"wait\"}]}"),
                call("queue_companion_plan", "{\"steps\":[{\"action\":\"invent_diamonds\"}]}"), call("queue_companion_plan", "not json")}) {
            JsonObject response = response(); response.getAsJsonArray("output").add(call("queue_companion_plan", "{\"steps\":[{\"action\":\"come\"}]}"));
            response.getAsJsonArray("output").add(bad);
            rejected(h, () -> CompanionPlanCodec.decode(message(response)));
        }
        JsonObject oversized = response();
        for (int i = 0; i < 13; i++) oversized.getAsJsonArray("output").add(call("queue_companion_plan", "{\"steps\":[{\"action\":\"come\"}]}"));
        rejected(h, () -> CompanionPlanCodec.decode(message(oversized)));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unfinishedOrRefusedAnswersNeverYieldAPartialPlan(GameTestHelper h) {
        for (String status : new String[]{"incomplete", "failed", "cancelled", "in_progress", "queued"}) {
            JsonObject response = response(); response.addProperty("status", status);
            response.getAsJsonArray("output").add(call("queue_companion_plan", "{\"steps\":[{\"action\":\"come\"}]}"));
            rejected(h, () -> message(response));
        }
        JsonObject refused = response(); refused.getAsJsonArray("output").add(call("queue_companion_plan", "{\"steps\":[{\"action\":\"come\"}]}"));
        refused.getAsJsonArray("output").add(json("{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}"));
        rejected(h, () -> message(refused));
        JsonObject partial = response(); JsonObject call = call("queue_companion_plan", "{}"); call.addProperty("status", "in_progress");
        partial.getAsJsonArray("output").add(call); rejected(h, () -> message(partial));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedOrEmptyOutputIsRejected(GameTestHelper h) {
        rejected(h, () -> message(response()));
        for (String bad : new String[]{"{\"type\":\"web_search_call\"}", "{\"type\":\"function_call\",\"name\":\"queue_companion_plan\",\"arguments\":{}}",
                "{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":null}]}"}) {
            JsonObject response = response(); response.getAsJsonArray("output").add(json(bad)); rejected(h, () -> message(response));
        }
        h.succeed();
    }

    private static JsonObject chat(String model, int budget) {
        JsonObject chat = json("{\"messages\":[{\"role\":\"system\",\"content\":\"rules\"},{\"role\":\"user\",\"content\":\"old\"},{\"role\":\"assistant\",\"content\":\"reply\"},{\"role\":\"user\",\"content\":\"new [current world]\"}]}" );
        chat.addProperty("model", model); chat.addProperty("max_tokens", budget); return chat;
    }
    private static JsonObject response() { return json("{\"status\":\"completed\",\"model\":\"gpt-6-luna\",\"output\":[]}"); }
    private static JsonObject call(String name, String arguments) {
        JsonObject call = new JsonObject(); call.addProperty("type", "function_call"); call.addProperty("name", name);
        call.addProperty("arguments", arguments); call.addProperty("status", "completed"); return call;
    }
    private static JsonObject message(JsonObject response) { return OpenAiResponses.completion(response).getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message"); }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void rejected(GameTestHelper h, Runnable decode) {
        try { decode.run(); } catch (IllegalStateException expected) { return; }
        h.fail("An invalid answer yielded a partial plan");
    }
}
