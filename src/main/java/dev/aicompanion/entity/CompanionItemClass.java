package dev.aicompanion.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.Set;

/**
 * The kinds of things sort_items puts into separate chests (CompanionSorting). The rules are here and nowhere else, tags first;
 * what no rule knows is «прочее». The order of the checks matters: a wooden pickaxe is a tool, a rotten flesh is a drop, not food.
 */
public enum CompanionItemClass {
    WOOD("дерево"), STONE("камень и земля"), ORES("руды и слитки"), FOOD("еда"), TOOLS("инструменты и броня"), DROPS("добыча с мобов"), OTHER("прочее");

    public final String label;
    CompanionItemClass(String label) { this.label = label; }

    private static final List<TagKey<Item>> DROP_TAGS = List.of(ItemTags.WOOL, Tags.Items.STRINGS, Tags.Items.BONES, Tags.Items.LEATHERS, Tags.Items.FEATHERS,
            Tags.Items.GUNPOWDERS, Tags.Items.SLIMEBALLS, Tags.Items.ENDER_PEARLS, Tags.Items.RODS_BLAZE, Tags.Items.RODS_BREEZE, Tags.Items.EGGS);
    private static final Set<Item> DROP_ITEMS = Set.of(Items.STRING, Items.BONE, Items.LEATHER, Items.FEATHER, Items.ROTTEN_FLESH, Items.GUNPOWDER,
            Items.SPIDER_EYE, Items.SLIME_BALL, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.GHAST_TEAR, Items.INK_SAC, Items.GLOW_INK_SAC,
            Items.PHANTOM_MEMBRANE, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE, Items.BREEZE_ROD, Items.MAGMA_CREAM,
            Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.NAUTILUS_SHELL, Items.SHULKER_SHELL, Items.NETHER_STAR, Items.EGG);
    private static final List<TagKey<Item>> ORE_TAGS = List.of(Tags.Items.ORES, Tags.Items.INGOTS, Tags.Items.NUGGETS, Tags.Items.GEMS, Tags.Items.RAW_MATERIALS,
            Tags.Items.DUSTS, ItemTags.COALS);
    private static final Set<Item> ORE_ITEMS = Set.of(Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ, Items.AMETHYST_SHARD, Items.NETHERITE_SCRAP,
            Items.ANCIENT_DEBRIS, Items.GLOWSTONE_DUST, Items.COAL, Items.CHARCOAL, Items.DIAMOND, Items.EMERALD, Items.COPPER_INGOT);
    private static final List<TagKey<Item>> WOOD_TAGS = List.of(ItemTags.LOGS, ItemTags.PLANKS, ItemTags.SAPLINGS, ItemTags.LEAVES, ItemTags.WOODEN_STAIRS,
            ItemTags.WOODEN_SLABS, ItemTags.WOODEN_FENCES, ItemTags.FENCE_GATES, ItemTags.WOODEN_DOORS, ItemTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_BUTTONS,
            ItemTags.WOODEN_PRESSURE_PLATES, ItemTags.SIGNS, ItemTags.HANGING_SIGNS, ItemTags.BOATS, ItemTags.CHEST_BOATS, ItemTags.BAMBOO_BLOCKS);
    private static final Set<Item> WOOD_ITEMS = Set.of(Items.STICK, Items.CRAFTING_TABLE, Items.CHEST, Items.TRAPPED_CHEST, Items.BARREL, Items.LADDER,
            Items.BOOKSHELF, Items.BOWL);
    private static final List<TagKey<Item>> STONE_TAGS = List.of(ItemTags.STONE_CRAFTING_MATERIALS, ItemTags.STONE_TOOL_MATERIALS, ItemTags.STONE_BRICKS,
            ItemTags.DIRT, ItemTags.SAND, ItemTags.TERRACOTTA, ItemTags.WALLS, ItemTags.STAIRS, ItemTags.SLABS, Tags.Items.STONES, Tags.Items.COBBLESTONES,
            Tags.Items.GRAVELS, Tags.Items.SANDS, Tags.Items.NETHERRACKS, Tags.Items.END_STONES, Tags.Items.OBSIDIANS, Tags.Items.SANDSTONE_BLOCKS,
            Tags.Items.CONCRETES, Tags.Items.CONCRETE_POWDERS, Tags.Items.GLAZED_TERRACOTTAS, Tags.Items.BRICKS);
    private static final Set<Item> STONE_ITEMS = Set.of(Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.STONE, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.GRAVEL, Items.CLAY, Items.CLAY_BALL,
            Items.FLINT, Items.MUD, Items.PACKED_MUD, Items.SOUL_SAND, Items.SOUL_SOIL, Items.GRASS_BLOCK, Items.MYCELIUM, Items.PODZOL, Items.TUFF,
            Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.BASALT, Items.SMOOTH_BASALT, Items.BRICK, Items.NETHER_BRICK);

    public static CompanionItemClass of(ItemStack stack) {
        if (stack.isDamageableItem() || stack.getItem() instanceof ArmorItem || stack.is(ItemTags.ARROWS) || stack.is(Tags.Items.TOOLS)) return TOOLS;
        if (isAny(stack, DROP_TAGS, DROP_ITEMS)) return DROPS;
        if (stack.has(DataComponents.FOOD)) return FOOD;
        if (isAny(stack, ORE_TAGS, ORE_ITEMS) || isMineralBlock(stack)) return ORES;
        // Wooden stairs and slabs are wood, so wood is asked before the building stone that holds all stairs and slabs.
        if (isAny(stack, WOOD_TAGS, WOOD_ITEMS)) return WOOD;
        if (isAny(stack, STONE_TAGS, STONE_ITEMS)) return STONE;
        return OTHER;
    }

    /** Blocks of iron, gold, coal, redstone… but not hay, slime or dried kelp, which the same tag holds. */
    private static boolean isMineralBlock(ItemStack stack) {
        return stack.is(Tags.Items.STORAGE_BLOCKS) && !stack.is(Tags.Items.STORAGE_BLOCKS_WHEAT) && !stack.is(Tags.Items.STORAGE_BLOCKS_SLIME)
                && !stack.is(Tags.Items.STORAGE_BLOCKS_DRIED_KELP) && !stack.is(Tags.Items.STORAGE_BLOCKS_BONE_MEAL);
    }

    private static boolean isAny(ItemStack stack, List<TagKey<Item>> tags, Set<Item> items) {
        if (items.contains(stack.getItem())) return true;
        for (TagKey<Item> tag : tags) if (stack.is(tag)) return true;
        return false;
    }
}
