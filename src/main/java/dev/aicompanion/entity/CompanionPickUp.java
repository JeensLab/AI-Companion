package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What lies on the ground near the companion, by the thing the player named: an item of the game's own registry, a group
 * of it (any log, any plank, any bucket, any wool) or every drop at once («всё»). Nothing here is a list of items: a
 * group is the item's own tag, so a log or a bucket of a mod is that group too. A named drop is called by its id and
 * the client says the name the game shows for it in the player's language, so a modded thing is named by its own lang
 * file as well. A meaning also names the work to fall back on when nothing of it lies around; where the registry gives
 * no work to fall back on, the honest answer is all there is.
 *
 * The lines the player reads are Russian; the item names inside them come from the game, not from a table here.
 */
public final class CompanionPickUp {
    private CompanionPickUp() { }

    /** «всё»: any drop lying around, taken by «подбери всё» or a bare «подбери». */
    public static final String ANY_ITEM = "minecraft:any";

    /**
     * A named meaning: what counts as it, how to call it in the three cases the lines need («предмет «…» лежит»,
     * «подобрал предмет «…»», «нет предмета «…»»), and the work that gets it when nothing of it lies near.
     */
    public record Meaning(String name, String accusative, String genitive, Predicate<ItemStack> matches,
                   String fallbackAction, String fallbackItem, int fallbackCount, String fallbackNote) {
        public boolean is(ItemStack stack) { return !stack.isEmpty() && matches.test(stack); }
    }

    /** A drop lying here, as the model's list: its id, the name the game shows for it and how many lie there. */
    public record Drop(String id, String name, int count) { }

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

    /** «Всё, что лежит»: every drop, in one order. */
    private static final Meaning ANY = new Meaning("вещи", "вещи", "вещей", stack -> true, null, null, 0, "");

    /**
     * A group of the registry's own tags: what the player means by «брёвна», «доски», «шерсть», «камень», «земля»,
     * «песок» or «ведро». The tag is the whole meaning, so the same word takes a modded log or bucket as well.
     */
    private record Group(TagKey<Item> tag, Item base, String name, String accusative, String genitive,
                         String action, String item, int count, String note) { }

    private static final List<Group> GROUPS = List.of(
            new Group(ItemTags.LOGS, null, "брёвна", "брёвна", "брёвен", "gather_wood", "", WOOD_COUNT, "Пойду за деревом."),
            new Group(ItemTags.PLANKS, null, "доски", "доски", "досок", "gather_wood", "", WOOD_COUNT, "Пойду за деревом."),
            new Group(ItemTags.WOOL, null, "шерсть", "шерсть", "шерсти", "hunt_animals", "minecraft:sheep", 1, "Иду охотиться."),
            // «Камень» is the stone block itself and what a stone tool is made of; both are one meaning to a player.
            new Group(ItemTags.STONE_TOOL_MATERIALS, Items.STONE, "камень", "камень", "камня", "gather_stone", "", GATHER_COUNT, "Пойду за камнем."),
            new Group(ItemTags.DIRT, null, "земля", "землю", "земли", "gather_dirt", "", GATHER_COUNT, "Накопаю земли."),
            new Group(ItemTags.SAND, null, "песок", "песок", "песка", "gather_loose", "minecraft:sand", GATHER_COUNT, "Возьму лопату."),
            new Group(Tags.Items.BUCKETS, null, "ведро", "ведро", "ведра", null, null, 0, ""));

    /** The meaning of a named item, or null for a name that is no item at all. */
    public static Meaning of(String item) {
        if (item == null) return null;
        String id = item.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return null;
        if (ANY_ITEM.equals(id) || "any".equals(id) || "all".equals(id) || "minecraft:all".equals(id)) return ANY;
        if (CompanionGame.ANY_MEAT.equals(id)) {
            return new Meaning("мясо", "мясо", "мяса", CompanionPickUp::anyMeat, "hunt_animals", "food", 1, "Иду охотиться.");
        }
        Item named = itemObject(id);
        if (named == null) return null;
        Meaning meat = meat(named);
        if (meat != null) return meat;
        Meaning group = group(named);
        if (group != null) return group;
        return single(named);
    }

