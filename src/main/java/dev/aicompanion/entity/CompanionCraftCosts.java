package dev.aicompanion.entity;

import java.util.Set;

/** What the craftable items cost in stone and iron; shared by the work, the chests and the plan checks. */
public final class CompanionCraftCosts {
    private CompanionCraftCosts() {}

    public static int stoneCost(String item) {
        return switch (item) {
            case "minecraft:stone_pickaxe", "minecraft:stone_axe" -> 3;
            case "minecraft:stone_sword", "minecraft:stone_hoe" -> 2;
            case "minecraft:stone_shovel" -> 1;
            case "minecraft:furnace" -> 8;
            default -> 0;
        };
    }

    /** Iron ingots one iron tool takes; 0 for anything else. */
    public static int ingotCost(String item) {
        return switch (item) {
            case "minecraft:iron_pickaxe", "minecraft:iron_axe" -> 3;
            case "minecraft:iron_sword", "minecraft:iron_hoe" -> 2;
            case "minecraft:iron_shovel" -> 1;
            default -> 0;
        };
    }

    public static boolean supportsCraft(String item, int count) {
        if (count < 1 || count > 16) return false;
        // Iron tools: the ingots are mined and smelted on the way, at most one ore order's worth.
        if (ingotCost(item) > 0) return ingotCost(item) * count <= CompanionWork.MAX_ORE;
        return stoneCost(item) > 0 || Set.of("minecraft:chest", "minecraft:crafting_table", "minecraft:stick",
                "minecraft:wooden_pickaxe", "minecraft:wooden_axe", "minecraft:wooden_sword",
                "minecraft:wooden_shovel", "minecraft:wooden_hoe").contains(item);
    }
}
