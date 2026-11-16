package dev.aicompanion.entity;

import dev.aicompanion.entity.CompanionExpedition.Ending;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiConsumer;

/** The way down the stairs opens first: his own plug from the last retreat is dug out of the step (moved out of {@link CompanionExpedition}). */
final class CompanionExpeditionPlugOut {
    private final CompanionWork w;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionSupply supply;
    private final BiConsumer<Ending, String> end;

    CompanionExpeditionPlugOut(CompanionWork w, CompanionExpeditionDig dig, CompanionExpeditionSupply supply, BiConsumer<Ending, String> end) {
        this.w = w;
        this.dig = dig;
        this.supply = supply;
        this.end = end;
    }

    /** His own plug from the last retreat comes out of the step before he goes down it; the block drops and is picked up like any other. */
    void takeOut(ServerLevel level, boolean hadStone) {
        BlockPos plug = w.mine.plug();
        CompanionExpeditionDig.Status status = dig.dig(level, plug);
        switch (status) {
            case WORKING -> { }
            case DONE -> { dig.flush(); w.mine.plugged(null); }
            case FULL -> end.accept(Ending.BLOCKED, "Сумка полна, свою пробку на лестнице убрать некуда.");
            case NO_TOOL -> {
                // The plug is rock like any other: a stone pickaxe made on the spot, else the iron one while it has wear to spare.
                if (supply.craftStonePickaxe()) return;
                if (!dig.ironForRock && dig.spareIron()) { dig.ironForRock = true; return; }
                CompanionExpeditionDig.Status taken = dig.takeOut(level, plug);
                switch (taken) {
                    case WORKING -> { }
                    case DONE -> { dig.flush(); w.mine.plugged(null); }
                    case NO_TOOL, NO_HAND, FULL, STUCK -> end.accept(Ending.SUPPLIES, CompanionExpeditionClimb.shortage(false, hadStone) + " Свою пробку на лестнице убрать нечем.");
                }
            }
            case NO_HAND, STUCK -> end.accept(Ending.BLOCKED, "Свою пробку на лестнице убрать не смог (" + switch (status) {
                case NO_HAND -> "не взял кирку в руку";
                case WORKING, DONE, NO_TOOL, FULL, STUCK -> "не вижу её, не дотягиваюсь или не разбирается";
            } + ").");
        }
    }
}
