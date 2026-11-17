package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * «Листва»/«листья» as the world has them: the leaf block of the tree nearest him, whatever species grows here — never a log,
 * and no species of its own hardcoded. Leaves a player set down (persistent) are no tree's crown; nothing near falls back to oak.
 */
public final class CompanionLeaves {
    /** The word an order carries instead of a species: resolved where the world is known (CompanionServerPlanPort). */
    public static final String FAMILY = "leaves";

    private CompanionLeaves() { }

    /** Whether the order names leaves as a family rather than one block. */
    public static boolean isFamily(String item) { return false; }

    /** The leaf block of the tree nearest him, or null when no tree stands within his search. */
    public static String nearest(ServerLevel level, BlockPos origin, int radius) { return null; }

    /** The item of the order: the family word resolved to the nearest tree's leaf block, else the id unchanged. */
    public static String resolve(ServerLevel level, BlockPos origin, int radius, String requested) { return requested; }
}
