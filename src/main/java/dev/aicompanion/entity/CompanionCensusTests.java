package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A census of the whole registry, not a sample: every state of every block must take the fastest tool of a standard bag that
 * gives its drop (the expectation from the game's own data — the loot tables and the recipes for a shears block, the block tags
 * and destroy speed for the fastest tool — never from a list of blocks), and every item must be classifiable. The gap check is
 * narrower: it covers every state the picker takes, and the one item the table gives on every roll (to a hand, the block's tool,
 * the shears, or the block itself to Silk Touch), when no recipe makes it; a state left to another skill, and an item a recipe
 * makes, are counted, not required in the source table. The numbers are printed once per test; a failure names the rows.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCensusTests {
    /** Enough tries to see a chance drop fail (a leaf gives its sapling one time in twenty). */
    private static final int ROLLS = 8;
    /** Rolls for the item census' evidence: a chance drop (gravel's flint one time in ten) is seen within this many. */
    private static final int EVIDENCE = 64;
    private static final int GROUND = 1;
    private static final int ROWS = 40;
    private static final int SAMPLES = 12;

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void everyBlockStateTakesTheFastestToolThatGivesItsDrop(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Entity breaker = new Marker(EntityType.MARKER, level);
        CompanionEntity c = spawn(h);
        List<ItemStack> bag = standardBag();
        // What a recipe makes is made, as in the source table: a block's shears drop that a recipe gives is no shears-only drop.
        Set<Item> made = new HashSet<>();
        for (var holder : level.getRecipeManager().getRecipes()) made.add(holder.value().getResultItem(level.registryAccess()).getItem());
        int states = 0, checked = 0, noDrops = 0, failed = 0;
        Map<String, Integer> failedBlocks = new TreeMap<>();
        List<String> rows = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                states++;
                if (state.isAir() || state.getDestroySpeed(level, BlockPos.ZERO) < 0) { noDrops++; continue; }
                fill(c, bag);
                ItemStack chosen = CompanionTools.bestToolForBlock(c, state);
                // What only shears give is the shears' block, however fast another tool of the bag is (an axe on grass gives seeds).
                // The expectation comes from the loot tables and the recipes, not from the rule under test: the sure drop only
                // the shears give among a hand and the bag, which no recipe makes.
                ItemStack expected = shornByTheTables(level, state, bag, made, breaker) ? new ItemStack(Items.SHEARS) : fastest(c, bag, state);
                Set<Item> toChosen = potential(level, state, chosen, breaker);
                if (toChosen.isEmpty()) {
                    // Nothing to the chosen tool: a block with no drop at all is out of this census; one that gives them to
                    // another tool of the bag is a failure of the rule.
                    Set<Item> toAny = new HashSet<>(potential(level, state, ItemStack.EMPTY, breaker));
                    for (ItemStack stack : bag) toAny.addAll(potential(level, state, stack, breaker));
                    if (toAny.isEmpty()) { noDrops++; continue; }
                    checked++; failed++; failedBlocks.merge(name(block), 1, Integer::sum);
                    add(rows, row(state, expected, chosen, "the chosen tool gives nothing"));
                    continue;
                }
                checked++;
                if (expected.isEmpty()) {
                    if (!chosen.isEmpty()) { failed++; failedBlocks.merge(name(block), 1, Integer::sum); add(rows, row(state, expected, chosen, "a bare hand is as fast")); }
                } else if (chosen.isEmpty()) {
                    failed++; failedBlocks.merge(name(block), 1, Integer::sum); add(rows, row(state, expected, chosen, "a bare hand where the tool is"));
                } else if (!takesDrop(chosen, state)) {
                    failed++; failedBlocks.merge(name(block), 1, Integer::sum); add(rows, row(state, expected, chosen, "the chosen tool gives no drop"));
                } else {
                    float want = CompanionTools.breakSpeed(c, expected, state), got = CompanionTools.breakSpeed(c, chosen, state);
                    if (got != want) { failed++; failedBlocks.merge(name(block), 1, Integer::sum); add(rows, row(state, expected, chosen, "speed " + got + " where " + want + " is the best")); }
                }
            }
        }
        System.out.println("CENSUS tools: states=" + states + " checked=" + checked + " noDrops=" + noDrops
                + " failed=" + failed + " blocks=" + failedBlocks);
        for (String row : rows) System.out.println("CENSUS tool row: " + row);
        h.assertTrue(failed == 0, "tool census: states=" + states + " checked=" + checked + " noDrops=" + noDrops
                + " failed=" + failed + " blocks=" + failedBlocks);
        // The silk path the coral gametest covers, checked against the game's own tables here too: a silk tool in the bag takes
        // a block that only Silk Touch gives as itself (a coral plant or fan, a grass block), not the block's plain drop.
        List<Block> silkOnly = List.of(Blocks.TUBE_CORAL, Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL, Blocks.BRAIN_CORAL_FAN,
                Blocks.BUBBLE_CORAL, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL,
                Blocks.HORN_CORAL_FAN, Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.PODZOL);
        List<ItemStack> silkBag = List.of(silk(h, Items.WOODEN_PICKAXE), silk(h, Items.SHEARS));
        int silkStates = 0, silkFailed = 0;
        List<String> silkRows = new ArrayList<>();
        for (Block block : silkOnly) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                silkStates++;
                fill(c, silkBag);
                ItemStack chosen = CompanionTools.bestSilkToolForBlock(c, state);
                Item own = block.asItem();
                if (chosen.isEmpty() || !potential(level, state, chosen, breaker).contains(own)) {
                    silkFailed++;
                    silkRows.add(name(block) + state.getValues() + " | expected a silk tool | chose " + describe(chosen)
                            + " | the silk tool gives not the block itself");
                }
            }
        }
        System.out.println("CENSUS silk: states=" + silkStates + " failed=" + silkFailed);
        for (String row : silkRows) System.out.println("CENSUS silk row: " + row);
        h.assertTrue(silkFailed == 0, "silk census: states=" + silkStates + " failed=" + silkFailed + " rows=" + silkRows);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void everyItemIsClassifiedAndEveryBlockSourceIsInTheTable(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Entity breaker = new Marker(EntityType.MARKER, level);
        Set<Item> made = new HashSet<>();
        for (var holder : level.getRecipeManager().getRecipes()) made.add(holder.value().getResultItem(level.registryAccess()).getItem());
        Map<Item, Set<Block>> expected = new HashMap<>();
        Map<Item, Set<Block>> evidence = new HashMap<>();
        Map<String, Integer> excluded = new TreeMap<>();
        int states = 0, taken = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                states++;
                if (CompanionPickable.taken(state)) {
                    taken++;
                    ItemStack given = CompanionPickable.sure(level, state, ItemStack.EMPTY, breaker);
                    if (given.isEmpty()) given = CompanionPickable.sure(level, state, CompanionPickable.tool(state), breaker);
                    if (given.isEmpty()) given = CompanionPickable.sure(level, state, new ItemStack(Items.SHEARS), breaker);
                    if (!given.isEmpty() && !made.contains(given.getItem()))
                        expected.computeIfAbsent(given.getItem(), key -> new HashSet<>()).add(block);
                    Item own = block.asItem();
                    if (own != Items.AIR && !made.contains(own)) {
                        ItemStack silked = CompanionPickable.sure(level, state, CompanionPickable.silkTool(level, state), breaker);
                        if (!silked.isEmpty() && silked.getItem() == own) expected.computeIfAbsent(own, key -> new HashSet<>()).add(block);
                    }
                    for (ItemStack tool : new ItemStack[]{ItemStack.EMPTY, CompanionPickable.tool(state), new ItemStack(Items.SHEARS), CompanionPickable.silkTool(level, state)})
                        record(evidence, level, breaker, state, tool, block);
                } else {
                    excluded.merge(excludedReason(state), 1, Integer::sum);
                    for (ItemStack tool : new ItemStack[]{ItemStack.EMPTY, CompanionPickable.tool(state), new ItemStack(Items.SHEARS)})
                        record(evidence, level, breaker, state, tool, block);
                }
            }
        }
        List<String> gaps = new ArrayList<>();
        for (Map.Entry<Item, Set<Block>> entry : expected.entrySet())
            for (Block block : entry.getValue())
                if (CompanionPickable.sources(level, entry.getKey()).stream().noneMatch(source -> source.block() == block))
                    gaps.add(name(entry.getKey()) + " <- " + name(block));
        Set<Item> mob = mobDrops(level, h.makeMockServerPlayerInLevel());
        int source = 0, crafted = 0, hunted = 0, blockOther = 0, none = 0;
        List<String> blockOtherNames = new ArrayList<>(), noneNames = new ArrayList<>(), mobNames = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!CompanionPickable.sources(level, item).isEmpty()) source++;
            else if (made.contains(item)) crafted++;
            else if (mob.contains(item)) { hunted++; mobNames.add(name(item)); }
            else if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
                BlockState def = blockItem.getBlock().defaultBlockState();
                blockOther++;
                blockOtherNames.add(name(item) + " (" + (CompanionPickable.taken(def) ? "picked, another drop" : excludedReason(def)) + ")");
            }
            else if (evidence.containsKey(item)) { blockOther++; blockOtherNames.add(name(item) + " (block drop)"); }
            else { none++; noneNames.add(name(item)); }
        }
        int items = source + crafted + hunted + blockOther + none;
        System.out.println("CENSUS items: items=" + items + " states=" + states + " takenStates=" + taken + " source=" + source
                + " crafted=" + crafted + " mob=" + hunted + " blockOther=" + blockOther + " none=" + none
                + " excluded=" + excluded + " gaps=" + gaps.size());
        System.out.println("CENSUS blockOther sample: " + blockOtherNames.subList(0, Math.min(SAMPLES, blockOtherNames.size())));
        System.out.println("CENSUS mob sample: " + mobNames.subList(0, Math.min(SAMPLES, mobNames.size())));
        System.out.println("CENSUS none sample: " + noneNames.subList(0, Math.min(SAMPLES, noneNames.size())));
        for (String gap : gaps) System.out.println("CENSUS gap: " + gap);
        h.assertTrue(gaps.isEmpty(), "source census: gaps=" + gaps.size() + " of " + expected.size()
                + " items a block gives; items=" + items + " source=" + source + " crafted=" + crafted + " mob=" + hunted
                + " blockOther=" + blockOther + " none=" + none + " first=" + gaps.stream().limit(3).toList());
        h.assertTrue(items == BuiltInRegistries.ITEM.size(), "item census: " + items + " classified of " + BuiltInRegistries.ITEM.size());
        // The row the owner met: a layer of snow gives its snowballs to a shovel, and the layer is the block a golem leaves.
        h.assertTrue(CompanionPickable.sources(level, Items.SNOWBALL).stream().anyMatch(one -> one.block() == Blocks.SNOW),
                "A snow layer is no source of snowballs: " + CompanionPickable.sources(level, Items.SNOWBALL));
        // The other row of that layer: a block no plain break gives gives itself to Silk Touch (a grass block, a coral block).
        h.assertTrue(CompanionPickable.sources(level, Items.GRASS_BLOCK).stream().anyMatch(one -> one.block() == Blocks.GRASS_BLOCK && one.silk()),
                "A grass block is no Silk Touch source of itself: " + CompanionPickable.sources(level, Items.GRASS_BLOCK));
        h.succeed();
    }

    /**
     * Whether the block gives an item on every roll only to the shears of a hand and the standard bag, and no recipe makes that
     * item: the shears rule's expectation from the game's own tables and recipes, never the rule itself. A drop that comes by
     * chance (a vine gives itself to a hand one time in three) is no sure drop, so it does not make the shears wrong.
     */
    private static boolean shornByTheTables(ServerLevel level, BlockState state, List<ItemStack> bag, Set<Item> made, Entity breaker) {
        ItemStack sheared = CompanionPickable.sure(level, state, new ItemStack(Items.SHEARS), breaker);
        if (sheared.isEmpty() || made.contains(sheared.getItem())) return false;
        if (!CompanionPickable.sure(level, state, ItemStack.EMPTY, breaker).isEmpty()) return false;
        // The bag's own shears are the shears already asked: only another tool of the bag would make this no shears block.
        for (ItemStack stack : bag)
            if (!stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR) && !CompanionPickable.sure(level, state, stack, breaker).isEmpty()) return false;
        return true;
    }

    /** The fastest stack of the bag that gives the block's drop: what the rule must choose, from the game's own numbers. */
    private static ItemStack fastest(CompanionEntity c, List<ItemStack> bag, BlockState state) {
        ItemStack fastest = ItemStack.EMPTY;
        float speed = 1;
        for (ItemStack stack : bag) {
            if (!takesDrop(stack, state)) continue;
            float mine = CompanionTools.breakSpeed(c, stack, state);
            if (mine > speed) { fastest = stack; speed = mine; }
        }
        return fastest;
    }

    /** A block a tool gives nothing of: no drop, or a tool that is not the one for it. */
    private static boolean takesDrop(ItemStack stack, BlockState state) {
        return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
    }

    /** Every item the block gives to this tool within the fixed rolls (a chance drop included). */
    private static Set<Item> potential(ServerLevel level, BlockState state, ItemStack tool, Entity breaker) {
        Set<Item> found = new HashSet<>();
        for (int index = 1; index <= ROLLS; index++)
            for (ItemStack drop : CompanionPickable.loot(level, state, tool, breaker, CompanionPickable.seed(index))) if (!drop.isEmpty()) found.add(drop.getItem());
        return found;
    }

    private static void record(Map<Item, Set<Block>> evidence, ServerLevel level, Entity breaker, BlockState state, ItemStack tool, Block block) {
        for (int index = 1; index <= EVIDENCE; index++)
            for (ItemStack drop : CompanionPickable.loot(level, state, tool, breaker, CompanionPickable.seed(index)))
                if (!drop.isEmpty()) evidence.computeIfAbsent(drop.getItem(), key -> new HashSet<>()).add(block);
    }

    /** The standard bag: a pickaxe, axe, shovel and hoe of every tier the game has, and the shears. */
    private static List<ItemStack> standardBag() {
        List<ItemStack> bag = new ArrayList<>();
        for (var tag : List.of(ItemTags.PICKAXES, ItemTags.AXES, ItemTags.SHOVELS, ItemTags.HOES))
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) bag.add(new ItemStack(holder.value()));
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR))
            bag.add(new ItemStack(holder.value()));
        return bag;
    }

    private static void fill(CompanionEntity c, List<ItemStack> bag) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, slot < bag.size() ? bag.get(slot).copy() : ItemStack.EMPTY);
    }

    /** The stack carrying Silk Touch, as this world's registry holds the enchantment. */
    private static ItemStack silk(GameTestHelper h, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        return stack;
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, GROUND, 16);
        c.setNoAi(true);
        c.setOwnerId(h.makeMockServerPlayerInLevel().getUUID());
        return c;
    }

    /** Every item a mob's own loot table can give, rolled over all kinds (a report class, not an assertion). */
    private static Set<Item> mobDrops(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        Set<Item> found = new HashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Entity entity = type.create(level);
            if (entity == null) continue;
            LootTable table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
            if (table == LootTable.EMPTY) continue;
            LootParams params;
            try {
                params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, entity)
                        .withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                        .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                        .create(LootContextParamSets.ENTITY);
            } catch (RuntimeException ignored) { continue; }
            for (int index = 1; index <= EVIDENCE; index++)
                for (ItemStack drop : table.getRandomItems(params, CompanionPickable.seed(index))) if (!drop.isEmpty()) found.add(drop.getItem());
        }
        return found;
    }

    /** Why the source table leaves a block out: the mine, the farm, a lamp, a container, another skill. */
    private static String excludedReason(BlockState state) {
        if (state.hasBlockEntity()) return "block entity";
        if (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER)) return "fluid";
        if (state.is(BlockTags.CROPS)) return "crop";
        if (state.is(BlockTags.SAPLINGS)) return "sapling";
        if (state.is(Blocks.BAMBOO_SAPLING)) return "bamboo shoot";
        if (state.is(Blocks.BIG_DRIPLEAF_STEM)) return "dripleaf stem";
        if (state.is(Blocks.TRIPWIRE)) return "tripwire";
        if (state.getLightEmission() >= 10) return "light";
        if (state.getBlock() instanceof FallingBlock) return "falling";
        if (state.is(Blocks.SNIFFER_EGG)) return "sniffer egg";
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")) return "stripped";
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return "pickaxe (mine)";
        if (state.getBlock().defaultDestroyTime() < 0) return "unbreakable";
        return "other";
    }

    private static String row(BlockState state, ItemStack expected, ItemStack chosen, String why) {
        return name(state.getBlock()) + state.getValues() + " | expected " + describe(expected) + " | chose " + describe(chosen) + " | " + why;
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "bare hand" : name(stack.getItem());
    }

    private static String name(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

    private static String name(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }

    private static void count(Map<String, Integer> counts, String reason) { counts.merge(reason, 1, Integer::sum); }

    private static void add(List<String> rows, String row) { if (rows.size() < ROWS) rows.add(row); }
}
