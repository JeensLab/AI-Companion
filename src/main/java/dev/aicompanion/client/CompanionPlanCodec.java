package dev.aicompanion.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Decodes model output without executing anything. Invalid plans fail as a whole. */
public final class CompanionPlanCodec {
    public static final int MAX_STEPS = 12;
    public static final int WOOD_STEP = 16;
    public static final Set<String> ACTIONS = Set.of("follow", "wait", "come", "protect", "stop_protect", "wander", "stop",
            "gather_wood", "gather_dirt", "gather_stone", "store_items", "take_items", "build_house", "craft_item", "place_block", "give_wood", "give_items", "equip_item", "unequip_item", "hunt_mobs", "smelt_items", "mine_ore", "hunt_animals", "obtain_item");
    private static final Pattern DSML = Pattern.compile("(?s)<\\|DSML\\|invoke\\s+name=\"queue_companion_plan\"[^>]*>.*?"
            + "<\\|DSML\\|parameter\\s+name=\"steps\"[^>]*>(.*?)</\\|DSML\\|parameter>.*?</\\|DSML\\|invoke>");

    private CompanionPlanCodec() { }

    public static CompanionReply decode(JsonObject message) {
        try { return decodeMessage(message); }
        catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException && exception.getMessage() != null
                    && exception.getMessage().startsWith("Модель")) throw exception;
            throw invalid();
        }
    }

    private static CompanionReply decodeMessage(JsonObject message) {
        List<PlannedAction> actions = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                JsonObject function = element.getAsJsonObject().getAsJsonObject("function");
                if (!"queue_companion_plan".equals(function.get("name").getAsString())) throw invalid();
                JsonElement args = function.get("arguments");
                actions.addAll(parsePlan(args.isJsonPrimitive() ? JsonParser.parseString(args.getAsString()) : args));
            }
        }
        String raw = textContent(message.get("content"));
        var matcher = DSML.matcher(raw);
        List<PlannedAction> embedded = new ArrayList<>();
        while (matcher.find()) embedded.addAll(parsePlan(JsonParser.parseString(matcher.group(1))));
        if (actions.isEmpty()) actions.addAll(embedded);
        String reply = raw.replaceAll("(?s)<\\|DSML\\|invoke\\b.*?</\\|DSML\\|invoke>", "")
                .replaceAll("</?\\|DSML\\|[^>]*>", "").trim().replaceFirst("(?i)^assistant\\s*:\\s*", "").trim();
        String json = raw.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        if (actions.isEmpty() && json.startsWith("{")) {
            try {
                JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();
                if (envelope.has("steps")) {
                    actions.addAll(parsePlan(envelope));
                    reply = envelope.has("reply") ? textContent(envelope.get("reply")) : "";
                }
            } catch (RuntimeException exception) { throw invalid(); }
        }
        if (actions.size() > MAX_STEPS) throw invalid();
        reply = CompanionDialogue.cleanReply(reply);
        if (reply.isBlank() || (!actions.isEmpty() && reply.matches("(?s).*(План принят|Понял, выполняю|Результат сообщу после действий).*"))) {
            if (actions.isEmpty()) throw new IllegalStateException("Модель вернула пустой ответ без команд. Попробуй повторить просьбу или выбрать другую модель OpenRouter.");
            reply = CompanionDialogue.acknowledge(actions);
        }
        // A plan is an intention. Completion is spoken only after the server verifies the result.
        if (!actions.isEmpty()) reply = CompanionDialogue.acknowledge(actions);
        return new CompanionReply(reply, List.copyOf(actions));
    }

    public static List<PlannedAction> parsePlan(JsonElement input) {
        try {
            JsonArray steps = input.isJsonArray() ? input.getAsJsonArray() : input.getAsJsonObject().getAsJsonArray("steps");
            if (steps == null || steps.size() > MAX_STEPS) throw invalid();
            List<PlannedAction> result = new ArrayList<>();
            for (JsonElement element : steps) {
                JsonObject step = element.getAsJsonObject();
                String type = step.get("action").getAsString();
                if (!ACTIONS.contains(type)) throw invalid();
                // No number from the model: what the prompt promises it (ore 4, stone 8), not a single block (game log 22.10).
                int count = step.has("count") ? step.get("count").getAsBigDecimal().intValueExact()
                        : "mine_ore".equals(type) ? CompanionPlanRequirements.DEFAULT_ORE : "gather_stone".equals(type) ? 8
                        : "hunt_animals".equals(type) ? CompanionPlanRequirements.DEFAULT_GAME : 1;
                int seconds = step.has("seconds") ? step.get("seconds").getAsBigDecimal().intValueExact() : 5;
                if (count < 1 || count > 64) throw invalid();
                // "Smelt the iron" names no number: all of it, not a single piece (game log 22.10).
                if ("smelt_items".equals(type) && !step.has("count")) count = PlannedAction.ALL_CARRIED;
                if ("build_house".equals(type) && count != 1) throw invalid();
                if ("wait".equals(type) && (seconds < 1 || seconds > 30)) throw invalid();
                String item = step.has("item") ? step.get("item").getAsString().trim() : "";
                if ("place_block".equals(type) && !"minecraft:chest".equals(item) && count != 1) throw invalid();
                // build_house: empty, «resume» (finish the saved house) or the name of a schematic file the player mentioned.
                if ("build_house".equals(type) && (item.length() > 64 || item.contains("/") || item.contains("\\") || item.contains("..") || "continue".equals(item))) throw invalid();
                if (("store_items".equals(type) || "take_items".equals(type)) && !item.isEmpty() && !Set.of("resources", "all", "logs", "stone").contains(item)
                        && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                if ("give_items".equals(type) && !"logs".equals(item) && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                if (("craft_item".equals(type) || "place_block".equals(type) || "equip_item".equals(type) || "smelt_items".equals(type) || "obtain_item".equals(type))
                        && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                String slot = step.has("slot") ? step.get("slot").getAsString() : "auto";
                if (!Set.of("auto", "mainhand", "offhand", "head", "chest", "legs", "feet").contains(slot)) throw invalid();
                String location = step.has("location") ? step.get("location").getAsString() : "near_player";
                if (!Set.of("near_player", "inside_house").contains(location)) throw invalid();
                if (!location.equals("near_player") && !Set.of("place_block", "store_items", "take_items").contains(type)) throw invalid();
                if ("mine_ore".equals(type)) {
                    var kind = dev.aicompanion.entity.CompanionOre.Kind.of(item);
                    if (kind == null) throw invalid();
                    item = "minecraft:" + kind.key() + "_ore";
                }
                if ("hunt_animals".equals(type)) {
                    var game = dev.aicompanion.entity.CompanionGame.Kind.of(item);
                    if (game == null && !dev.aicompanion.entity.CompanionGame.anyKind(item)) throw invalid();
                    item = game == null ? item.equals("food") ? "food" : "any"
                            : (dev.aicompanion.entity.CompanionGame.foodRun(item) ? "food:" : "minecraft:") + game.key;
                }
                // One wood or ore step is time-bounded; a larger honest request becomes consecutive steps of at most 16.
                if ("gather_wood".equals(type) || "mine_ore".equals(type) || "hunt_animals".equals(type)) {
                    int chunk = "mine_ore".equals(type) ? dev.aicompanion.entity.CompanionWork.MAX_ORE : "hunt_animals".equals(type) ? dev.aicompanion.entity.CompanionWork.MAX_GAME : WOOD_STEP;
                    for (int left = count; left > 0; left -= chunk) result.add(new PlannedAction(type, 0, Math.min(chunk, left), item, slot, location));
                } else result.add(new PlannedAction(type, "wait".equals(type) ? seconds : 0, count, item, slot, location));
                if (result.size() > MAX_STEPS) throw invalid();
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) { throw invalid(); }
    }

    private static String textContent(JsonElement content) {
        if (content == null || content.isJsonNull()) return "";
        if (content.isJsonPrimitive()) return content.getAsString().trim();
        StringBuilder result = new StringBuilder();
        if (content.isJsonArray()) for (JsonElement part : content.getAsJsonArray()) {
            if (part.isJsonObject() && part.getAsJsonObject().has("text")) result.append(part.getAsJsonObject().get("text").getAsString()).append('\n');
        }
        return result.toString().trim();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Модель передала некорректный план. Никакие его шаги не запущены; повтори просьбу.");
    }
}
