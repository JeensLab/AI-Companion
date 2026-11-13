package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A species of wood a player can name («наруби берёзы»): the game's own per-species tags, so stripped logs and wood blocks count too.
 * An empty item is no species at all (any log); anything that is not an item of one of these tags is not a species.
 */
public enum CompanionWoodKind {
    OAK(BlockTags.OAK_LOGS, ItemTags.OAK_LOGS, "дуба"),
    SPRUCE(BlockTags.SPRUCE_LOGS, ItemTags.SPRUCE_LOGS, "ели"),
    BIRCH(BlockTags.BIRCH_LOGS, ItemTags.BIRCH_LOGS, "берёзы"),
    JUNGLE(BlockTags.JUNGLE_LOGS, ItemTags.JUNGLE_LOGS, "тропического дерева"),
    ACACIA(BlockTags.ACACIA_LOGS, ItemTags.ACACIA_LOGS, "акации"),
    DARK_OAK(BlockTags.DARK_OAK_LOGS, ItemTags.DARK_OAK_LOGS, "тёмного дуба"),
    MANGROVE(BlockTags.MANGROVE_LOGS, ItemTags.MANGROVE_LOGS, "мангрового дерева"),
    CHERRY(BlockTags.CHERRY_LOGS, ItemTags.CHERRY_LOGS, "вишни"),
    CRIMSON(BlockTags.CRIMSON_STEMS, ItemTags.CRIMSON_STEMS, "багровых стволов"),
    WARPED(BlockTags.WARPED_STEMS, ItemTags.WARPED_STEMS, "искажённых стволов");

    public final TagKey<Block> blocks;
    public final TagKey<Item> items;
    private final String genitive;

    CompanionWoodKind(TagKey<Block> blocks, TagKey<Item> items, String genitive) {
        this.blocks = blocks; this.items = items; this.genitive = genitive;
    }

    /** «берёзы»: the species in the place of «брёвен …». */
    public String genitive() { return genitive; }

    /** The refusal when none of the species stands within his search. */
    public String none() { return Character.toUpperCase(genitive.charAt(0)) + genitive.substring(1) + " рядом не нашёл."; }

    /** The refusal when its trees stand near but none could be got to. */
    public String outOfReach() { return "До брёвен " + genitive + " рядом не смог добраться."; }

    /** The species an item id names, or null: empty, not an item, or not wood of a species (bamboo, stone). */
    public static CompanionWoodKind of(String item) {
        var id = item == null || item.isBlank() ? null : ResourceLocation.tryParse(item.trim());
        var found = id == null ? java.util.Optional.<Item>empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (found.isEmpty()) return null;
        ItemStack stack = new ItemStack(found.get());
        for (CompanionWoodKind kind : values()) if (stack.is(kind.items)) return kind;
        return null;
    }
}
