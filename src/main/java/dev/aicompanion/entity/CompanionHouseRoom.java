package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The cells of a building somebody stands in (a mob that stays, the player too). A block goes into neither him nor the cell, and nothing is
 * set down above such a cell, or the one who stands there would be sealed in under it (a chicken in the hole of a sunk floor). The cell is
 * waited for only when nothing else can be done, for five minutes in all since the last block was set down, whoever comes and goes meanwhile.
 */
final class CompanionHouseRoom {
    static final int WAIT_TICKS = 20 * 60 * 5;
    private final CompanionEntity owner;
    private final CompanionHouseSite site;
    private final HashSet<Integer> occupied = new HashSet<>();
    /** The lowest occupied cell of each column (x, z) of the building: what stands above it waits too. */
    private final Map<Long, Integer> lowest = new HashMap<>();
    private int waited;
    private long waitTick = -100;

    CompanionHouseRoom(CompanionEntity owner, CompanionHouseSite site) { this.owner = owner; this.site = site; }

    boolean isEmpty() { return occupied.isEmpty(); }
    void add(int index) { occupied.add(index); }
    /** A block was set down: the wait starts again. */
    void placed() { waited = 0; }
    void reset() { occupied.clear(); lowest.clear(); waited = 0; }

    /** Who stands in the cells of the step (a block goes into neither him nor the cell), or null. */
    Entity occupant(ServerLevel level, CompanionHouse.Step step) {
        return site.occupant(level, step.pos(), step.second() == null ? step.pos() : step.second());
    }

    /** The cells nobody stands in any more are free; the columns above the others are noted. */
    void refresh(ServerLevel level, List<CompanionHouse.Step> steps, BitSet placed) {
        occupied.removeIf(index -> placed.get(index) || occupant(level, steps.get(index)) == null);
        lowest.clear();
        for (int index : occupied) {
            BlockPos pos = steps.get(index).pos();
            lowest.merge(column(pos), pos.getY(), Math::min);
        }
    }
    private static long column(BlockPos pos) { return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL); }

    /** Left alone for now: somebody stands in it, or it is above a cell somebody stands in. */
    boolean skips(int index, List<CompanionHouse.Step> steps) {
        if (occupied.contains(index)) return true;
        if (lowest.isEmpty()) return false;
        BlockPos pos = steps.get(index).pos();
        Integer below = lowest.get(column(pos));
        return below != null && pos.getY() > below;
    }

    /** One more tick of waiting: true while within the limit, counted over all the waiting since the last block. */
    boolean tickWait() { return ++waited <= WAIT_TICKS; }
    /** He is waiting for a cell now: the work does not count it as standing idle. */
    boolean waiting(ServerLevel level) { return level.getGameTime() - waitTick <= 1; }

    /** Only blocks in cells somebody stands in are left: he says so and waits, five minutes at most, then stops with the building saved. */
    CompanionHouse.Result wait(ServerLevel level, List<CompanionHouse.Step> steps) {
        waitTick = level.getGameTime();
        CompanionHouse.Step step = steps.get(occupied.iterator().next());
        Entity who = occupant(level, step);
        String where = step.pos().toShortString() + (who == null ? "" : ": там стоит «" + who.getName().getString() + "»");
        if (tickWait()) { owner.workStatus("Жду, пока освободится место для блока на " + where + "."); return CompanionHouse.Result.working(); }
        waited = 0;
        return CompanionHouse.Result.failed("Жду уже " + WAIT_TICKS / 1200 + " минут, блок на " + where + ". Стройку сохранил; скажи «дострой», когда место освободится.");
    }
}
