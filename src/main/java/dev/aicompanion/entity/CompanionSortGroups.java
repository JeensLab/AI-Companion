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
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** A chest of the set: its place in the order of nearness to the player, its size and how much of each group it holds now (in cells, by pieces: where a group lies most). */
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

    /** What a group of stacks takes of a chest, in cells: by kinds of item, as the chest keeps them (the unfinished stacks of one kind merge). */
    public static Map<Group, Double> volume(Iterable<ItemStack> stacks) {
        Map<Group, Double> volume = new EnumMap<>(Group.class);
        List<ItemStack> kinds = new ArrayList<>(); List<Integer> counts = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            int at = 0;
            while (at < kinds.size() && !ItemStack.isSameItemSameComponents(kinds.get(at), stack)) at++;
            if (at == kinds.size()) { kinds.add(stack); counts.add(stack.getCount()); } else counts.set(at, counts.get(at) + stack.getCount());
        }
        for (int i = 0; i < kinds.size(); i++) volume.merge(of(kinds.get(i)), Math.ceil((double) counts.get(i) / kinds.get(i).getMaxStackSize()), Double::sum);
        return volume;
    }

    /**
     * Which chests (by index, in the order they fill) take each group of the set. The biggest group picks first, the chest where it
     * already lies most (the nearer one on a tie), and goes on into more chests while its volume does not fit their sizes; with
     * fewer chests than groups the big ones keep a chest each and all the rest share the last. A group that still does not fit
     * spills into one more chest, the one with the most room left once every group has its own (a spill into a chest another
     * group fills to the brim would only trade places with it). Chests no group needs stay out of the plan.
     * {@code volume} is the cells each group takes, chests and what the bag carries together ({@link #volume}): the order does not change on the way.
     */
    public static Map<Group, List<Integer>> plan(List<Chest> chests, Map<Group, Double> volume) {
        volume = new EnumMap<>(volume);
        volume.values().removeIf(cells -> cells <= 0);
        Map<Group, Double> sizes = volume;
        List<Group> order = new ArrayList<>(volume.keySet());
        order.sort(Comparator.comparing((Group group) -> -sizes.get(group)).thenComparing(Enum::ordinal));
        List<Chest> free = new ArrayList<>(chests);
        Map<Integer, Double> used = new HashMap<>();
        Map<Group, Double> spill = new LinkedHashMap<>();
        Map<Group, List<Integer>> result = new EnumMap<>(Group.class);
        for (int i = 0; i < order.size() && !free.isEmpty(); i++) {
            Group group = order.get(i);
            if (free.size() == 1) {
                for (int j = i; j < order.size(); j++) { result.put(order.get(j), new ArrayList<>(List.of(free.getFirst().index()))); used.merge(free.getFirst().index(), volume.get(order.get(j)), Double::sum); }
                break;
            }
            double rest = 0;
            for (int j = i + 1; j < order.size(); j++) rest += volume.get(order.get(j));
            double average = free.stream().mapToInt(Chest::size).average().orElse(1);
            int reserve = i + 1 < order.size() ? Math.max(1, (int) Math.ceil(rest / average)) : 0;
            int limit = Math.max(1, free.size() - reserve);
            double need = volume.get(group);
            List<Integer> mine = new ArrayList<>();
            while (need > 0 && mine.size() < limit) {
                Chest best = nearestWith(free, group);
                free.remove(best); mine.add(best.index()); used.merge(best.index(), Math.min(need, best.size()), Double::sum); need -= best.size();
            }
            if (need > 0) spill.put(group, need);
            result.put(group, mine);
        }
        // Did not fit the chests it could take: the rest goes to the chest with the most room left, group by group in the order they picked.
        spill.forEach((group, rest) -> chests.stream().filter(chest -> !result.get(group).contains(chest.index()))
                .min(Comparator.comparingDouble((Chest chest) -> used.getOrDefault(chest.index(), 0.0) - chest.size())
                        .thenComparing(chest -> -chest.cells().getOrDefault(group, 0.0)).thenComparingDouble(Chest::distance).thenComparingInt(Chest::index))
                .ifPresent(chest -> { result.get(group).add(chest.index()); used.merge(chest.index(), rest, Double::sum); }));
        return result;
    }

    /** The chest where the group already lies most; the nearer one on a tie. */
    private static Chest nearestWith(List<Chest> free, Group group) {
        return free.stream().min(Comparator.comparing((Chest chest) -> -chest.cells().getOrDefault(group, 0.0)).thenComparingDouble(Chest::distance).thenComparingInt(Chest::index)).orElseThrow();
    }
}
