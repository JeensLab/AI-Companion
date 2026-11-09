package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What lies on the ground near the companion, by the meaning the player named: «курица» is the chicken meat (raw or
 * cooked), «алмазы» the gem and its ore, «брёвна» any log, «камни» stone materials. «Подбери» takes what lies here and
 * nothing else; a meaning also names the work to fall back on when nothing of it lies around.
 *
 * The lines the player reads are Russian: the server's own language is en_us, so an item's hover name is «Rotten Flesh»,
 * «Water Bucket» or «Diamond Pickaxe» there, and the words and their cases come from {@link CompanionItemWords}.
 */
final class CompanionPickUp {
    private CompanionPickUp() { }

    /**
     * A named meaning: what counts as it, how to call it in the three cases the lines need («курица лежит», «подобрал
     * курицу», «нет курицы»), and the work that gets it when nothing of it lies near.
     */
    record Meaning(String name, String accusative, String genitive, Predicate<ItemStack> matches,
                   String fallbackAction, String fallbackItem, int fallbackCount, String fallbackNote) {
        boolean is(ItemStack stack) { return !stack.isEmpty() && matches.test(stack); }
    }

    /** How long the order walks to a drop before it says so; the pickup chase gives up on its own sooner. */
    static final int ORDER_TICKS = 20 * 60;
    /** How far around him the ground is scanned, as the setting allows it. */
    static final int MIN_RADIUS = 4;
    static final int MAX_RADIUS = 32;
    /** The count the phrase route gives when the player named no number: the fallback then asks for its own amount. */
    static final int ALL_AT_ONCE = 64;
    /** What a fallback gather asks for when the player named no number. */
    private static final int GATHER_COUNT = 8;
    private static final int WOOD_COUNT = 16;
    private static final int FLINT_COUNT = 2;
    private static final int ORE_COUNT = 4;

