package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * «Листва»/«листья» as the world has them: the leaf block of a tree he can really gather from, whatever species grows
 * here — never a log, and no species of its own hardcoded. A leaf a player set down (persistent) is no tree's crown.
 * The nearest leaf is not enough: a tall crown may hang over him out of reach while another tree's leaves stand by
 * (the owner, 17.11: «Рядом не вижу … oak leaves» under a tall tree he could gather from). A crown above every stand
 * cell is no loss either: with soil in the bag he builds up to it (CompanionPillar) and cuts it from the column. So the
 * target is chosen by the picking path's own rule, and with no leaf of any tree he can come at the order names none.
 */
public final class CompanionLeaves {
    /** The word an order carries instead of a species: resolved where the world is known (CompanionServerPlanPort). */
    public static final String FAMILY = "leaves";

    private CompanionLeaves() { }

    /** Whether the order names leaves as a family rather than one block. */
    public static boolean isFamily(String item) {
        return item != null && (FAMILY.equals(item) || ("#" + FAMILY).equals(item));
    }

    /** The leaf block of a tree he can gather from, or null when no tree's leaves are within his reach. */
    public static String nearest(CompanionEntity c, ServerLevel level) {
        BlockPos found = CompanionPicking.nearestLeaf(c, level);
        return found == null ? null : BuiltInRegistries.ITEM.getKey(level.getBlockState(found).getBlock().asItem()).toString();
    }

    /** The item of the order: the family word resolved to a reachable tree's leaf block (null when there is none), else the id unchanged. */
    public static String resolve(CompanionEntity c, ServerLevel level, String requested) {
        if (!isFamily(requested)) return requested;
        return nearest(c, level);
    }

    /**
     * The leaf block he already carries at least {@code count} of, the most of any he has, or null: an order the bag meets needs
     * no tree, and its answer is that count (the owner, 17.11: sixteen oak leaves in the bag, «добудь листву» refused).
     */
    public static String inBag(CompanionEntity c, int count) {
        return carried(c).entrySet().stream().filter(entry -> entry.getValue() >= count)
                .max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
    }

    /**
     * How many leaves of one kind he carries, the most of any: an order short of its count answers with it («У меня 15 из 16»),
     * never with a flat refusal that hides the part of the order he did bring.
     */
    public static int mostCarried(CompanionEntity c) {
        return carried(c).values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static java.util.Map<String, Integer> carried(CompanionEntity c) {
        java.util.Map<String, Integer> carried = new java.util.LinkedHashMap<>();
        for (int slot = 0; slot < c.getContainerSize(); slot++) add(carried, c.getItem(slot));
        add(carried, c.getMainHandItem());
        add(carried, c.getOffhandItem());
        return carried;
    }

    private static void add(java.util.Map<String, Integer> carried, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.LEAVES)) return;
        carried.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
    }

    /** Whether the leaves are there but the soil is not: a crown above every stand cell that a short column of his would reach. */
    public static boolean soilForACrown(CompanionEntity c, ServerLevel level) {
        return CompanionPicking.soilForACrown(c, level);
    }

    /** Whether the leaves are above even the longest column of his: not out of reach for want of soil, but too high to climb to. */
    public static boolean aboveAnyColumn(CompanionEntity c, ServerLevel level) {
        return CompanionPicking.aboveAnyColumn(c, level);
    }
}
