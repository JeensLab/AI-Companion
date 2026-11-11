package dev.aicompanion.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Responses wire format; transport, dialogue history and plan validation remain in the existing client. */
public final class OpenAiResponses {
    private OpenAiResponses() { }

    public static JsonObject request(JsonObject chat) {
        JsonObject body = new JsonObject();
        String model = chat.get("model").getAsString();
        body.addProperty("model", model);
        body.add("input", chat.getAsJsonArray("messages").deepCopy());
        body.add("max_output_tokens", chat.get("max_tokens").deepCopy());
        body.addProperty("store", false);
        if (model.equals("gpt-6-luna") || model.equals("gpt-6.1-terra")) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "none");
            body.add("reasoning", reasoning);
        }
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

    /** Normalize only a complete answer, before the existing codec validates the entire plan. */
    public static JsonObject completion(JsonObject response) {
        if (!string(response, "status").equals("completed"))
            throw new IllegalStateException("OpenAI не завершил ответ. Поручение не запущено; попробуй ещё раз.");
        if (!response.has("output") || !response.get("output").isJsonArray()) throw malformed();
        JsonArray calls = new JsonArray();
        StringBuilder text = new StringBuilder();
        for (JsonElement element : response.getAsJsonArray("output")) {
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
        if (text.toString().isBlank() && calls.isEmpty()) throw malformed();
        JsonObject message = new JsonObject();
        message.addProperty("content", text.toString());
        if (!calls.isEmpty()) message.add("tool_calls", calls);
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", calls.isEmpty() ? "stop" : "tool_calls");
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