    /** A meat and its cooked counterpart are one meaning to a player: «курица» is the raw one and the fried one. */
    private static Meaning meat(Item item) {
        String path = key(item);
        Item raw = path.startsWith("cooked_") ? counterpart(item, "") : item;
        Item cooked = path.startsWith("cooked_") ? item : counterpart(item, "cooked_");
        if (raw == null || cooked == null) return null;
        var game = CompanionGame.Kind.of(BuiltInRegistries.ITEM.getKey(raw).toString());
        String fallback = game == null ? null : "minecraft:" + game.key;
        return new Meaning(name(raw), accusative(raw), genitive(raw), stack -> stack.is(raw) || stack.is(cooked),
                fallback == null ? null : "hunt_animals", fallback, 1, fallback == null ? "" : "Иду охотиться.");
    }

    /** The tag group the item belongs to, or null for one that is only itself. */
    private static Meaning group(Item item) {
        ItemStack stack = new ItemStack(item);
        for (Group group : GROUPS) {
            if (group.base() == item || stack.is(group.tag())) {
                return new Meaning(group.name(), group.accusative(), group.genitive(),
                        drop -> group.base() != null && drop.is(group.base()) || drop.is(group.tag()),
                        group.action(), group.item(), group.count(), group.note());
            }
        }
        return null;
    }

    /** A thing that is only itself; a shovel's loose drop still names the work that gets it when none lies near. */
    private static Meaning single(Item item) {
        List<Item> family = family(item);
        if (item == Items.GRAVEL) return new Meaning(name(item), accusative(item), genitive(item),
                stack -> stack.is(Items.GRAVEL), "gather_loose", "minecraft:gravel", GATHER_COUNT, "Возьму лопату.");
        if (item == Items.FLINT) return new Meaning(name(item), accusative(item), genitive(item),
                stack -> stack.is(Items.FLINT), "gather_loose", "minecraft:flint", FLINT_COUNT, "Возьму лопату.");
        return new Meaning(name(item), accusative(item), genitive(item), stack -> {
            for (Item member : family) if (stack.is(member)) return true;
            return false;
        }, null, null, 0, "");
    }

    /** The affixes the game builds another form of the same thing with: «глубинносланцевая руда», «сырое железо», «слиток». */
    private static final List<String> FORM_PREFIXES = List.of("deepslate_", "nether_", "raw_", "glow_");
    private static final List<String> FORM_SUFFIXES = List.of("_ore", "_ingot", "_block", "_lazuli");

    /**
     * Every form of the thing the game holds: «железо» is the ore, the raw drop, the ingot and the block, «чернила» both
     * ink sacs. A form is built from the same base id by the game's own affixes, so the family is every item with that
     * base — a mod's own ore, raw drop, ingot and block count the same way.
     */
    private static List<Item> family(Item item) {
        String base = base(key(item));
        List<Item> family = new ArrayList<>();
        for (Item other : BuiltInRegistries.ITEM) {
            if (other != Items.AIR && base.equals(base(key(other)))) family.add(other);
        }
        return family;
    }

