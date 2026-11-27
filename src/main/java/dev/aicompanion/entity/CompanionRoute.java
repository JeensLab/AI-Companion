package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single route: the one way the activities lead the companion to a goal. A walk to one of the cells from which a block is
 * worked, the nearest of many blocks on foot, a far goal in legs, and an outcome that tells «far, keep walking» from «no way».
 * The search behind it is the companion's own (CompanionSearch); every walk it leads ends in one {@code route_outcome} event of the journal.
 */
public final class CompanionRoute {
    /** What is wanted of the cells a block is worked from (see CompanionMiningNavigation.standCells). */
    record Need(double reach, boolean clearLeaves, boolean avoidStandingOnTarget, boolean forTheDrop) { }

    /** WALK: a whole route by one search. FAR: none within one search, but one a wider search finds (walked on, searched again). NONE: no way. */
    enum Kind { WALK, FAR, NONE }

    /** A block chosen by the walk to a cell it can be worked from, that walk and its length in blocks; NONE says why. */
    record Reach(Kind kind, BlockPos block, Path path, double length, String reason) {
        boolean found() { return kind != Kind.NONE; }
        /** No way was looked for (the tick's cells were spent): not a failure, ask again next tick. */
        boolean busy() { return BUSY.equals(reason); }
    }

    private static final int PATIENCE = 160;
    static final String BUSY = "busy";
    /** Not even the short search ran (he is on a ladder or in a jump): neither a far goal nor no way is known. */
    static final String NOT_SEARCHED = "not searched";
    /** How far a far walk counts walking as progress: twice the time its length takes at his pace, and ten seconds more. */
    static long farLimit(double length) { return (long)(2 * length / .19) + 200; }

    private final CompanionWork w;
    // The walk as the journal tells it: to what, from where, when, what the searches had cost by then; null once told.
    private BlockPos goal, from;
    private long began;
    private boolean far;
    private long farUntil;
    // The paths chosen as far ones (by identity): the kind goes with the path, whichever search it came from.
    private final Set<Path> farPaths = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private long failedAt = Long.MIN_VALUE / 2;
    private int failures;
    private CompanionNavigation.Meter failedMeter;
    private CompanionNavigation.Meter meter;
    /** A wide search of these cells that failed: when, and whether its failure holds for these cells alone (it ran out of budget) or for any subset. */
    private record Wide(long at, boolean exact) { }
    private final Map<Set<BlockPos>, Wide> wideFailed = new HashMap<>();
    private long changesSeen;
    /** Whether the last failed search proved there is no way: its wide pass saw the whole reachable region, not a spent budget. */
    private boolean provenMiss;

    CompanionRoute(CompanionWork w) { this.w = w; }

    /**
     * The block a player would go to first: one search to the cells of all of them at once, so the nearest on foot wins, not the
     * nearest by the straight line. Beyond one search the same is asked of a wider one: that is a far goal, not a lost one.
     */
    Reach nearest(Collection<BlockPos> blocks, Need need) {
        CompanionEntity owner = w.owner;
        Map<BlockPos, BlockPos> blockAt = new HashMap<>();
        for (BlockPos block : blocks) for (BlockPos stand : CompanionMiningNavigation.standCells(owner, block, need.reach(), need.clearLeaves(), need.avoidStandingOnTarget())) {
            if (need.forTheDrop() && !CompanionMiningNavigation.dropCollectable(owner.level(), block, stand)) continue;
            BlockPos other = blockAt.get(stand);
            if (other == null || stand.distSqr(block) < stand.distSqr(other)) blockAt.put(stand, block.immutable());
        }
        if (blockAt.isEmpty()) return failed(blocks, "no stand cell");
        Map<BlockPos, BlockPos> chosen = unoccupied(owner, blockAt);
        Reach found = find(chosen.keySet(), 0, chosen::get);
        return found.found() || found.busy() ? found : failed(blocks, found.reason());
    }

    /**
     * The way to one of these cells (a drop's pickup spots, the place he came from): one search, else the wide one. The way itself
     * if it comes all the way ({@code accuracy}: how close counts), else none.
     */

    Reach cells(Set<BlockPos> cells, int accuracy) {
        Reach found = find(cells, accuracy, cell -> null);
        return found.found() || found.busy() || cells.isEmpty() ? found : failed(cells, found.reason());
    }

