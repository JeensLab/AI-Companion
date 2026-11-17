package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

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
 * pot comes with it) are no source. What gives nothing sure to a hand but itself to shears (leaves, a vine, a cobweb, grass, a fern,
 * a dead bush) is a source for shears. What grows on the bed of the sea holds its water (kelp, seagrass, a sea pickle), and a coral
 * block gives the dead block to a pickaxe: those are sources too (CompanionPicking dives for them). A block that gives itself to
 * Silk Touch is a source of itself as well (a snow layer, a grass block, a coral block): the item the block is, which no plain
 * break gives.
 *
 * Left to other skills and later work: what a pickaxe mines (stone of every kind: the mine), crops and saplings (the farm and the
 * replanting are his own; a young crop broken for its seed is a loss), blocks that hold things.
 */
final class CompanionPickable {
    /** A block and the least of the item one of it gives; {@code shears}: to shears only, {@code silk}: to Silk Touch only. */
    record Source(Block block, int yield, boolean shears, boolean silk) { }

    /** A drop that comes by chance is seen to fail within this many tries (a dead bush gives sticks two times in three). */
    private static final int ROLLS = 16;
    /** Light of a lamp: a brown mushroom glows at one, glowstone and the lanterns at fifteen. */
    private static final int LAMP = 10;
    // ponytail: built once for a server; a data pack reloaded in a running world keeps the old tables until the world is reopened.
    private static WeakReference<MinecraftServer> builtFor = new WeakReference<>(null);
    private static Map<Item, List<Source>> sources = Map.of();
    /** The blocks that give their own item to shears only: grass, a fern, a dead bush, leaves set down where a hand gets nothing sure. */
    private static Set<Block> shearsOnly = Set.of();

    /** The blocks that give the item when broken; empty for an item no block gives so. */
    static synchronized List<Source> sources(ServerLevel level, Item item) {
        ensure(level);
        return sources.getOrDefault(item, List.of());
    }

    /** Every leaf block the world has: the family word of an order, whatever species grows here. */
    static synchronized List<Source> leaves(ServerLevel level) {
        ensure(level);
        List<Source> all = new ArrayList<>();
        for (List<Source> list : sources.values()) for (Source source : list)
            if (source.block().defaultBlockState().is(BlockTags.LEAVES)) all.add(source);
        return all;
    }

    /** Whether the item is had with shears only: no block gives it to a hand. */
    static synchronized boolean shorn(ServerLevel level, Item item) {
        List<Source> all = sources(level, item);
        return !all.isEmpty() && all.stream().allMatch(Source::shears);
    }

    /** Whether the block itself is taken with shears only (grass, a fern, a dead bush): the right tool for it is no faster than a hand. */
    static synchronized boolean shorn(ServerLevel level, BlockState state) {
        ensure(level);
        return shearsOnly.contains(state.getBlock());
    }

    private static void ensure(ServerLevel level) {
        if (builtFor.get() != level.getServer()) {
            build(level);
            builtFor = new WeakReference<>(level.getServer());
        }
    }

    private static void build(ServerLevel level) {
        Map<Item, List<Source>> found = new HashMap<>();
        Set<Block> shornBlocks = new HashSet<>();
        // What a recipe makes is made (wool, planks, a clay block): only what the world alone gives is taken from it.
        Set<Item> made = new HashSet<>();
        for (var holder : level.getRecipeManager().getRecipes()) made.add(holder.value().getResultItem(level.registryAccess()).getItem());
        // A layer of snow gives its snowballs to a shovel, and a chorus flower gives itself to a hand, but their loot tables ask
        // for the entity that broke it: a bare null breaker gives nothing there, and both were no source at all. A marker is an
        // inert entity of the level, enough for that check.
        Entity breaker = new Marker(EntityType.MARKER, level);
        for (Block block : BuiltInRegistries.BLOCK) {
            // The default state first: a door, a fence, a leaf gives the same item open or shut, along its axis or not. Only where
            // the default gives nothing does every state count (a bush gives its berries ripe, not young).
            if (scan(level, block, block.defaultBlockState(), made, found, shornBlocks, breaker)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates())
                scan(level, block, state, made, found, shornBlocks, breaker);
        }
        sources = found;
        shearsOnly = shornBlocks;
    }

    /** One state of the block: what it gives to a hand, the tool of its tag, shears, or Silk Touch. Whether it gave anything. */
    private static boolean scan(ServerLevel level, Block block, BlockState state, Set<Item> made, Map<Item, List<Source>> found,
                                Set<Block> shornBlocks, Entity breaker) {
        if (!taken(state)) return false;
        boolean any = false;
        // A hand first, then the tool the block's tag names: a block may give nothing to a bare hand though it needs no
        // correct tool for its drop. Shears only where both give nothing sure: a leaf gives a sapling now and then.
        ItemStack given = sure(level, state, ItemStack.EMPTY, breaker);
        if (given.isEmpty()) given = sure(level, state, tool(state), breaker);
        boolean shears = given.isEmpty();
        if (shears) given = sure(level, state, new ItemStack(Items.SHEARS), breaker);
        if (!given.isEmpty() && !made.contains(given.getItem())) {
            if (!gives(found, given.getItem(), block))
                found.computeIfAbsent(given.getItem(), key -> new ArrayList<>()).add(new Source(block, given.getCount(), shears, false));
            if (shears) shornBlocks.add(block);
            any = true;
        }
        // What the block itself is: a grass block, a coral block give themselves to Silk Touch, the item no plain break gives
        // and no recipe makes (a snow layer is made of snow blocks, so its own item is the recipe's, not the layer's).
        Item own = block.asItem();
        if (own == Items.AIR || made.contains(own) || gives(found, own, block)) return any;
        ItemStack silked = sure(level, state, silkTool(level, state), breaker);
        if (!silked.isEmpty() && silked.getItem() == own) {
            found.computeIfAbsent(own, key -> new ArrayList<>()).add(new Source(block, silked.getCount(), false, true));
            any = true;
        }
        return any;
    }

