package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;

/**
 * The crops Oleg sows and reaps («сделай огород», «собери урожай»): what goes into the ground, what grows from it and how it is
 * called. The four ordinary food crops, and the stems of a pumpkin and a melon; any other seed is not planted on a model's word.
 */
public final class CompanionCrops {
    private CompanionCrops() { }

    /** Farmland tiles one farm_crops order makes (a bigger field is several orders), the usual field, and the most crops one harvest takes. */
    public static final int MAX_TILES = 16, DEFAULT_TILES = 8, MAX_HARVEST = 64;

    public enum Kind {
        WHEAT(Items.WHEAT_SEEDS, Blocks.WHEAT, "семян пшеницы", "пшеницу"),
        CARROT(Items.CARROT, Blocks.CARROTS, "моркови", "морковь"),
        POTATO(Items.POTATO, Blocks.POTATOES, "картофеля", "картофель"),
        BEETROOT(Items.BEETROOT_SEEDS, Blocks.BEETROOTS, "семян свёклы", "свёклу"),
        PUMPKIN(Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM, "семян тыквы", "тыкву", Blocks.PUMPKIN, Blocks.ATTACHED_PUMPKIN_STEM, Items.PUMPKIN, 4, "тыквы"),
        MELON(Items.MELON_SEEDS, Blocks.MELON_STEM, "семян арбуза", "арбуз", Blocks.MELON, Blocks.ATTACHED_MELON_STEM, Items.MELON_SLICE, 1, "ломтика арбуза");

        /** What is put into the ground (carrots and potatoes are their own seed), and the plant that grows from it. */
        public final Item seed;
        public final Block crop;
        /** «Нет семян пшеницы», «нет моркови» and «посадил пшеницу», «посадил морковь». */
        public final String lacking, plant;
        /**
         * A stem's: the fruit that grows beside it, the stem it turns into while the fruit stands, the produce the seeds are made of
         * (so many seeds of one) and «ни тыквы»; null and 0 for the four crops that ripen where they stand.
         */
        public final Block fruit, attached;
        public final Item produce;
        public final int seedsOfOne;
        public final String produceLacking;
        Kind(Item seed, Block crop, String lacking, String plant) { this(seed, crop, lacking, plant, null, null, null, 0, null); }
        Kind(Item seed, Block crop, String lacking, String plant, Block fruit, Block attached, Item produce, int seedsOfOne, String produceLacking) {
            this.seed = seed; this.crop = crop; this.lacking = lacking; this.plant = plant;
            this.fruit = fruit; this.attached = attached; this.produce = produce; this.seedsOfOne = seedsOfOne; this.produceLacking = produceLacking;
        }

        /** A pumpkin or a melon: its stem bears the fruit on the free soil beside it, again and again. */
        public boolean stem() { return fruit != null; }

        public String seedId() { return BuiltInRegistries.ITEM.getKey(seed).toString(); }

        /** The seed or the produce, with or without «minecraft:», singular or plural; null for anything else (and for «any»). */
        public static Kind of(String item) {
            return switch (item == null ? "" : item.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "")) {
                case "wheat_seeds", "wheat" -> WHEAT;
                case "carrot", "carrots" -> CARROT;
                case "potato", "potatoes" -> POTATO;
                case "beetroot_seeds", "beetroot", "beetroots" -> BEETROOT;
                case "pumpkin_seeds", "pumpkin", "pumpkins" -> PUMPKIN;
                case "melon_seeds", "melon", "melons", "melon_slice" -> MELON;
                default -> null;
            };
        }

        /** The kind whose stem the block is, bare or bearing; null for anything else. */
        public static Kind stemOf(Block block) {
            for (Kind kind : values()) if (kind.stem() && (kind.crop == block || kind.attached == block)) return kind;
            return null;
        }

        /** The crop the item is reaped from: wheat, a carrot, a potato, a beetroot; null for a seed that is not the harvest itself. */
        public static Kind yielding(Item item) {
            return item == Items.WHEAT ? WHEAT : item == Items.CARROT ? CARROT : item == Items.POTATO ? POTATO : item == Items.BEETROOT ? BEETROOT : null;
        }
    }

    /** Whether the block is a pumpkin or a melon a stem beside it holds: grown there, not set down by someone. */
    public static boolean onStem(Level level, BlockPos fruit) {
        if (!level.isLoaded(fruit)) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos at = fruit.relative(side);
            if (!level.isLoaded(at)) continue;
            var stem = level.getBlockState(at);
            Kind kind = stem.getBlock() instanceof AttachedStemBlock ? Kind.stemOf(stem.getBlock()) : null;
            if (kind != null && stem.getValue(AttachedStemBlock.FACING) == side.getOpposite() && level.getBlockState(fruit).is(kind.fruit)) return true;
        }
        return false;
    }

    /** «Any» crop: no kind named, or one of the words that mean all of them. */
    public static boolean anyKind(String item) {
        String id = item == null ? "" : item.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        return id.isEmpty() || List.of("any", "all", "crops", "resources").contains(id);
    }
}