    /** The meaning of a named item, or null for a name that is no item at all. */
    static Meaning of(String item) {
        if (item == null) return null;
        String id = item.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return null;
        var game = CompanionGame.Kind.of(id);
        if (game != null) {
            var words = CompanionItemWords.of(game.meat);
            String meat = key(game.meat);
            Item cooked = item("cooked_" + meat);
            return new Meaning(words.name(), words.accusative(), words.genitive(),
                    stack -> stack.is(game.meat) || cooked != null && stack.is(cooked),
                    "hunt_animals", "minecraft:" + game.key, 1, "Иду охотиться.");
        }
        if (CompanionGame.ANY_MEAT.equals(id)) return new Meaning("мясо", "мясо", "мяса", CompanionPickUp::anyMeat,
                "hunt_animals", "food", 1, "Иду охотиться.");
        var ore = CompanionOre.Kind.of(id);
        if (ore != null) {
            var items = oreItems(ore);
            return new Meaning(ore.word, ore.word, ore.genitive, stack -> items.contains(stack.getItem()),
                    "mine_ore", "minecraft:" + ore.key() + "_ore", ORE_COUNT, "Поищу руду.");
        }
        Item named = itemObject(id);
        if (named == null) return null;
        // «Ведро» is any bucket: the empty one and the one with water in it, as a player would take either.
        if (named == Items.BUCKET || key(named).endsWith("_bucket")) {
            var words = CompanionItemWords.of(Items.BUCKET);
            return new Meaning(words.name(), words.accusative(), words.genitive(),
                    stack -> stack.is(Items.BUCKET) || key(stack.getItem()).endsWith("_bucket"), null, null, 0, "");
        }
        // «Чернила» and «чернильный мешок» are the ink sac, the squid's own drop.
        if (named == Items.INK_SAC || named == Items.GLOW_INK_SAC) {
            var words = CompanionItemWords.of(Items.INK_SAC);
            return new Meaning(words.name(), words.accusative(), words.genitive(),
                    stack -> stack.is(Items.INK_SAC) || stack.is(Items.GLOW_INK_SAC), null, null, 0, "");
        }
        var stack = new ItemStack(named);
        if (stack.is(ItemTags.LOGS)) return new Meaning("бревно", "бревно", "бревна", drop -> drop.is(ItemTags.LOGS),
                "gather_wood", "", WOOD_COUNT, "Пойду за деревом.");
        if (stack.is(ItemTags.PLANKS)) return new Meaning("доска", "доску", "досок", drop -> drop.is(ItemTags.PLANKS),
                "gather_wood", "", WOOD_COUNT, "Пойду за деревом.");
        if (stack.is(ItemTags.WOOL)) return new Meaning("шерсть", "шерсть", "шерсти", drop -> drop.is(ItemTags.WOOL),
                "hunt_animals", "minecraft:sheep", 1, "Иду охотиться.");
        if (stack.is(ItemTags.STONE_TOOL_MATERIALS) || named == Items.STONE || named == Items.DEEPSLATE) {
            return new Meaning("камень", "камень", "камня",
                    drop -> drop.is(ItemTags.STONE_TOOL_MATERIALS) || drop.is(Items.STONE) || drop.is(Items.DEEPSLATE),
                    "gather_stone", "", GATHER_COUNT, "Пойду за камнем.");
        }
        if (named == Items.DIRT || named == Items.GRASS_BLOCK || named == Items.COARSE_DIRT || named == Items.ROOTED_DIRT) {
            return new Meaning("земля", "землю", "земли", drop -> drop.is(Items.DIRT) || drop.is(Items.GRASS_BLOCK)
                    || drop.is(Items.COARSE_DIRT) || drop.is(Items.ROOTED_DIRT), "gather_dirt", "", GATHER_COUNT, "Накопаю земли.");
        }
        if (named == Items.SAND) return new Meaning("песок", "песок", "песка", drop -> drop.is(Items.SAND),
                "gather_loose", "minecraft:sand", GATHER_COUNT, "Возьму лопату.");
        if (named == Items.RED_SAND) return new Meaning("красный песок", "красный песок", "красного песка", drop -> drop.is(Items.RED_SAND),
                "gather_loose", "minecraft:red_sand", GATHER_COUNT, "Возьму лопату.");
        if (named == Items.GRAVEL) return new Meaning("гравий", "гравий", "гравия", drop -> drop.is(Items.GRAVEL),
                "gather_loose", "minecraft:gravel", GATHER_COUNT, "Возьму лопату.");
        if (named == Items.FLINT) return new Meaning("кремень", "кремень", "кремня", drop -> drop.is(Items.FLINT),
                "gather_loose", "minecraft:flint", FLINT_COUNT, "Возьму лопату.");
        if (named == Items.LEATHER) return new Meaning("кожа", "кожу", "кожи", drop -> drop.is(Items.LEATHER),
                "hunt_animals", "minecraft:cow", 1, "Иду охотиться.");
        if (named == Items.FEATHER) return new Meaning("перо", "перо", "пера", drop -> drop.is(Items.FEATHER),
                "hunt_animals", "minecraft:chicken", 1, "Иду охотиться.");
        if (named == Items.EGG) return new Meaning("яйцо", "яйцо", "яйца", drop -> drop.is(Items.EGG),
                "hunt_animals", "minecraft:chicken", 1, "Иду охотиться.");
        if (named == Items.STICK) return new Meaning("палка", "палку", "палки", drop -> drop.is(Items.STICK), null, null, 0, "");
        // Anything else is taken by its own name; with no work to fall back on the honest answer is all there is.
        var words = CompanionItemWords.of(named);
        if (words != null) return new Meaning(words.name(), words.accusative(), words.genitive(), drop -> drop.is(named), null, null, 0, "");
        String quoted = "«" + stack.getHoverName().getString() + "»";
        return new Meaning(quoted, quoted, quoted, drop -> drop.is(named), null, null, 0, "");
    }

