package dev.aicompanion.entity;

import dev.aicompanion.entity.CompanionExpedition.Ending;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The way out of a pool with no walk to its bank: a block under his feet, then a staircase cut and laid up towards the goal of the walk (moved out of {@link CompanionExpedition}). */
final class CompanionExpeditionWade {
    /** What the expedition does next: DONE, the way is found (the failed walks are forgotten); RETRY, the walk is tried again after a while. */
    enum Result { WORKING, DONE, RETRY }

    /** Ticks waited for the float to bring him up to the top of the water, where the block under his feet goes. */
    private static final int WADE_PATIENCE = 20 * 15;
    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionSupply supply;
    private final CompanionExpeditionStairsUp stairs;
    private final BiConsumer<Ending, String> end;
    private final Consumer<String> restart;
    private boolean wading;
    private int ticks, steps;

    CompanionExpeditionWade(CompanionWork w, CompanionExpeditionDig dig, CompanionExpeditionSupply supply, BiConsumer<Ending, String> end, Consumer<String> restart) {
        this.w = w;
        this.owner = w.owner;
        this.dig = dig;
        this.supply = supply;
        this.stairs = new CompanionExpeditionStairsUp(w, dig);
        this.end = end;
        this.restart = restart;
    }

    boolean active() { return wading; }

    void reset() {
        stairs.reset();
        wading = false;
        ticks = steps = 0;
    }

    /** The stairs of an arrival are forgotten. */
    void resetStairs() { stairs.reset(); }

    /**
     * Afloat with no walk to the goal, or on a staircase begun out of the water with none from there either: the way is a block under his feet and
     * a staircase of his own cut and laid up towards the goal, as a player climbs out of a pool under a cliff (seed 7: a water-filled pit, its
     * bank three blocks over the water). True when that is now his work.
     */
    boolean begin(BlockPos goal) {
        if (!owner.isInWater() && stairs.steps() == 0 || owner.isInLava()) return false;
        w.route.legsEnd(false);
        owner.getNavigation().stop();
        dig.forget();
        stairs.begin(goal);
        wading = true;
        ticks = steps = 0;
        owner.workStatus(owner.isInWater() ? "Я в воде, а на берег не выйти: кладу блок под ноги и прокапываю лестницу наверх." : "Дороги к цели и отсюда нет, прокапываю лестницу дальше.");
        return true;
    }

    /** One tick of the way out of the water: the block under his feet at the top of the water, then the staircase; it ends once a step of it has a walk to the goal. */
    Result tick(ServerLevel level, BlockPos goal) {
        if (owner.isInWater()) {
            BlockPos top = owner.blockPosition();
            while (level.getFluidState(top.above()).is(FluidTags.WATER)) top = top.above();
            BlockPos floor = top.below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                CompanionSeal.Result placed = CompanionSeal.place(owner, floor);
                if (placed.placed()) { w.mine.sealed(floor); ticks = 0; }
                else if (placed == CompanionSeal.Result.NO_BLOCK) { wading = false; end.accept(Ending.BLOCKED, "Я в воде, берега рядом нет, а блоков, чтобы выбраться из неё, в сумке нет."); }
                else if (++ticks > WADE_PATIENCE) { wading = false; return Result.RETRY; }
                return Result.WORKING;
            }
        }
        switch (stairs.tick(level)) {
            case WORKING -> {
                if (stairs.steps() == steps) return Result.WORKING;
                steps = stairs.steps();
                if (w.route.legs(goal, CompanionExpedition.ENTRANCE_NEAR) != CompanionTravel.Step.FAILED) { wading = false; return Result.DONE; }
            }
            case ARRIVED -> { wading = false; return Result.DONE; }
            case NO_TOOL -> {
                if (supply.craftStonePickaxe()) return Result.WORKING;
                if (dig.ironLeft(0) || dig.hardPickaxe() != null) { dig.ironForRock(0); return Result.WORKING; }
                wading = false;
                end.accept(Ending.SUPPLIES, CompanionExpeditionClimb.shortage(false, w.items.countCarried(Items.STONE_PICKAXE) > 0) + " Из воды наверх не выбраться.");
            }
            case STUCK -> { wading = false; restart.accept("Из воды наверх не выбрался: " + stairs.why() + "."); }
        }
        return Result.WORKING;
    }
}
