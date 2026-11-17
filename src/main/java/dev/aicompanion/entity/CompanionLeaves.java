package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * «Листва»/«листья» as the world has them: the leaf block of the tree nearest him, whatever species grows here — never a log,
 * and no species of its own hardcoded. A leaf a player set down (persistent) is no tree's crown; nothing near falls back to oak.
 */
public final class CompanionLeaves {
    /** The word an order carries instead of a species: resolved where the world is known (CompanionServerPlanPort). */
    public static final String FAMILY = "leaves";
    /** How far above and below him a crown is looked for: the height his gather searches at. */
    private static final int DEPTH = 12;

    private CompanionLeaves() { }

    /** Whether the order names leaves as a family rather than one block. */
    public static boolean isFamily(String item) {
        return item != null && (FAMILY.equals(item) || ("#" + FAMILY).equals(item));
    }

    /** The leaf block of the tree nearest him, or null when no tree stands within his search. */
    public static String nearest(ServerLevel level, BlockPos origin, int radius) {
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - DEPTH);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + DEPTH);
        double far = (double) radius * radius;
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, minY - origin.getY(), -radius), origin.offset(radius, maxY - origin.getY(), radius))) {
            double distance = pos.distSqr(origin);
            if (distance > far || distance >= bestDistance || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            // A leaf a player set down does not fade and is no tree's crown: only what grows on its own names the tree.
            if (!(state.getBlock() instanceof LeavesBlock) || state.getValue(LeavesBlock.PERSISTENT)) continue;
            bestDistance = distance;
            best = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
        }
        return best;
    }

    /** The item of the order: the family word resolved to the nearest tree's leaf block, else the id unchanged. */
    public static String resolve(ServerLevel level, BlockPos origin, int radius, String requested) {
        if (!isFamily(requested)) return requested;
        String found = nearest(level, origin, radius);
        return found != null ? found : BuiltInRegistries.ITEM.getKey(Items.OAK_LEAVES).toString();
    }
}
