package dev.aicompanion.entity;

/** What the expedition makes of its own bag on the spot, so that a missing tool is no reason to climb to the surface. */
final class CompanionExpeditionSupply {
    private static final int MAX_PICKAXES = 4;

    private final CompanionEntity owner;
    private int pickaxes;

    CompanionExpeditionSupply(CompanionEntity owner) { this.owner = owner; }

    void reset() { pickaxes = 0; }

    /** A stone pickaxe from the stone he has mined, at a bench he sets down or carries; bounded per step so a bad craft cannot loop. */
    boolean craftStonePickaxe() {
        if (pickaxes >= MAX_PICKAXES) return false;
        pickaxes++;
        return owner.craftFromBag("minecraft:stone_pickaxe", 1).success();
    }
}
