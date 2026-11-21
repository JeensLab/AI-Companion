package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A staircase cut up through the rock towards the entrance on the surface when the record's way up is gone (a restart under the ground
 * whose cells above lie across rock, water or a cave): what a player does with a pickaxe and a lost mine. One step is a cell ahead and one
 * higher: the cell over his head, the step and the cell over it come out, a floor is laid under the step where there is none, liquid
 * beside it is sealed first, and he steps up. At the goal's height the steps go level towards it, and the goal's cell is the arrival. With
 * every step up shut (water over the rock on each side: the pocket of an aquifer, runs descent and void of seed 7) the step is level,
 * never straight back, as a player tunnels out from under a lake before he cuts up. A side that cannot be opened is not tried from the
 * same stand again.
 */
final class CompanionExpeditionStairsUp {
    enum Result { WORKING, ARRIVED, STUCK, NO_TOOL }

    private static final int MAX_STEPS = 256, STEP_PATIENCE = 20 * 40, MAX_SEALS = 32, MAX_STUCK = 6, BOB = 2;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    /** Where each step goes into the mine's record of the way up; none for a climb for another goal (to the player), which leaves the record alone and says its own line. */
    private final Consumer<BlockPos> record;
    private final String status;
    /**
     * The stairs of his ways are walked on after it (a short staircase up to his old stairs over a pit): no step cuts the floor of one of their steps or lays
     * a floor into one, not even in the second pass. A staircase to the surface leaves them behind, and its way out from under a lake may run under the
     * entrance of the pit.
     */
    private final boolean keepsStairs;
    private final Set<Long> refused = new HashSet<>();
    /** The stands this climb has stood on: a level step back onto one goes round a chamber with no way out, not up (a pit in bedrock). */
    private final Set<BlockPos> stood = new HashSet<>();
    private BlockPos goal, from, to;
    private Direction dir;
    private List<BlockPos> cells = List.of();
    private int index, steps, stepTicks, seals, stuck;
    private boolean moved;
    private CompanionExpeditionFloor laying;
    private String why = "";

    CompanionExpeditionStairsUp(CompanionWork w, CompanionExpeditionDig dig) { this(w, dig, true, null); }

    CompanionExpeditionStairsUp(CompanionWork w, CompanionExpeditionDig dig, boolean recordsWay, String status) {
        this(w, dig, recordsWay ? cell -> w.mine.wayCut(cell) : null, status, false);
    }

    CompanionExpeditionStairsUp(CompanionWork w, CompanionExpeditionDig dig, Consumer<BlockPos> record, String status, boolean keepsStairs) {
        this.w = w;
        this.owner = w.owner;
        this.dig = dig;
        this.record = record;
        this.status = status;
        this.keepsStairs = keepsStairs;
    }

    void begin(BlockPos goal) {
        reset();
        this.goal = goal;
    }

    /** A goal that moves with him (the lip of the pit he stands in now): the stairs keep what they have done. */
    void aim(BlockPos goal) { this.goal = goal; }

    int steps() { return steps; }

    String why() { return why; }

    Result tick(ServerLevel level) {
        if (to == null) {
            BlockPos here = stand(level);
            if (here.getY() >= goal.getY() && here.distManhattan(goal) <= 1) return Result.ARRIVED;
            if (!plan(level, here)) return Result.STUCK;
            // A seal placed this tick is no step: the side is looked at again next tick.
            if (to == null) return Result.WORKING;
        }
        if (++stepTicks > STEP_PATIENCE) return giveUp(level, "patience");
        if (!moved) {
            if (index < cells.size()) return open(level);
            if (laying != null) {
                switch (dig.lay(laying)) {
                    case LAID -> laying = null;
                    case NO_BLOCK -> { return stop("в пути наверх нет пола, а блоков, чтобы его настелить, нет"); }
                    case FAILED -> { return giveUp(level, "floor"); }
                    case WORKING, PLACED -> { return Result.WORKING; }
                }
            }
            if (!dig.reach(to)) return dig.walkStuck() ? giveUp(level, "walk") : Result.WORKING;
            moved = true;
        }
        dig.flush();
        if (record != null) record.accept(to);
        stood.add(from);
        stood.add(to);
        steps++;
        stuck = 0;
        owner.workStatus(status != null ? status : "Своей лестницы наверх нет, прокапываю новую: ступень " + steps + ", ноги на высоте " + to.getY() + ".");
        to = null;
        return steps >= MAX_STEPS ? stop("лестница наверх вышла слишком длинной") : Result.WORKING;
    }

