package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Milestones of a long goal: conditions checked against {@link CompanionFacts}, each with a short plan of known actions that
 * reaches it. The plan is built only for the next unmet milestone and only from fresh facts; a milestone already met is skipped,
 * so an interruption, a reload or a death never repeats what is done.
 */
public final class CompanionMilestones {
    /** Kinds of gear in the order of what they are better than; netherite only ever counts as "at least diamond". */
    public enum Tier {
        IRON("iron", Items.IRON_INGOT), DIAMOND("diamond", Items.DIAMOND), NETHERITE("netherite", null);
        final String prefix; final Item material;
        Tier(String prefix, Item material) { this.prefix = prefix; this.material = material; }
    }

    public enum Piece {
        PICKAXE("pickaxe", 3, null), SWORD("sword", 2, null), HELMET("helmet", 5, EquipmentSlot.HEAD),
        CHESTPLATE("chestplate", 8, EquipmentSlot.CHEST), LEGGINGS("leggings", 7, EquipmentSlot.LEGS), BOOTS("boots", 4, EquipmentSlot.FEET);
        final String suffix; final int cost; final EquipmentSlot slot;
        Piece(String suffix, int cost, EquipmentSlot slot) { this.suffix = suffix; this.cost = cost; this.slot = slot; }
        Item item(Tier tier) { return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(tier.prefix + "_" + suffix)); }
    }

    public enum Kind { PIECE, MATERIALS, FOOD, STOCK, SKILL, DRAGON, EXPEDITION }

    /** One step of a milestone's plan: an allowlisted action as the plan runner takes it. */
    public record Step(String type, String item, int count, String slot) { }

    private static final Set<Item> READY_FOOD = Set.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
            Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BREAD, Items.BAKED_POTATO, Items.GOLDEN_CARROT);
    private static final Set<Item> RAW_MEAT = Set.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT);
    /** What a step of the mine starts with: below the minimum he tops up to the stock on the surface (supplies are never milestones, they are used up below). */
    private static final int TORCHES_MIN = 16, TORCHES_STOCK = 32, STONE_PICKAXES_MIN = 2, STONE_PICKAXES_STOCK = 4, FOOD_MIN = 4, IRON_SWORD_INGOTS = 2;
    /** Swords he goes down with; without one he fights the tunnel's monsters with his pickaxes (a wooden or golden one is no better). */
    private static final Set<Item> MINE_SWORDS = Set.of(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);

    /**
     * {@code supported}: Oleg has the skills for it now; the first unmet one without them is where the goal stops and says so.
     * A diamond milestone falls back to iron for the whole goal once diamonds cannot be got.
     */
    public record Milestone(String key, Kind kind, Piece piece, Tier tier, Item item, int count, String label, boolean supported) {
        /** A diamond milestone, the only kind that can fall back to iron. */
        boolean concernsDiamonds(boolean fallback) { return kind == Kind.MATERIALS && effective(tier, fallback) == Tier.DIAMOND; }

        public String label(boolean fallback) {
            return switch (kind) {
                case PIECE -> (piece.slot == null ? "" : "надеть ") + "«" + CompanionWork.itemName(piece.item(effective(tier, fallback))) + "»";
                case MATERIALS -> "материал на снаряжение: «" + CompanionWork.itemName(effective(tier, fallback).material) + "»";
                default -> label;
            };
        }

        public boolean satisfied(CompanionFacts facts, boolean fallback, List<Milestone> all) {
            return switch (kind) {
                case PIECE -> piece.slot == null ? carriedAtLeast(facts, piece, effective(tier, fallback)) > 0
                        : atLeast(piece, effective(tier, fallback)).contains(facts.worn(piece.slot));
                case MATERIALS -> facts.carried(effective(tier, fallback).material) >= need(facts, fallback, all);
                case FOOD -> facts.carried(READY_FOOD::contains) >= count;
                case STOCK, EXPEDITION -> facts.carried(item) >= count;
                case SKILL -> false;
                case DRAGON -> facts.dragonDead();
            };
        }

        /**
         * What a step toward this milestone can change, compared with equals: a run that ended with the same list moved nothing,
         * one that ended with another list made progress even if the milestone is not met yet.
         */
        public List<Integer> progress(CompanionFacts facts, boolean fallback, List<Milestone> all) {
            List<Integer> signature = new ArrayList<>();
            signature.add(satisfied(facts, fallback, all) ? 1 : 0);
            Tier tier = effective(this.tier, fallback);
            switch (kind) {
                case PIECE -> {
                    signature.add(carriedAtLeast(facts, piece, tier));
                    signature.add(materialCount(facts, tier));
                    if (tier == Tier.IRON) signature.add(facts.carried(Items.RAW_IRON));
                }
                case MATERIALS -> {
                    signature.add(materialCount(facts, tier));
                    if (tier == Tier.IRON) signature.add(facts.carried(Items.RAW_IRON));
                }
                case FOOD -> {
                    signature.add(facts.carried(READY_FOOD::contains));
                    signature.add(facts.carried(RAW_MEAT::contains));
                }
                case STOCK -> signature.add(facts.carried(item));
                case EXPEDITION -> { signature.add(facts.carried(item)); signature.add(facts.mineOpened()); }
                case SKILL, DRAGON -> { }
            }
            return signature;
        }

        /** Whether a batch of his own that cooks {@code output} is what this milestone waits for: it is not made again meanwhile. */
        public boolean dependsOn(Item output, CompanionFacts facts, boolean fallback) {
            return switch (kind) {
                case PIECE -> carriedAtLeast(facts, piece, effective(tier, fallback)) == 0 && output == effective(tier, fallback).material;
                case MATERIALS -> output == effective(tier, fallback).material;
                case FOOD -> READY_FOOD.contains(output);
                case STOCK -> output == item;
                case EXPEDITION -> READY_FOOD.contains(output) && facts.carried(READY_FOOD::contains) < FOOD_MIN;
                case SKILL, DRAGON -> false;
            };
        }

        private static int materialCount(CompanionFacts facts, Tier tier) {
            return tier.material == null ? 0 : facts.carried(tier.material);
        }

        /** The steps from here, for the facts as they are now; empty for a milestone he has no skill for. */
        public List<Step> method(CompanionFacts facts, boolean fallback, List<Milestone> all) {
            Tier tier = effective(this.tier, fallback);
            return switch (kind) {
                case PIECE -> {
                    Item wanted = piece.item(tier);
                    if (piece.slot == null) yield List.of(obtain(wanted, 1));
                    Item carried = bestCarried(facts, piece, tier);
                    yield carried != null ? List.of(equip(carried)) : List.of(obtain(wanted, 1), equip(wanted));
                }
                case MATERIALS -> List.of(obtain(tier.material, need(facts, fallback, all)));
                case FOOD -> foodSteps(facts, count);
                case EXPEDITION -> {
                    List<Step> steps = new ArrayList<>();
                    if (facts.carried(Items.TORCH) < TORCHES_MIN) steps.add(obtain(Items.TORCH, TORCHES_STOCK));
                    if (facts.carried(Items.STONE_PICKAXE) < STONE_PICKAXES_MIN) steps.add(obtain(Items.STONE_PICKAXE, STONE_PICKAXES_STOCK));
                    if (facts.carried(MINE_SWORDS::contains) == 0)
                        steps.add(obtain(facts.carried(Items.IRON_INGOT) >= IRON_SWORD_INGOTS ? Items.IRON_SWORD : Items.STONE_SWORD, 1));
                    if (facts.carried(READY_FOOD::contains) < FOOD_MIN) steps.addAll(foodSteps(facts, FOOD_MIN));
                    steps.add(new Step("dig_for_diamonds", "", Math.max(1, count - facts.carried(item)), "auto"));
                    yield steps;
                }
                case STOCK -> supported ? List.of(obtain(item, count)) : List.of();
                case SKILL, DRAGON -> List.of();
            };
        }

        private static List<Step> foodSteps(CompanionFacts facts, int target) {
            int missing = target - facts.carried(READY_FOOD::contains), raw = facts.carried(RAW_MEAT::contains);
            Step cook = new Step("smelt_items", CompanionGame.ANY_MEAT, CompanionWork.ALL_CARRIED, "auto");
            return raw >= missing ? List.of(cook)
                    : List.of(new Step("hunt_animals", "food", Math.min(CompanionWork.MAX_GAME, missing - raw), "auto"), cook);
        }

        /** Material for the pieces of this tier that are neither met nor carried: a carried helmet only needs putting on. */
        private int need(CompanionFacts facts, boolean fallback, List<Milestone> all) {
            Tier tier = effective(this.tier, fallback);
            int need = 0;
            for (Milestone other : all)
                if (other.kind == Kind.PIECE && other.tier == this.tier && !other.satisfied(facts, fallback, all)
                        && carriedAtLeast(facts, other.piece, tier) == 0) need += other.piece.cost;
            return need;
        }
    }

    static Milestone piece(Piece piece, Tier tier) {
        return new Milestone("piece:" + tier.prefix + ":" + piece.suffix, Kind.PIECE, piece, tier, piece.item(tier), 1, "", true);
    }

    static Milestone materials(Tier tier) {
        return new Milestone("materials:" + tier.prefix, Kind.MATERIALS, null, tier, tier.material, 0, "", true);
    }

    static Milestone food(int count) {
        return new Milestone("food", Kind.FOOD, null, null, null, count, "запас готовой еды: " + count, true);
    }

    static Milestone stock(Item item, int count, String label, boolean supported) {
        return new Milestone("stock:" + BuiltInRegistries.ITEM.getKey(item).getPath(), Kind.STOCK, null, null, item, count, label, supported);
    }

    /** The mine: done when the bag holds {@code count} diamonds; each step of it is bounded, the goal repeats the step. */
    static Milestone expedition(int count) {
        return new Milestone("expedition:diamond", Kind.EXPEDITION, null, null, Items.DIAMOND, count, "добыть алмазы: " + count, true);
    }

    static Milestone skill(String name, String label) {
        return new Milestone("skill:" + name, Kind.SKILL, null, null, null, 0, label, false);
    }

    static Milestone dragon() {
        return new Milestone("dragon", Kind.DRAGON, null, null, null, 0, "победить дракона Края", false);
    }

    static Tier effective(Tier tier, boolean fallback) { return fallback && tier == Tier.DIAMOND ? Tier.IRON : tier; }

    /** The piece of this tier or a better one. */
    static List<Item> atLeast(Piece piece, Tier tier) {
        List<Item> items = new ArrayList<>();
        for (Tier better : Tier.values()) if (better.ordinal() >= tier.ordinal()) items.add(piece.item(better));
        return items;
    }

    private static int carriedAtLeast(CompanionFacts facts, Piece piece, Tier tier) {
        return atLeast(piece, tier).stream().mapToInt(facts::carried).sum();
    }

    private static Item bestCarried(CompanionFacts facts, Piece piece, Tier tier) {
        List<Item> items = atLeast(piece, tier);
        for (int i = items.size() - 1; i >= 0; i--) if (facts.carried(items.get(i)) > 0) return items.get(i);
        return null;
    }

    private static Step obtain(Item item, int count) {
        return new Step("obtain_item", BuiltInRegistries.ITEM.getKey(item).toString(), count, "auto");
    }

    private static Step equip(Item item) {
        return new Step("equip_item", BuiltInRegistries.ITEM.getKey(item).toString(), 1, "auto");
    }
}
