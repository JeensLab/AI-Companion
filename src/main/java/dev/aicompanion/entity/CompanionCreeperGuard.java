package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The watch against a creeper that sees him: a goal of its own at the top priority, so that nothing (an order, a pickup, a fight)
 * keeps him from it, in any stage of the mine and with no order at all. On his own stairs or tunnel he shuts the way between them with a
 * block from the bag, a few stands off, and waits behind it; anywhere else he walks away, towards his own passage if it is farther from the
 * creeper than he is. It never strikes the creeper (see the calm in the cave episode). The block is one of the mine's temporary
 * blocks: it is taken up again when the walk reaches it ({@link CompanionExpeditionDig}) and the creeper is gone.
 */
final class CompanionCreeperGuard {
    /** How near a creeper in sight is a reason to act: its fuse starts at three blocks and a blast kills from full health inside about 2.5. */
    static final double NEAR = 9;
    private static final int LOOK_EVERY = 5, PLAN_EVERY = 10, EPISODE_TICKS = 20 * 12, CALM_TICKS = 20, PLACE_TRIES = 20;
    private static final int FLEE_CELLS = 6, SHUT_STANDS = 3, ON_ROUTE_SQR = 2, ROUTE_CREEPER_SQR = 4, PASSAGE_RANGE = 24, FLEE_RANGE = 12, FLEE_HEIGHT = 6;
    private static final double FLEE_SPEED = 1.3;

    private final CompanionEntity owner;
    private boolean active, finished, noBlock;
    private long begun, lastSeen, nextLook, nextPlan;
    private int tries;
    private List<BlockPos> cells = List.of();
    private Vec3 flee;

    CompanionCreeperGuard(CompanionEntity owner) { this.owner = owner; }

    boolean canStart() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return false;
        long now = level.getGameTime();
        if (now < nextLook) return false;
        nextLook = now + LOOK_EVERY;
        Creeper creeper = seen(level);
        noBlock = false;
        return creeper != null && plan(level, creeper);
    }

    boolean canContinue() { return active && !finished && owner.isAlive() && owner.level().getGameTime() - begun < EPISODE_TICKS; }

    void start() {
        long now = owner.level().getGameTime();
        active = true;
        finished = false;
        begun = lastSeen = now;
        nextPlan = now + PLAN_EVERY;
        tries = 0;
        owner.getNavigation().stop();
        owner.workStatus("Рядом крипер, " + (cells.isEmpty() ? "ухожу от него." : "закрываю проход блоком."));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "creeper_guard", "entity", owner.getUUID(), "action", cells.isEmpty() ? "flee" : "block", "cells", cells);
    }

    void tick() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Creeper creeper = seen(level);
        if (creeper == null) {
            if (now - lastSeen >= CALM_TICKS) finished = true;
            return;
        }
        lastSeen = now;
        owner.getLookControl().setLookAt(creeper, 30, 30);
        if (now >= nextPlan || cells.isEmpty() && flee == null) {
            nextPlan = now + PLAN_EVERY;
            if (!plan(level, creeper)) { finished = true; return; }
        }
        if (!cells.isEmpty()) { shut(); return; }
        if (flee != null) owner.getNavigation().moveTo(flee.x, flee.y, flee.z, FLEE_SPEED);
    }

    void stop() {
        if (!active) return;
        active = false;
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

    /** The nearest creeper with a way to hurt him that he can see; one with its AI off can neither walk nor swell. */
    private Creeper seen(ServerLevel level) {
        Creeper nearest = null;
        double best = Double.MAX_VALUE;
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(NEAR),
                creeper -> creeper.isAlive() && !creeper.isNoAi() && creeper.distanceTo(owner) < NEAR && owner.hasLineOfSight(creeper))) {
            double distance = creeper.distanceToSqr(owner);
            if (distance < best) { nearest = creeper; best = distance; }
        }
        return nearest;
    }

    /** What to do about this creeper: blocks to set (on his own route underground) or a place to walk to. False when there is nothing to do. */
    private boolean plan(ServerLevel level, Creeper creeper) {
        cells = List.of();
        flee = null;
        CompanionMineSite mine = owner.work.mine;
        List<BlockPos> way = mine.exists() ? CompanionExpeditionClimb.wayUp(mine) : List.of();
        int at = indexOf(way, owner.blockPosition(), ON_ROUTE_SQR);
        boolean underground = at >= 0 && !level.canSeeSky(owner.blockPosition());
        if (underground && !noBlock && CompanionSeal.available(owner) > 0) {
            cells = shutCells(mine, way, at, creeper);
            if (!cells.isEmpty()) return true;
        }
        Vec3 target = underground ? alongRoute(way, at, creeper) : awayFrom(way, creeper);
        flee = target;
        return flee != null;
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
    private Vec3 alongRoute(List<BlockPos> way, int at, Creeper creeper) {
        int side = sideOf(way, at, creeper);
        int target = Math.max(0, Math.min(way.size() - 1, at - side * FLEE_CELLS));
        return target == at ? null : Vec3.atBottomCenterOf(way.get(target));
    }

    /** Off the route: to the nearest cell of his own passage that is farther from the creeper than he is, else simply away from it. */
    private Vec3 awayFrom(List<BlockPos> way, Creeper creeper) {
        double mine = creeper.distanceToSqr(owner);
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
