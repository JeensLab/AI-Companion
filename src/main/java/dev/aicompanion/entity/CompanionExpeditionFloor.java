package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** The floor of a new stand that was air, a replaceable block or liquid: he lays a real block there from the old stand before he steps onto it. */
final class CompanionExpeditionFloor {
    enum Outcome { WORKING, LAID, NO_BLOCK, FAILED }

    private static final int TRIES = 20;

    final BlockPos cell;
    private int tries;

    CompanionExpeditionFloor(BlockPos cell) { this.cell = cell; }

    /** One tick of laying; LAID once the cell holds a sturdy block, however it got there. */
    Outcome lay(CompanionEntity owner, ServerLevel level) {
        if (level.hasChunkAt(cell) && level.getBlockState(cell).isFaceSturdy(level, cell, Direction.UP)) return Outcome.LAID;
        CompanionSeal.Result placed = CompanionSeal.place(owner, cell);
        if (placed.placed()) { tries = 0; return Outcome.WORKING; }
        if (placed == CompanionSeal.Result.NO_BLOCK) return Outcome.NO_BLOCK;
        return ++tries > TRIES ? Outcome.FAILED : Outcome.WORKING;
    }
}
