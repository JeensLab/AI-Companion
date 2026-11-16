package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which block of a building he sets down next. The blocks go layer by layer (stage by stage), the lowest first; a layer none of whose blocks
 * can be set down now (out of reach from the ground and from any column of dirt, nothing to stand on, a block of somebody else in its cell)
 * does not hold the layers above it back: what can be set down goes in the order of the layers, the doors last of all. Whenever a block is set
 * down the lower ones are asked again. The work ends as «cannot get to the rest» only when nothing at all of what is left can be set down.
 */
final class CompanionBuildOrder {
    /** A block to go on with (and the way to it when it is walked to), a result that ends this tick, or one of the verdicts below. */
    record Choice(int index, Path path, CompanionHouse.Result result) {
        /** Nothing is left that has its material and is not waited for. */
        static final Choice NO_BLOCK = new Choice(-1, null, null);
        /** Blocks are left that have their material, and none can be set down now. */
        static final Choice NONE = new Choice(-2, null, null);
        static final Choice WORKING = new Choice(-3, null, CompanionHouse.Result.working());
    }
    private static final int COLUMN_SEARCHES_PER_TICK = 4, NAMED = 3;
    private final CompanionHouse house;
    private final CompanionEntity owner;
    /** The first refusal of a column for want of dirt in this pass: said if nothing else can be set down. */
    private CompanionHouse.Result noDirt;

    CompanionBuildOrder(CompanionHouse house, CompanionEntity owner) { this.house = house; this.owner = owner; }

    // ---- what the bag allows

