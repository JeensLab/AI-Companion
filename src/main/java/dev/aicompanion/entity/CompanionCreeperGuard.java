package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * The watch against a creeper that sees him: a goal of its own at the top priority, so that nothing (an order, a pickup, a fight)
 * keeps him from it, in any stage of the mine and with no order at all.
 * <p>
 * On an expedition, armed and with health to spare, he fights a creeper with the sword like any other mob (the melee goal), and this goal
 * takes him only while its fuse is lit and he cannot strike once more before it is time to go: he steps back, fast and away from it, until
 * the fuse is out ({@link Creeper#getSwellDir}), and the melee goal brings him back to finish it. Vanilla's fuse is 30 ticks, it goes out
 * beyond 7 blocks or without a line of sight, and a blast reaches 6 blocks, so the way back must be free and short.
 * <p>
 * The rest is the fallback, for when he cannot fight it (no sword, low health, no way back, too many retreats from it): on his own stairs or
 * tunnel he shuts the way between them with a block from the bag, a few stands off, and waits behind it; anywhere else he walks away, towards
 * his own passage if it is farther from the creeper than he is. The block is one of the mine's temporary blocks: it is taken up again when
 * the walk reaches it ({@link CompanionExpeditionDig}) and the creeper is gone.
 */
final class CompanionCreeperGuard {
    /** How near a creeper in sight is a reason to act: its fuse starts at three blocks and a blast kills from full health inside about 2.5. */
    static final double NEAR = 9;
    /** How near another hostile is on him, a blow away. */
    private static final double ON_HIM = 3;
    private static final int LOOK_EVERY = 5, FIGHT_LOOK_EVERY = 2, PLAN_EVERY = 10, EPISODE_TICKS = 20 * 12, CALM_TICKS = 20, PLACE_TRIES = 20;
    private static final int FLEE_CELLS = 6, SHUT_STANDS = 3, ON_ROUTE_SQR = 2, ROUTE_CREEPER_SQR = 4, PASSAGE_RANGE = 24, FLEE_RANGE = 12, FLEE_HEIGHT = 6;
    private static final double FLEE_SPEED = 1.3, ARRIVED_SQR = 4;
    /**
     * The fight. Health above which a creeper is met with the sword; the fuse's length in ticks and a margin of it left over; a blade's
     * reach and the closing speed of the two, for the time to get within it; the retreats from creepers he bears before he gives up the
     * fight, and how long they are remembered.
     */
    static final float FIGHT_HEALTH = 12;
    private static final int FUSE = 30, SWING_MARGIN = 2, GIVE_UP = 6, FORGET_TICKS = 20 * 60;
    private static final double REACH = 1.6, CLOSING_SPEED = .15;
    /**
     * The way out of a lit fuse: the fuse goes out only beyond 7 blocks (a little more for a margin), at his pace in blocks a tick, after the
     * ticks it takes the goal to take the turn and the path to start; the speed of that dash, and the cells of the route it runs along.
     */
    private static final double SAFE_GAP = 7.4, FLEE_PACE = .4, FIGHT_FLEE_SPEED = 1.6;
    private static final int LATENCY = 4, FIGHT_CELLS = 9;
    /** A creeper with a lit fuse is looked at before any other: this much nearer in the sort. */
    private static final double LIT_FIRST = 10_000;
    /**
     * The way out must not lead to another creeper: the creepers about are taken in to this range (in sight or not), the way out keeps off each
     * of them by what he has now or the blast gap, whichever is less, give or take the slack; and the places tried when his own preference fails.
     */
    private static final double THREAT_RANGE = 16, GAP_SLACK = .25;
    private static final int RING_HEADINGS = 8, AWAY_TRIES = 4, PATH_TESTS = 6;
    private static final int[] RING_RADII = {5, 9};

    private final CompanionEntity owner;
    private boolean active, finished, noBlock, fighting, shielded;
    private long begun, lastSeen, nextLook, nextPlan, lastRetreat;
    private int tries, retreats;
    private List<BlockPos> cells = List.of();
    private Vec3 flee;
    private final CompanionShieldGuard shield;

    CompanionCreeperGuard(CompanionEntity owner) { this.owner = owner; shield = new CompanionShieldGuard(owner); }

    /** The shield is up against a lit fuse: the idle lowering of it (CompanionEntity) waits. */
    boolean shielding() { return shield.holding(); }

    /** True when a creeper is met with the sword: on an expedition, armed, healthy, and not after a run of retreats from it. */
    boolean fights() {
        if (!owner.work.expedition.active() || owner.getHealth() <= FIGHT_HEALTH || !owner.equipment.hasMeleeWeapon()) return false;
        if (retreats > 0 && owner.level().getGameTime() - lastRetreat > FORGET_TICKS) retreats = 0;
        return retreats < GIVE_UP;
    }

    boolean canStart() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return false;
        long now = level.getGameTime();
        if (now < nextLook) return false;
        nextLook = now + (fights() ? FIGHT_LOOK_EVERY : LOOK_EVERY);
        Creeper creeper = seen(level);
        noBlock = false;
        shielded = creeper != null && shield.covers(level, creeper);
        return creeper != null && (shielded || !(!lit(creeper) && engaged()) && plan(level, creeper, false));
    }

    /**
     * Armed on an expedition with another hostile on him: a creeper whose fuse is not lit is no reason to turn his back on it, whatever his
     * health. The turn is the fight's; the guard takes it back when the fuse is lit.
     */
    private boolean engaged() {
        if (!owner.work.expedition.active() || !owner.equipment.hasMeleeWeapon()) return false;
        return !owner.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(ON_HIM),
                foe -> !(foe instanceof Creeper) && foe.isAlive() && !foe.isNoAi() && owner.hasLineOfSight(foe)).isEmpty();
    }

    /** A creeper that can hurt him stands in sight within this many blocks, foe or not: a pickup or a torch must not take his turn then. */
    boolean nearby(double range) {
        return !owner.level().getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(range),
                creeper -> creeper.isAlive() && !creeper.isNoAi() && creeper.distanceTo(owner) < range && owner.hasLineOfSight(creeper)).isEmpty();
    }

    /** Vanilla sets the swell direction only on its next tick after {@code ignite()}, so the flag counts as a lit fuse too. */
    static boolean lit(Creeper creeper) { return creeper.getSwellDir() > 0 || creeper.isIgnited(); }

    boolean canContinue() { return active && !finished && owner.isAlive() && owner.level().getGameTime() - begun < EPISODE_TICKS; }

    void start() {
        long now = owner.level().getGameTime();
        active = true;
        finished = false;
        begun = lastSeen = now;
        nextPlan = now + PLAN_EVERY;
        tries = 0;
        owner.getNavigation().stop();
        if (fighting) {
            retreats++;
            lastRetreat = now;
        }
        owner.workStatus(shielded ? "Крипер поджёг фитиль, прикрываюсь щитом." : fighting ? "Крипер поджёг фитиль, отхожу, потом добью." : "Рядом крипер, " + (cells.isEmpty() ? "ухожу от него." : "закрываю проход блоком."));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "creeper_guard", "entity", owner.getUUID(),
                "action", shielded ? "shield" : fighting ? "retreat" : cells.isEmpty() ? "flee" : "block", "cells", cells);
    }

    void tick() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Creeper creeper = seen(level);
        if (shielded) {
            shielded = creeper != null && shield.hold(level, creeper);
            if (shielded) lastSeen = now; else finished = true;
            return;
        }
        if (creeper == null) {
            if (now - lastSeen >= CALM_TICKS) finished = true;
            return;
        }
        lastSeen = now;
        owner.getLookControl().setLookAt(creeper, 30, 30);
        if ((fighting || engaged()) && !lit(creeper)) { finished = true; return; }
        if (now >= nextPlan || cells.isEmpty() && flee == null) {
            nextPlan = now + PLAN_EVERY;
            if (!plan(level, creeper, true)) { finished = true; return; }
        }
        if (!cells.isEmpty()) { shut(); return; }
        if (flee != null) owner.getNavigation().moveTo(flee.x, flee.y, flee.z, fighting ? FIGHT_FLEE_SPEED : FLEE_SPEED);
    }

    void stop() {
        if (!active) return;
        active = fighting = shielded = false;
        shield.release();
        flee = null;
        cells = List.of();
        owner.getNavigation().stop();
        owner.work.credit(owner.level().getGameTime() - begun);
    }

    /** One tick of placing the blocks; with none in the bag, or after too many tries, he walks away instead. */
    private void shut() {
        List<BlockPos> left = new ArrayList<>(cells);
        for (BlockPos cell : cells) {
            CompanionSeal.Result result = CompanionSeal.place(owner, cell);
            if (result == CompanionSeal.Result.PLACED) {
                owner.work.mine.temporary(cell);
                left.remove(cell);
            } else if (result == CompanionSeal.Result.NO_BLOCK || ++tries > PLACE_TRIES) {
                noBlock = true;
                left.clear();
                break;
            }
        }
        cells = left;
        if (cells.isEmpty()) nextPlan = 0;
    }

    /** The nearest creeper with a way to hurt him that he can see (one with a lit fuse first); one with its AI off can neither walk nor swell. */
    private Creeper seen(ServerLevel level) {
        Creeper nearest = null;
        double best = Double.MAX_VALUE;
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(NEAR),
                creeper -> creeper.isAlive() && !creeper.isNoAi() && creeper.distanceTo(owner) < NEAR && owner.hasLineOfSight(creeper))) {
            double distance = creeper.distanceToSqr(owner) - (lit(creeper) ? LIT_FIRST : 0);
            if (distance < best) { nearest = creeper; best = distance; }
        }
        return nearest;
    }

    /**
     * What to do about this creeper. Fighting it: whether to step back now, to a place away from it (along his route under the ground).
     * Otherwise, or with no way back: blocks to set on his route underground, or a place to walk to. False when there is nothing to do.
     */
    private boolean plan(ServerLevel level, Creeper creeper, boolean going) {
        Vec3 before = flee;
        cells = List.of();
        flee = null;
        CompanionMineSite mine = owner.work.mine;
        List<BlockPos> way = mine.exists() ? CompanionExpeditionClimb.wayUp(mine) : List.of();
        int at = indexOf(way, owner.blockPosition(), ON_ROUTE_SQR);
        boolean underground = at >= 0 && !level.canSeeSky(owner.blockPosition());
        List<Creeper> threats = threats(level);
        if (fights()) {
            if (!mustLeave(creeper, going)) return false;
            boolean keep = going && before != null && owner.position().distanceToSqr(before) > ARRIVED_SQR;
            Vec3 preferred = keep ? before : underground ? alongRoute(way, at, creeper, FIGHT_CELLS) : awayFrom(way, creeper, SAFE_GAP + 1);
            Vec3 away = retreatSpot(way, threats, preferred);
            fighting = away != null;
            if (fighting) { flee = away; return true; }
        }
        if (underground && !noBlock && CompanionSeal.available(owner) > 0) {
            cells = shutCells(mine, way, at, creeper);
            if (!cells.isEmpty()) return true;
        }
        Vec3 target = underground ? alongRoute(way, at, creeper, FLEE_CELLS) : awayFrom(way, creeper, 0);
        Vec3 clear = retreatSpot(way, threats, target);
        flee = clear != null ? clear : target;
        return flee != null;
    }

    /** Every creeper about that could hurt him, in sight or not: the way out of one must not lead to another. */
    private List<Creeper> threats(ServerLevel level) {
        return level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(THREAT_RANGE), creeper -> creeper.isAlive() && !creeper.isNoAi());
    }

    /**
     * The preferred place to go if the walk to it keeps clear of every creeper about; else the best place that is: farthest from the nearest
     * of them (the blast gap is enough), and one he can walk to. Null when there is none.
     */
    private Vec3 retreatSpot(List<BlockPos> way, List<Creeper> threats, Vec3 preferred) {
        if (preferred != null && clear(preferred, threats)) return preferred;
        if (threats.isEmpty()) return null;
        List<Vec3> spots = new ArrayList<>();
        for (BlockPos cell : way) {
            Vec3 spot = Vec3.atBottomCenterOf(cell);
            if (owner.distanceToSqr(spot) <= PASSAGE_RANGE * PASSAGE_RANGE) spots.add(spot);
        }
        for (int heading = 0; heading < RING_HEADINGS; heading++) for (int radius : RING_RADII) {
            double angle = heading * 2 * Math.PI / RING_HEADINGS;
            spots.add(owner.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        Vec3 centre = Vec3.ZERO;
        for (Creeper creeper : threats) centre = centre.add(creeper.position());
        centre = centre.scale(1.0 / threats.size());
        for (int i = 0; i < AWAY_TRIES; i++) {
            Vec3 spot = LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, centre);
            if (spot != null) spots.add(spot);
        }
        List<Vec3> ranked = spots.stream().filter(spot -> clear(spot, threats))
                .sorted(Comparator.comparingDouble((Vec3 spot) -> -gap(spot, threats)).thenComparingDouble(owner.position()::distanceToSqr)).limit(PATH_TESTS).toList();
        for (Vec3 spot : ranked) if (reachable(spot)) return spot;
        return null;
    }

    /** The walk from where he stands to the spot passes no creeper nearer than he is to it now (or than the blast gap). */
    private boolean clear(Vec3 spot, List<Creeper> threats) {
        Vec3 from = owner.position();
        for (Creeper creeper : threats) {
            double floor = Math.min(from.distanceTo(creeper.position()), SAFE_GAP);
            if (segmentDistance(from, spot, creeper.position()) < floor - GAP_SLACK) return false;
        }
        return true;
    }

    /** How far the spot is from the nearest creeper, counted up to the blast gap and no farther. */
    private static double gap(Vec3 spot, List<Creeper> threats) {
        double nearest = SAFE_GAP + 1;
        for (Creeper creeper : threats) nearest = Math.min(nearest, spot.distanceTo(creeper.position()));
        return nearest;
    }

    private static double segmentDistance(Vec3 from, Vec3 to, Vec3 point) {
        Vec3 line = to.subtract(from);
        double length = line.lengthSqr();
        double along = length == 0 ? 0 : Math.max(0, Math.min(1, point.subtract(from).dot(line) / length));
        return from.add(line.scale(along)).distanceTo(point);
    }

    private boolean reachable(Vec3 spot) {
        Path path = owner.getNavigation().createPath(BlockPos.containing(spot), 0);
        return path != null && path.canReach();
    }

    /** The fuse is lit and he cannot strike once more and still get out of its range in time: the ticks it has run, the walk to its reach, the start of the dash and the dash itself, against the fuse. */
    private boolean mustLeave(Creeper creeper, boolean going) {
        if (!lit(creeper)) return false;
        if (going) return true;
        double ran = creeper.getSwelling(0) * (FUSE - 2), distance = owner.distanceTo(creeper);
        double arrive = Math.max(0, distance - REACH) / CLOSING_SPEED, escape = Math.max(0, SAFE_GAP - Math.min(distance, REACH)) / FLEE_PACE;
        return ran + arrive + LATENCY + escape + SWING_MARGIN > FUSE;
    }

    /** The head-height cells of the stand a few steps off on the creeper's side, far enough that its fuse is not lit; empty when that is shut already or out of reach. */
    private List<BlockPos> shutCells(CompanionMineSite mine, List<BlockPos> way, int at, Creeper creeper) {
        int side = sideOf(way, at, creeper), creeperAt = indexOf(way, creeper.blockPosition(), ROUTE_CREEPER_SQR);
        ServerLevel level = (ServerLevel) owner.level();
        for (int k = SHUT_STANDS; k >= 1; k--) {
            int index = at + side * k;
            if (index < 0 || index >= way.size() || creeperAt >= 0 && Math.abs(creeperAt - at) <= k) continue;
            List<BlockPos> open = new ArrayList<>();
            for (BlockPos cell : headRow(mine, way, index)) if (CompanionSeal.replaceable(level, cell)) open.add(cell);
            if (open.isEmpty()) return List.of();
            if (open.stream().allMatch(cell -> CompanionSeal.reachable(owner, cell))) return open;
        }
        return List.of();
    }

    /** The cell above the stand, and in a 2x2 tunnel the one beside it too: a creeper is nearly two blocks tall and cannot pass under them. */
    private static List<BlockPos> headRow(CompanionMineSite mine, List<BlockPos> way, int index) {
        BlockPos cell = way.get(index);
        List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
        if (!tunnels.isEmpty() && index < tunnels.get(tunnels.size() - 1).length()) {
            BlockPos beside = cell.relative(CompanionShaftPlan.right(tunnels.get(tunnels.size() - 1).direction()));
            return List.of(cell.above(), beside.above());
        }
        return List.of(cell.above());
    }

    /** +1 when the creeper is towards the entrance, -1 when it is towards the depth; one off the route is at the end of it that it is nearer to (a cave opens onto the depth end). */
    private int sideOf(List<BlockPos> way, int at, Creeper creeper) {
        int creeperAt = indexOf(way, creeper.blockPosition(), ROUTE_CREEPER_SQR);
        if (creeperAt >= 0 && creeperAt != at) return Integer.signum(creeperAt - at);
        double depth = creeper.distanceToSqr(Vec3.atCenterOf(way.get(0))), entrance = creeper.distanceToSqr(Vec3.atCenterOf(way.get(way.size() - 1)));
        return entrance < depth ? 1 : -1;
    }

    /** On his route, away from the creeper's side for a few cells; null where the route ends. */
    private Vec3 alongRoute(List<BlockPos> way, int at, Creeper creeper, int cells) {
        int side = sideOf(way, at, creeper);
        int target = Math.max(0, Math.min(way.size() - 1, at - side * cells));
        return target == at ? null : Vec3.atBottomCenterOf(way.get(target));
    }

    /** Off the route: to the nearest cell of his own passage that is farther from the creeper than he is, else simply away from it. */
    private Vec3 awayFrom(List<BlockPos> way, Creeper creeper, double gap) {
        double mine = Math.max(creeper.distanceToSqr(owner), gap * gap);
        BlockPos best = null;
        double bestDistance = PASSAGE_RANGE * PASSAGE_RANGE;
        for (BlockPos cell : way) {
            double distance = owner.distanceToSqr(Vec3.atBottomCenterOf(cell));
            if (distance < bestDistance && creeper.distanceToSqr(Vec3.atBottomCenterOf(cell)) > mine) { best = cell; bestDistance = distance; }
        }
        if (best != null) return Vec3.atBottomCenterOf(best);
        return LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, creeper.position());
    }

    private static int indexOf(List<BlockPos> way, BlockPos here, int within) {
        int best = -1;
        double bestDistance = within + 1;
        for (int i = 0; i < way.size(); i++) {
            double distance = way.get(i).distSqr(here);
            if (distance <= within && distance < bestDistance) { best = i; bestDistance = distance; }
        }
        return best;
    }

    /** The goal: priority 0 beats every goal that moves him, and a pickup or an order at a higher number cannot take the turn from it. */
    static final class GuardGoal extends Goal {
        private final CompanionEntity c;

        GuardGoal(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { return c.creeperGuard.canStart(); }
        @Override public boolean canContinueToUse() { return c.creeperGuard.canContinue(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.creeperGuard.start(); }
        @Override public void tick() { c.creeperGuard.tick(); }
        @Override public void stop() { c.creeperGuard.stop(); }
    }
}
