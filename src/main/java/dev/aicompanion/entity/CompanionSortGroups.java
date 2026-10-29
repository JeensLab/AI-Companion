package dev.aicompanion.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The seven sort groups (issue 32) and the pure planner that says which chest takes which group. No world, no state. */
public final class CompanionSortGroups {
    private CompanionSortGroups() { }

    /** In the order the player's phrase and the report list them. */
    public enum Group {
        STONE("камень и земля"), WOOD("дерево"), ORE("руда и металлы"), FOOD("еда"), GEAR("инструменты, оружие и броня"), MOB("добыча с мобов"), OTHER("остальное");
        public final String label;
        Group(String label) { this.label = label; }
    }

    /** A chest of the set: its place in the order of nearness to the player, its size and how many cells of each group it holds now. */
    public record Chest(int index, double distance, int size, Map<Group, Double> cells) { }

    private static final Set<Item> MOB_DROPS = Set.of(Items.ROTTEN_FLESH, Items.BONE, Items.BONE_MEAL, Items.STRING, Items.GUNPOWDER, Items.SPIDER_EYE, Items.FEATHER,
            Items.LEATHER, Items.SLIME_BALL, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.PHANTOM_MEMBRANE, Items.MAGMA_CREAM, Items.INK_SAC,
            Items.GLOW_INK_SAC, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.TURTLE_SCUTE);
    private static final Set<Item> SOIL_AND_STONE = Set.of(Items.STONE, Items.DEEPSLATE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.TUFF, Items.DIRT,
            Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.PODZOL, Items.GRASS_BLOCK, Items.MYCELIUM, Items.GRAVEL, Items.NETHERRACK);
    private static final Set<Item> ORES_AND_METALS = Set.of(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.REDSTONE,
            Items.QUARTZ, Items.AMETHYST_SHARD, Items.ANCIENT_DEBRIS);

    /** Tools, weapons and armour by their durability or armour slot; mob drops before food (rotten flesh is both); then stone, wood, ores, food; the rest is «other». */
    public static Group of(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.getMaxDamage() > 0 || item instanceof ArmorItem) return Group.GEAR;
        if (MOB_DROPS.contains(item)) return Group.MOB;
        if (SOIL_AND_STONE.contains(item) || stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(ItemTags.SAND)) return Group.STONE;
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Items.STICK)) return Group.WOOD;
        if (ORES_AND_METALS.contains(item) || stack.is(ItemTags.COALS) || stack.is(ItemTags.COAL_ORES) || stack.is(ItemTags.IRON_ORES) || stack.is(ItemTags.GOLD_ORES)
                || stack.is(ItemTags.COPPER_ORES) || stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.LAPIS_ORES)
                || stack.is(ItemTags.REDSTONE_ORES)) return Group.ORE;
        if (stack.has(DataComponents.FOOD)) return Group.FOOD;
        return Group.OTHER;
    }

    /** What a stack takes of a chest, in cells: the group's volume counts by cells, not by pieces. */
    public static double cells(ItemStack stack) { return stack.isEmpty() ? 0 : (double) stack.getCount() / stack.getMaxStackSize(); }

    /**
     * Which chests (by index, in the order they fill) take each group of the set. The biggest group picks first, the chest where it
     * already lies most (the nearer one on a tie); a group that does not fit goes on into the next chest. With fewer chests than
     * groups the big ones keep a chest each and all the rest share the last. Chests no group needs stay out of the plan.
     * {@code carried} is what the bag holds now: it counts into the size of the groups, so the order does not change on the way.
     */
    public static Map<Group, List<Integer>> plan(List<Chest> chests, Map<Group, Double> carried) {
        Map<Group, Double> volume = new EnumMap<>(Group.class);
        for (Chest chest : chests) chest.cells().forEach((group, cells) -> volume.merge(group, cells, Double::sum));
        carried.forEach((group, cells) -> volume.merge(group, cells, Double::sum));
        volume.values().removeIf(cells -> cells <= 0);
        List<Group> order = new ArrayList<>(volume.keySet());
        order.sort(Comparator.comparing((Group group) -> -volume.get(group)).thenComparing(Enum::ordinal));
        List<Chest> free = new ArrayList<>(chests);
        Map<Group, List<Integer>> result = new EnumMap<>(Group.class);
        for (int i = 0; i < order.size() && !free.isEmpty(); i++) {
            Group group = order.get(i);
            if (free.size() == 1) { for (int j = i; j < order.size(); j++) result.put(order.get(j), List.of(free.getFirst().index())); break; }
            double rest = 0;
            for (int j = i + 1; j < order.size(); j++) rest += volume.get(order.get(j));
            int size = free.getFirst().size();
            int reserve = i + 1 < order.size() ? Math.max(1, (int) Math.ceil(rest / size)) : 0;
            int take = Math.max(1, Math.min((int) Math.ceil(volume.get(group) / size), free.size() - reserve));
            List<Integer> mine = new ArrayList<>();
            for (int k = 0; k < take; k++) {
                Chest best = free.stream().min(Comparator.comparing((Chest chest) -> -chest.cells().getOrDefault(group, 0.0)).thenComparingDouble(Chest::distance).thenComparingInt(Chest::index)).orElseThrow();
                free.remove(best); mine.add(best.index());
            }
            result.put(group, mine);
        }
        return result;
    }
}
