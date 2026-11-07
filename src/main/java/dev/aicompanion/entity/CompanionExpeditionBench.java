package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The bench he set down on a step of his stairs to make a pickaxe is taken up by hand again before he goes on: in a passage one cell
 * wide it would shut the way. On the surface, the bench of the kit he set down himself goes back into the bag before the way down. The cells are the mine's temporary blocks, which are saved, so a taking cut short by a quit goes on
 * after the load. The effort is bounded per step; a bench left standing stays in the record and the next step tries again.
 */
final class CompanionExpeditionBench {
    private static final int PATIENCE = 20 * 15, PICKUP_WAIT = 20 * 3, NEAR = 24;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private int ticks;
    private long pickupUntil;

    CompanionExpeditionBench(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
    }

    void reset() { ticks = 0; pickupUntil = 0; }

    /** Ticks spent, for the stall watchdog: the taking is work, not standing still. */
    int progress() { return ticks; }

    /** True while this tick goes to taking a bench up or waiting for it to be picked up; false when there is none to take or the step's effort is spent. */
    boolean tick(ServerLevel level) {
        if (level.getGameTime() < pickupUntil) {
            if (owner.hasPendingLoot()) return true;
            pickupUntil = 0;
        }
        if (ticks >= PATIENCE) return false;
        BlockPos bench = nearest(level);
        if (bench == null) return false;
        ticks++;
        int reach = w.items.reachBlock(level, bench, false);
        if (reach < 0) { giveUp(bench, "no way to it"); return false; }
        if (reach == 0) return true;
        switch (w.handOver.takeUp(level, bench)) {
            case WORKING -> { }
            case FULL -> { giveUp(bench, "bag full"); return false; }
            case DONE -> {
                w.mine.taken(bench);
                pickupUntil = level.getGameTime() + PICKUP_WAIT;
                dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "temporary_taken", "entity", owner.getUUID(), "position", bench);
            }
        }
        return true;
    }

    private void giveUp(BlockPos bench, String why) {
        ticks = PATIENCE;
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "temporary_left", "entity", owner.getUUID(), "position", bench, "why", why);
    }

    /**
     * The nearest bench of the mine's temporary blocks within reach of a walk; a cell with nothing in it any more is forgotten.
     * On the surface with no bench in the bag, a bench he set down himself near by counts too: the kit goes down with him (void-2 of seed 7:
     * the bench of the kit stayed by the entrance after a craft, and no pickaxe could be made below).
     */
    private BlockPos nearest(ServerLevel level) {
        BlockPos best = null;
        double bestDistance = NEAR * NEAR;
        for (BlockPos cell : List.copyOf(w.mine.temporary())) {
            if (!level.hasChunkAt(cell)) continue;
            BlockState state = level.getBlockState(cell);
            if (state.isAir()) { w.mine.taken(cell); continue; }
            double distance = owner.distanceToSqr(cell.getCenter());
            if (state.is(Blocks.CRAFTING_TABLE) && distance <= bestDistance) { best = cell; bestDistance = distance; }
        }
        if (best != null || w.items.countCarried(Items.CRAFTING_TABLE) > 0 || !CompanionExpeditionRecord.underSky(level, owner.blockPosition().above())) return best;
        for (BlockPos cell : owner.places.placed("minecraft:crafting_table")) {
            if (!level.hasChunkAt(cell) || !level.getBlockState(cell).is(Blocks.CRAFTING_TABLE)) continue;
            double distance = owner.distanceToSqr(cell.getCenter());
            if (distance <= bestDistance) { best = cell; bestDistance = distance; }
        }
        return best;
    }
}
