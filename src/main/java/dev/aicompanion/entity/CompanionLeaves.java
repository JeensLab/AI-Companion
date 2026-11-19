package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * «Листва»/«листья» as the world has them: the leaf block of a tree he can really gather from, whatever species grows
 * here — never a log, and no species of its own hardcoded. A leaf a player set down (persistent) is no tree's crown, but it
 * is still leaves: it is cut when no tree's own leaf is reachable for the order, and only then (the owner, 18.11: the leaves
 * beside him were his own, and he saw none at all).
 * The nearest leaf is not enough: a tall crown may hang over him out of reach while another tree's leaves stand by
 * (the owner, 17.11: «Рядом не вижу … oak leaves» under a tall tree he could gather from). The species an order named is no
 * limit either: when that tree cannot give the order, the leaves of any other tree or hedge about answer it (any
 * #minecraft:leaves) before it is refused. A crown above every stand
 * cell is no loss either: with soil in the bag he builds up to it (CompanionPillar) and cuts it from the column, and the soil
 * for it he digs himself when the bag has none and a block that gives dirt stands about him (18.11: «дай земли» over dirt he
 * could dig). So the target is chosen by the picking path's own rule, and with no leaf of any tree he can come at the order
 * names none.
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
        return leafAt(level, CompanionPicking.nearestLeaf(c, level));
    }

    private static String leafAt(ServerLevel level, BlockPos found) {
        return found == null ? null : BuiltInRegistries.ITEM.getKey(level.getBlockState(found).getBlock().asItem()).toString();
    }

    /** The item of the order: the family word resolved to a reachable tree's leaf block (null when there is none), else the id unchanged. */
    public static String resolve(CompanionEntity c, ServerLevel level, String requested) {
        if (!isFamily(requested)) return requested;
        return nearest(c, level);
    }

    /**
     * The leaf block an order will come at, as the gather itself comes at it: the species it named when a tree's own leaves of
     * it can be come at, else another tree's or hedge's leaves about, then the leaves a player set down — and null when no leaf
     * is reachable. A leaf he set down is no crown of its species, so the named species is named only while a tree grew it in
     * his reach: the soil the plan digs is sized for the leaves it really comes at. A named species is no limit on a leaf order.
     */
    public static String leafTarget(CompanionEntity c, ServerLevel level, String requested) {
        Item named = null;
        if (requested != null && !isFamily(requested) && isLeaf(requested)) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(requested);
            named = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        }
        return leafAt(level, CompanionPicking.firstLeaf(c, level, named));
    }

    /**
     * The leaf block he already carries enough of — one species alone, or several kinds together, then the family word — or
     * null: an order the bag meets needs no tree, and its answer is that count (the owner, 17.11: sixteen oak leaves in the
     * bag, «добудь листву» refused).
     */
    public static String inBag(CompanionEntity c, int count) {
        java.util.Map<String, Integer> carried = bySpecies(c);
        if (carried.values().stream().mapToInt(Integer::intValue).sum() < count) return null;
        return carried.entrySet().stream().filter(entry -> entry.getValue() >= count)
                .max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(FAMILY);
    }

    /**
     * How many leaves he carries, every kind together: a leaf order asks for leaves, and any species of them counts toward
     * its count (eight oak and eight spruce answer for sixteen). An order short of its count answers with this («У меня 15
     * из 16»), never with a flat refusal that hides the part of the order he did bring.
     */
    public static int carried(CompanionEntity c) {
        return bySpecies(c).values().stream().mapToInt(Integer::intValue).sum();
    }

    private static java.util.Map<String, Integer> bySpecies(CompanionEntity c) {
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

    /** Whether the item of an order is a leaf block: a species resolved for it, or one the player named. */
    public static boolean isLeaf(String item) {
        if (item == null) return false;
        var id = net.minecraft.resources.ResourceLocation.tryParse(item);
        var leaf = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return leaf != null && new ItemStack(leaf).is(ItemTags.LEAVES);
    }

    /** Whether the leaves are there but the soil is not: a crown above every stand cell that a short column of his would reach. */
    public static boolean soilForACrown(CompanionEntity c, ServerLevel level) {
        return CompanionPicking.soilForACrown(c, level);
    }

    /**
     * How much soil he must dig for the crown of the leaves an order will come at: the steps of the longest column among the
     * leaves no stand he can walk to serves, less what he carries, or 0 when the stands serve the whole order or the bag already
     * has the soil — so a crown is climbed on soil he digs himself, and «дай земли» is only for a world with none about (the
     * owner, 18.11). A stair that serves only part of a crown is sized for the far leaves too (19.11).
     */
    public static int soilToDig(CompanionEntity c, ServerLevel level, String item, int count) {
        return CompanionPicking.soilToDig(c, level, item, count);
    }

    /** Whether the leaves are above even the longest column of his: not out of reach for want of soil, but too high to climb to. */
    public static boolean aboveAnyColumn(CompanionEntity c, ServerLevel level) {
        return CompanionPicking.aboveAnyColumn(c, level);
    }
}