    /** The next step, a cell up while below the goal and level at its height or when every step up is shut: towards the goal where the rock allows, else along the last direction, else any side left; false when every side is shut. */
    private boolean plan(ServerLevel level, BlockPos here) {
        boolean up = here.getY() < goal.getY();
        if (step(level, here, up, false)) return true;
        if (up && step(level, here, false, true)) return true;
        why = "путь наверх закрыт";
        return false;
    }

    private boolean step(ServerLevel level, BlockPos here, boolean up, boolean sideways) {
        CompanionShaftPlan.Avoid own = w.mine.avoid();
        Set<BlockPos> waded = waded(level, here);
        for (Set<BlockPos> forbidden : List.of(own.forbidden(), Set.<BlockPos>of())) {
            CompanionShaftPlan.Avoid avoid = new CompanionShaftPlan.Avoid(forbidden, own.refused(), cell -> false, waded);
            for (Direction d : order(here)) {
                if (refused.contains(key(here, d)) || sideways && dir != null && d == dir.getOpposite()) continue;
                BlockPos step = up ? here.relative(d).above() : here.relative(d);
                if (!up && stood.contains(step)) continue;
                List<BlockPos> column = up ? List.of(here.above(2), step, step.above()) : List.of(step, step.above());
                // The floor of a step of his stairs, or a step over one of them (its floor would be laid into it): see keepsStairs.
                if (keepsStairs && (column.stream().anyMatch(w.mine::carriesStand) || w.mine.carriesStand(step.below(2)))) continue;
                CompanionShaftPlan.Hazard hazard = CompanionShaftPlan.assess(level, here, column, List.of(step.below()), w.mine.seals(), avoid, true);
                switch (hazard.kind()) {
                    case BLOCKED -> { continue; }
                    case SEAL -> {
                        // The liquid is sealed from where he stands, and the side looked at again next tick.
                        if (seals >= MAX_SEALS) continue;
                        CompanionSeal.Result placed = CompanionSeal.place(owner, hazard.cell());
                        if (placed.placed()) { w.mine.sealed(hazard.cell()); seals++; return true; }
                        if (placed == CompanionSeal.Result.NO_BLOCK) { stop("жидкость на пути наверх надо заделать, а блоков для этого в сумке нет"); return false; }
                        refused.add(key(here, d));
                        continue;
                    }
                    case FLOOR -> laying = new CompanionExpeditionFloor(hazard.cell());
                    case OK -> laying = null;
                }
                from = here;
                to = step;
                dir = d;
                cells = column;
                index = 0;
                stepTicks = 0;
                moved = false;
                return true;
            }
        }
        return false;
    }

    /**
     * The cell the step is cut from: bobbing under the top of water (the cell over his feet is water too), the top water cell of his column
     * (no more than {@link #BOB} over his feet). Seed 7, a lake in a pit: bobbing a hair under its top, the cell under it was taken (or the floor
     * of a shallow pool, the first one with firm ground under it), every side of it was water, and the way up was «закрыт» until a higher bob.
     * With his feet in the top water cell the cell is found as on land: a bank beside him is the better stand (the same lake, run water of seed 7).
     */
    private BlockPos stand(ServerLevel level) {
        BlockPos feet = owner.blockPosition();
        if (!level.getFluidState(feet).is(FluidTags.WATER) || !level.getFluidState(feet.above()).is(FluidTags.WATER)) return CompanionExpeditionRecord.standingCell(level, owner);
        for (int up = 0; up < BOB && level.getFluidState(feet.above()).is(FluidTags.WATER); up++) feet = feet.above();
        return level.getFluidState(feet.above()).is(FluidTags.WATER) ? CompanionExpeditionRecord.standingCell(level, owner) : feet;
    }

