package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a block gives when it is simply broken, from the game's own loot tables (the player 12.11: he should get everything a
 * player gets; one skill and the game's data instead of a list of two hundred blocks): sugar cane, a flower, a cactus, a pumpkin
 * give themselves, clay gives clay balls, a melon its slices, snow snowballs. Only what a hand or a tool he makes takes, and only
 * a block that gives the one item every time, and an item no recipe makes: leaves (a sapling now and then), grass (seeds sometimes) and a potted flower (the
 * pot comes with it) are no source.
 *
 * Left to other skills and later work: what a pickaxe mines (stone of every kind: the mine), crops and saplings (the farm and the
 * replanting are his own; a young crop broken for its seed is a loss), blocks that hold things, anything under water.
 */
final class CompanionPickable {
    /** A block and the least of the item one of it gives. */
    record Source(Block block, int yield) { }

    /** A drop that comes by chance is seen to fail within this many tries (a dead bush gives sticks two times in three). */
    private static final int ROLLS = 16;
    // ponytail: built once for a server; a data pack reloaded in a running world keeps the old tables until the world is reopened.
    private static WeakReference<MinecraftServer> builtFor = new WeakReference<>(null);
    private static Map<Item, List<Source>> sources = Map.of();

    /** The blocks that give the item when broken; empty for an item no block gives so. */
    static synchronized List<Source> sources(ServerLevel level, Item item) {
        if (builtFor.get() != level.getServer()) {
            sources = build(level);
            builtFor = new WeakReference<>(level.getServer());
        }
        return sources.getOrDefault(item, List.of());
    }

    private static Map<Item, List<Source>> build(ServerLevel level) {
        Map<Item, List<Source>> found = new HashMap<>();
        // What a recipe makes is made (wool, planks, a clay block): only what the world alone gives is taken from it.
        Set<Item> made = new HashSet<>();
        for (var holder : level.getRecipeManager().getRecipes()) made.add(holder.value().getResultItem(level.registryAccess()).getItem());
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (!taken(state)) continue;
            ItemStack tool = tool(state);
            Item only = null;
            int least = Integer.MAX_VALUE;
            for (int roll = 0; roll < ROLLS && least > 0; roll++) {
                int count = 0;
                for (ItemStack drop : CompanionTools.drops(state, level, BlockPos.ZERO, null, null, tool)) {
                    if (only == null) only = drop.getItem();
                    if (drop.getItem() != only) { count = 0; break; }
                    count += drop.getCount();
                }
                least = Math.min(least, count);
            }
            if (only != null && least > 0 && !made.contains(only)) found.computeIfAbsent(only, key -> new ArrayList<>()).add(new Source(block, least));
        }
        return found;
    }

    private static boolean taken(BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getBlock().defaultDestroyTime() < 0) return false;
        if (state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) return false;
        // Outside those tags: a bamboo shoot is a sapling, a dripleaf's stem brings its head down unclaimed, a tripwire is someone's trap.
        if (state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.BIG_DRIPLEAF_STEM) || state.is(Blocks.TRIPWIRE)) return false;
        return !state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    /** The plainest tool that takes the block: none where a hand does. */
    private static ItemStack tool(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return ItemStack.EMPTY;
        return new ItemStack(state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? Items.WOODEN_SHOVEL : state.is(BlockTags.MINEABLE_WITH_AXE) ? Items.WOODEN_AXE
                : state.is(BlockTags.MINEABLE_WITH_HOE) ? Items.WOODEN_HOE : Items.AIR);
    }
}
