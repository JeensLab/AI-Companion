package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * What the expedition does when a block it broke opens onto a cave (CompanionCaveSense says which kind). A safe cave is looked
 * over for diamond within sixteen blocks of the breach and two minutes, with the way back to his tunnel in hand; any other is
 * not entered. Either way the cave air that touches his mine is plugged with real blocks before the dig goes on.
 */
final class CompanionExpeditionCave {
    private enum Phase { IDLE, WAIT, EXPLORE, BACK, SEAL }

    private static final int EXPLORE_RANGE = 16, EXPLORE_TICKS = 20 * 120, SPACING = 5, SETTLE = 30, PIT_TRIES = 60, SEAL_PATIENCE = 200, WATCH_EVERY = 10, LOOK_EVERY = 5, NOTES = 6;
    private static final double MELEE = 3, ARRIVED = 2.5;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionOre ore;
    private final BooleanSupplier enough;
    private final Set<BlockPos> sealed = new HashSet<>(), visited = new HashSet<>(), unreachable = new HashSet<>();
    private final List<String> notes = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private CompanionCaveSense.Cave cave;
    private Set<BlockPos> touch = new HashSet<>();
    private BlockPos home, waypoint, pit;
    private String failure;
    private long until;
    private int seenBroken, idle, settle, taken, work, tries;

    CompanionExpeditionCave(CompanionWork w, CompanionExpeditionDig dig, CompanionExpeditionOre ore, BooleanSupplier enough) {
        this.w = w;
        this.owner = w.owner;
        this.dig = dig;
        this.ore = ore;
        this.enough = enough;
    }

    boolean busy() { return phase != Phase.IDLE; }

    /** What the stall watchdog sees move: plugs placed and places reached. */
    int progress() { return work; }

    /** Why the cave left him unable to go on, or null. */
    String failure() { return failure; }

    /** What happened at caves, for the report. */
    String notes() { return String.join(" ", notes); }

    /** The cells a plug stands in: a tunnel through one of them would open the cave again. */
    boolean crossesSeal(List<BlockPos> cells) {
        for (BlockPos cell : cells) if (sealed.contains(cell)) return true;
        return false;
    }

    void reset() {
        phase = Phase.IDLE;
        cave = null;
        touch = new HashSet<>();
        home = waypoint = pit = null;
        failure = null;
        sealed.clear();
        visited.clear();
        unreachable.clear();
        notes.clear();
        ore.limit(pos -> true);
        seenBroken = dig.broken;
        idle = settle = taken = work = tries = 0;
    }

    /**
     * True while the cave has this tick. {@code planCells}: the cells of the advance under way (none between advances);
     * {@code mining}: he is digging his mine, not walking it or fetching ore from afar.
     */
    boolean tick(ServerLevel level, List<BlockPos> planCells, boolean mining) {
        if (failure != null) return true;
        if (phase == Phase.IDLE) {
            if (dig.broken == seenBroken) return false;
            seenBroken = dig.broken;
            return mining && detect(level, planCells);
        }
        calm();
        return switch (phase) {
            case WAIT -> waiting(level, planCells);
            case EXPLORE -> explore(level);
            case BACK -> back();
            default -> seal(level, planCells);
        };
    }

