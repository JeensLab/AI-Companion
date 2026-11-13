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
 * craft that needs them. Sand, gravel and flint are dug (flint out of gravel), so glass and a flint and steel follow from their
 * recipes. What a block gives when it is simply broken (sugar cane, flowers, clay: CompanionPickable) is found and picked, what a
 * monster drops (string, bones, gunpowder: CompanionMobDrops) is fought for when one is about. A ripe field about him is reaped
 * (wheat, carrots, potatoes, beetroot; none ripe is said, nothing is sown and waited for), and bone meal comes out of a composter,
 * of the scrap in his bag and the plants he picks for it, before any skeleton is fought. What a tool in hand gets (CompanionChores)
 * is got so: wool shorn off a sheep with shears he has, milk of a cow into his bucket, the eggs that lie about the hens, a log
 * of his stripped with an axe, the combs and the honey of a full hive over a campfire. What none of the skills can get
 * (an ink sac, a blaze rod…) is said before anything starts.
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
    private static final String FLINT = "minecraft:flint";
    private static final int FLINT_STEP = 8;
    private static final String LOGS = "#logs", PLANKS = "#planks", STONE = "#stone";

    private final ServerLevel level;
    private final List<ItemStack> carried;
    /** A bench stands within his reach: a 3x3 recipe needs no log for a new one. */
    private boolean bench;
    private final Map<String, Integer> bag = new HashMap<>();
    private final List<Step> steps = new ArrayList<>();
    private final Set<String> active = new HashSet<>();
    private String why;
    /**
     * Whether the world about him has the item for the taking: a block that gives it, a ripe crop of it, a composter standing by.
     * Null when the world about him is not known: nothing is picked or reaped then.
     */
    private final java.util.function.Predicate<Item> grows;
    private final Map<Item, Boolean> grown = new HashMap<>();
    /** Whether a monster that drops the item is about him now; null when the world about him is not known: none is hunted then. */
    private final java.util.function.Predicate<Item> roams;
    private final Map<Item, Boolean> roamed = new HashMap<>();
    /** He carries a sword or an axe for a fight. */
    private final boolean blade;
    /** What he would pick and does not see about him: named when the plan is refused. */
    private final Set<String> absent = new java.util.LinkedHashSet<>();
    private static final String ABSENT = "Рядом не вижу";
    private static final String BONE_MEAL = "minecraft:bone_meal";
    /** Layers of a composter that make one bone meal, and how much more is gathered than the average needs: each piece fills by chance. */
    private static final int LAYERS = 7;
    private static final double SPARE = 1.25;

    private CompanionObtain(ServerLevel level, List<ItemStack> carried, boolean bench, java.util.function.Predicate<Item> grows, java.util.function.Predicate<Item> roams) {
        this.level = level; this.carried = carried; this.bench = bench; this.grows = grows; this.roams = roams;
        blade = carried.stream().anyMatch(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES));
        for (ItemStack stack : carried) if (!stack.isEmpty()) bag.merge(key(stack.getItem()), stack.getCount(), Integer::sum);
    }

    public static Plan plan(ServerLevel level, List<ItemStack> carried, boolean benchNearby, String requested, int count) {
        return plan(level, carried, benchNearby, requested, count, null);
    }

    /** {@code grows}: whether a block that gives the item is within his reach now, so of the flowers a dye takes the one that grows here is picked. */
    public static Plan plan(ServerLevel level, List<ItemStack> carried, boolean benchNearby, String requested, int count, java.util.function.Predicate<Item> grows) {
        return plan(level, carried, benchNearby, requested, count, grows, null);
    }

    /** {@code roams}: whether a monster that drops the item is within his reach now. */
    public static Plan plan(ServerLevel level, List<ItemStack> carried, boolean benchNearby, String requested, int count, java.util.function.Predicate<Item> grows,
                            java.util.function.Predicate<Item> roams) {
        var id = ResourceLocation.tryParse(requested == null ? "" : requested.trim());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) return new Plan(List.of(), "Не понял, какой предмет нужен.");
        CompanionObtain planner = new CompanionObtain(level, carried, benchNearby || carried.stream().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE)), grows, roams);
        // Logs the player asked for by name are that species, wherever the plan goes on; inside a chain wood stays a family (key).
        var species = CompanionWoodKind.of(id.toString());
        if (species != null && plainLog(id.getPath())) {
            int have = carried.stream().filter(stack -> stack.is(species.items)).mapToInt(ItemStack::getCount).sum();
            return new Plan(have >= count ? List.of() : List.of(new Step("gather_wood", id.toString(), count - have)), null);
        }
        // A stripped block is the plain one of his, set down and worked with an axe (CompanionChores): the plain one first, its own way.
        var grown = CompanionChores.unstripped(item);
        if (grown != null) return planner.stripped(id, item, grown.asItem(), count);
        // Wood of a species that chopping does not give: the wood block is crafted of its own logs.
        if (species != null) return namedWood(carried, id, item, count);
        String target = key(item);
        // Stone is a family inside a chain only: asked for by name, digging promises cobblestone and nothing else.
        if (target.equals(STONE) && item != Items.COBBLESTONE)
            return new Plan(List.of(), carried(carried, item) >= count ? null : "Камень копаю какой попадётся: именно «" + name(item) + "» добыть не умею.");
        if (planner.bag.getOrDefault(target, 0) >= count) return new Plan(List.of(), null);
        // The wood families are named by the exact item at the top: «доски» means real planks in the bag at the end.
        boolean ok = target.equals(PLANKS) || target.equals("minecraft:stick")
                ? planner.need(target, count, 1) && planner.add("craft_item", id.toString(), count)
                : planner.need(target, count, 0);
        if (!ok) {
            String reason = planner.why == null ? "Не знаю, как добыть «" + name(item) + "»." : planner.why;
            // «Красную краску» he looked for in every flower that gives it: all of them are named, not the first.
            return new Plan(List.of(), planner.absent.isEmpty() || reason.startsWith(ABSENT) ? reason : reason + " " + ABSENT + ": " + String.join(", ", planner.absent) + ".");
        }
        if (planner.steps.size() > MAX_STEPS) return new Plan(List.of(), "Для «" + name(item) + "» слишком длинная цепочка дел. Давай по частям.");
        return new Plan(List.copyOf(merged(planner.steps)), null);
    }

    private static int carried(List<ItemStack> carried, Item item) {
        return carried.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** «stripped_oak_log»: an axe of his, the logs of that species chopped (the wood block made, the bamboo block crafted), then strip_log. */
    private Plan stripped(ResourceLocation id, Item item, Item grown, int count) {
        int missing = count - carried(carried, item);
        if (missing <= 0) return new Plan(List.of(), null);
        if (!strip(id.toString(), grown, missing, 0)) return new Plan(List.of(), why == null ? "Не знаю, как добыть «" + name(item) + "»." : why);
        return new Plan(List.copyOf(merged(steps)), null);
    }

    /** The steps that strip {@code count} more: an axe unless he has one, the plain blocks he does not carry, strip_log. */
    private boolean strip(String stripped, Item grown, int count, int depth) {
        boolean axe = carried.stream().anyMatch(stack -> stack.is(ItemTags.AXES)) || steps.stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals("minecraft:wooden_axe"));
        if (!axe && !need("minecraft:wooden_axe", 1, depth + 1)) return false;
        ResourceLocation plain = BuiltInRegistries.ITEM.getKey(grown);
        // ponytail: the plain blocks are counted as he carries them now, not less what this plan takes of them elsewhere; short of them,
        // strip_log says how many it made and the chain plans the rest again.
        int lacking = count - carried(carried, grown);
        if (lacking > 0 && plainLog(plain.getPath())) addSplit("gather_wood", plain.toString(), lacking, 64);
        else if (lacking > 0 && CompanionWoodKind.of(plain.toString()) != null) {
            Plan wood = namedWood(carried, plain, grown, count);
            if (wood.refused()) return refuse(wood.refusal());
            steps.addAll(wood.steps());
        } else if (lacking > 0 && !produce(plain.toString(), lacking, depth + 1)) return false;
        return addSplit("strip_log", stripped, count, 64);
    }

    /** «birch_wood»: four logs of that species make three. */
    private static Plan namedWood(List<ItemStack> carried, ResourceLocation id, Item item, int count) {
        String path = id.getPath();
        int missing = count - carried(carried, item);
        if (missing <= 0) return new Plan(List.of(), null);
        var log = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.replace("_wood", "_log").replace("_hyphae", "_stem"));
        Item logItem = BuiltInRegistries.ITEM.getOptional(log).orElse(null);
        if (logItem == null) return new Plan(List.of(), "Не знаю, как добыть «" + name(item) + "».");
        int logs = ceil(missing, 3) * 4 - carried(carried, logItem);
        List<Step> steps = new ArrayList<>();
        for (int left = logs; left > 0; left -= 64) steps.add(new Step("gather_wood", log.toString(), Math.min(64, left)));
        steps.add(new Step("craft_item", id.toString(), missing));
        return new Plan(List.copyOf(steps), null);
    }

    /** Takes {@code count} of the key from the bag, getting what is missing first. */
    private boolean need(String key, int count, int depth) {
        int have = bag.getOrDefault(key, 0);
        if (have >= count) { bag.put(key, have - count); return true; }
        bag.put(key, 0);
        int missing = count - have;
        if (depth > MAX_DEPTH || !active.add(key)) return refuse("Цепочка для «" + label(key) + "» слишком запутанная.");
        try { return produce(key, missing, depth); } finally { active.remove(key); }
    }

    /** Gets {@code count} of the key into the bag and takes it straight out again (it is used by the caller). */
    private boolean produce(String key, int count, int depth) {
        switch (key) {
            case LOGS -> { return addSplit("gather_wood", "", count, 64); }
            case STONE -> { return addSplit("gather_stone", "", count, 64); }
            case PLANKS -> { return need(LOGS, ceil(count, 4), depth + 1); }               // the craft that needs them makes them
            case "minecraft:stick" -> { return need(PLANKS, ceil(count, 4) * 2, depth + 1); }
            case "minecraft:dirt" -> { return addSplit("gather_dirt", "", count, 64); }
            case "minecraft:sand", "minecraft:red_sand", "minecraft:gravel" -> { return addSplit("gather_loose", key, count, 64); }
            case FLINT -> { return addSplit("gather_loose", key, count, FLINT_STEP); }   // gravel dug (and re-placed) until this much flint drops
            // An empty bucket of his (or one made now) filled from living water (CompanionWaterFill); none to be had in the Nether or the
            // End, or with the game's rule that refills water off: then no bucket is made for it either.
            case "minecraft:water_bucket" -> {
                String refusal = CompanionWaterFill.refusal(level);
                if (refusal != null) return refuse(refusal);
                return need("minecraft:bucket", count, depth + 1) && add("fill_bucket", key, count);
            }
            // Lava he walks to, set by his bucket of water and mined (CompanionObsidian); with a pickaxe that takes it only, he does not go for diamonds.
            case "minecraft:obsidian" -> {
                String refusal = CompanionObsidian.refusal(level, carried);
                if (refusal != null) return refuse(refusal);
                return need("minecraft:water_bucket", 1, depth + 1) && add("mine_obsidian", key, count);
            }
            // An empty bucket of his (or one made now) under a cow, a mooshroom or a goat about him (CompanionChores).
            case "minecraft:milk_bucket" -> {
                if (!about(Items.MILK_BUCKET)) { absent.add("коровы, чтобы надоить молока"); return refuse(ABSENT + " коровы, чтобы надоить молока."); }
                return need("minecraft:bucket", count, depth + 1) && add("milk_animal", key, count);
            }
            // A full hive about him: three combs to his shears, honey into his bottle (CompanionChores), a campfire of his under it
            // unless one burns there. Shears are made by the whole chain: there is no other way to a comb, as a blow is to wool.
            case "minecraft:honeycomb", "minecraft:honey_bottle" -> {
                Item wanted = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
                // ponytail: honey bottles are not poured back out of a honey block he carries; the recipe is there when someone asks.
                if (!about(wanted)) { absent.add("полного улья с костром или местом для него"); return refuse(ABSENT + " полного улья, под которым горит костёр или есть место для него."); }
                if (key.equals("minecraft:honeycomb") ? !hasShears() && !need("minecraft:shears", 1, depth + 1) : !need("minecraft:glass_bottle", count, depth + 1)) return false;
                return (about(Items.CAMPFIRE) || need("minecraft:campfire", 1, depth + 1)) && addSplit("harvest_hive", key, count, 64);
            }
            // Bones he carries are ground as the recipe says; else a composter, the safe way; a skeleton only with nothing here to compost.
            case BONE_MEAL -> {
                if (bag.getOrDefault("minecraft:bone", 0) == 0 && bag.getOrDefault("minecraft:bone_block", 0) == 0) {
                    var saved = save();
                    boolean hadBench = bench;
                    if (composted(count, depth)) return true;
                    restore(saved);
                    bench = hadBench;
                    // The one way left is a bone, and that is a skeleton's: both are named when neither is about.
                    if (!hunted(Items.BONE)) return refuse(ABSENT + " ни цветов для компостера, ни скелета, с которого падает кость.");
                }
            }
            default -> { }
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
        // A stripped log inside a chain (a hanging sign takes six of its species): stripped of logs chopped by name.
        var grown = CompanionChores.unstripped(item);
        if (grown != null) { int lacking = count - carried(carried, item); return lacking <= 0 || strip(key, grown.asItem(), lacking, depth); }
        // Wool of a sheep about him that wears the colour is shorn, with the shears he has: the sheep lives. No shears: as before.
        if (CompanionChores.fleece(item) != null && about(item) && shears(depth)) return addSplit("shear_sheep", key, count, 64);
        // A blow at a shorn sheep gives no wool: where the world about him is known, one is hunted for it only if it wears white wool.
        if (item == Items.WHITE_WOOL && grows != null && !about(item)) { absent.add("овцы с шерстью"); return refuse(ABSENT + " овцы, на которой есть шерсть."); }
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
        // A field grows it: the ripe crops about him are reaped and sown again. None ripe: growing takes a day, he does not wait.
        CompanionCrops.Kind crop = CompanionCrops.Kind.yielding(item);
        if (crop != null) {
            if (about(item)) return addSplit("harvest_crops", crop.seedId(), ceil(count, crop == CompanionCrops.Kind.CARROT || crop == CompanionCrops.Kind.POTATO ? 2 : 1), CompanionCrops.MAX_HARVEST);
            // Named among what is not about him too: a refusal inside a recipe (bread) is forgotten with the recipe.
            absent.add("спелой грядки, чтобы собрать " + crop.plant + " (могу сделать огород: вырастет — соберу)");
            return refuse(ABSENT + " спелой грядки, чтобы собрать " + crop.plant + ". Могу сделать огород: вырастет — соберу.");
        }
        // Eggs lie where the hens walk: those are picked up. A hen lays in five to ten minutes, and that is not waited for.
        if (item == Items.EGG) {
            if (about(item)) return addSplit("collect_eggs", key, count, 64);
            absent.add("снесённых яиц (куры несутся раз в 5–10 минут)");
            return refuse(ABSENT + " снесённых яиц: куры несутся раз в 5–10 минут.");
        }
        // A block gives it when broken (sugar cane, a flower, clay): found and picked, if one is to be had here.
        if (picked(item)) return addSplit("gather_block", key, count, 64);
        // A monster drops it (string, a bone, gunpowder): fought for, if one is about.
        List<String> prey = CompanionMobDrops.names(level, item);
        // Not with bare hands (in game 12.11: five spiders, one killed, and he was dead): a wooden sword first, unless he carries a blade.
        if (hunted(item)) return (armed() || need("minecraft:wooden_sword", 1, depth + 1)) && addSplit("hunt_drop", key, count, 64);
        if (!CompanionPickable.sources(level, item).isEmpty()) { absent.add(name(item)); return refuse(ABSENT + ", где взять «" + name(item) + "»: отведи меня туда, где это есть."); }
        if (!prey.isEmpty()) { absent.add(name(item) + " (" + String.join(", ", prey) + ")"); return refuse(ABSENT + ", с кого добыть «" + name(item) + "» (" + String.join(", ", prey) + ")."); }
        return refuse("Не знаю, где взять «" + name(item) + "»: копать, охотиться, плавить и крафтить его я не умею" + (why == null ? "." : ". " + why));
    }

    /** Of the recipes that make it, the cheapest: blue dye of the lapis he carries or the cornflower at hand rather than of a mine. */
    private boolean craft(Item item, int count, int depth) {
        String before = why;
        List<net.minecraft.world.item.crafting.CraftingRecipe> recipes = new ArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (!result.isEmpty() && result.is(item) && !holder.value().isSpecial()) recipes.add(holder.value());
        }
        net.minecraft.world.item.crafting.CraftingRecipe best = recipes.size() == 1 ? recipes.getFirst() : null;
        int bestCost = Integer.MAX_VALUE;
        if (best == null) for (var recipe : recipes) {
            var saved = save();
            boolean hadBench = bench;
            int start = cost();
            if (craft(recipe, item, count, depth) && cost() - start < bestCost) { bestCost = cost() - start; best = recipe; }
            restore(saved);
            bench = hadBench;
        }
        if (best == null) return false;
        var saved = save();
        boolean hadBench = bench;
        if (craft(best, item, count, depth)) { why = before; return true; }
        restore(saved);
        bench = hadBench;
        return false;
    }

    /** The steps of one recipe; on false the caller puts the plan back as it was. */
    private boolean craft(net.minecraft.world.item.crafting.CraftingRecipe recipe, Item item, int count, int depth) {
        int crafts = ceil(count, recipe.getResultItem(level.registryAccess()).getCount());
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            String option = cheapest(ingredient, crafts, depth);
            if (option == null) return false;
            needed.merge(option, crafts, Integer::sum);
        }
        // A 3x3 recipe and no bench: one more log for it (craft_item sets one down).
        boolean large = recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped ? shaped.getWidth() > 2 || shaped.getHeight() > 2
                : recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count() > 4;
        if (large && !bench) needed.merge(LOGS, 1, Integer::sum);
        for (var entry : needed.entrySet()) if (!need(entry.getKey(), entry.getValue(), depth + 1)) return false;
        if (large) bench = true;
        return add("craft_item", BuiltInRegistries.ITEM.getKey(item).toString(), count);
    }

    /** Of the items an ingredient takes, the one already carried, else the one with the shortest chain; null if none can be got. */
    private String cheapest(Ingredient ingredient, int count, int depth) {
        if (ingredient == null) return null;
        // A recipe that names one stripped block (a hanging sign) takes no other log: that block, not the family of logs.
        ItemStack[] named = ingredient.getItems();
        if (named.length == 1 && CompanionChores.unstripped(named[0].getItem()) != null) return cheapest(List.of(BuiltInRegistries.ITEM.getKey(named[0].getItem()).toString()), count, depth);
        List<String> options = new ArrayList<>();
        for (ItemStack option : ingredient.getItems()) { String key = key(option.getItem()); if (!options.contains(key)) options.add(key); }
        return cheapest(options, count, depth);
    }
    private String cheapest(List<String> options, int count, int depth) {
        for (String option : options) if (bag.getOrDefault(option, 0) >= count) return option;
        String best = null;
        int bestCost = Integer.MAX_VALUE;
        // Only six are tried, so what a skill takes straight from the world goes first: white wool among the sixteen a dyeing takes.
        List<String> ordered = options.stream().sorted(java.util.Comparator.comparing((String option) -> !direct(option))).toList();
        for (String option : ordered.subList(0, Math.min(ordered.size(), 6))) {
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
            for (ItemStack option : recipe.getIngredients().getFirst().getItems()) { String key = key(option.getItem()); if (!options.contains(key)) options.add(key); }
        }
        // What he carries first, then raw materials before blocks (the ore block itself is not what a pickaxe gives).
        options.sort(java.util.Comparator.comparing((String key) -> !key.contains("raw_")));
        return options;
    }

    private boolean picked(Item item) {
        return !CompanionPickable.sources(level, item).isEmpty() && about(item);
    }

    private boolean about(Item item) {
        return grows != null && grown.computeIfAbsent(item, grows::test);
    }

    /**
     * Bone meal of a composter (compost_meal): the plant scrap of his bag (flowers, leaves, the saplings and seeds above his
     * reserve), and where that is not enough on average, the flower about him that fills the most. Scrap only: the food, the
     * harvest and the cane he carries are the player's. A composter of his own unless one stands by. False with nothing to compost.
     */
    private boolean composted(int count, int depth) {
        double scrap = CompanionComposting.scrapLayers(carried);
        // ponytail: the scrap is counted as he carries it now, not less what this plan takes of it elsewhere (a flower for a dye);
        // and a composter left part full by the last try is not known here. Either way the chain plans the rest again.
        if (scrap < LAYERS * count) {
            double missing = LAYERS * count * SPARE - scrap;
            Item best = null;
            int bestPieces = Integer.MAX_VALUE;
            for (Item plant : BuiltInRegistries.ITEM) {
                ItemStack one = new ItemStack(plant);
                if (!CompanionComposting.scrap(one) || CompanionPickable.sources(level, plant).isEmpty()) continue;
                int pieces = (int) Math.ceil(missing / net.minecraft.world.level.block.ComposterBlock.getValue(one));
                if (pieces < bestPieces && picked(plant)) { best = plant; bestPieces = pieces; }
            }
            if (best != null) addSplit("gather_block", key(best), bestPieces, 64);
            // None to pick: the little he carries still goes into a composter that stands by; none is built for it.
            else if (scrap <= 0 || !about(Items.COMPOSTER)) return false;
        }
        // His own composter the work makes itself, of any wood (in game 12.11: planned as a recipe it came to oak slabs among dark
        // oaks, and the craft refused): seven slabs are two logs, a bench for them one more.
        // Planks and slabs he carries make it as well: wood is got only if the bag does not.
        if (!about(Items.COMPOSTER) && bag.getOrDefault("minecraft:composter", 0) == 0 && !CompanionCrafting.plan(level, carried, "minecraft:composter", 1, bench).success()
                && !need(LOGS, bench ? 2 : 3, depth + 1)) return false;
        return addSplit("compost_meal", "scrap", count, 64);
    }

    /** A blade for the fight is his: carried, or made earlier in this plan. */
    private boolean armed() {
        return blade || steps.stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals("minecraft:wooden_sword"));
    }

    /** Shears for the sheep are his: carried, made earlier in this plan, or made now of two ingots he carries; no mine is dug for them. */
    private boolean shears(int depth) {
        return hasShears() || bag.getOrDefault("minecraft:iron_ingot", 0) >= 2 && need("minecraft:shears", 1, depth + 1);
    }

    private boolean hasShears() {
        return bag.getOrDefault("minecraft:shears", 0) > 0 || steps.stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals("minecraft:shears"));
    }

    private boolean hunted(Item item) {
        return roams != null && !CompanionMobDrops.sources(level, item).isEmpty() && roamed.computeIfAbsent(item, roams::test);
    }

    /** A family, an ore's drop, an animal's, a block's that stands here or the wool of a sheep he has shears for: one of his skills gets it, whatever the bag holds. */
    private boolean direct(String key) {
        if (key.startsWith("#")) return true;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
        for (CompanionOre.Kind ore : CompanionOre.Kind.values()) if (ore.drop == item) return true;
        return animalFor(item) != null || picked(item) || hunted(item) || CompanionChores.fleece(item) != null && about(item) && bag.getOrDefault("minecraft:shears", 0) > 0;
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
        return switch (ore) {
            case COPPER -> 2;
            case LAPIS, REDSTONE -> 4; // raw drops one block gives without Fortune
            case COAL, IRON, GOLD, DIAMOND, EMERALD -> 1;
        };
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
            if (last != null && gather && last.type().equals(step.type()) && last.item().equals(step.item()) && last.count() + step.count() <= (step.item().equals(FLINT) ? FLINT_STEP : 64))
                result.set(result.size() - 1, new Step(step.type(), step.item(), last.count() + step.count()));
            else result.add(step);
        }
        return result;
    }

    /** A log as it grows: stripped logs and wood blocks are crafted or made with an axe, chopping does not give them. */
    private static boolean plainLog(String path) { return !path.startsWith("stripped_") && (path.endsWith("_log") || path.endsWith("_stem")); }

    /** Wood and crafting stone are families: any log, any planks, any cobblestone-like block serve the same recipes. */
    private static String key(Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.LOGS)) return LOGS;
        if (stack.is(ItemTags.PLANKS)) return PLANKS;
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_TOOL_MATERIALS)) return STONE;
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
    private static String label(String key) {
        return switch (key) { case LOGS -> "брёвна"; case PLANKS -> "доски"; case STONE -> "булыжник"; default -> name(BuiltInRegistries.ITEM.get(ResourceLocation.parse(key))); };
    }
    private static String name(Item item) { return new ItemStack(item).getHoverName().getString(); }
    private static int ceil(int value, int by) { return (value + by - 1) / by; }
}
