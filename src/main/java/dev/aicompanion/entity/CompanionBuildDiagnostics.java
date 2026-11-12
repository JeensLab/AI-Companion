package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Why a template building gave up with «Не могу подойти …»: one {@code build_blocked} journal event with the verdict of the build loop's own
 * checks on each block still to set down and on the cells the site clearing still has to free. Runs once, at the moment of giving up, and
 * only reads: it changes neither the building's state nor the world nor the bag. The one exception is the route checks (a handful at most):
 * they ask the navigation for a short search, which keeps what it has found for the next ask, as a route search of the loop would.
 * Positions are relative to the building's corner, as in its template. Reason codes are listed with {@link #reason}.
 */
final class CompanionBuildDiagnostics {
    private static final int MAX_STEPS = 40, MAX_QUEUE = 20, ROUTE_CHECKS = 8, COLUMN_CHECKS = 4;
    private final CompanionHouse house;
    private final ServerLevel level;
    private final CompanionEntity owner;
    private int routeChecks;
    private int columnChecks;
    private Set<BlockPos> cells;

    private CompanionBuildDiagnostics(CompanionHouse house, ServerLevel level, CompanionEntity owner) { this.house = house; this.level = level; this.owner = owner; }

    /** Writes the event when the failed result is one of the two «Не могу подойти» endings of a template building. */
    static void blocked(CompanionHouse house, ServerLevel level, CompanionEntity owner, String where, CompanionHouse.Result result) {
        if (!dev.aicompanion.CompanionDiagnostics.enabled() || !house.template || house.base == null || house.building == null || house.loaded == null
                || result.state() != CompanionHouse.State.FAILED || !result.message().startsWith("Не могу подойти")) return;
        try { new CompanionBuildDiagnostics(house, level, owner).write(where, result.message()); }
        catch (RuntimeException ignored) { /* diagnostics never change the work */ }
    }

