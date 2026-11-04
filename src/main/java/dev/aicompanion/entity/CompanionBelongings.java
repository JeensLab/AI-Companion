package dev.aicompanion.entity;

import dev.aicompanion.inventory.CompanionEquipmentContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * What a death place is worth walking back for. Vanilla items only: everything that is not worth a trip is listed as
 * junk, the rest carries a price, so the decision is plain arithmetic and never a guess about a modded item.
 */
public final class CompanionBelongings {
    /** Below this the belongings are left where they fell: one diamond is ten, a stone pickaxe is nothing. */
    public static final double WORTH_GOING = 10;
    /** This much food in the bag is worth the walk on its own. */
    private static final int FOOD_WORTH_GOING = 20;
    /** A bag of food above twenty is worth a walk on its own: the bonus alone reaches the threshold. */
    private static final double FOOD_POINTS = 10;
    /** Anything not named here and not junk is worth a little, so a pile of one thing still adds up. */
    private static final double UNNAMED = .1;

    private static final Set<Item> JUNK = Set.of(
            Items.DIRT, Items.GRASS_BLOCK, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.PODZOL, Items.MYCELIUM,
            Items.SAND, Items.RED_SAND, Items.SANDSTONE, Items.RED_SANDSTONE, Items.GRAVEL, Items.CLAY, Items.CLAY_BALL,
            Items.FLINT, Items.STONE, Items.COBBLESTONE, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.TUFF,
            Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.CALCITE, Items.BASALT, Items.SMOOTH_BASALT,
            Items.BLACKSTONE, Items.NETHERRACK, Items.SOUL_SAND, Items.SOUL_SOIL, Items.SNOW, Items.SNOWBALL,
            Items.ICE, Items.PACKED_ICE, Items.MOSS_BLOCK, Items.MOSS_CARPET, Items.VINE, Items.GLOW_LICHEN,
            Items.SHORT_GRASS, Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN, Items.DEAD_BUSH,
            Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS,
            Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD,
            Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING,
            Items.DARK_OAK_SAPLING, Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE, Items.BAMBOO,
            Items.OAK_LEAVES, Items.SPRUCE_LEAVES, Items.BIRCH_LEAVES, Items.JUNGLE_LEAVES, Items.ACACIA_LEAVES,
            Items.DARK_OAK_LEAVES, Items.CHERRY_LEAVES, Items.MANGROVE_LEAVES,
            Items.STICK, Items.TORCH, Items.COAL, Items.CHARCOAL, Items.ROTTEN_FLESH, Items.ARROW,
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
            Items.DARK_OAK_PLANKS, Items.CHERRY_PLANKS, Items.MANGROVE_PLANKS, Items.BAMBOO_PLANKS,
            Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,
            Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE,
            Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE);

    private CompanionBelongings() { }

    /** Everything he carries now, as the price of a walk back to where it would fall. */
    public static double worth(CompanionEntity companion) {
        double total = 0;
        int food = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            total += worth(stack);
            if (stack.has(DataComponents.FOOD)) food += stack.getCount();
        }
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            ItemStack stack = companion.getItemBySlot(slot);
            total += worth(stack);
            if (stack.has(DataComponents.FOOD)) food += stack.getCount();
        }
        for (ItemStack kept : companion.playersThings.held()) {
            total += worth(kept);
            if (kept.has(DataComponents.FOOD)) food += kept.getCount();
        }
        if (food > FOOD_WORTH_GOING) total += FOOD_POINTS;
        return total;
    }

    public static double worth(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (JUNK.contains(item)) return 0;
        double each = unit(stack);
        if (stack.isEnchanted()) each += 8;
        return each * stack.getCount();
    }

    /** The price of one of them; gear goes by its material, not by its kind. */
    private static double unit(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP || item == Items.ANCIENT_DEBRIS) return 12;
        if (item == Items.DIAMOND) return 10;
        if (item == Items.DIAMOND_BLOCK) return 90;
        if (item == Items.EMERALD) return 8;
        if (item == Items.EMERALD_BLOCK) return 72;
        if (item == Items.IRON_INGOT) return 3;
        if (item == Items.NETHERITE_BLOCK) return 108;
        if (item == Items.IRON_NUGGET) return .3;
        if (item == Items.GOLD_NUGGET) return .2;
        if (item == Items.IRON_DOOR || item == Items.IRON_TRAPDOOR || item == Items.IRON_BARS) return 1;
        if (item == Items.IRON_BLOCK) return 27;
        if (item == Items.GOLD_INGOT) return 2;
        if (item == Items.GOLD_BLOCK) return 18;
        if (item == Items.ENCHANTED_BOOK) return 8;
        if (item == Items.ENDER_PEARL || item == Items.BLAZE_ROD) return 3;
        if (item == Items.SHULKER_SHELL) return 6;
        if (item == Items.TOTEM_OF_UNDYING) return 15;
        if (item == Items.ELYTRA) return 20;
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return 15;
        if (item == Items.GOLDEN_APPLE) return 5;
        if (item == Items.LAPIS_LAZULI || item == Items.REDSTONE || item == Items.QUARTZ
                || item == Items.COPPER_INGOT || item == Items.RAW_COPPER || item == Items.RAW_IRON
                || item == Items.RAW_GOLD || item == Items.DIAMOND_ORE || item == Items.DEEPSLATE_DIAMOND_ORE) return 1;
        if (item == Items.IRON_ORE || item == Items.DEEPSLATE_IRON_ORE) return 1;
        if (item == Items.SHIELD) return 4;
        if (item == Items.BOW || item == Items.CROSSBOW) return 3;
        if (item == Items.FLINT_AND_STEEL || item == Items.SHEARS || item == Items.BUCKET) return 2;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("netherite_")) return 15;
        if (path.startsWith("diamond_")) return 12;
        if (path.startsWith("iron_")) return 5;
        if (path.startsWith("golden_")) return 3;
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) return 2;
        return UNNAMED;
    }
}
