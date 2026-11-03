package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the expedition makes of its own bag on the spot, so that a missing tool is no reason to climb to the surface. */
final class CompanionExpeditionSupply {
    private static final int MAX_PICKAXES = 4, MAX_SWORDS = 2, SWORD_STONE = 2, PICKAXE_STONE = 3, SWORD_STICKS = 1, PICKAXE_STICKS = 2, BENCH_PLANKS = 4;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private int pickaxes, swords;

    CompanionExpeditionSupply(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    void reset() { pickaxes = swords = 0; }

    /**
     * A stone pickaxe from the stone he has mined, at a bench he sets down; bounded per step so a bad craft cannot loop. A bench
     * is allowed even with another one remembered within sixteen blocks (the walk to it is the climb this avoids). The one he
     * sets down goes to the mine's temporary blocks at once, and {@link CompanionExpeditionBench} takes it up again.
     */
    boolean craftStonePickaxe() {
        if (pickaxes >= MAX_PICKAXES) return false;
        pickaxes++;
        return craft("minecraft:stone_pickaxe");
    }

    /** Stone, wood for the sticks and a bench (carried, or made of the wood) are in the bag: a stone pickaxe can be made on the spot. */
    boolean canMakePickaxe() {
        int planks = CompanionTools.count(owner, ItemTags.PLANKS), logs = CompanionTools.count(owner, ItemTags.LOGS);
        boolean wood = w.items.countCarried(Items.STICK) >= PICKAXE_STICKS || planks >= PICKAXE_STICKS || logs > 0;
        boolean bench = w.items.countCarried(Items.CRAFTING_TABLE) > 0 || planks >= BENCH_PLANKS || logs > 0;
        return wood && bench && CompanionTools.count(owner, ItemTags.STONE_TOOL_MATERIALS) >= PICKAXE_STONE;
    }

    /** The stone pickaxe in hand, and the stone and sticks of a sword and a pickaxe to spare: the sword must never eat what the pickaxe is to be made of. */
    boolean spareForSword() {
        int sticks = 0;
        for (ItemStack stack : owner.companionInventory) if (stack.is(Items.STICK)) sticks += stack.getCount();
        return owner.equipment.hasStonePickaxe() && sticks >= SWORD_STICKS + PICKAXE_STICKS
                && CompanionTools.count(owner, ItemTags.STONE_TOOL_MATERIALS) >= SWORD_STONE + PICKAXE_STONE;
    }

    /** A torch takes a stick: wood in the bag makes more, otherwise the two sticks of the next stone pickaxe stay (underground there is no wood). */
    boolean spareForTorch() {
        int sticks = 0;
        for (ItemStack stack : owner.companionInventory) {
            if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS)) return true;
            if (stack.is(Items.STICK)) sticks += stack.getCount();
        }
        return sticks > PICKAXE_STICKS;
    }

    /** A stone sword the same way, so that a fight is never met with a pickaxe; bounded per step too. */
    boolean craftStoneSword() {
        if (swords >= MAX_SWORDS) return false;
        swords++;
        return craft("minecraft:stone_sword");
    }

    private boolean craft(String item) {
        boolean allowed = owner.newBenchAllowed;
        owner.allowNewBench(true);
        owner.placement.benchSetDown = null;
        owner.placement.takenUpAtOnce = true;
        boolean made;
        try {
            made = owner.craftFromBag(item, 1).success();
        } finally {
            owner.allowNewBench(allowed);
            owner.placement.takenUpAtOnce = false;
        }
        BlockPos bench = owner.placement.benchSetDown;
        if (bench != null) w.mine.temporary(bench);
        return made;
    }
}