    private void write(String where, String message) {
        var steps = house.steps;
        cells = new HashSet<>();
        for (var step : steps) { cells.add(step.pos()); if (step.second() != null) cells.add(step.second()); }
        int gate = house.order.gate();
        int loopStage = Integer.MAX_VALUE;
        for (int index = 0; index < steps.size(); index++)
            if (!house.placed.get(index) && !house.room.skips(index, steps) && house.order.buildable(index, gate)) loopStage = Math.min(loopStage, steps.get(index).stage());
        List<Integer> left = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) if (!house.placed.get(index)) left.add(index);
        left.sort(Comparator.<Integer>comparingInt(index -> steps.get(index).stage()).thenComparingDouble(index -> owner.distanceToSqr(steps.get(index).pos().getCenter())));
        List<String> rows = new ArrayList<>();
        for (int index : left.subList(0, Math.min(MAX_STEPS, left.size()))) rows.add(row(index, gate, loopStage));
        List<String> queued = new ArrayList<>();
        var queue = house.clearing.queued();
        for (BlockPos pos : queue.subList(0, Math.min(MAX_QUEUE, queue.size()))) queued.add(queueRow(pos));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "build_blocked",
                "entity", owner.getUUID(), "where", where, "message", message, "building", house.building.name(), "base", house.base.toShortString(),
                "rotation", house.rotation.name(), "placed", house.placed.cardinality(), "total", house.total, "position", owner.blockPosition().toShortString(),
                "relative", owner.blockPosition().subtract(house.base).toShortString(), "gate", gate == Integer.MAX_VALUE ? -1 : gate,
                "loopStage", loopStage == Integer.MAX_VALUE ? -1 : loopStage, "onScaffold", house.onScaffold, "skipAffordable", house.skipAffordable,
                "cleared", house.cleared, "scaffolds", house.scaffolds, "scaffoldTried", house.scaffoldTried.size(), "noColumn", house.noColumn.size(),
                "unreachable", house.unreachable.size(), "remaining", left.size(), "steps", String.join(" ; ", rows),
                "queueSize", queue.size(), "queue", String.join(" ; ", queued));
    }

    // ---- the blocks of the building

    private String row(int index, int gate, int loopStage) {
        var step = house.steps.get(index);
        StringBuilder flags = new StringBuilder();
        if (house.unreachable.contains(index)) flags.append(" marked_unreachable");
        if (house.noColumn.contains(index)) flags.append(" in_noColumn");
        if (step.stage() > loopStage) flags.append(" later_stage");
        return rel(step.pos()) + " " + id(step.state()) + " stage=" + step.stage() + " " + reason(index, step, gate) + flags;
    }

    /**
     * The first reason the loop does not take the block, in the order of its checks: no_material (the bag has none; cannot_get: nor can he get
     * it), held_by_gate (a lower stage lacks material that he can get), held_by_door (a door goes last), occupied (somebody stands in its cell
     * or below it), not_free (a world block stands in its cell: diggable or not_his, in_sight from where he stands), unsupported (no solid
     * neighbour), placeable_now (the loop would take it), no_stand_cell (no cell on the ground within reach and in sight; rejected=counts),
     * route_none / route_partial / route_busy / route_unchecked (the short search to the stand cells, the first few only), scaffold_limit
     * (twelve columns used), column_none (no dirt column helps; counts and the best ground cells) or column_available (a column would help).
     */
    private String reason(int index, CompanionHouse.Step step, int gate) {
        var steps = house.steps;
        if (!house.order.affordable(step)) return "no_material" + (house.materials.cannotGet(step.item()) ? "(cannot_get)" : "");
        if (step.stage() > gate) return "held_by_gate(gate=" + gate + ")";
        if (!house.order.buildable(index, gate)) return "held_by_door";
        if (house.room.skips(index, steps)) {
            Entity who = house.room.occupant(level, step);
            return "occupied(" + (who == null ? "above_an_occupied_cell" : BuiltInRegistries.ENTITY_TYPE.getKey(who.getType()) + "@" + rel(who.blockPosition())) + ")";
        }
        if (!house.site.free(level, step)) return notFree(step);
        if (!house.site.supported(level, step)) return "unsupported";
        if (house.canPlaceFromHere(level, step)) return "placeable_now";
        var stand = stand(step.pos(), step.second());
        if (stand.feet.isEmpty()) return "no_stand_cell(rejected=" + stand.rejected + ")";
        String route = route(stand.feet);
        if (route.equals("ok")) return "route_ok(feet=" + stand.feet.size() + ")";
        String head = "route_" + route + "(feet=" + stand.feet.size() + ")";
        if (house.scaffolds >= CompanionHouse.MAX_SCAFFOLDS) return head + " scaffold_limit";
        if (columnChecks++ >= COLUMN_CHECKS) return head + " column_unchecked";
        return head + " " + column(step.pos(), step.second());
    }

    private String notFree(CompanionHouse.Step step) {
        StringBuilder out = new StringBuilder("not_free(");
        for (BlockPos cell : step.second() == null ? new BlockPos[]{step.pos()} : new BlockPos[]{step.pos(), step.second()}) {
            if (house.site.vacant(level, cell)) continue;
            BlockState state = level.getBlockState(cell);
            String why = house.clearing.obstruction(level, cell) == null ? "diggable" : "not_his";
            out.append(rel(cell)).append(' ').append(id(state)).append(' ').append(why).append(" in_sight=").append(house.visible(level, owner.getEyePosition(), cell)).append(' ');
        }
        return out.toString().stripTrailing() + ")";
    }

    // ---- where he could stand

    private record Stand(Set<BlockPos> feet, Map<String, Integer> rejected) { }

    /** The cells the loop's route() takes as goals for a block at {@code pos}: on the ground within four blocks, free for his body, the block in sight. */
    private Stand stand(BlockPos pos, BlockPos second) {
        Set<BlockPos> feet = new HashSet<>();
        Map<String, Integer> rejected = new LinkedHashMap<>();
        for (int y = 0; y <= 1; y++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos foot = new BlockPos(pos.getX() + dx, house.base.getY() + y, pos.getZ() + dz);
            if (!level.hasChunkAt(foot) || !level.hasChunkAt(foot.below())) { rejected.merge("unloaded", 1, Integer::sum); continue; }
            if (!level.getFluidState(foot).isEmpty() || !level.getFluidState(foot.above()).isEmpty()) { rejected.merge("fluid", 1, Integer::sum); continue; }
            if (!level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)) { rejected.merge("no_floor", 1, Integer::sum); continue; }
            AABB body = new AABB(foot.getX() + .2, foot.getY(), foot.getZ() + .2, foot.getX() + .8, foot.getY() + owner.getBbHeight(), foot.getZ() + .8);
            if (body.intersects(new AABB(pos)) || second != null && body.intersects(new AABB(second))) { rejected.merge("body_in_block", 1, Integer::sum); continue; }
            if (!level.noCollision(owner, body)) { rejected.merge("collision", 1, Integer::sum); continue; }
            if (!house.visible(level, Vec3.atBottomCenterOf(foot).add(0, owner.getEyeHeight(), 0), pos)) { rejected.merge("not_visible", 1, Integer::sum); continue; }
            feet.add(foot);
        }
        return new Stand(feet, rejected);
    }

    /** ok / partial (a path that stops short) / none / busy (this tick's cells are spent) / unchecked (the checks of this event are used up). */
    private String route(Set<BlockPos> goals) {
        if (routeChecks++ >= ROUTE_CHECKS || !(owner.getNavigation() instanceof CompanionNavigation nav)) return "unchecked";
        Path path = nav.search(goals, 0);
        return path == CompanionNavigation.BUSY ? "busy" : path == null ? "none" : path.canReach() ? "ok" : "partial";
    }

    // ---- the dirt column

    private record Ground(BlockPos pos, int progress, String why, int levels) { }

    /** The search of CompanionScaffoldColumns.find again, counting why each ground cell fails (the code, then the first three best with the detail). */
    private String column(BlockPos pos, BlockPos second) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Ground> grounds = new ArrayList<>();
        int floorY = house.base.getY();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy : new int[]{0, -1, 1, -2}) {
            BlockPos ground = new BlockPos(pos.getX() + dx, floorY + dy, pos.getZ() + dz);
            Ground best = ground(ground, pos, second);
            counts.merge(best.why.split(":")[0], 1, Integer::sum);
            grounds.add(best);
        }
        grounds.sort(Comparator.<Ground>comparingInt(g -> -g.progress).thenComparingInt(Ground::levels).thenComparingDouble(g -> owner.distanceToSqr(g.pos.getCenter())));
        List<Ground> top = grounds.subList(0, Math.min(3, grounds.size()));
        StringBuilder out = new StringBuilder();
        if (!grounds.isEmpty() && grounds.get(0).progress == 5) {
            Ground ok = grounds.get(0);
            // The loop asks the way to the foot of the first five options; one is enough here.
            String foot = route(Set.of(ok.pos));
            out.append(foot.equals("ok") ? "column_available" : "column_none(foot_route_" + foot + ")");
        } else out.append("column_none");
        out.append("(counts=").append(counts).append(" best=[");
        for (Ground g : top) out.append(rel(g.pos)).append(' ').append(g.why).append("; ");
        return out.toString().stripTrailing() + "])";
    }

    /** How far a ground cell gets in the column search (progress 5: it would be an option); the reason where it stops. */
    private Ground ground(BlockPos ground, BlockPos pos, BlockPos second) {
        if (!level.hasChunkAt(ground)) return new Ground(ground, 0, "unloaded", 0);
        if (cells.contains(ground)) return new Ground(ground, 0, "ground_in_building", 0);
        if (house.scaffoldTried.contains(ground)) return new Ground(ground, 0, "ground_tried", 0);
        if (!level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP)) return new Ground(ground, 0, "no_floor", 0);
        Ground best = new Ground(ground, 1, "cell_taken", 0);
        for (int levels = 1; levels <= CompanionScaffold.MAX_HEIGHT; levels++) {
            BlockPos block = ground.above(levels - 1);
            BlockState state = level.getBlockState(block);
            if (cells.contains(block) || !level.getFluidState(block).isEmpty() || !state.canBeReplaced() || !state.getCollisionShape(level, block).isEmpty()) {
                // The column cannot go higher: what the best level reached stays.
                return levels == 1 ? new Ground(ground, 1, "cell_taken:" + id(state) + "@" + rel(block), 1) : best;
            }
            BlockPos feet = ground.above(levels);
            Ground here;
            if (cells.contains(feet) || cells.contains(feet.above())) here = new Ground(ground, 2, "feet_in_building@" + rel(feet), levels);
            else if (!level.hasChunkAt(feet.above())) here = new Ground(ground, 2, "unloaded_above", levels);
            else if (!level.noCollision(new AABB(feet.getX(), feet.getY(), feet.getZ(), feet.getX() + 1, feet.getY() + 2, feet.getZ() + 1))) here = new Ground(ground, 2, "no_headroom:" + blocking(feet), levels);
            else {
                AABB body = new AABB(ground.getX() + .2, feet.getY(), ground.getZ() + .2, ground.getX() + .8, feet.getY() + owner.getBbHeight(), ground.getZ() + .8);
                if (body.intersects(new AABB(pos)) || second != null && body.intersects(new AABB(second))) here = new Ground(ground, 3, "body_in_block", levels);
                else if (!house.visible(level, Vec3.atBottomCenterOf(feet).add(0, owner.getEyeHeight(), 0), pos)) here = new Ground(ground, 4, "not_visible", levels);
                else return new Ground(ground, 5, "option(levels=" + levels + ")", levels);
            }
            if (here.progress > best.progress || best.levels == 0) best = here;
        }
        return best;
    }

    /** The first block with a collision shape in the two cells a body standing at {@code feet} takes. */
    private String blocking(BlockPos feet) {
        for (BlockPos cell : new BlockPos[]{feet, feet.above()}) {
            BlockState state = level.getBlockState(cell);
            if (!state.getCollisionShape(level, cell).isEmpty()) return id(state) + "@" + rel(cell);
        }
        return "?";
    }

    // ---- what the site clearing still has to free

    private String queueRow(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        String why = state.isAir() ? "air" : house.clearing.obstruction(level, pos) == null ? "diggable" : "not_his";
        StringBuilder out = new StringBuilder(rel(pos) + " " + id(state) + " " + why);
        if (house.clearing.extra(pos)) out.append(" round_the_box");
        out.append(" in_sight=").append(house.visible(level, owner.getEyePosition(), pos));
        var stand = stand(pos, null);
        out.append(" feet=").append(stand.feet.size());
        if (stand.feet.isEmpty()) return out + " no_stand_cell(rejected=" + stand.rejected + ")";
        return out + " route_" + route(stand.feet);
    }

    private String rel(BlockPos pos) { var r = pos.subtract(house.base); return r.getX() + "," + r.getY() + "," + r.getZ(); }
    private static String id(BlockState state) { return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); }
}