    /** Whether this block already has a source for the item. */
    private static boolean gives(Map<Item, List<Source>> found, Item item, Block block) {
        for (Source source : found.getOrDefault(item, List.of())) if (source.block() == block) return true;
        return false;
    }

    /** A fixed seed for the n-th roll: small seeds give Java's random the same first draw, so they are spread out. */
    static long seed(int index) { return index * 1_000_003L; }

    /** The one item the block gives to the tool every time, as many as the least of it; empty if there is none. */
    static ItemStack sure(ServerLevel level, BlockState state, ItemStack tool, Entity breaker) {
        Item only = null;
        int least = Integer.MAX_VALUE;
        for (int index = 1; index <= ROLLS && least > 0; index++) {
            int count = 0;
            for (ItemStack drop : loot(level, state, tool, breaker, seed(index))) {
                if (drop.isEmpty()) continue;
                if (only == null) only = drop.getItem();
                if (drop.getItem() != only) { count = 0; break; }
                count += drop.getCount();
            }
            least = Math.min(least, count);
        }
        return only == null || least <= 0 ? ItemStack.EMPTY : new ItemStack(only, least);
    }

    /**
     * The block's drops to this tool, from the game's own table with a fixed seed: the same rows on every run, so the census and
     * the source table cannot disagree over a drop that comes by chance. The breaker is a real entity: a table may ask for it.
     */
    static List<ItemStack> loot(ServerLevel level, BlockState state, ItemStack tool, Entity breaker, long seed) {
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) return List.of();
        ResourceKey<LootTable> key = state.getBlock().getLootTable();
        if (key == BuiltInLootTables.EMPTY) return List.of();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO))
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, breaker)
                .create(LootContextParamSets.BLOCK);
        return level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(params, seed);
    }

    static boolean taken(BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || state.getBlock().defaultDestroyTime() < 0) return false;
        // Water is no source (it gives nothing), what grows in it is; lava and any other fluid are no place of his.
        if (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER)) return false;
        if (state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) return false;
        // Outside those tags: a bamboo shoot is a sapling, a dripleaf's stem brings its head down unclaimed, a tripwire is someone's trap.
        if (state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.BIG_DRIPLEAF_STEM) || state.is(Blocks.TRIPWIRE)) return false;
        // Someone's things no recipe makes, standing where no building of the player's is known: a lamp (glowstone for its dust, a sea
        // lantern, a froglight), a dragon egg (it falls, as nothing else here does: sand and gravel are another skill's), a sniffer egg,
        // a stripped block (stripping is not his skill, so one that stands was put there).
        if (state.getLightEmission() >= LAMP || state.getBlock() instanceof FallingBlock || state.is(Blocks.SNIFFER_EGG)
                || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")) return false;
        // The mine has no coral: the block is mined here for the dead block it gives. Nor has it the Nether's own stone, which any pickaxe takes.
        return !state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.CORAL_BLOCKS) || nether(state.getBlock());
    }

    /** The Nether's own stone: the only sources he takes there (CompanionPicking), the rest of this skill's kinds stay in the overworld. */
    static boolean nether(Block block) { return block == Blocks.NETHERRACK || block == Blocks.BASALT; }

    /** Whether every block that gives the item wants a pickaxe (a coral block for the dead one). */
    static boolean mined(ServerLevel level, Item item) {
        List<Source> all = sources(level, item);
        return !all.isEmpty() && all.stream().allMatch(source -> source.block().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE));
    }

    /** Whether every block that gives the item gives it to a shovel only (snow its snowballs). */
    static boolean shoveled(ServerLevel level, Item item) {
        List<Source> all = sources(level, item);
        return !all.isEmpty() && all.stream().allMatch(source -> {
            BlockState state = source.block().defaultBlockState();
            return state.requiresCorrectToolForDrops() && state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        });
    }

    /** The plainest tool that takes the block for its drop: none where a hand does. */
    static ItemStack tool(BlockState state) {
        for (ItemStack candidate : new ItemStack[]{new ItemStack(Items.WOODEN_SHOVEL), new ItemStack(Items.WOODEN_AXE),
                new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WOODEN_PICKAXE)}) {
            if (state.requiresCorrectToolForDrops() ? candidate.isCorrectToolForDrops(state) : candidate.getDestroySpeed(state) > 1)
                return candidate;
        }
        return ItemStack.EMPTY;
    }

    /** The same tool with Silk Touch: the block itself instead of its drop, where the game gives it so. */
    static ItemStack silkTool(ServerLevel level, BlockState state) {
        ItemStack tool = tool(state);
        if (tool.isEmpty()) tool = new ItemStack(Items.SHEARS);
        tool.enchant(level.registryAccess().holderOrThrow(Enchantments.SILK_TOUCH), 1);
        return tool;
    }
}