    /** The water of his own stand, feet or head: he wades in it, as a player climbs out of a pool, and no block can go where he is. Lava is no wade. */
    private static Set<BlockPos> waded(ServerLevel level, BlockPos stand) {
        Set<BlockPos> cells = new HashSet<>(2);
        for (BlockPos cell : List.of(stand, stand.above())) if (level.getFluidState(cell).is(FluidTags.WATER)) cells.add(cell);
        return cells;
    }

    private List<Direction> order(BlockPos here) {
        int dx = goal.getX() - here.getX(), dz = goal.getZ() - here.getZ();
        List<Direction> order = new ArrayList<>(4);
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) order.add(dx > 0 ? Direction.EAST : Direction.WEST);
        else if (dz != 0) order.add(dz > 0 ? Direction.SOUTH : Direction.NORTH);
        if (dir != null && !order.contains(dir)) order.add(dir);
        for (Direction d : Direction.Plane.HORIZONTAL) if (!order.contains(d)) order.add(d);
        return order;
    }

    private Result open(ServerLevel level) {
        if (!dig.reach(from)) return dig.walkStuck() ? giveUp(level, "walk") : Result.WORKING;
        BlockPos cell = cells.get(index);
        CompanionShaftPlan.Avoid wading = new CompanionShaftPlan.Avoid(Set.of(), (stand, d) -> false, c -> false, waded(level, from));
        CompanionShaftPlan.Hazard flowed = CompanionShaftPlan.classify(level, from, cell, w.mine.seals(), wading, laying == null ? null : laying.cell);
        if (flowed.kind() == CompanionShaftPlan.Kind.SEAL) {
            CompanionSeal.Result placed = CompanionSeal.place(owner, flowed.cell());
            if (placed.placed()) { w.mine.sealed(flowed.cell()); seals++; return Result.WORKING; }
            return placed == CompanionSeal.Result.NO_BLOCK ? stop("жидкость на пути наверх надо заделать, а блоков для этого в сумке нет") : giveUp(level, "seal");
        }
        switch (dig.dig(level, cell)) {
            case DONE -> index++;
            case NO_TOOL -> { return Result.NO_TOOL; }
            case FULL -> { if (!dig.makeRoom(level)) return stop("сумка полна, камень класть некуда"); }
            case STUCK, NO_HAND -> { dig.diagnoseDig(cell); dig.forget(); return giveUp(level, "dig"); }
            case WORKING -> { }
        }
        return Result.WORKING;
    }

    /** The side is not tried from this stand again; too many such sides in a row end the climb. */
    private Result giveUp(ServerLevel level, String what) {
        if (from != null && dir != null) refused.add(key(from, dir));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "stairs_up_refused", "entity", owner.getUUID(), "from", from, "direction", dir, "what", what, "steps", steps);
        to = null;
        laying = null;
        dig.forget();
        return ++stuck > MAX_STUCK ? stop("путь наверх закрыт") : Result.WORKING;
    }

    private Result stop(String reason) {
        why = reason;
        to = null;
        return Result.STUCK;
    }

    private static long key(BlockPos stand, Direction d) { return stand.asLong() * 4 + d.get2DDataValue(); }

    void reset() {
        refused.clear();
        stood.clear();
        goal = from = to = null;
        dir = null;
        cells = List.of();
        index = steps = stepTicks = seals = stuck = 0;
        moved = false;
        laying = null;
        why = "";
    }
}
