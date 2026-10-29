package dev.aicompanion.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The seven sort groups (issue 32) and the pure planner that says which chest keeps how much of each kind. No world, no state. */
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

    /** The stacks merged by kind (item and components): one stack per kind, its count the sum, which may pass a full stack. */
    public static List<ItemStack> kinds(Iterable<ItemStack> stacks) {
        List<ItemStack> kinds = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            int at = 0;
            while (at < kinds.size() && !ItemStack.isSameItemSameComponents(kinds.get(at), stack)) at++;
            if (at == kinds.size()) kinds.add(stack.copy()); else kinds.get(at).grow(stack.getCount());
        }
        return kinds;
    }

    /** The cells a kind takes once its unfinished stacks merge. */
    public static int cells(ItemStack kind) { return (kind.getCount() + kind.getMaxStackSize() - 1) / kind.getMaxStackSize(); }

    /**
     * Which chests (by index, in the order they fill) take each group of the set. The biggest group picks first, the chest where it
     * already lies most (the nearer one on a tie), and goes on into more chests while its volume does not fit their sizes, keeping
     * enough chests for the groups after it; with fewer chests than groups the big ones keep a chest each and all the rest share the
     * last. A further chest is the one where the group lies most against what other groups have there: the last cells of a big group
     * do not take the chest another group fills. What does not fit the chests of its group is for {@link #layout}. Chests no group needs
     * stay out of the plan. {@code volume} is the cells each group takes, chests and what the bag carries together.
     */
    public static Map<Group, List<Integer>> plan(List<Chest> chests, Map<Group, Double> volume) {
        volume = new EnumMap<>(volume);
        volume.values().removeIf(cells -> cells <= 0);
        Map<Group, Double> sizes = volume;
        List<Group> order = new ArrayList<>(volume.keySet());
        order.sort(Comparator.comparing((Group group) -> -sizes.get(group)).thenComparing(Enum::ordinal));
        List<Chest> free = new ArrayList<>(chests);
        Map<Group, List<Integer>> result = new EnumMap<>(Group.class);
        for (int i = 0; i < order.size() && !free.isEmpty(); i++) {
            Group group = order.get(i);
            if (free.size() == 1) {
                for (int j = i; j < order.size(); j++) result.put(order.get(j), new ArrayList<>(List.of(free.getFirst().index())));
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
                Chest best = mine.isEmpty() ? nearestWith(free, group) : free.stream().max(Comparator.comparingDouble((Chest chest) -> 2 * chest.cells().getOrDefault(group, 0.0)
                        - chest.cells().values().stream().mapToDouble(Double::doubleValue).sum()).thenComparingDouble(chest -> -chest.distance()).thenComparingInt(chest -> -chest.index())).orElseThrow();
                free.remove(best); mine.add(best.index()); need -= best.size();
            }
            result.put(group, mine);
        }
        return result;
    }

    /**
     * How many pieces of each kind each chest keeps: {@code [chest index][kind]}, {@code kinds} as {@link #kinds} gives them for the chests
     * and the bag's load together. The kinds of a group fill its chests ({@link #plan}) in cells, the biggest kinds first; what does not fit
     * goes to the chests with the most cells left. So while the kinds take no more cells than the set has, every piece has a place.
     */
    public static int[][] layout(List<Chest> chests, List<ItemStack> kinds) {
        Map<Group, Double> volume = new EnumMap<>(Group.class);
        for (ItemStack kind : kinds) volume.merge(of(kind), (double) cells(kind), Double::sum);
        Map<Group, List<Integer>> plan = plan(chests, volume);
        int size = chests.stream().mapToInt(Chest::index).max().orElse(-1) + 1;
        int[] free = new int[size];
        int[][] taken = new int[size][kinds.size()];
        for (Chest chest : chests) free[chest.index()] = chest.size();
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < kinds.size(); k++) order.add(k);
        order.sort(Comparator.comparing((Integer k) -> -volume.get(of(kinds.get(k)))).thenComparing(k -> of(kinds.get(k)).ordinal())
                .thenComparing(k -> -cells(kinds.get(k))).thenComparing(k -> BuiltInRegistries.ITEM.getId(kinds.get(k).getItem())));
        Map<Integer, Integer> left = new LinkedHashMap<>();
        for (int k : order) {
            int need = cells(kinds.get(k));
            for (int chest : plan.getOrDefault(of(kinds.get(k)), List.of())) {
                int take = Math.min(need, free[chest]);
                taken[chest][k] += take; free[chest] -= take; need -= take;
            }
            if (need > 0) left.put(k, need);
        }
        left.forEach((k, need) -> {
            Group group = of(kinds.get(k));
            while (need > 0) {
                Chest best = chests.stream().filter(chest -> free[chest.index()] > 0).max(Comparator.comparingInt((Chest chest) -> free[chest.index()])
                        .thenComparing(chest -> holds(taken[chest.index()], kinds, group)).thenComparingDouble(chest -> chest.cells().getOrDefault(group, 0.0))
                        .thenComparingDouble(chest -> -chest.distance()).thenComparingInt(chest -> -chest.index())).orElse(null);
                if (best == null) break;
                int take = Math.min(need, free[best.index()]);
                taken[best.index()][k] += take; free[best.index()] -= take; need -= take;
            }
        });
        // Pieces from cells: a chest keeps no more than its cells hold, the farther chests get the unfinished stack.
        int[][] keep = new int[size][kinds.size()];
        for (int k = 0; k < kinds.size(); k++) {
            int rest = kinds.get(k).getCount();
            for (int chest = 0; chest < size; chest++) {
                keep[chest][k] = Math.min(rest, taken[chest][k] * kinds.get(k).getMaxStackSize());
                rest -= keep[chest][k];
            }
        }
        return keep;
    }

    private static boolean holds(int[] taken, List<ItemStack> kinds, Group group) {
        for (int k = 0; k < kinds.size(); k++) if (taken[k] > 0 && of(kinds.get(k)) == group) return true;
        return false;
    }

    /** The chest where the group already lies most; the nearer one on a tie. */
    private static Chest nearestWith(List<Chest> free, Group group) {
        return free.stream().min(Comparator.comparing((Chest chest) -> -chest.cells().getOrDefault(group, 0.0)).thenComparingDouble(Chest::distance).thenComparingInt(Chest::index)).orElseThrow();
    }
}
