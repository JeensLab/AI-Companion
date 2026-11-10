package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the expedition makes of its own bag on the spot, so that a missing tool is no reason to climb to the surface. */
final class CompanionExpeditionSupply {
    private static final int MAX_FAILED_PICKAXES = 2, MAX_SWORDS = 2, SWORD_STONE = 2, PICKAXE_STONE = 3, SWORD_STICKS = 1, PICKAXE_STICKS = 2, BENCH_PLANKS = 4;

    /** Stone pickaxes he may still need in a mine before he is back at the surface; the digging keeps this much stone for them, whatever it seals with. */
    private static final int PICKAXES_KEPT = 4;
    static final int STONE_RESERVE = PICKAXES_KEPT * PICKAXE_STONE;
    /** Free slots a pickaxe chain needs at most: planks, sticks and the bench in turn, then the pickaxe. */
    private static final int CRAFT_SLOTS = 3;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private int failedPickaxes, swords;

    CompanionExpeditionSupply(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    void reset() { failedPickaxes = swords = 0; }

    /**
     * A stone pickaxe from the stone he has mined, at a bench he sets down; as many as the bag's stone and sticks allow; a craft that fails costs nothing and would be retried every tick, so after
     * {@link #MAX_FAILED_PICKAXES} failures in a row it is not tried again until {@link #reset()}. A bench
     * is allowed even with another one remembered within sixteen blocks (the walk to it is the climb this avoids). The one he
     * sets down goes to the mine's temporary blocks at once, and {@link CompanionExpeditionBench} takes it up again.
     */
    boolean craftStonePickaxe() {
        if (failedPickaxes >= MAX_FAILED_PICKAXES) return false;
        if (canMakePickaxe()) makeSlots();
        boolean made = craft("minecraft:stone_pickaxe");
        failedPickaxes = made ? 0 : failedPickaxes + 1;
        return made;
    }

    /**
     * The bag holds what a stone pickaxe takes, yet the craft failed (no room for a bench on this stand): the iron pickaxe digs on to
     * another stand rather than he climbs, and {@link #forgiveFailedCrafts} lets the crafting try again there.
     */
    boolean stuckCrafting() { return failedPickaxes > 0 && canMakePickaxe(); }

    void forgiveFailedCrafts() { failedPickaxes = 0; }

    /** Stone, wood for the sticks and a bench (carried, or made of the wood) are in the bag: a stone pickaxe can be made on the spot. */
    boolean canMakePickaxe() { return craftable(w) > 0; }

    /** How many stone pickaxes the bag's stone, sticks (or wood for them) and bench can still make; wood alone counts as one. */
    static int craftable(CompanionWork w) {
        CompanionEntity owner = w.owner;
        int planks = CompanionTools.count(owner, ItemTags.PLANKS), logs = CompanionTools.count(owner, ItemTags.LOGS), sticks = w.items.countCarried(Items.STICK);
        boolean bench = w.items.countCarried(Items.CRAFTING_TABLE) > 0 || planks >= BENCH_PLANKS || logs > 0;
        if (!bench) return 0;
        int woodSets = Math.max(sticks / PICKAXE_STICKS, planks >= PICKAXE_STICKS || logs > 0 ? 1 : 0);
        return Math.min(woodSets, CompanionTools.count(owner, ItemTags.STONE_TOOL_MATERIALS) / PICKAXE_STONE);
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

    /** Coal or charcoal in the bag, and sticks (or wood for them) to spare: torches can be made on the spot. */
    boolean canMakeTorch() {
        for (ItemStack stack : w.items.carriedItems()) if (stack.is(ItemTags.COALS)) return spareForTorch();
        return false;
    }

    /** A stone sword the same way, so that a fight is never met with a pickaxe; bounded per expedition too. */
    boolean craftStoneSword() {
        if (swords >= MAX_SWORDS) return false;
        swords++;
        return craft("minecraft:stone_sword");
    }

    /** A full bag fails the whole chain: the rubble he already throws out when a diamond has no slot goes out first, only as many stacks as the chain needs. */
    private void makeSlots() {
        if (!(owner.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        while (freeSlots() < CRAFT_SLOTS && CompanionExpeditionDig.tossSpare(owner, level)) { }
    }

    private int freeSlots() {
        int free = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).isEmpty()) free++;
        return free;
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