    private Reach find(Set<BlockPos> cells, int accuracy, java.util.function.Function<BlockPos, BlockPos> blockOf) {
        CompanionEntity owner = w.owner;
        provenMiss = false;
        Kind kind = Kind.WALK;
        boolean wideGoing = nav().running(cells, accuracy, nav().followRange(3));
        Path path = wideGoing ? null : nav().search(cells, accuracy, nav().followRange(1));
        // The search goes on at the next tick (it keeps what it has found): not a failure.
        if (path == CompanionNavigation.BUSY) return new Reach(Kind.NONE, null, null, 0, BUSY);
        if (wideGoing || !reaches(path, cells, accuracy)) {
            // No path at all: nothing was searched (he is in the air); that is no evidence of a far goal or of none.
            if (path == null && !wideGoing) return new Reach(Kind.NONE, null, null, 0, NOT_SEARCHED);
            // A goal that no wide search reaches is not searched wide again at once (a failed one costs a whole search): five seconds.
            long now = owner.level().getGameTime();
            // A block changed near him since: what failed then may not fail now.
            if (nav().changes() != changesSeen) { changesSeen = nav().changes(); wideFailed.clear(); }
            // Failed wide for these cells; for a set that holds them all too, if that search ran out of range (a group that failed has
            // failed for each of its faces), not if it ran out of budget (it may have spent it on a near cell that cannot be reached).
            for (var entry : wideFailed.entrySet())
                if (now - entry.getValue().at() < 100 && (entry.getValue().exact() ? entry.getKey().equals(cells) : entry.getKey().containsAll(cells))) {
                    // A failure that ran out of range holds for every subset of those cells: no way is proven.
                    provenMiss = !entry.getValue().exact();
                    return new Reach(Kind.NONE, null, null, 0, "no route");
                }
            if (!(owner.getNavigation() instanceof CompanionNavigation nav)) return new Reach(Kind.NONE, null, null, 0, "no route");
            path = nav.wide(cells, accuracy);
            // The wide search goes on at the next tick (this tick's cells are spent): not a failure.
            if (path == CompanionNavigation.BUSY) return new Reach(Kind.NONE, null, null, 0, BUSY);
            if (!reaches(path, cells, accuracy)) {
                if (wideFailed.size() > 64) wideFailed.values().removeIf(old -> now - old.at() >= 100);
                if (wideFailed.size() > 256) wideFailed.clear();
                wideFailed.put(Set.copyOf(cells), new Wide(now, nav.lastSearchExhausted()));
                // Out of range is a proof for these cells and every subset; out of budget is a proof for this set alone.
                provenMiss = !nav.lastSearchExhausted();
                // It went on while the short one was not asked: the short one, from here and in the world as it is now, first.
                if (wideGoing) return new Reach(Kind.NONE, null, null, 0, BUSY);
                return new Reach(Kind.NONE, null, null, 0, "no route");
            }
            kind = Kind.FAR;
            // The path being walked keeps its far mark whatever is dropped.
            if (farPaths.size() > 32) farPaths.removeIf(old -> old != w.path);
            farPaths.add(path);
        }
        return new Reach(kind, blockOf.apply(path.getTarget()), path, CompanionMiningNavigation.length(owner, path), kind == Kind.FAR ? "far" : "near");
    }

    /** This tick's cells are spent: a choice that goes on searching waits for the next tick. */
    boolean busy() { return w.owner.getNavigation() instanceof CompanionNavigation nav && nav.overBudget(); }

    Reach to(BlockPos block, Need need) { return nearest(List.of(block), need); }