    /** A block just broken opens onto air that is not his mine: judge it. */
    private boolean detect(ServerLevel level, List<BlockPos> planCells) {
        BlockPos at = dig.lastBroken;
        if (at == null) return false;
        Set<BlockPos> mine = CompanionCaveSense.mineCells(w.mine);
        mine.addAll(planCells);
        mine.add(at);
        List<BlockPos> mouth = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos next = at.relative(side);
            if (!mine.contains(next) && level.isLoaded(next) && CompanionShaftPlan.open(level, next)) mouth.add(next);
        }
        if (mouth.isEmpty()) return false;
        CompanionCaveSense.Cave found = CompanionCaveSense.fill(level, at, mouth, mine);
        if (found.surface()) return false;
        cave = found;
        touch = mine;
        CompanionCaveSense.Facts facts = CompanionCaveSense.sense(w, level, cave, false);
        CompanionCaveSense.Danger danger = CompanionCaveSense.judge(cave, facts);
        note("Наткнулся на пещеру (" + CompanionCaveSense.describe(cave, facts) + ").");
        // VERY_DANGEROUS is sealed like DANGEROUS for now; the retreat up his stairs to rest is the next step and starts here.
        phase = danger == CompanionCaveSense.Danger.SAFE ? Phase.WAIT : Phase.SEAL;
        idle = 0;
        return true;
    }

    /** A safe cave is entered between two advances, never in the middle of one. */
    private boolean waiting(ServerLevel level, List<BlockPos> planCells) {
        if (!planCells.isEmpty()) return false;
        if (CompanionCaveSense.judge(cave, CompanionCaveSense.sense(w, level, cave, true)) != CompanionCaveSense.Danger.SAFE) { sealing(); return true; }
        home = owner.blockPosition();
        until = level.getGameTime() + EXPLORE_TICKS;
        visited.clear();
        unreachable.clear();
        visited.add(cave.breach());
        taken = settle = 0;
        BlockPos breach = cave.breach();
        ore.limit(pos -> pos.distSqr(breach) <= EXPLORE_RANGE * EXPLORE_RANGE);
        phase = Phase.EXPLORE;
        return true;
    }

    private boolean explore(ServerLevel level) {
        if (pit != null) { plug(); return true; }
        long now = level.getGameTime();
        if (enough.getAsBoolean() || now >= until) return leave("");
        if (now % WATCH_EVERY == 0 && CompanionCaveSense.judge(cave, CompanionCaveSense.sense(w, level, cave, true)) != CompanionCaveSense.Danger.SAFE) {
            return leave("В пещере стало опасно, вернулся.");
        }
        if (settle > 0) { settle--; return true; }
        if (ore.busy() || now % LOOK_EVERY == 0) {
            switch (ore.step(level, CompanionExpeditionOre.Mode.SIGHT)) {
                case WORKING -> { return true; }
                case DONE -> { taken++; work++; settle = SETTLE; pit = dig.lastBroken; tries = 0; return true; }
                case FULL -> { return leave("Сумка полна, добытое некуда класть."); }
                case NONE -> { }
            }
        }
        if (waypoint == null) {
            waypoint = nextWaypoint(level);
            if (waypoint == null) return leave("");
        }
        switch (w.route.legs(waypoint, ARRIVED)) {
            case WALKING -> { }
            case NEAR -> { visited.add(waypoint); waypoint = null; work++; }
            case FAILED -> { unreachable.add(waypoint); waypoint = null; }
        }
        return true;
    }

    /** The hole an ore left is filled at once, so that no pit is left in the cave floor; if it cannot be, it stays. */
    private void plug() {
        CompanionSeal.Result result = CompanionSeal.place(owner, pit);
        if (result == CompanionSeal.Result.PLACED) work++;
        if (result == CompanionSeal.Result.OCCUPIED && owner.getBoundingBox().intersects(new AABB(pit))) stepOut();
        if (result != CompanionSeal.Result.PLACED && result != CompanionSeal.Result.NO_BLOCK && ++tries < PIT_TRIES) return;
        pit = null;
    }

    /** He picked the ore up by stepping into its hole: out of it again, so that it can be filled. */
    private void stepOut() {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos out = pit.above().relative(side);
            if (!CompanionMiningNavigation.standable(owner, out)) continue;
            dig.walkTo(out);
            return;
        }
    }

    /** The nearest place in range not yet looked from, which he can walk back up from. */
    private BlockPos nextWaypoint(ServerLevel level) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : cave.air()) {
            double distance = owner.distanceToSqr(Vec3.atBottomCenterOf(cell));
            if (distance >= bestDistance || cell.distSqr(cave.breach()) > EXPLORE_RANGE * EXPLORE_RANGE || unreachable.contains(cell)) continue;
            if (!CompanionMiningNavigation.standable(owner, cell) || seen(cell)) continue;
            if (!CompanionMiningNavigation.canWalkBackUp(owner, cell, home.getY())) { unreachable.add(cell); continue; }
            best = cell;
            bestDistance = distance;
        }
        return best;
    }

    private boolean seen(BlockPos cell) {
        for (BlockPos place : visited) if (place.distSqr(cell) < SPACING * SPACING) return true;
        return false;
    }

    private boolean leave(String why) {
        ore.limit(pos -> true);
        waypoint = null;
        note((taken > 0 ? "Взял в пещере алмазов: " + taken + ". " : "Алмазов в пещере не взял. ") + why);
        phase = Phase.BACK;
        return true;
    }

    private boolean back() {
        switch (w.route.legs(home, ARRIVED)) {
            case WALKING -> { }
            case NEAR -> {
                // The legs end within two blocks of home, which can still be the first cell of the cave: the last steps are his own.
                if (!cave.air().contains(owner.blockPosition())) sealing();
                else if (dig.walkTo(home)) sealing();
                else if (dig.walkStuck()) failure = "Не смог вернуться из пещеры в свой тоннель.";
            }
            case FAILED -> failure = "Не смог вернуться из пещеры в свой тоннель.";
        }
        return true;
    }

    private void sealing() {
        phase = Phase.SEAL;
        idle = 0;
    }

    /** Plugs the cave air that touches his mine, nearest first; false once nothing is left to do, or while the advance has to open the way to it. */
    private boolean seal(ServerLevel level, List<BlockPos> planCells) {
        touch.addAll(planCells);
        List<BlockPos> open = openTouching(level);
        if (open.isEmpty()) return finish("Пролом заделал.");
        if (++idle > SEAL_PATIENCE) return finish("Пролом заделать до конца не вышло.");
        boolean occupied = false;
        for (BlockPos cell : open) {
            if (!CompanionSeal.replaceable(level, cell)) { level.destroyBlock(cell, true, owner); return true; }
            switch (CompanionSeal.place(owner, cell)) {
                case PLACED -> { w.mine.sealed(cell); sealed.add(cell); work++; idle = 0; return true; }
                case NO_BLOCK -> { return finish("Пролом заделать нечем."); }
                case OCCUPIED -> occupied |= !owner.getBoundingBox().intersects(new AABB(cell));
                default -> { }
            }
        }
        if (occupied) return true;
        BlockPos stand = standFor(level, open.get(0));
        if (stand == null) return planCells.isEmpty();
        if (!dig.walkTo(stand) && dig.walkStuck()) idle = SEAL_PATIENCE;
        return true;
    }

    private boolean finish(String text) {
        note(text);
        phase = Phase.IDLE;
        cave = null;
        waypoint = null;
        seenBroken = dig.broken;
        return false;
    }

    /** Open cells of the cave beside the mine (or beside the advance under way), nearest first. */
    private List<BlockPos> openTouching(ServerLevel level) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : cave.air()) {
            if (!level.isLoaded(cell) || !CompanionShaftPlan.open(level, cell)) continue;
            for (Direction side : Direction.values()) {
                if (touch.contains(cell.relative(side))) { cells.add(cell); break; }
            }
        }
        cells.sort((a, b) -> Double.compare(owner.distanceToSqr(Vec3.atCenterOf(a)), owner.distanceToSqr(Vec3.atCenterOf(b))));
        return cells;
    }

    /** An open cell of his mine from which the cell can be reached and seen; away from a creeper if there is one. Null when there is none yet. */
    private BlockPos standFor(ServerLevel level, BlockPos target) {
        Vec3 creeper = creeperAt(level);
        BlockPos best = null;
        double bestKey = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-3, -3, -3), target.offset(3, 3, 3))) {
            if (!touch.contains(p) || !CompanionShaftPlan.open(level, p) || !CompanionShaftPlan.open(level, p.above())) continue;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
            if (!CompanionShaftPlan.clearTo(level, new Vec3(p.getX() + .5, p.getY() + 1.62, p.getZ() + .5), target)) continue;
            double key = creeper == null ? owner.distanceToSqr(Vec3.atBottomCenterOf(p)) : -creeper.distanceToSqr(Vec3.atBottomCenterOf(p));
            if (key < bestKey) { best = p.immutable(); bestKey = key; }
        }
        return best;
    }

    private Vec3 creeperAt(ServerLevel level) {
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(CompanionCaveSense.WATCH_RADIUS))) {
            if (creeper.isAlive() && cave.air().contains(creeper.blockPosition())) return creeper.position();
        }
        return null;
    }

    /** He never strikes a creeper, and a chase does not lead him into the cave. */
    private void calm() {
        LivingEntity target = owner.getTarget();
        if (target == null) return;
        boolean chase = phase != Phase.EXPLORE && cave != null && cave.air().contains(target.blockPosition()) && owner.distanceToSqr(target) > MELEE * MELEE;
        if (target instanceof Creeper || chase) owner.setTarget(null);
    }

    private void note(String text) {
        String line = text.strip();
        if (!line.isEmpty() && notes.size() < NOTES) notes.add(line);
    }
}
