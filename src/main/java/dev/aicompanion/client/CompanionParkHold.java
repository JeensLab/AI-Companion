package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionGame;
import dev.aicompanion.entity.CompanionOre;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides whether a queued request has to wait for a parked furnace batch. Only item ids are compared: it needs no
 * world, so it is safe under the queue lock. A held request is skipped, not reordered, until the batch is collected.
 */
public final class CompanionParkHold {
    /** A parked batch as the rule sees it: the raw item in the furnace and what comes out. */
    public record Parked(String input, String output) { }

    private static final Set<String> ITEM_BOUND = Set.of("give_items", "store_items", "craft_item", "verify_item", "obtain_item", "place_block");
    private static final Set<String> RECIPE_BOUND = Set.of("craft_item", "obtain_item");
    private static final Set<String> GENERIC = Set.of("", "all", "resources");
    private static final Set<String> STONE = Set.of("stone", "cobblestone", "blackstone", "cobbled_deepslate");
    private static final Set<String> LOG_SUFFIXES = Set.of("_log", "_stem", "_wood", "_hyphae");
    private static final int MAX_RECIPE_ITEMS = 64;
    private static final Function<String, Set<String>> NO_INGREDIENTS = item -> Set.of();
    private CompanionParkHold() { }

    public static boolean held(List<PlannedAction> steps, Collection<Parked> parked) { return held(steps, parked, NO_INGREDIENTS); }

    /**
     * {@code ingredients} maps a normalized item id (lowercase, no {@code minecraft:} prefix) to the normalized ids its recipe
     * consumes; the rule follows them transitively, so a craft or obtain request that needs a parked product waits for it.
     * The server supplies it from the recipe data; the rule itself touches no world.
     */
    public static boolean held(List<PlannedAction> steps, Collection<Parked> parked, Function<String, Set<String>> ingredients) {
        if (steps.isEmpty() || parked.isEmpty()) return false;
        PlannedAction first = steps.getFirst();
        return parked.stream().anyMatch(batch -> depends(first, batch, ingredients));
    }

    private static boolean depends(PlannedAction step, Parked batch, Function<String, Set<String>> ingredients) {
        String type = step.type(), item = id(step.item());
        if (ITEM_BOUND.contains(type)) return GENERIC.contains(item) || touches(item, batch) || RECIPE_BOUND.contains(type) && needs(item, batch, ingredients);
        return switch (type) {
            case "smelt_items" -> item.equals(id(batch.input())) || item.equals(id(batch.output()))
                    || id(CompanionGame.ANY_MEAT).equals(item) && CompanionGame.Kind.of(batch.input()) != null;
            case "hunt_animals" -> produces(batch, huntProducts(item));
            case "mine_ore" -> {
                CompanionOre.Kind ore = CompanionOre.Kind.of(step.item());
                yield ore != null && produces(batch, Set.of(id(ore.drop)));
            }
            case "gather_wood" -> touches("logs", batch);
            case "gather_stone" -> touches("stone", batch);
            case "gather_loose" -> touches(item, batch);
            case "gather_dirt", "follow", "wait", "come", "protect", "stop_protect", "wander", "stop", "enter_portal", "collect_smelt",
                 "take_items", "sort_chests", "build_house", "give_wood", "equip_item", "unequip_item", "hunt_mobs" -> false;
            default -> true;
        };
    }

    /** The batch's input or output is what the request names; the aliases "logs" and "stone" name a whole group the plan codec allows. */
    private static boolean touches(String item, Parked batch) { return named(item, id(batch.input())) || named(item, id(batch.output())); }

    private static boolean named(String item, String product) {
        if (item.equals(product)) return true;
        return switch (item) {
            case "logs" -> LOG_SUFFIXES.stream().anyMatch(product::endsWith);
            case "stone" -> STONE.contains(product);
            default -> false;
        };
    }

    private static boolean needs(String item, Parked batch, Function<String, Set<String>> ingredients) {
        Set<String> seen = new HashSet<>(); Deque<String> pending = new ArrayDeque<>(List.of(item));
        while (!pending.isEmpty() && seen.size() < MAX_RECIPE_ITEMS) {
            String current = pending.poll();
            if (!seen.add(current)) continue;
            for (String ingredient : ingredients.apply(current)) {
                String needed = id(ingredient);
                if (needed.isEmpty()) continue;
                if (touches(needed, batch)) return true;
                pending.add(needed);
            }
        }
        return false;
    }

    private static boolean produces(Parked batch, Set<String> products) {
        return products.contains(id(batch.input())) || products.contains(id(batch.output()));
    }

    /** What the named game drops (the meat, plus the hide, feathers or wool); «any» game drops every kind. */
    private static Set<String> huntProducts(String item) {
        CompanionGame.Kind kind = CompanionGame.Kind.of(item);
        if (kind == null) return Set.of(id(Items.BEEF), id(Items.PORKCHOP), id(Items.MUTTON), id(Items.CHICKEN), id(Items.RABBIT),
                id(Items.LEATHER), id(Items.FEATHER), id(Items.RABBIT_HIDE), id(Items.WHITE_WOOL));
        Item extra = switch (kind) {
            case COW -> Items.LEATHER; case CHICKEN -> Items.FEATHER; case RABBIT -> Items.RABBIT_HIDE; case SHEEP -> Items.WHITE_WOOL;
            case PIG -> kind.meat;
        };
        return Set.copyOf(List.of(id(kind.meat), id(extra)));
    }

    private static String id(Item item) { return id(BuiltInRegistries.ITEM.getKey(item).toString()); }
    static String id(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
    }
}