    /** The block has its material in the bag or the hands. */
    boolean affordable(CompanionHouse.Step step) {
        var item = step.item();
        if (owner.getMainHandItem().is(item) || owner.getOffhandItem().is(item)) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) return true;
        return false;
    }
    /**
     * The layer the building may be built up to now: a block missing its material (one he can get) holds the layers above it back,
     * as a roof is not put on walls that are not there (the last blocks of the walls would be hidden behind it).
     */
    int gate() {
        var steps = house.steps;
        int gate = Integer.MAX_VALUE;
        for (int index = 0; index < steps.size(); index++)
            if (!house.placed.get(index) && !affordable(steps.get(index)) && !house.materials.cannotGet(steps.get(index).item())) gate = Math.min(gate, steps.get(index).stage());
        return gate;
    }
    /**
     * A door is the last block of all: put in before what is still to be carried in (a bed, a torch asked of the player) it would shut the
     * way. He opens a wooden door, but a game run (12.11, the log house resumed behind its door) found him standing in the door's cell with
     * the door shut on him, the navigation stopped and nothing to open it again: no building waits for him to go in through a door he set.
     * So a door waits while another block lacks its material or has somebody in its cell; a block only out of reach holds it back no longer
     * (the doors are the last layer, so the blocks that can be set down are all down when it comes).
     */
    boolean buildable(int index, int gate) {
        var steps = house.steps;
        var step = steps.get(index);
        if (house.placed.get(index) || step.stage() > gate || !affordable(step)) return false;
        if (!step.door()) return true;
        for (int other = 0; other < steps.size(); other++)
            if (other != index && !house.placed.get(other) && !steps.get(other).door() && (!affordable(steps.get(other)) || house.room.skips(other, steps))) return false;
        return true;
    }
    boolean anyBuildable() {
        int gate = gate();
        for (int index = 0; index < house.steps.size(); index++) if (buildable(index, gate)) return true;
        return false;
    }
    boolean lacksMaterial() {
        for (int index = 0; index < house.steps.size(); index++) if (!house.placed.get(index) && !affordable(house.steps.get(index))) return true;
        return false;
    }

    // ---- the next block

    /** The next block: the nearest of the lowest layer with one that can be set down now. The plank house keeps to its lowest layer. */
    Choice choose(ServerLevel level) {
        var steps = house.steps;
        int gate = house.template ? gate() : Integer.MAX_VALUE;
        List<Integer> open = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++)
            if (!house.placed.get(index) && !house.room.skips(index, steps) && (!house.template || buildable(index, gate))) open.add(index);
        if (open.isEmpty()) return Choice.NO_BLOCK;
        open.sort(Comparator.<Integer>comparingInt(index -> steps.get(index).stage()).thenComparingDouble(index -> owner.distanceToSqr(steps.get(index).pos().getCenter())));
        noDirt = null;
        int from = 0;
        while (from < open.size()) {
            int stage = steps.get(open.get(from)).stage();
            int to = from;
            while (to < open.size() && steps.get(open.get(to)).stage() == stage) to++;
            Choice choice = inStage(level, open.subList(from, to), stage, gate);
            if (choice != null) return choice;
            from = to;
        }
        return Choice.NONE;
    }

    /**
     * A block of this layer to go on with. NONE: nothing above this layer is asked (the plank house; on a column, a block of the layer waits for
     * the ground). Null: no block of the layer can be set down, the layers above are asked.
     */
    private Choice inStage(ServerLevel level, List<Integer> blocks, int stage, int gate) {
        var steps = house.steps;
        boolean forTheGround = false;
        for (int index : blocks) {
            if (house.unreachable.contains(index)) continue;
            var step = steps.get(index);
            if (!house.site.free(level, step)) {
                if (!house.template) return new Choice(-1, null, CompanionHouse.Result.failed("На месте стройки появился другой блок. Не буду его ломать. Освободи место и попроси достроить дом."));
                // A cell of a template building may hold what he digs out first: the ground the floor is sunk into, a bush grown since.
                // What is not his to break holds only its own block back.
                if (house.obstruction(level, step) == null) return new Choice(index, null, null);
                house.unreachable.add(index);
                continue;
            }
            if (!house.site.supported(level, step)) continue;
            if (house.canPlaceFromHere(level, step)) return new Choice(index, null, null);
            // From the column only what is within reach is placed; the rest waits for the descent.
            if (house.onScaffold) { forTheGround |= !house.noColumn.contains(index); continue; }
            Path route = house.route(level, step);
            if (route != null && route.canReach()) return new Choice(index, route, null);
            // The tick's cells went elsewhere: the rest of the steps are asked next tick, none is written off.
            if (route == null && house.routeBusy()) return Choice.WORKING;
            house.unreachable.add(index);
        }
        if (!house.template) return Choice.NONE;
        if (house.onScaffold) return forTheGround ? Choice.NONE : null;
        CompanionHouse.Result up = scaffoldFor(level, stage, gate);
        if (up == null) return null;
        if (up.state() != CompanionHouse.State.FAILED) return new Choice(-1, null, up);
        // No dirt for a column is the refusal of this layer only: the layers above go on, and it is said if nothing else can be set down.
        if (noDirt == null) noDirt = up;
        return null;
    }

    /** Nothing of this stage is reached from the ground: a column of dirt for the nearest block that one reaches. Null when no column helps. */
    private CompanionHouse.Result scaffoldFor(ServerLevel level, int stage, int gate) {
        if (house.scaffolds >= CompanionHouse.MAX_SCAFFOLDS) return null;
        var steps = house.steps;
        List<Integer> unreached = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++)
            if (!house.placed.get(index) && steps.get(index).stage() == stage && house.site.free(level, steps.get(index)) && house.site.supported(level, steps.get(index)) && buildable(index, gate)) unreached.add(index);
        unreached.sort(Comparator.comparingDouble(index -> owner.distanceToSqr(steps.get(index).pos().getCenter())));
        int searched = 0;
        for (int index : unreached) {
            if (house.noColumn.contains(index)) continue;
            // Each search is many ray casts, and no column may help any block of the stage: a few per tick, the rest the next tick.
            if (searched++ >= COLUMN_SEARCHES_PER_TICK) return CompanionHouse.Result.working();
            CompanionHouse.Result up = house.columnFor(level, steps.get(index).pos(), steps.get(index).second());
            if (up == null) { house.noColumn.add(index); continue; }
            return up;
        }
        return null;
    }

    // ---- when nothing can be set down

    /**
     * The end of an order none of whose blocks left can be set down: how many are left and, of the first three, which and why. The refusal of
     * a column for want of dirt, when there was one, says it better.
     */
    CompanionHouse.Result stuck(ServerLevel level) {
        if (!house.template) return CompanionHouse.Result.failed("Не могу подойти к оставшимся блокам дома. Освободи проход и скажи «дострой дом».");
        if (noDirt != null) return noDirt;
        var steps = house.steps;
        int gate = gate();
        List<Integer> left = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) if (!house.placed.get(index)) left.add(index);
        left.sort(Comparator.<Integer>comparingInt(index -> steps.get(index).stage()).thenComparingDouble(index -> owner.distanceToSqr(steps.get(index).pos().getCenter())));
        StringBuilder said = new StringBuilder("Не могу подойти к оставшимся блокам дома: осталось " + left.size() + " из " + steps.size() + ". ");
        for (int index : left.subList(0, Math.min(NAMED, left.size()))) {
            var step = steps.get(index);
            said.append(step.state().getBlock().getName().getString()).append(" на ").append(step.pos().toShortString()).append(": ").append(reason(level, index, gate)).append(". ");
        }
        if (left.size() > NAMED) said.append("И ещё ").append(left.size() - NAMED).append(". ");
        return CompanionHouse.Result.failed(said + house.clearing.inTheWay(level, house.base) + "Стройку сохранил; освободи проход и скажи «дострой дом».");
    }

    /** Why a block left cannot be set down, in a few words, from what is there now (no search is made). */
    private String reason(ServerLevel level, int index, int gate) {
        var steps = house.steps;
        var step = steps.get(index);
        if (!affordable(step)) return "нет материала";
        if (house.room.skips(index, steps)) {
            Entity who = house.room.occupant(level, step);
            return who == null ? "занято: над ним кто-то стоит" : "занято: " + who.getName().getString();
        }
        if (!buildable(index, gate)) return step.door() ? "дверь ставлю последней" : "жду остальные блоки";
        if (!house.site.free(level, step)) {
            for (BlockPos cell : step.second() == null ? new BlockPos[]{step.pos()} : new BlockPos[]{step.pos(), step.second()})
                if (!house.site.vacant(level, cell)) return "мешает: " + level.getBlockState(cell).getBlock().getName().getString();
        }
        return house.site.supported(level, step) ? "не вижу, откуда поставить" : "нет опоры";
    }
}
