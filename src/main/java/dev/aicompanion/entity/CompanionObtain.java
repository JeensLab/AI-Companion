package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Добудь предмет» like a player thinks it through (the player 22.10: one general «добыть» instead of code for every item): from the
 * game's own data — what a block drops, what an animal drops, what a furnace makes, what a recipe needs — down to the skills Oleg
 * already has (wood, stone, soil, ore, hunting, furnace, crafting). The result is an ordinary list of those steps, run and checked
 * one by one; nothing is handed out, everything is mined, hunted, smelted and crafted for real.
 *
 * Wood is one family (any logs, any planks: the recipes take them all), so is crafting stone; planks and sticks are made by the
 * craft that needs them. What none of the skills can get (sand, string, ender pearls…) is said before anything starts.
 */
public final class CompanionObtain {
    /** One step of the chain: an allowlisted action, its item and count. */
    public record Step(String type, String item, int count) { }
    /** The chain, or why there is none. */
    public record Plan(List<Step> steps, String refusal) {
        public boolean refused() { return refusal != null; }
    }

    static final int MAX_STEPS = 16;
    private static final int MAX_DEPTH = 6;
    /** Any log, any planks, any crafting stone; «#logs:spruce» — logs of one species, for what only that wood makes. */
    private static final String LOGS = "#logs", PLANKS = "#planks", STONE = "#stone", SPECIES = "#logs:";

    private final ServerLevel level;
    /** A bench stands within his reach: a 3x3 recipe needs no log for a new one. */
    private boolean bench;
    /** What he carries, item by item: a key takes from all items it stands for. */
    private final Map<String, Integer> bag = new HashMap<>();
    private final List<Step> steps = new ArrayList<>();
    private final Set<String> active = new HashSet<>();
    private String why;

    private CompanionObtain(ServerLevel level, List<ItemStack> carried, boolean bench) {
        this.level = level; this.bench = bench;
        for (ItemStack stack : carried) if (!stack.isEmpty()) bag.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
    }