    /**
     * The drops of that meaning lying within the radius, nearest first: in water, on a ledge or at another height all
     * count, a gift handed to the player and a stack aimed at somebody else do not.
     */
    static List<ItemEntity> find(ServerLevel level, BlockPos center, int radius, Meaning meaning, int limit, java.util.UUID playerId) {
        List<ItemEntity> found = new ArrayList<>();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius))) {
            if (!within(drop, center, radius) || !pickable(drop, playerId) || !meaning.is(drop.getItem())) continue;
            found.add(drop);
        }
        found.sort(Comparator.comparingDouble(drop -> drop.distanceToSqr(center.getCenter())));
        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** How many items those drops hold: «Рядом лежит курица (2), но сумка полна.» */
    static int total(List<ItemEntity> drops) {
        int count = 0;
        for (ItemEntity drop : drops) count += drop.getItem().getCount();
        return count;
    }

    /**
     * «Лежит рядом: камень (3), курица (1).» — what really lies around, for the honest answer when nothing matched.
     * The radius is the scan's own: the answer never names a drop the order would not have looked at. The names are
     * Russian: the server reads the item's hover name in en_us («Water Bucket», «Diamond Pickaxe»).
     */
    static String lying(ServerLevel level, BlockPos center, int radius, java.util.UUID playerId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius))) {
            if (!within(drop, center, radius) || !pickable(drop, playerId)) continue;
            String label = label(drop.getItem());
            counts.merge(label, drop.getItem().getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "Рядом вообще ничего не лежит.";
        StringBuilder line = new StringBuilder("Лежит рядом: ");
        int shown = 0;
        for (var entry : counts.entrySet()) {
            if (shown++ > 0) line.append(", ");
            line.append(entry.getKey()).append(" (").append(entry.getValue()).append(')');
            if (shown == 3) break;
        }
        return line.append('.').toString();
    }

    /** A drop the order may take: alive, not empty, not a gift, not junk he let go and not aimed at somebody else. */
    private static boolean pickable(ItemEntity drop, java.util.UUID playerId) {
        if (!drop.isAlive() || drop.getItem().isEmpty()) return false;
        if (drop.getPersistentData().getBoolean("aicompanion:gift")) return false;
        // Junk he let go to free a slot: the loose pickup leaves it, so the order must leave it too.
        if (drop.getPersistentData().getBoolean(CompanionBag.JUNK)) return false;
        return drop.getTarget() == null || drop.getTarget().equals(playerId);
    }

    /** The radius is a distance, not the reach of the box the entities were asked for: a drop just outside is outside. */
    private static boolean within(ItemEntity drop, BlockPos center, int radius) {
        return drop.position().distanceToSqr(center.getCenter()) <= (double) radius * radius;
    }

    /** What a drop is called in Russian: by the meaning it belongs to, else by the word table, else its own name in quotes. */
    private static String label(ItemStack stack) {
        var meaning = of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (meaning != null) return meaning.name();
        String word = CompanionItemWords.label(stack);
        return word != null ? word : "«" + stack.getHoverName().getString() + "»";
    }

    private static boolean anyMeat(ItemStack stack) {
        for (CompanionGame.Kind kind : CompanionGame.Kind.values()) {
            Item cooked = item("cooked_" + key(kind.meat));
            if (stack.is(kind.meat) || cooked != null && stack.is(cooked)) return true;
        }
        return false;
    }

    /** The gem or ore block, its deepslate variant, the raw drop and the smelted result: all of them are that ore. */
    private static java.util.Set<Item> oreItems(CompanionOre.Kind ore) {
        java.util.Set<Item> items = new java.util.HashSet<>();
        items.add(ore.drop);
        for (String suffix : new String[]{"", "_ore", "_ingot"}) {
            Item found = item(ore.key() + suffix);
            if (found != null) items.add(found);
        }
        for (String prefix : new String[]{"deepslate_", "raw_", "nether_"}) {
            Item found = item(prefix + ore.key() + "_ore");
            if (found == null) found = item(prefix + ore.key());
            if (found != null) items.add(found);
        }
        return items;
    }

    private static String key(Item item) { return BuiltInRegistries.ITEM.getKey(item).getPath(); }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(path)).orElse(null);
    }

    private static Item itemObject(String id) {
        var location = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }
}
