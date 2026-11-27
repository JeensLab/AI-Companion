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
    /** How many times one walking route may be walked: the written steps stay capped at {@link #MAX_STEPS}. */
    public static final int MAX_REPEAT = 100;
    public static final int WOOD_STEP = 16;
    public static final Set<String> ACTIONS = Set.of("follow", "wait", "come", "protect", "stop_protect", "wander", "stop", "enter_portal", "open_end_portal",
            "gather_wood", "gather_dirt", "gather_loose", "gather_stone", "store_items", "take_items", "sort_chests", "build_house", "craft_item", "place_block", "give_wood", "give_items", "equip_item", "unequip_item", "hunt_mobs", "smelt_items", "mine_ore", "hunt_animals", "build_pen", "breed_animals", "scout_traders", "trade_items", "farm_crops", "harvest_crops", "use_bone_meal", "obtain_item", "pursue_goal", "walk_heading", "survey_terrain", "recall_landmark", "guide_landmark", "set_home", "build_portal", "build_cobble_gen", "build_golem", "pick_up", "remember_place", "go_to_place", "repair_item", "disenchant_item", "compost_items", "campfire_song", "fish");
    /** Flint is dug out of gravel, which drops it rarely: one step asks for at most this much. */
    static final String FLINT = "minecraft:flint";
    static final int FLINT_STEP = 8;
    static final int FLINT_DEFAULT = 2;
    /** The items a gather_loose order may name: the loose blocks the server digs with a shovel, and the flint in gravel. */
    private static final Set<String> LOOSE_ITEMS = Set.of("minecraft:sand", "minecraft:red_sand", "minecraft:gravel", FLINT);
    /** The stone dug by name where it shows (CompanionPickable): obtain_item's, not gather_stone's, which digs any stone. */
    private static final Set<String> NAMED_STONE = Set.of("minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:calcite",
            "minecraft:cobbled_deepslate");
    /** One invoke of the plan tool: its parameters are read inside its own tags, so another invoke's repeat is not taken for this plan's. */
    private static final Pattern DSML_INVOKE = Pattern.compile("(?s)<\\|DSML\\|invoke\\s+name=\"queue_companion_plan\"[^>]*>(.*?)</\\|DSML\\|invoke>");
    private static final Pattern DSML_STEPS = Pattern.compile("(?s)<\\|DSML\\|parameter\\s+name=\"steps\"[^>]*>(.*?)</\\|DSML\\|parameter>");
    private static final Pattern DSML_REPEAT = Pattern.compile("(?s)<\\|DSML\\|parameter\\s+name=\"repeat\"[^>]*>(.*?)</\\|DSML\\|parameter>");

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
        int repeat = 1;
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                JsonObject function = element.getAsJsonObject().getAsJsonObject("function");
                if (!"queue_companion_plan".equals(function.get("name").getAsString())) throw invalid();
                JsonElement args = function.get("arguments");
                DecodedPlan plan = parsePlanWithRepeat(args.isJsonPrimitive() ? JsonParser.parseString(args.getAsString()) : args);
                actions.addAll(plan.actions());
                repeat = mergedRepeat(repeat, plan.repeat());
            }
        }
        String raw = textContent(message.get("content"));
        var matcher = DSML_INVOKE.matcher(raw);
        List<PlannedAction> embedded = new ArrayList<>();
        int embeddedRepeat = 1;
        while (matcher.find()) {
            String invoke = matcher.group(1);
            var steps = DSML_STEPS.matcher(invoke);
            if (!steps.find()) continue;
            DecodedPlan plan = parsePlanWithRepeat(JsonParser.parseString(steps.group(1)));
            embedded.addAll(plan.actions());
            // Two invokes of one answer may not ask for different counts, as two tool calls may not.
            embeddedRepeat = mergedRepeat(embeddedRepeat, plan.repeat());
            embeddedRepeat = mergedRepeat(embeddedRepeat, dsmlRepeat(invoke));
        }
        if (actions.isEmpty()) {
            actions.addAll(embedded);
            if (!embedded.isEmpty()) repeat = mergedRepeat(repeat, embeddedRepeat);
        }
        String reply = raw.replaceAll("(?s)<\\|DSML\\|invoke\\b.*?</\\|DSML\\|invoke>", "")
                .replaceAll("</?\\|DSML\\|[^>]*>", "").trim().replaceFirst("(?i)^assistant\\s*:\\s*", "").trim();
        String json = raw.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        if (actions.isEmpty() && json.startsWith("{")) {
            try {
                JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();
                if (envelope.has("steps")) {
                    DecodedPlan plan = parsePlanWithRepeat(envelope);
                    actions.addAll(plan.actions());
                    repeat = mergedRepeat(repeat, plan.repeat());
                    reply = envelope.has("reply") ? textContent(envelope.get("reply")) : "";
                }
            } catch (RuntimeException exception) { throw refusedOrInvalid(exception); }
        }
        if (actions.size() > MAX_STEPS) throw invalid();
        requireGoalAlone(actions);
        requireWalkOnly(actions, repeat);
        reply = CompanionDialogue.cleanReply(reply);
        if (reply.isBlank() || (!actions.isEmpty() && reply.matches("(?s).*(План принят|Понял, выполняю|Результат сообщу после действий).*"))) {
            if (actions.isEmpty()) throw new IllegalStateException("Модель вернула пустой ответ без команд. Попробуй повторить просьбу или выбрать другую модель в настройках.");
            reply = CompanionDialogue.acknowledge(actions, repeat);
        }
        // A plan is an intention. Completion is spoken only after the server verifies the result.
        if (!actions.isEmpty()) reply = CompanionDialogue.acknowledge(actions, repeat);
        return new CompanionReply(reply, List.copyOf(actions), repeat);
    }

    /** A repeat covers the whole answer: two plans of one message may not ask for different counts. */
    private static int mergedRepeat(int current, int declared) {
        if (declared == 1) return current;
        if (current != 1 && current != declared) throw invalid();
        return declared;
    }

    /** The repeat named beside the steps inside one invoke, or 1 when it names none. */
    private static int dsmlRepeat(String invoke) {
        var matcher = DSML_REPEAT.matcher(invoke);
        return matcher.find() ? repeatValue(JsonParser.parseString(matcher.group(1).trim())) : 1;
    }

    /** The repeat the model wrote: a whole number 1..MAX_REPEAT, or the plan is invalid. */
    private static int repeatOf(JsonObject plan) {
        return plan.has("repeat") ? repeatValue(plan.get("repeat")) : 1;
    }

    private static int repeatValue(JsonElement value) {
        int repeat;
        try { repeat = value.getAsBigDecimal().intValueExact(); }
        catch (RuntimeException exception) { throw invalid(); }
        if (repeat < 1 || repeat > MAX_REPEAT) throw invalid();
        return repeat;
    }

    /**
     * A repeat is a route over markers: every written step must be go_to_place, or a remember_place that runs once, and
     * at least one leg must be walked, or the whole plan is invalid.
     */
    private static void requireWalkOnly(List<PlannedAction> steps, int repeat) {
        if (repeat <= 1) return;
        if (steps.stream().noneMatch(step -> "go_to_place".equals(step.type()))) throw invalid();
        if (steps.stream().anyMatch(step -> !"go_to_place".equals(step.type()) && !"remember_place".equals(step.type()))) throw invalid();
    }

    /** The `location` values the model is offered: the two places and every container a store or take order may name. */
    public static final List<String> LOCATIONS = locations();

    private static List<String> locations() {
        List<String> names = new ArrayList<>(List.of("near_player", "inside_house"));
        names.addAll(dev.aicompanion.entity.CompanionContainers.NAMEABLE.stream().sorted().toList());
        return List.copyOf(names);
    }

    /** near_player for any step; inside_house for placing and storing; a container block id for store_items and take_items only. */
    private static boolean validLocation(String location, String type) {
        boolean storage = Set.of("store_items", "take_items").contains(type);
        if (dev.aicompanion.entity.CompanionContainers.KINDS.contains(location)) return storage;
        return location.equals("near_player") || location.equals("inside_house") && (storage || type.equals("place_block"));
    }

    public static List<PlannedAction> parsePlan(JsonElement input) {
        return parsePlanWithRepeat(input).actions();
    }

    /** The written steps and how many times the walk is walked: only a route of go_to_place steps, with an optional remember_place once, may carry a repeat. */
    public static DecodedPlan parsePlanWithRepeat(JsonElement input) {
        try {
            JsonArray steps = input.isJsonArray() ? input.getAsJsonArray() : input.getAsJsonObject().getAsJsonArray("steps");
            if (steps == null || steps.size() > MAX_STEPS) throw invalid();
            int repeat = input.isJsonArray() ? 1 : repeatOf(input.getAsJsonObject());
            List<PlannedAction> result = new ArrayList<>();
            for (int index = 0; index < steps.size(); index++) {
                JsonElement element = steps.get(index);
                JsonObject step = element.getAsJsonObject();
                step.entrySet().removeIf(field -> field.getValue().isJsonNull()); // "count": null is a missing count, not a broken plan
                String type = step.get("action").getAsString();
                if (!ACTIONS.contains(type)) throw invalid();
                // No number from the model: what the prompt promises it (ore 4, stone 8), not a single block (game log 22.10).
                int count = step.has("count") ? step.get("count").getAsBigDecimal().intValueExact()
                        : "mine_ore".equals(type) ? CompanionPlanRequirements.DEFAULT_ORE
                        : "gather_loose".equals(type) && FLINT.equals(step.has("item") ? step.get("item").getAsString().trim() : "") ? FLINT_DEFAULT
                        : ("gather_stone".equals(type) || "gather_loose".equals(type)) ? 8
                        : "hunt_animals".equals(type) ? CompanionPlanRequirements.DEFAULT_GAME
                        : "farm_crops".equals(type) ? dev.aicompanion.entity.CompanionCrops.DEFAULT_TILES
                        : "harvest_crops".equals(type) || "use_bone_meal".equals(type) ? dev.aicompanion.entity.CompanionCrops.MAX_HARVEST
                        : "build_pen".equals(type) ? 4
                        // «Подбери» without a number takes everything of that kind lying around.
                        : "pick_up".equals(type) ? 64
                        // A heading with no distance is walked until «стоп».
                        : "walk_heading".equals(type) ? 0 : 1;
                int seconds = step.has("seconds") ? step.get("seconds").getAsBigDecimal().intValueExact() : 5;
                // obtain_item may ask for a few stacks («два стака», «добудь 500 листвы»): its chain splits them into steps of a
                // stack each, and the plan carries the order in the same steps below.
                int most = "obtain_item".equals(type) ? dev.aicompanion.entity.CompanionWork.MAX_LEAVES
                        : "trade_items".equals(type) ? dev.aicompanion.entity.CompanionTradeOrder.MAX_COUNT : 64;
                if ("walk_heading".equals(type) ? count < 0 || count > dev.aicompanion.entity.CompanionCourse.MAX_BLOCKS : count < 1 || count > most) throw invalid();
                // "Smelt the iron" names no number: all of it, not a single piece (game log 22.10).
                if ("smelt_items".equals(type) && !step.has("count")) count = PlannedAction.ALL_CARRIED;
                // «Закинь саженцы в компостер» names no number: all of them, not one (as the smelting above).
                if ("compost_items".equals(type) && !step.has("count")) count = PlannedAction.ALL_CARRIED;
                // «Порыбачь» names no number: until he is stopped (CompanionFishing).
                if ("fish".equals(type) && !step.has("count")) count = PlannedAction.ALL_CARRIED;
                if (("build_house".equals(type) || "sort_chests".equals(type) || "set_home".equals(type) || "build_portal".equals(type) || "open_end_portal".equals(type) || "build_cobble_gen".equals(type) || "build_golem".equals(type) || "campfire_song".equals(type)) && count != 1) throw invalid();
                if ("wait".equals(type) && (seconds < 1 || seconds > 30)) throw invalid();
                String item = step.has("item") ? step.get("item").getAsString().trim() : "";
                // gather_stone digs any stone: granite, tuff or cobbled deepslate named there is obtain_item's, dug by name where it shows
                // (in game 17.11: «добудь 2 диорита» given to gather_stone dug two of plain stone and said done).
                String stoneId = item.contains(":") ? item : "minecraft:" + item;
                if ("gather_stone".equals(type) && NAMED_STONE.contains(stoneId)) { type = "obtain_item"; item = stoneId; }
                // «Подбери всё» may come with no item or with a word for the whole ground: the codec's own meaning for any drop.
                if ("pick_up".equals(type) && (item.isEmpty() || "any".equals(item) || "all".equals(item) || "minecraft:all".equals(item)))
                    item = dev.aicompanion.entity.CompanionPickUp.ANY_ITEM;
                if ("set_home".equals(type) && !item.isEmpty()) throw invalid();
                // The round of traders takes no item and no number: anything written there spoils the plan.
                if ("scout_traders".equals(type) && (!item.isEmpty() || count != 1)) throw invalid();
                // A trade names its goods (a book also its enchantment), maybe what to pay and whom, and the player's word; the fields ride in the item text.
                if ("trade_items".equals(type)) {
                    item = dev.aicompanion.entity.CompanionTradeOrder.read(step).pack();
                    // The real number rides in the packed order; the step's own count must pass the server's one-order limit of 64.
                    count = 1;
                }
                // A building is named from the catalogue (or a kind of it) or not at all («resume» goes on with the one left unfinished); the name is kept as the catalogue spells it.
                // «дом», «house», «маленький дом», «дом 5×5»: the plain house, as an empty item always was.
                if ("build_house".equals(type) && dev.aicompanion.entity.CompanionVillageBuildings.plankHouse(item)) item = "";
                if ("build_house".equals(type) && dev.aicompanion.entity.CompanionVillageBuildings.resume(item) != null) item = dev.aicompanion.entity.CompanionVillageBuildings.resume(item);
                else if ("build_house".equals(type) && !item.isEmpty()) {
                    String named = dev.aicompanion.entity.CompanionVillageBuildings.named(item, false);
                    // Not invalid(): the player is told what the model named and what is like it (the message is kept by decode).
                    if (named == null) throw new IllegalStateException("Модель назвала постройку «" + item.substring(0, Math.min(item.length(), 40)) + "», ничего не запущено. " + dev.aicompanion.entity.CompanionVillageBuildings.refusal(item));
                    item = named;
                }
                // The kind of tune: none named, the saddest or a sadder one, the happiest or a happier one, or simply another one.
                if ("campfire_song".equals(type) && !dev.aicompanion.entity.CompanionCampfire.MOODS.contains(item)) throw invalid();
                // One golem a request, iron or snow (decision 12).
                if ("build_golem".equals(type) && !dev.aicompanion.entity.CompanionGolem.KINDS.contains(item)) throw invalid();
                // A new portal («построй новый»), one here («построй здесь»), or none named: then a portal near or known is enough or led to.
                if ("build_portal".equals(type) && !item.isEmpty() && !"new".equals(item) && !"here".equals(item)) throw invalid();
                // A long goal is a catalog id with a level of 1 or 2 (iron, diamond); the model cannot make one up.
                if ("pursue_goal".equals(type) && (count > 2 || !dev.aicompanion.entity.CompanionPlaybooks.IDS.contains(item))) throw invalid();
                if ("walk_heading".equals(type) && !dev.aicompanion.entity.CompanionCourse.valid(item)) throw invalid();
                // A species of wood or nothing: whatever else the model wrote there («logs», a stone) names no species and is any log, as before.
                if ("gather_wood".equals(type) && dev.aicompanion.entity.CompanionWoodKind.of(item) == null) item = "";
                if ("gather_loose".equals(type) && (!LOOSE_ITEMS.contains(item) || FLINT.equals(item) && count > FLINT_STEP)) throw invalid();
                if ("place_block".equals(type) && !"minecraft:chest".equals(item) && count != 1) throw invalid();
                if (("store_items".equals(type) || "take_items".equals(type)) && !item.isEmpty() && !Set.of("resources", "all", "logs", "stone").contains(item)
                        && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                if ("give_items".equals(type) && !"logs".equals(item) && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                // «Почини кирку» / «сними чары с меча»: an item, or a kind with no material (any pickaxe of his).
                if (("repair_item".equals(type) || "disenchant_item".equals(type)) && !dev.aicompanion.entity.CompanionGrinding.KINDS.contains(item)
                        && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                // «Сделай костную муку в компостере»: plant scrap or one of its parts, or an item the player named.
                if ("compost_items".equals(type) && !item.isEmpty() && !dev.aicompanion.entity.CompanionComposting.GROUPS.contains(item)
                        && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                // fish: any fish, or one of the four the game's fishing gives.
                if ("fish".equals(type) && item.isEmpty()) item = dev.aicompanion.entity.CompanionFishing.ANY;
                if ("fish".equals(type) && !dev.aicompanion.entity.CompanionFishing.NAMES.contains(item)) throw invalid();
                if (("craft_item".equals(type) || "place_block".equals(type) || "equip_item".equals(type) || "smelt_items".equals(type) || "obtain_item".equals(type)
                        || "pick_up".equals(type)) && !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid();
                String slot = step.has("slot") ? step.get("slot").getAsString() : "auto";
                if ("survey_terrain".equals(type) && (count != 1 || !slot.equals("auto") || !dev.aicompanion.terrain.TerrainSurvey.TOPICS.contains(item))) throw invalid();
                if (("recall_landmark".equals(type) || "guide_landmark".equals(type)) &&
                        (count != 1 || !slot.equals("auto") || !dev.aicompanion.terrain.LandmarkMemory.validQuery(item))) throw invalid();
                if (!Set.of("auto", "mainhand", "offhand", "head", "chest", "legs", "feet").contains(slot)) throw invalid();
                String location = step.has("location") ? step.get("location").getAsString() : "near_player";
                if (!validLocation(location, type)) throw invalid();
                // A marker is named by the player's own word: 1..24 characters, never a coordinate triple.
                if (("remember_place".equals(type) || "go_to_place".equals(type))
                        && (!dev.aicompanion.entity.CompanionMarkerNames.valid(item) || count != 1 || !"near_player".equals(location))) throw invalid();
                // A leaf order of several stacks is carried as growing targets of a stack each while the plan has room for
                // them, the bag checked against the whole count: «добудь 500 листвы» is targets of 64 up to 448 and the whole
                // 500, not refused for being longer than a stack. The split fits the steps the plan has left after the written
                // steps still to come. With no room for a step a stack the last one carries the rest (768 beside another step
                // is eleven steps for twelve stacks: 64 up to 640, then 768 — 128 in the last), and the plan is no refusal.
                if ("obtain_item".equals(type) && count > 64) {
                    int budget = MAX_STEPS - result.size() - (steps.size() - 1 - index);
                    int chunks = Math.min(budget, (count + 63) / 64);
                    if (chunks < 1) throw invalid();
                    for (int part = 1; part < chunks; part++) result.add(new PlannedAction(type, 0, 64 * part, item, slot, location));
                    result.add(new PlannedAction(type, 0, count, item, slot, location));
                    if (result.size() > MAX_STEPS) throw invalid();
                    continue;
                }
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
                // Bone meal goes on the sapling nearest the player (one; «oak_sapling», «saplings» say the same), or on young crops as
                // harvest_crops names them (the meal itself named: any crop).
                String meal = "use_bone_meal".equals(type) ? item.toLowerCase(java.util.Locale.ROOT).replaceFirst("^minecraft:", "") : "";
                if (meal.contains("sapling") || meal.contains("propagule") || meal.equals("tree")) { item = "sapling"; count = 1; }
                else if ("farm_crops".equals(type) || "harvest_crops".equals(type) || "use_bone_meal".equals(type)) {
                    if (meal.equals("bone_meal")) item = "any";
                    // One of the crops, a pumpkin and a melon too (each named by its seeds), or for a harvest any of them; anything else spoils the plan.
                    var crop = dev.aicompanion.entity.CompanionCrops.Kind.of(item);
                    if (crop == null && !dev.aicompanion.entity.CompanionCrops.anyKind(item)) throw invalid();
                    item = crop != null ? crop.seedId() : "farm_crops".equals(type) ? "minecraft:wheat_seeds" : "any";
                }
                // A larger field is the same field in consecutive steps of at most 16 tiles.
                if ("farm_crops".equals(type)) {
                    for (int left = count; left > 0; left -= dev.aicompanion.entity.CompanionCrops.MAX_TILES)
                        result.add(new PlannedAction(type, 0, Math.min(dev.aicompanion.entity.CompanionCrops.MAX_TILES, left), item, slot, location));
                    if (result.size() > MAX_STEPS) throw invalid();
                    continue;
                }
                if ("build_pen".equals(type) || "breed_animals".equals(type)) {
                    var animal = dev.aicompanion.entity.CompanionGame.Kind.of(item);
                    if (animal == null && !dev.aicompanion.entity.CompanionGame.anyKind(item) || count > dev.aicompanion.entity.CompanionGame.MAX_PEN) throw invalid();
                    item = animal == null ? "any" : "minecraft:" + animal.key;
                }
                // One wood or ore step is time-bounded; a larger honest request becomes consecutive steps of at most 16.
                if ("gather_wood".equals(type) || "mine_ore".equals(type) || "hunt_animals".equals(type)) {
                    int chunk = "mine_ore".equals(type) ? dev.aicompanion.entity.CompanionWork.MAX_ORE : "hunt_animals".equals(type) ? dev.aicompanion.entity.CompanionWork.MAX_GAME : WOOD_STEP;
                    for (int left = count; left > 0; left -= chunk) result.add(new PlannedAction(type, 0, Math.min(chunk, left), item, slot, location));
                } else result.add(new PlannedAction(type, "wait".equals(type) ? seconds : 0, count, item, slot, location));
                if (result.size() > MAX_STEPS) throw invalid();
            }
            requireGoalAlone(result);
            requirePortalLast(result);
            requireWalkOnly(result, repeat);
            return new DecodedPlan(List.copyOf(result), repeat);
        } catch (RuntimeException exception) { throw refusedOrInvalid(exception); }
    }

    /** A validated plan: the steps as the model wrote them and the repeat it asked for, 1 when it named none. */
    public record DecodedPlan(List<PlannedAction> actions, int repeat) { }

    /**
     * The written steps as the queue runs them: the go_to_place steps {@code repeat} times in order, and every other step
     * — a remembered place, a block the wording added — once before the walk; the same list when the repeat is one.
     */
    public static List<PlannedAction> repeated(List<PlannedAction> actions, int repeat) {
        if (repeat <= 1) return actions;
        List<PlannedAction> once = actions.stream().filter(step -> !"go_to_place".equals(step.type())).toList();
        List<PlannedAction> walk = actions.stream().filter(step -> "go_to_place".equals(step.type())).toList();
        List<PlannedAction> result = new ArrayList<>(once.size() + walk.size() * repeat);
        result.addAll(once);
        for (int copy = 0; copy < repeat; copy++) result.addAll(walk);
        return List.copyOf(result);
    }

    /** A long goal runs on the server by itself: it is the only step of its plan, or the whole plan is invalid. */
    private static void requireGoalAlone(List<PlannedAction> steps) {
        if (steps.size() > 1 && steps.stream().anyMatch(step -> "pursue_goal".equals(step.type()))) throw invalid();
    }

    /** Any step after it would end the walk to the portal at once, or find him in another world. */
    private static void requirePortalLast(List<PlannedAction> steps) {
        for (int i = 0; i < steps.size() - 1; i++) if ("enter_portal".equals(steps.get(i).type())) throw invalid();
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

    /** A building the model named that the catalogue lacks keeps its own message (what is like it); any other fault is an invalid plan. */
    private static IllegalStateException refusedOrInvalid(RuntimeException exception) {
        return exception instanceof IllegalStateException refused && refused.getMessage() != null && refused.getMessage().startsWith("Модель назвала постройку") ? refused : invalid();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Модель передала некорректный план. Никакие его шаги не запущены; повтори просьбу.");
    }

    /** True when the answer was rejected as a plan (not a transport or provider failure): the local route may answer instead. */
    public static boolean rejected(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (cause instanceof IllegalStateException && message != null
                    && (message.startsWith("Модель передала некорректный план")
                        || message.startsWith("Модель вернула пустой ответ")
                        || message.startsWith("Модель ответила текстом")
                        || message.startsWith("Ответ модели оборвался")
                        || message.startsWith("Просьба содержит слишком много шагов"))) return true;
            if (cause.getCause() == cause) break;
        }
        return false;
    }
}
