package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;

/**
 * The crops Oleg sows and reaps («сделай огород», «собери урожай»): what goes into the ground, what grows from it and how it is
 * called. Only the four ordinary food crops; any other seed is not planted on a model's word.
 */
public final class CompanionCrops {
    private CompanionCrops() { }

    /** Farmland tiles one farm_crops order makes (a bigger field is several orders), the usual field, and the most crops one harvest takes. */
    public static final int MAX_TILES = 16, DEFAULT_TILES = 8, MAX_HARVEST = 64;

    public enum Kind {
        WHEAT(Items.WHEAT_SEEDS, Blocks.WHEAT, "семян пшеницы", "пшеницу"),
        CARROT(Items.CARROT, Blocks.CARROTS, "моркови", "морковь"),
        POTATO(Items.POTATO, Blocks.POTATOES, "картофеля", "картофель"),
        BEETROOT(Items.BEETROOT_SEEDS, Blocks.BEETROOTS, "семян свёклы", "свёклу");

        /** What is put into the ground (carrots and potatoes are their own seed), and the plant that grows from it. */
        public final Item seed;
        public final Block crop;
        /** «Нет семян пшеницы», «нет моркови» and «посадил пшеницу», «посадил морковь». */
        public final String lacking, plant;
        Kind(Item seed, Block crop, String lacking, String plant) { this.seed = seed; this.crop = crop; this.lacking = lacking; this.plant = plant; }

        public String seedId() { return BuiltInRegistries.ITEM.getKey(seed).toString(); }

        /** The seed or the produce, with or without «minecraft:», singular or plural; null for anything else (and for «any»). */
        public static Kind of(String item) {
            return switch (item == null ? "" : item.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "")) {
                case "wheat_seeds", "wheat" -> WHEAT;
                case "carrot", "carrots" -> CARROT;
                case "potato", "potatoes" -> POTATO;
                case "beetroot_seeds", "beetroot", "beetroots" -> BEETROOT;
                default -> null;
            };
        }
    }

    /** «Any» crop: no kind named, or one of the words that mean all of them. */
    public static boolean anyKind(String item) {
        String id = item == null ? "" : item.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        return id.isEmpty() || List.of("any", "all", "crops", "resources").contains(id);
    }
}