    public static Plan plan(ServerLevel level, List<ItemStack> carried, boolean benchNearby, String requested, int count) {
        var id = ResourceLocation.tryParse(requested == null ? "" : requested.trim());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) return new Plan(List.of(), "Не понял, какой предмет нужен.");
        CompanionObtain planner = new CompanionObtain(level, carried, benchNearby || carried.stream().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE)));
        // What is asked for is that item: dark oak planks are not oak planks, a stripped log is not a log (review of PR #6: «Доски из
        // тёмного дуба 0 из 5» with oak planks in the bag; game log 27.10: «Stripped Oak Wood 0 из 7»). A plain log is any log of its
        // species, cobblestone any crafting stone.
        String species = species(item);
        String target = item == Items.COBBLESTONE ? STONE : species != null && plainLog(item) ? SPECIES + species : id.toString();
        if (planner.have(target) >= count) return new Plan(List.of(), null);
        // Planks and sticks are named by the exact item at the top: the craft that makes them is a step of its own.
        boolean crafted = new ItemStack(item).is(ItemTags.PLANKS) || item == Items.STICK;
        boolean ok = crafted
                ? planner.need(target, count, 1) && planner.add("craft_item", id.toString(), count)
                : planner.need(target, count, 0);
        if (!ok) return new Plan(List.of(), planner.why == null ? "Не знаю, как добыть «" + name(item) + "»." : planner.why);
        if (planner.steps.size() > MAX_STEPS) return new Plan(List.of(), "Для «" + name(item) + "» слишком длинная цепочка дел. Давай по частям.");
        return new Plan(List.copyOf(merged(planner.steps)), null);
    }

    /** Takes {@code count} of the key from the bag, getting what is missing first. */
    private boolean need(String key, int count, int depth) {
        int have = have(key);
        if (have >= count) { take(key, count); return true; }
        take(key, have);
        int missing = count - have;
        if (depth > MAX_DEPTH || !active.add(key)) return refuse("Цепочка для «" + label(key) + "» слишком запутанная.");
        try { return produce(key, missing, depth); } finally { active.remove(key); }
    }

    /** Gets {@code count} of the key into the bag and takes it straight out again (it is used by the caller). */
    private boolean produce(String key, int count, int depth) {
        switch (key) {
            case LOGS -> { return add("gather_wood", "", count); }
            case STONE -> { return add("gather_stone", "", count); }
            case PLANKS -> { return need(LOGS, ceil(count, 4), depth + 1); }               // the craft that needs them makes them
            case "minecraft:stick" -> { return need(PLANKS, ceil(count, 4) * 2, depth + 1); }
            case "minecraft:dirt" -> { return add("gather_dirt", "", count); }
            default -> { }
        }
        // Logs of one species: those trees are chopped, not the nearest of any kind (review of PR #6: oak was chopped for dark oak).
        if (key.startsWith(SPECIES)) return add("gather_wood", "minecraft:" + logOf(key.substring(SPECIES.length())), count);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
        // Planks of a species: from its logs; the craft that needs them makes them.
        String species = species(item);
        if (species != null && new ItemStack(item).is(ItemTags.PLANKS)) return need(SPECIES + species, ceil(count, 4), depth + 1);
        for (CompanionOre.Kind ore : CompanionOre.Kind.values())
            if (ore.drop == item) return addSplit("mine_ore", "minecraft:" + ore.key() + "_ore", ceil(count, perOre(ore)), CompanionWork.MAX_ORE);
        CompanionGame.Kind game = animalFor(item);
        if (game != null) return addSplit("hunt_animals", "minecraft:" + game.key, perAnimal(item, count), CompanionWork.MAX_GAME);
        // A furnace makes it (ingots, cooked meat, charcoal, glass, smooth stone): its raw material first.
        String smelted = cheapest(smeltingInputs(item), count, depth);
        // Named by the result ("minecraft:charcoal"): the furnace then takes whichever raw material of it he carries.
        if (smelted != null) return need(smelted, count, depth + 1) && add("smelt_items", BuiltInRegistries.ITEM.getKey(item).toString(), count);
        // A recipe makes it: its ingredients, then the craft.
        if (craft(item, count, depth)) return true;
        return refuse("Не знаю, где взять «" + name(item) + "»: копать, охотиться, плавить и крафтить его я не умею" + (why == null ? "." : ". " + why));
    }

    private boolean craft(Item item, int count, int depth) {
        String before = why;
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            var recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !result.is(item) || recipe.isSpecial()) continue;
            int crafts = ceil(count, result.getCount());
            Map<String, Integer> needed = new LinkedHashMap<>();
            boolean known = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                String option = cheapest(ingredient, crafts, depth);
                if (option == null) { known = false; break; }
                needed.merge(option, crafts, Integer::sum);
            }
            if (!known) continue;
            // A 3x3 recipe and no bench: one more log for it (craft_item sets one down).
            boolean large = recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped ? shaped.getWidth() > 2 || shaped.getHeight() > 2
                    : recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count() > 4;
            if (large && !bench) needed.merge(LOGS, 1, Integer::sum);
            var saved = save();
            boolean all = true;
            for (var entry : needed.entrySet()) if (!need(entry.getKey(), entry.getValue(), depth + 1)) { all = false; break; }
            if (all && add("craft_item", BuiltInRegistries.ITEM.getKey(item).toString(), count)) { why = before; if (large) bench = true; return true; }
            restore(saved);
        }
        return false;
    }

    /** Of the items an ingredient takes, the one already carried, else the one with the shortest chain; null if none can be got. */
    private String cheapest(Ingredient ingredient, int count, int depth) {
        if (ingredient == null) return null;
        return cheapest(options(ingredient), count, depth);
    }
    /**
     * What an ingredient takes, as keys: a family only when the recipe itself takes any of it (a chest takes any planks, a bench any
     * logs); oak stairs take oak planks, so that is the key — other planks in the bag do not make them.
     */
    private static List<String> options(Ingredient ingredient) {
        List<Item> items = new ArrayList<>();
        for (ItemStack option : ingredient.getItems()) if (!items.contains(option.getItem())) items.add(option.getItem());
        if (items.isEmpty()) return List.of();
        Set<String> kinds = new HashSet<>();
        for (Item item : items) kinds.add(String.valueOf(species(item)));
        if (items.stream().allMatch(item -> new ItemStack(item).is(ItemTags.PLANKS)))
            return kinds.size() > 1 ? List.of(PLANKS) : List.of(BuiltInRegistries.ITEM.getKey(items.getFirst()).toString());
        if (items.stream().allMatch(item -> new ItemStack(item).is(ItemTags.LOGS)))
            return kinds.size() > 1 || kinds.contains("null") ? List.of(LOGS) : List.of(SPECIES + kinds.iterator().next());
        if (items.size() > 1 && items.stream().allMatch(item -> family(item).equals(STONE))) return List.of(STONE);
        List<String> options = new ArrayList<>();
        for (Item item : items) { String key = BuiltInRegistries.ITEM.getKey(item).toString(); if (!options.contains(key)) options.add(key); }
        return options;
    }
    private String cheapest(List<String> options, int count, int depth) {
        for (String option : options) if (have(option) >= count) return option;
        String best = null;
        int bestCost = Integer.MAX_VALUE;
        for (String option : options.subList(0, Math.min(options.size(), 6))) {
            var saved = save();
            int before = cost();
            if (need(option, count, depth + 1) && cost() - before < bestCost) { bestCost = cost() - before; best = option; }
            restore(saved);
        }
        return best;
    }

    /** Everything any furnace recipe turns into the item: an iron ingot comes from raw iron as well as from the ore block. */
    private List<String> smeltingInputs(Item item) {
        List<String> options = new ArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            var recipe = holder.value();
            if (!recipe.getResultItem(level.registryAccess()).is(item)) continue;
            // Recycling a tool or armour in the furnace is no way to get nuggets: a shovel is not crafted to be melted down.
            for (ItemStack option : recipe.getIngredients().getFirst().getItems()) { if (option.isDamageableItem()) continue; String key = family(option.getItem()); if (!options.contains(key)) options.add(key); }
        }
        // What he carries first, then raw materials before blocks (the ore block itself is not what a pickaxe gives).
        options.sort(java.util.Comparator.comparing((String key) -> !key.contains("raw_")));
        return options;
    }

    private static CompanionGame.Kind animalFor(Item item) {
        if (item == Items.LEATHER) return CompanionGame.Kind.COW;
        if (item == Items.FEATHER) return CompanionGame.Kind.CHICKEN;
        if (item == Items.RABBIT_HIDE) return CompanionGame.Kind.RABBIT;
        if (new ItemStack(item).is(ItemTags.WOOL)) return item == Items.WHITE_WOOL ? CompanionGame.Kind.SHEEP : null;
        for (CompanionGame.Kind kind : CompanionGame.Kind.values()) if (kind.meat == item) return kind;
        return null;
    }
    /** Animals for that many drops: a sheep gives one wool, a cow one or two beef and about one leather. */
    private static int perAnimal(Item item, int count) {
        return item == Items.BEEF || item == Items.PORKCHOP ? ceil(count, 2) : count;
    }
    private static int perOre(CompanionOre.Kind ore) {
        return switch (ore) { case COPPER -> 2; case LAPIS, REDSTONE -> 4; default -> 1; };
    }

    private boolean add(String type, String item, int count) { steps.add(new Step(type, item, count)); return true; }
    private boolean addSplit(String type, String item, int count, int most) {
        for (int left = count; left > 0; left -= most) steps.add(new Step(type, item, Math.min(most, left)));
        return true;
    }
    private boolean refuse(String reason) { if (why == null) why = reason; return false; }
    private int cost() {
        int cost = 0;
        for (Step step : steps) cost += switch (step.type()) { case "craft_item" -> 1; case "smelt_items" -> 2; default -> 3; };
        return cost;
    }
    private record Saved(int steps, Map<String, Integer> bag, String why) { }
    private Saved save() { return new Saved(steps.size(), new HashMap<>(bag), why); }
    private void restore(Saved saved) {
        while (steps.size() > saved.steps()) steps.removeLast();
        bag.clear(); bag.putAll(saved.bag()); why = saved.why();
    }

    /** The same gathering one after another becomes one step: three «gather_wood 1» are «gather_wood 3». */
    private static List<Step> merged(List<Step> steps) {
        List<Step> result = new ArrayList<>();
        for (Step step : steps) {
            Step last = result.isEmpty() ? null : result.getLast();
            boolean gather = step.type().startsWith("gather_");
            if (last != null && gather && last.type().equals(step.type()) && last.item().equals(step.item()) && last.count() + step.count() <= 64)
                result.set(result.size() - 1, new Step(step.type(), step.item(), last.count() + step.count()));
            else result.add(step);
        }
        return result;
    }

    /** How many of what the key stands for he carries now. */
    private int have(String key) {
        int count = 0;
        for (var entry : bag.entrySet()) if (stands(key, entry.getKey())) count += entry.getValue();
        return count;
    }
    /** Takes that many of what the key stands for out of the bag of the plan. */
    private void take(String key, int count) {
        for (var entry : bag.entrySet()) {
            if (count <= 0) break;
            if (!stands(key, entry.getKey())) continue;
            int taken = Math.min(count, entry.getValue());
            entry.setValue(entry.getValue() - taken);
            count -= taken;
        }
    }
    private static boolean stands(String key, String carried) {
        if (key.equals(carried)) return true;
        if (!key.startsWith("#")) return false;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(carried));
        if (key.startsWith(SPECIES)) return plainLog(item) && key.substring(SPECIES.length()).equals(species(item));
        return family(item).equals(key);
    }
    /** A log as a tree gives it: not stripped, not bark-covered wood. */
    private static boolean plainLog(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return new ItemStack(item).is(ItemTags.LOGS) && !path.startsWith("stripped_") && (path.endsWith("_log") || path.endsWith("_stem"));
    }
    /** The wood species of a log, wood or planks item («dark_oak»), or null. */
    static String species(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
        for (String suffix : List.of("_log", "_wood", "_stem", "_hyphae", "_planks")) if (path.endsWith(suffix)) return path.substring(0, path.length() - suffix.length());
        return null;
    }
    private static String logOf(String species) {
        Item stem = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(species + "_stem"));
        return species + (stem != Items.AIR ? "_stem" : "_log");
    }

    /** Wood and crafting stone are families: any log, any planks, any cobblestone-like block serve the same recipes. */
    private static String family(Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.LOGS)) return LOGS;
        if (stack.is(ItemTags.PLANKS)) return PLANKS;
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_TOOL_MATERIALS)) return STONE;
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
    private static String label(String key) {
        if (key.startsWith(SPECIES)) return name(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(logOf(key.substring(SPECIES.length())))));
        return switch (key) { case LOGS -> "брёвна"; case PLANKS -> "доски"; case STONE -> "булыжник"; default -> name(BuiltInRegistries.ITEM.get(ResourceLocation.parse(key))); };
    }
    private static String name(Item item) { return new ItemStack(item).getHoverName().getString(); }
    private static int ceil(int value, int by) { return (value + by - 1) / by; }
}
