package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;

/** What the expedition makes of its own bag on the spot, so that a missing tool is no reason to climb to the surface. */
final class CompanionExpeditionSupply {
    private static final int MAX_PICKAXES = 4;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private int pickaxes;

    CompanionExpeditionSupply(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    void reset() { pickaxes = 0; }

    /**
     * A stone pickaxe from the stone he has mined, at a bench he sets down; bounded per step so a bad craft cannot loop. A bench
     * is allowed even with another one remembered within sixteen blocks (the walk to it is the climb this avoids). The one he
     * sets down goes to the mine's temporary blocks at once, and {@link CompanionExpeditionBench} takes it up again.
     */
    boolean craftStonePickaxe() {
        if (pickaxes >= MAX_PICKAXES) return false;
        pickaxes++;
        boolean allowed = owner.newBenchAllowed;
        owner.allowNewBench(true);
        owner.placement.lastTable = null;
        owner.placement.takenUpAtOnce = true;
        boolean made;
        try {
            made = owner.craftFromBag("minecraft:stone_pickaxe", 1).success();
        } finally {
            owner.allowNewBench(allowed);
            owner.placement.takenUpAtOnce = false;
        }
        BlockPos bench = owner.placement.lastTable;
        if (bench != null) w.mine.temporary(bench);
        return made;
    }
}
