package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Sealing blocks for the plugs, got from the rock beside the stairs when the bag is nearly out of them: from a mine begun under the
 * ground there is no way up to fetch any. Niches are cut sideways from the stand he is on, one cell wide and two high, never into the
 * cell behind it (the floor of the step above) and only where every neighbour of the cell is solid rock, so a niche never opens onto
 * a cave or a liquid. The effort is bounded per step and per stand; what is not got here is got on the stands further down.
 */
final class CompanionExpeditionStock {
    static final int LOW = 16, WANTED = 32;
    private static final int PATIENCE = 20 * 90, NICHE_DEPTH = 3, PICKUP_WAIT = 20 * 3;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final List<BlockPos> dug = new ArrayList<>();
    private BlockPos spentAt;
    private boolean gathering;
    private int ticks;
    private long pickupUntil;

    CompanionExpeditionStock(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.owner = w.owner;
        this.dig = dig;
    }

    void reset() {
        dug.clear();
        spentAt = null;
        gathering = false;
        ticks = 0;
        pickupUntil = 0;
    }

    /** Ticks spent, for the stall watchdog: cutting a niche is work, not standing still. */
    int progress() { return ticks; }

    /** True while this tick goes to getting stone from the walls of {@code stand}; false when the bag has enough or there is nothing to take here. */
    boolean tick(ServerLevel level, BlockPos stand, Direction dir) { return tick(level, stand, List.of(dir.getClockWise(), dir.getCounterClockWise())); }

    /** As {@link #tick} for a stand with no stairs to run along (the way up, a corridor): the rock on every side of it. */
    boolean tickAround(ServerLevel level, BlockPos stand) { return tick(level, stand, List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)); }

    private boolean tick(ServerLevel level, BlockPos stand, List<Direction> sides) {
        if (stand.equals(spentAt) || ticks >= PATIENCE) return false;
        if (level.getGameTime() < pickupUntil) {
            if (owner.hasPendingLoot()) return true;
            pickupUntil = 0;
        }
        int have = CompanionSeal.available(owner);
        if (have >= (gathering ? WANTED : LOW)) { gathering = false; return false; }
        gathering = true;
        BlockPos cell = nextCell(level, stand, sides);
        if (cell == null) return spend(stand);
        ticks++;
        if (!owner.blockPosition().equals(stand)) {
            if (dig.reach(stand)) return true;
            return dig.walkStuck() ? spend(stand) : true;
        }
        switch (dig.dig(level, cell)) {
            case WORKING -> { }
            case DONE -> {
                dig.flush();
                dug.add(cell.immutable());
                pickupUntil = level.getGameTime() + PICKUP_WAIT;
            }
            default -> { dig.forget(); return spend(stand); }
        }
        return true;
    }

    private boolean spend(BlockPos stand) {
        spentAt = stand.immutable();
        dug.clear();
        return false;
    }

    /** The next cell of a niche beside the stand: feet then head, the nearer side first, deeper along the same side. */
    private BlockPos nextCell(ServerLevel level, BlockPos stand, List<Direction> sides) {
        for (Direction side : sides) {
            for (int depth = 1; depth <= NICHE_DEPTH; depth++) {
                BlockPos feet = stand.relative(side, depth);
                for (BlockPos cell : List.of(feet, feet.above())) {
                    if (dug.contains(cell)) continue;
                    if (CompanionShaftPlan.open(level, cell)) break;
                    return diggable(level, stand, cell) ? cell : null;
                }
            }
        }
        return null;
    }

    /** Plain rock all round, bar the stand's own column and the niche already cut. */
    private boolean diggable(ServerLevel level, BlockPos stand, BlockPos cell) {
        if (!level.hasChunkAt(cell) || CompanionShaftPlan.assess(level, stand, List.of(cell), List.of(), w.mine.seals()).kind() != CompanionShaftPlan.Kind.OK) return false;
        for (Direction d : Direction.values()) {
            BlockPos next = cell.relative(d);
            boolean mine = dug.contains(next) || next.getX() == stand.getX() && next.getZ() == stand.getZ() && next.getY() >= stand.getY() && next.getY() <= stand.getY() + 2;
            if (!mine && (!level.hasChunkAt(next) || CompanionShaftPlan.open(level, next))) return false;
        }
        return true;
    }
}