    /**
     * Somebody standing in a stand cell: walking into him only pushes him about, and the way never ends (the player beside a sand column).
     * The free cells are preferred; when every cell is taken they all stay, as he can push the player or an animal aside. One entity query for all cells.
     */
    public static Map<BlockPos, BlockPos> unoccupied(CompanionEntity owner, Map<BlockPos, BlockPos> stands) {
        Map<BlockPos, AABB> bodies = new HashMap<>();
        AABB all = null;
        for (BlockPos stand : stands.keySet()) {
            var body = new AABB(stand.getX() + .2, stand.getY(), stand.getZ() + .2, stand.getX() + .8, stand.getY() + owner.getBbHeight(), stand.getZ() + .8);
            bodies.put(stand, body);
            all = all == null ? body : all.minmax(body);
        }
        if (all == null) return stands;
        var standing = owner.level().getEntities(owner, all, entity -> entity instanceof LivingEntity living && living.isAlive());
        if (standing.isEmpty()) return stands;
        Map<BlockPos, BlockPos> free = new HashMap<>();
        for (var entry : stands.entrySet()) {
            AABB body = bodies.get(entry.getKey());
            if (standing.stream().noneMatch(entity -> entity.getBoundingBox().intersects(body))) free.put(entry.getKey(), entry.getValue());
        }
        return free.isEmpty() ? stands : free;
    }

    /**
     * «No way» is told only where the wide search can see: with the target within one and a half follow ranges in a straight line
     * and no whole route to it. Farther off it sees nothing at its cell budget, and a stuck walk is told as stuck, not as no way.
     */
    boolean noWayTo(BlockPos target) { return Boolean.TRUE.equals(noWay(target)); }

