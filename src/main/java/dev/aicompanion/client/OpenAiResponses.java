package dev.aicompanion.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Responses wire format; transport, dialogue history and plan validation remain in the existing client. */
public final class OpenAiResponses {
    private OpenAiResponses() { }

    /**
     * The Responses body of a chat request. Responses has no chat_template_kwargs: the thinking switch of the chat body
     * becomes reasoning.effort — «none» when thinking is off (the chat body's default, and what kept the measured answers
     * at 4–12 s instead of 180 s), and no reasoning field when the owner turned thinking on, so the provider's own effort
     * stands instead of one the mod invents. The field is only sent to the models that take it; another model keeps its
     * own behaviour. Sampling is sent only when the owner set it, exactly as typed, as on the chat transport: a model that
     * does not take one answers with its own error, which is the honest word on a range the mod does not impose.
     */
    public static JsonObject request(JsonObject chat) {
        JsonObject body = new JsonObject();
        String model = chat.get("model").getAsString();
        body.addProperty("model", model);
        body.add("input", chat.getAsJsonArray("messages").deepCopy());
        body.add("max_output_tokens", chat.get("max_tokens").deepCopy());
        body.addProperty("store", false);
        if (takesReasoningEffort(model) && !thinking(chat)) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "none");
            body.add("reasoning", reasoning);
        }
        if (chat.has("temperature")) body.add("temperature", chat.get("temperature").deepCopy());
        if (chat.has("top_p")) body.add("top_p", chat.get("top_p").deepCopy());
        if (chat.has("tools")) {
            JsonArray tools = new JsonArray();
            for (JsonElement element : chat.getAsJsonArray("tools")) {
                JsonObject tool = element.getAsJsonObject().getAsJsonObject("function").deepCopy();
                tool.addProperty("type", "function");
                // Responses otherwise normalizes optional plan fields into a strict schema.
                tool.addProperty("strict", false);
                tools.add(tool);
            }
            body.add("tools", tools);
            if (chat.has("tool_choice")) body.add("tool_choice", chat.get("tool_choice").deepCopy());
            if (chat.has("parallel_tool_calls")) body.add("parallel_tool_calls", chat.get("parallel_tool_calls").deepCopy());
        }
        // Every turn has text history plus its current world snapshot. We do not continue a native tool loop:
        // validated calls become a game plan, and later game facts enter the next fresh turn as text.
        // No previous_response_id, unanswered calls or opaque reasoning is retained or replayed.
        return body;
    }

    /** The two models this provider documents as taking reasoning.effort; another model is never sent the field. */
    private static boolean takesReasoningEffort(String model) {
        return model.equals("gpt-6-luna") || model.equals("gpt-6.1-terra");
    }

    /** Whether the chat body asked for thinking. Absent means off: that is the chat body's own default and the owner's. */
    private static boolean thinking(JsonObject chat) {
        JsonElement kwargs = chat.get("chat_template_kwargs");
        return kwargs != null && kwargs.isJsonObject() && kwargs.getAsJsonObject().has("enable_thinking")
                && kwargs.getAsJsonObject().get("enable_thinking").getAsBoolean();
    }

    /** The same Responses body without its reasoning switch, for a provider that refused the field by name. */
    public static JsonObject withoutReasoning(JsonObject body) {
        JsonObject plain = body.deepCopy();
        plain.remove("reasoning");
        return plain;
    }

    /** Normalize complete answers or an empty token-limit result, before the existing plan checks. */
    public static JsonObject completion(JsonObject response) {
        String status = string(response, "status");
        boolean length = status.equals("incomplete") && response.has("incomplete_details")
                && response.get("incomplete_details").isJsonObject()
                && string(response.getAsJsonObject("incomplete_details"), "reason").equals("max_output_tokens");
        if (!status.equals("completed") && !length)
            throw new IllegalStateException("OpenAI не завершил ответ. Поручение не запущено; попробуй ещё раз.");
        if (!response.has("output") || !response.get("output").isJsonArray()) throw malformed();
        JsonArray calls = new JsonArray();
        StringBuilder text = new StringBuilder();
        // A limited answer may contain complete calls before an unfinished one. Never expose any of them.
        if (!length) for (JsonElement element : response.getAsJsonArray("output")) {
            if (!element.isJsonObject()) throw malformed();
            JsonObject item = element.getAsJsonObject();
            if (item.has("status") && !string(item, "status").equals("completed"))
                throw new IllegalStateException("OpenAI не завершил часть ответа. Поручение не запущено.");
            switch (string(item, "type")) {
                case "reasoning" -> { /* Not dialogue and not a game command. */ }
                case "message" -> {
                    if (!string(item, "role").equals("assistant") || !item.has("content") || !item.get("content").isJsonArray()) throw malformed();
                    for (JsonElement part : item.getAsJsonArray("content")) {
                        if (!part.isJsonObject()) throw malformed();
                        JsonObject content = part.getAsJsonObject();
                        switch (string(content, "type")) {
                            case "output_text" -> {
                                if (!text.isEmpty()) text.append('\n');
                                text.append(string(content, "text"));
                            }
                            case "refusal" -> throw new IllegalStateException("OpenAI отказался выполнять запрос. Поручение не запущено.");
                            default -> throw malformed();
                        }
                    }
                }
                case "function_call" -> {
                    JsonObject function = new JsonObject();
                    function.addProperty("name", string(item, "name"));
                    function.addProperty("arguments", string(item, "arguments"));
                    JsonObject call = new JsonObject();
                    call.addProperty("type", "function");
                    call.add("function", function);
                    calls.add(call);
                }
                default -> throw malformed();
            }
        }
        // Completed silence is valid for ambient remarks; the foreground codec still rejects an empty plan.
        JsonObject message = new JsonObject();
        message.addProperty("content", text.toString());
        if (!calls.isEmpty()) message.add("tool_calls", calls);
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", length ? "length" : calls.isEmpty() ? "stop" : "tool_calls");
        choice.add("message", message);
        JsonArray choices = new JsonArray(); choices.add(choice);
        JsonObject result = new JsonObject(); result.add("choices", choices);
        for (String field : new String[]{"id", "model"}) if (response.has(field)) result.add(field, response.get(field).deepCopy());
        if (response.has("usage") && response.get("usage").isJsonObject()) {
            JsonObject usage = response.getAsJsonObject("usage").deepCopy();
            if (usage.has("output_tokens_details")) usage.add("completion_tokens_details", usage.get("output_tokens_details").deepCopy());
            result.add("usage", usage);
        }
        return result;
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw malformed();
        return value.getAsString();
    }

    private static IllegalStateException malformed() {
        return new IllegalStateException("OpenAI вернул непонятный ответ. Поручение не запущено.");
    }
}