    /** The id without the affixes of a form: «raw_iron_block» and «iron_ore» are both «iron». */
    private static String base(String path) {
        String base = path;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : FORM_PREFIXES) {
                if (base.startsWith(prefix) && base.length() > prefix.length()) {
                    base = base.substring(prefix.length());
                    changed = true;
                }
            }
            for (String suffix : FORM_SUFFIXES) {
                if (base.endsWith(suffix) && base.length() > suffix.length()) {
                    base = base.substring(0, base.length() - suffix.length());
                    changed = true;
                }
            }
        }
        return base;
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

    /** How many items those drops hold: «Лежит рядом: курица (2), но сумка полна.» */
    static int total(List<ItemEntity> drops) {
        int count = 0;
        for (ItemEntity drop : drops) count += drop.getItem().getCount();
        return count;
    }

    /**
     * «Лежит рядом: minecraft:cobblestone (3), minecraft:chicken (1).» — what really lies around, for the honest answer
     * when nothing matched. The radius is the scan's own: the answer never names a drop the order would not have looked
     * at. The ids are the game's; the client says their names in the player's language, so a modded drop is named by its
     * own lang file too.
     */
    static String lying(ServerLevel level, BlockPos center, int radius, java.util.UUID playerId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius))) {
            if (!within(drop, center, radius) || !pickable(drop, playerId)) continue;
            counts.merge(id(drop.getItem()), drop.getItem().getCount(), Integer::sum);
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

    /**
     * «курица (2), алмаз (1)»: what lies around a point, for the model's game snapshot. Unlike the order's own list this
     * has no player to judge gifts by, so every living drop counts; the names are the game's own in the language of the
     * caller — the client's, so the model reads what the player reads.
     */
    public static String nearby(Level level, Vec3 center, double radius) {
        List<ItemEntity> drops = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class,
                new AABB(center, center).inflate(radius)));
        drops.sort(Comparator.comparingDouble(drop -> drop.position().distanceToSqr(center)));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemEntity drop : drops) {
            // The box reaches further than the radius at its corners: a drop just outside is outside.
            if (!drop.isAlive() || drop.getItem().isEmpty() || drop.position().distanceToSqr(center) > radius * radius) continue;
            counts.merge(label(drop.getItem()), drop.getItem().getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "ничего не лежит";
        StringBuilder line = new StringBuilder();
        int shown = 0;
        for (var entry : counts.entrySet()) {
            if (shown++ > 0) line.append(", ");
            line.append(entry.getKey()).append(" (").append(entry.getValue()).append(')');
            if (shown == 6) break;
        }
        return line.toString();
    }

    /**
     * «, на полу рядом=курица (2), алмаз (1)» for the model's snapshot: the companion's own scan radius from the
     * settings, and nothing at all when his ground pickup is switched off — the line must not promise drops he will
     * not take.
     */
    public static String snapshotLine(Level level, Vec3 center, CompanionEntity companion) {
        if (companion != null && !companion.pickUpNearby()) return "";
        double radius = companion == null ? 16.0D : companion.pickUpRadius();
        return ", на полу рядом=" + nearby(level, center, radius);
    }

    /**
     * The drops lying around that the order may take, each with its id and the name the game shows for it: the exact
     * list the model picks from when the player's word is not one the registry answers. A gift, junk he let go and a
     * stack aimed at somebody else are left out, so the model cannot pick what the order would refuse.
     */
    public static List<Drop> drops(Level level, Vec3 center, double radius, java.util.UUID playerId) {
        Map<String, Drop> byId = new LinkedHashMap<>();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(radius))) {
            if (!drop.isAlive() || drop.getItem().isEmpty() || drop.position().distanceToSqr(center) > radius * radius) continue;
            if (!pickable(drop, playerId)) continue;
            String id = id(drop.getItem());
            Drop seen = byId.get(id);
            byId.put(id, new Drop(id, label(drop.getItem()), (seen == null ? 0 : seen.count()) + drop.getItem().getCount()));
        }
        return new ArrayList<>(byId.values());
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

    /** What a drop is called in the game: the name its own lang file gives it, custom name and all. */
    private static String label(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    /** Any meat of the game's animals, raw or cooked: «мясо» without a kind of its own. */
    private static boolean anyMeat(ItemStack stack) {
        for (CompanionGame.Kind kind : CompanionGame.Kind.values()) {
            Item cooked = counterpart(kind.meat, "cooked_");
            if (stack.is(kind.meat) || cooked != null && stack.is(cooked)) return true;
        }
        return false;
    }

    /** The id of a thing, in quotes for a line: «minecraft:amethyst_shard», a modded id just the same. */
    private static String name(Item item) {
        return "«" + id(new ItemStack(item)) + "»";
    }

    /** «подобрал предмет «…»»: the frame declines, the name the client puts inside does not. */
    private static String accusative(Item item) {
        return "предмет " + name(item);
    }

    /** «нет предмета «…»». */
    private static String genitive(Item item) {
        return "предмета " + name(item);
    }

    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    private static String key(Item item) { return BuiltInRegistries.ITEM.getKey(item).getPath(); }

    /** The item next to this one in its own namespace: a mod's «cooked_…» is next to its raw meat just the same. */
    private static Item counterpart(Item item, String prefix) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        var location = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), prefix + key.getPath());
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    private static Item itemObject(String id) {
        var location = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }
}
