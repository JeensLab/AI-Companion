package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * «Листва»/«листья» as the world has them: the leaf block of a tree he can really gather from, whatever species grows
 * here — never a log, and no species of its own hardcoded. A leaf a player set down (persistent) is no tree's crown.
 * The nearest leaf is not enough: a tall crown may hang over him out of reach while another tree's leaves stand by
 * (the owner, 17.11: «Рядом не вижу … oak leaves» under a tall tree he could gather from). So the target is chosen by
 * the picking path's own rule, and with no reachable leaf of any tree the order names none.
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
}