    /**
     * The same, null while it is not known: the diagnosis searches by the shares of the ticks like any walk (a sealed player would cost
     * two whole searches in one tick), so a caller that wants the answer asks again next tick.
     */
    Boolean noWay(BlockPos target) {
        double range = w.owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) * 1.5;
        if (w.owner.distanceToSqr(target.getCenter()) > range * range) return false;
        Reach reach = cells(Set.of(target), 1);
        if (reach.busy()) return null;
        return !reach.found() && !NOT_SEARCHED.equals(reach.reason());
    }

    /**
     * The same, true only where it is proven: the wide search went over all he can reach within its range inside its cell budget and
     * the target was not in it (an animal on a ledge behind a wall). One that ran out of cells proves nothing, whatever the distance.
     */
    Boolean provenNoWay(BlockPos target) {
        Reach reach = cells(Set.of(target), 1);
        if (reach.busy()) return null;
        if (reach.found() || NOT_SEARCHED.equals(reach.reason())) return false;
        float range = nav().followRange(3);
        if (w.owner.distanceToSqr(target.getCenter()) > range * range) return false;
        long now = w.owner.level().getGameTime();
        for (var entry : wideFailed.entrySet())
            if (now - entry.getValue().at() < 100 && !entry.getValue().exact() && entry.getKey().contains(target)) return true;
        return false;
    }

    /** One search to any of these cells: the way if it comes all the way, else null. For the short last steps, not for far goals. */
    Path toCells(Set<BlockPos> cells) { return toCells(cells, 0); }

    /** The same where coming within {@code accuracy} blocks of a cell counts. */
    Path toCells(Set<BlockPos> cells, int accuracy) {
        Path path = nav().search(cells, accuracy);
        if (path == CompanionNavigation.BUSY) { busyAnswers++; return null; }
        // A search that did not run at all (he is in the air, in a jump) is not an answer: «not known yet», ask again next tick.
        if (path == null && nav().lastNotSearched()) { notSearchedAnswers++; return null; }
        return path != null && path.canReach() ? path : null;
    }

    private int busyAnswers;
    /**
     * How many times {@link #toCells} has answered null because this tick's cells were spent, not because there is no way. A caller
     * that cannot tell the two apart from the answer reads it before and after: changed means "not known yet, ask again next tick".
     */
    int busyAnswers() { return busyAnswers; }
    private int notSearchedAnswers;
    /** The same for a search that did not run at all this tick: no way is not known either. */
    int notSearchedAnswers() { return notSearchedAnswers; }

    private CompanionNavigation nav() { return (CompanionNavigation)w.owner.getNavigation(); }

    void walk(Path path) { CompanionMiningNavigation.walk(w.owner, path); }
    void walk(Path path, double speed) { CompanionMiningNavigation.walk(w.owner, path, speed); }

    /** The leg being walked is over (or he is stuck in it): time for the next search. */
    boolean legOver() { return done() || stuck(); }
    /** The leg is walked to its end / he cannot get on along it. */
    boolean done() { return w.owner.getNavigation().isDone(); }
    boolean stuck() { return w.owner.getNavigation().isStuck(); }

    void stop() { w.owner.getNavigation().stop(); }

    /** A far goal in legs (CompanionTravel); NEAR once within {@code near} blocks of it. */
    CompanionTravel.Step legs(BlockPos target, double near) { return w.owner.travel().toward(target, near); }

    /** The same towards a goal that moves (the player): the legs and their progress are kept while it shifts by less than sixteen blocks. */
    CompanionTravel.Step follow(BlockPos target, double near) { return w.owner.travel().follow(target, near, false); }

    /** The same running when the player does (the following keeps up with a sprinting player). */
    CompanionTravel.Step follow(BlockPos target, double near, boolean runs) { return w.owner.travel().follow(target, near, runs); }

    /** The legs are over, the goal reached or not: the walk is told to the journal if it was not yet. */
    void legsEnd(boolean arrived) { if (arrived) w.owner.travel().arrived(); else w.owner.travel().reset(); }

    /**
     * One tick of the approach to a block he works from a standing place (ore, a bench to take): 1 in reach and sight (and, for the
     * drop, where it can be picked up), 0 walking, -1 no way or no progress. A walk that needed a wider search counts walking as
     * progress; the others, coming closer to the block.
     */
    int approach(ServerLevel level, BlockPos block, boolean forTheDrop) { return approach(level, block, new Need(3.5, false, true, forTheDrop)); }

    /** The same for a block worked from other cells than ore is (the stairs down: standing on the dug cell is allowed). */
    int approach(ServerLevel level, BlockPos block, Need need) {
        boolean forTheDrop = need.forTheDrop();
        if (!block.equals(goal)) begin(block);
        if (w.path != null && farPaths.contains(w.path)) setFar(w.path);
        double distance = w.owner.distanceToSqr(block.getCenter());
        if (distance <= 3.8D * 3.8D && CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), block)
                && (!forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, w.owner.blockPosition()))) { w.owner.getNavigation().stop(); told("ARRIVED", far ? "far" : "near"); return 1; }
        // The way's own column stands where the block is in a swing: the swing goes on from it even where the drop lies below —
        // the walk to the drop comes after the block is broken, never between two swings (PATH7, #105: an ore in the air was
        // swung at one tick and reset the next, until the watchdog).
        if (!(w.owner.temporaryPillar.isWayActive() && w.owner.temporaryPillar.inReach(block))) w.breaking.reset();
        if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = level.getGameTime(); }
        // A way of his own standing (a column being raised, a floor across a gap) is progress: the block comes closer step by
        // step, and between two steps nothing moves for a while.
        if (w.owner.temporaryPillar.isWayActive() || w.owner.temporaryBridge.isActive()) w.lastProgressTick = level.getGameTime();
        // Along a route longer than one search the straight distance first grows: walking is progress then.
        if (far && level.getGameTime() < farUntil && (w.approachMoved == null || w.owner.position().distanceToSqr(w.approachMoved) > .7 * .7)) { w.approachMoved = w.owner.position(); w.lastProgressTick = level.getGameTime(); }
        if (level.getGameTime() - w.lastProgressTick > PATIENCE) { told("FAILED", "no progress"); return -1; }
        if (w.path == null || legOver()) {
            Reach reach = to(block, need);
            // This tick's cells went to another choice: keep walking the leg he has, ask again next tick.
            if (reach.busy()) return 0;
            // Nothing was searched at all (he is in the air, the first tick of the order): that is no evidence of no way.
            if (NOT_SEARCHED.equals(reach.reason())) return 0;
            w.path = reach.path();
            if (reach.kind() == Kind.FAR) setFar(w.path);
            // A failed search was told by nearest() already.
            if (!reach.found()) {
                int way = buildUp(block, reach);
                if (way < 0) { meter = null; return -1; }
                return way;
            }
        }
        if (w.path == null || !w.path.canReach()) { told("FAILED", "no route"); return -1; }
        walk(w.path);
        return 0;
    }

    /**
     * A way up to a block no walk reaches (PATH7, #105). Only where the miss is proven — the wide search saw the whole reachable
     * region, or no stand cell reaches the block at all — the block stands two to sixteen blocks above his feet within four
     * across, and the work's own stairs or tunnel are not the cells he would stand on (CompanionWork.allowsRouteRecovery, the
     * mirror of the recovery's own guard). The column is a leaf crown's climb with its own limit; the switch is the player's.
     * 1: the block is in reach now, 0: climbing, -1: no way, the caller refuses as before.
     */
    private int buildUp(BlockPos block, Reach reach) {
        CompanionEntity owner = w.owner;
        if (!owner.buildWay()) return noWay(block, "switch off");
        // The work's own stairs and its ore tunnel are the way back, not a trap to build out of (CompanionWork.allowsRouteRecovery).
        if (!w.allowsRouteRecovery()) return noWay(block, "work keeps its own way");
        if (owner.recovery.active()) return noWay(block, "recovery running");
        CompanionPillar pillar = owner.temporaryPillar;
        // A leaf crown's column is not ours to raise; one coming down is left to finish — his own way is walked down, and the block
        // is asked again once it is (no refusal over a column that stands yet).
        if (pillar.isRecovering()) return 0;
        // The climb must be ticked every tick of its climb, the jump's own arc included: that is when its step is set down.
        // So the way already standing is ticked before anything else, even while he is off the ground in its jump.
        if (pillar.isWayActive()) return tickWay(pillar, block);
        // A floor across the gap already being laid is ticked before anything else: its plan stands while it is his.
        if (owner.temporaryBridge.isActive() || owner.temporaryBridge.isRecovering()) return tickBridge(block);
        if (pillar.isActive()) {
            // A column of the way that failed is still coming down (its soil with it): the next answer waits for it, rather than
            // a refusal over a column that stands there yet.
            if (pillar.isRecovering()) return 0;
            return noWay(block, "a leaf crown's column stands");
        }
        // Between two steps before a way is raised (a hop on the way to it): the block is not given up, asked again next tick.
        if (!owner.onGround()) return 0;
        // The one geometry answer, logged as the decision the player can read: up a column, or a floor across a gap. It asks only
        // where the block is — how far up, how far across, whether a bank is behind a gap — never what the block is. It is asked
        // before the walk's own miss is judged: a block a column or a floor reaches is no refusal over a search that did not prove
        // its miss (the owner, 27.11: an ore three up and nine across was refused as «not proven: no route» while a column of four
        // stood within a step of it).
        CompanionReach.How how = CompanionReach.how(owner, (ServerLevel) owner.level(), block, 3.5D, true, false, CompanionPillar.WAY_SUPPORTS);
        CompanionReach.decision(owner, block, how);
        if (how.bridge()) return tickBridge(block);
        if (how.up()) return tickWay(pillar, block);
        if (how.way() == CompanionReach.Way.WALK || how.way() == CompanionReach.Way.ACROSS) {
            // A stand cell reaches it, yet the walk could not get there (a gap, a wall): a floor laid across the gap before it
            // is the same geometry answer (PATH7: «мост через провал»).
            CompanionReach.BridgePlan plan = CompanionReach.bridge(owner, (ServerLevel) owner.level(), block, 3.5D, true, CompanionPillar.WAY_SUPPORTS, false);
            // A floor to lay is what makes a bridge: a bank and the far cell with a floor between them already is no bridge.
            if (plan != null && plan.length() > 0) return tickBridge(block);
            // No bank at its own level to lay a floor from: a column up to the block is the other way the same answer gives — a
            // stand cell no walk comes at is still worked from the top of his own soil (the owner, 27.11: an ore over a ledge two
            // steps up was neither walked to nor climbed to, and the way gave up on it).
            return tickWay(pillar, block);
        }
        // No way of any shape: the walk's own answer stands. A miss that is not proven is no refusal — asked again next tick,
        // the search may have spent its cells on another choice.
        if (!provenMiss && !"no stand cell".equals(reach.reason())) return 0;
        return noWay(block, how.why().isEmpty() ? reach.reason() : how.why());
    }

    /** One tick of the floor across a gap: 1 in reach, 0 laying or walking, -1 no way. */
    private int tickBridge(BlockPos block) {
        CompanionBridge.Outcome outcome = w.owner.temporaryBridge.tickToward(block);
        if (outcome == CompanionBridge.Outcome.FAILED) return noWay(block, "bridge failed: " + w.owner.temporaryBridge.failureReason());
        return outcome == CompanionBridge.Outcome.READY ? 1 : 0;
    }

    private int tickWay(CompanionPillar pillar, BlockPos block) {
        CompanionPillar.Outcome outcome = pillar.tickToward(block, false, CompanionPillar.WAY_SUPPORTS);
        if (outcome == CompanionPillar.Outcome.FAILED) {
            String why = pillar.failureReason();
            // A column that stood but cannot serve this block comes down (its foot was planned for another block): the block is
            // asked again from the ground, never refused over a column that is only being taken down (the owner, 27.11: the
            // second gold was «с этой опоры не достану» and the way gave up on it while its own column came down).
            if (pillar.isRecovering()) return 0;
            // Every foot of the column failed: a floor across the gap before it is the other way the same geometry answer gives
            // (PATH7: «мост через провал»), never a refusal while one can still be laid. It waits for the failed column's soil.
            if (!pillar.isActive()) {
                CompanionReach.BridgePlan plan = CompanionReach.bridge(w.owner, (ServerLevel) w.owner.level(), block, 3.5D, true,
                        CompanionPillar.WAY_SUPPORTS, false);
                if (plan != null && plan.length() > 0) return tickBridge(block);
            }
            return noWay(block, "column failed: " + why);
        }
        return outcome == CompanionPillar.Outcome.READY ? 1 : 0;
    }

    /** The last way refusal told to the journal, so a tick-by-tick retry says it once. */
    private String wayRefusal = "";

    private int noWay(BlockPos block, String why) {
        if (!why.equals(wayRefusal)) {
            wayRefusal = why;
            // The refusal names the block and its real distance, not the first thing that came to mind (PATH7, #105), and is
            // said in the game log: a refusal the player cannot read is a refusal he cannot check.
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(w.owner.level().getBlockState(block).getBlock()).toString();
            String text = "не иду к " + block.toShortString() + " «" + id + "»: " + CompanionReach.distance(w.owner, block) + " — " + why;
            dev.aicompanion.CompanionDiagnostics.say(w.owner.diagnosticTrace(), "way_refused", text,
                    "entity", w.owner.getUUID(), "why", why, "block", block, "position", w.owner.blockPosition(),
                    "id", id, "rise", block.getY() - w.owner.blockPosition().getY(), "distance", CompanionReach.distance(w.owner, block));
        }
        return -1;
    }

    private void setFar(Path path) {
        far = true;
        if (farUntil == 0 && path != null) farUntil = w.owner.level().getGameTime() + farLimit(CompanionMiningNavigation.length(w.owner, path));
    }

    /** The walk to this goal is over by other means (the goal was dropped, the work ended). */
    void abandon() { told("ABANDONED", "goal dropped"); goal = null; }

    private void begin(BlockPos block) {
        told("ABANDONED", "replaced");
        wayRefusal = "";
        goal = block.immutable(); farUntil = 0; from = w.owner.blockPosition(); began = w.owner.level().getGameTime(); far = false;
        meter = CompanionNavigation.meterOf(w.owner);
    }

    /** The route-outcome event of the journal, once per walk. */
    private void told(String outcome, String reason) {
        if (meter == null || goal == null) return;
        CompanionNavigation.outcome(w.owner, from, goal, outcome, reason, w.owner.level().getGameTime() - began, Math.sqrt(from.distSqr(goal)),
                CompanionNavigation.meterOf(w.owner).since(meter));
        meter = null;
    }

    private Reach failed(Collection<BlockPos> blocks, String reason) {
        // One event per ten seconds, whatever the reasons alternate; the failures in between are counted in it.
        long now = w.owner.level().getGameTime();
        failures++;
        if (!blocks.isEmpty() && now - failedAt >= 200) {
            BlockPos block = blocks.iterator().next();
            var metered = CompanionNavigation.meterOf(w.owner);
            CompanionNavigation.outcome(w.owner, w.owner.blockPosition(), block, "FAILED", reason + " (" + failures + " searches)", 0,
                    w.owner.position().distanceTo(block.getCenter()), failedMeter == null ? metered : metered.since(failedMeter));
            failedMeter = metered; failedAt = now; failures = 0;
        }
        return new Reach(Kind.NONE, null, null, 0, reason);
    }

    private static boolean reaches(Path path, Set<BlockPos> cells, int accuracy) {
        return path != null && path.canReach() && (accuracy > 0 || cells.contains(path.getTarget()));
    }
}
