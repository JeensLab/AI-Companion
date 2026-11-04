package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * The watch against a creeper that sees him: a goal of its own at the top priority, so that nothing (an order, a pickup, a fight)
 * keeps him from it, with no order at all.
 * <p>
 * On a long goal, armed and with health to spare, he fights a creeper with the sword like any other mob (the melee goal), and this goal
 * takes him the moment its fuse is lit, not a blow later: his pace round corners is far below a straight run's, and the fuse is 30 ticks. He
 * steps back, fast and away from it, until the fuse is out ({@link Creeper#getSwellDir}), and the melee goal brings him back to finish it.
 * Vanilla's fuse goes out beyond 7 blocks or without a line of sight, and a blast reaches 6 blocks.
 * <p>
 * The rest is the fallback, for when he cannot fight it (no sword, low health, too many retreats from it, no long goal): he walks away from a
 * creeper whose fuse is not lit, and dashes from one whose fuse is.
 */
final class CompanionCreeperGuard {
    /** How near a creeper in sight is a reason to act: its fuse starts at three blocks and a blast kills from full health inside about 2.5. */
    static final double NEAR = 9;
    private static final int LOOK_EVERY = 5, FIGHT_LOOK_EVERY = 2, PLAN_EVERY = 10, EPISODE_TICKS = 20 * 12, CALM_TICKS = 20;
    private static final int FLEE_RANGE = 12, FLEE_HEIGHT = 6;
    private static final double FLEE_SPEED = 1.3;
    /**
     * The fight. Health above which a creeper is met with the sword; the retreats from creepers he bears before he gives up the fight, and
     * how long they are remembered.
     */
    static final float FIGHT_HEALTH = 12;
    private static final int GIVE_UP = 6, FORGET_TICKS = 20 * 60;
    /** Steps away from a creeper that has not left the things he went for, before he lets them lie, and for how long. */
    private static final int FLEES_FOR_LOOT = 4, LEAVE_LOOT_TICKS = 20 * 300;
    /** The way out of a lit fuse: the fuse goes out only beyond 7 blocks (a little more for a margin); the speed of that dash. */
    static final double SAFE_GAP = 7.4;
    private static final double FIGHT_FLEE_SPEED = 1.6;
    /**
     * A fuse burns out in 30 ticks. A creeper that has kept lighting one for this long, never out for more than the gap, without blowing is not burning down: it loses sight of
     * him and gets it back, as when he bobs in water behind a block. Such a fuse is no reason to stand and wait, until it has burned this share of its length.
     */
    private static final int STALE_FUSE_TICKS = 60, FUSE_LOOK_GAP = 30;
    private static final float BURNING = .35F;
    private static final double TURN_AT = 30;
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
    private boolean active, finished, fighting, dashing;
    private long begun, lastSeen, nextLook, nextPlan, lastRetreat, nextSwing, lastFlee, fuseSince, fuseLook;
    private int retreats, flees;
    private Vec3 flee;
    private Creeper watched, fuseOf;
    private String shown = "";
    private final CompanionFuseEscape escape;

    CompanionCreeperGuard(CompanionEntity owner) { this.owner = owner; escape = new CompanionFuseEscape(owner); }

    /** True when a creeper is met with the sword: on a long goal, armed, healthy, and not after a run of retreats from it. */
    boolean fights() {
        if (!CompanionAgenda.suppressesDeeds(owner) || owner.getHealth() <= FIGHT_HEALTH || !owner.equipment.hasMeleeWeapon()) return false;
        if (retreats > 0 && owner.level().getGameTime() - lastRetreat > FORGET_TICKS) retreats = 0;
        return retreats < GIVE_UP;
    }

    boolean canStart() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return false;
        long now = level.getGameTime();
        if (now < nextLook) return false;
        nextLook = now + (fights() ? FIGHT_LOOK_EVERY : LOOK_EVERY);
        Creeper creeper = seen(level);
        watched = creeper;
        lookAtFuse(creeper, now);
        return creeper != null && plan(level, creeper);
    }

    /** A creeper that can hurt him stands in sight within this many blocks, foe or not: a pickup or a torch must not take his turn then. */
    boolean nearby(double range) {
        return !owner.level().getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(range),
                creeper -> creeper.isAlive() && !creeper.isNoAi() && creeper.distanceTo(owner) < range && owner.hasLineOfSight(creeper)).isEmpty();
    }

    /** Vanilla sets the swell direction only on its next tick after {@code ignite()}, so the flag counts as a lit fuse too. */
    static boolean lit(Creeper creeper) { return creeper.getSwellDir() > 0 || creeper.isIgnited(); }

    /** The creeper whose fuse he has seen lit, and since when it has kept lighting. */
    private void lookAtFuse(Creeper creeper, long now) {
        if (creeper == null || !lit(creeper)) return;
        if (creeper != fuseOf || now - fuseLook > FUSE_LOOK_GAP) { fuseOf = creeper; fuseSince = now; }
        fuseLook = now;
    }

    /** A lit fuse that is a reason to leave everything else: not one that has stayed lit past its time without burning down (see {@link #STALE_FUSE_TICKS}). */
    boolean burns(Creeper creeper) {
        return lit(creeper) && (creeper != fuseOf || owner.level().getGameTime() - fuseSince < STALE_FUSE_TICKS || creeper.getSwelling(0) >= BURNING);
    }

    boolean canContinue() { return active && !finished && owner.isAlive() && owner.level().getGameTime() - begun < EPISODE_TICKS; }

    void start() {
        long now = owner.level().getGameTime();
        active = true;
        finished = false;
        begun = lastSeen = now;
        nextPlan = now + PLAN_EVERY;
        owner.getNavigation().stop();
        escape.reset();
        shown = "";
        int path = -1;
        // The melee goal does not run while the guard holds the turn, so it cannot bring the sword out for it.
        if (fighting || dashing) {
            owner.selectWeaponForCombat();
            path = escape.plan((ServerLevel) owner.level(), threats((ServerLevel) owner.level()));
        }
        if (fighting) {
            retreats++;
            lastRetreat = now;
        }
        owner.workStatus(fighting ? "Крипер поджёг фитиль, отхожу, потом добью." : "Рядом крипер, ухожу от него.");
        event(fighting ? "retreat" : dashing ? "dash" : "flee", watched, path);
        if (!fighting) leaveLootAfterFlees(now);
    }

    /** A creeper that stays by the things he goes for, with nothing to meet it, would have him step away and come back for ever: the things are let lie. */
    private void leaveLootAfterFlees(long now) {
        if (now - lastFlee > FORGET_TICKS || !owner.loot.hasPending()) flees = 0;
        lastFlee = now;
        if (!owner.loot.hasPending() || ++flees < FLEES_FOR_LOOT) return;
        flees = 0;
        owner.loot.shun(now + LEAVE_LOOT_TICKS);
        owner.autonomy().report("Крипер не отходит от вещей, оставляю их там.");
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "creeper_guard", "entity", owner.getUUID(), "action", "leave_loot");
    }

    void tick() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Creeper creeper = seen(level);
        lookAtFuse(creeper, now);
        if (creeper == null) {
            if (now - lastSeen >= CALM_TICKS) finished = true;
            return;
        }
        lastSeen = now;
        owner.getLookControl().setLookAt(creeper, 30, 30);
        if (lit(creeper) && !burns(creeper) && (fighting || dashing)) event("stale_fuse", creeper, -1);
        if (fighting && !burns(creeper)) { finished = true; return; }
        if (fighting) { retreat(level, creeper); return; }
        if (dashing && burns(creeper)) { retreat(level, creeper); return; }
        if (now >= nextPlan || flee == null || dashing != burns(creeper)) {
            nextPlan = now + PLAN_EVERY;
            if (!plan(level, creeper)) { finished = true; return; }
        }
        if (flee != null) owner.getNavigation().moveTo(flee.x, flee.y, flee.z, fighting ? FIGHT_FLEE_SPEED : FLEE_SPEED);
    }

    void stop() {
        if (!active) return;
        active = fighting = dashing = false;
        escape.reset();
        flee = null;
        owner.getNavigation().stop();
        owner.work.credit(owner.level().getGameTime() - begun);
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
     * What to do about this creeper. Fighting it: whether to step back now ({@link #retreat}). With its fuse lit and no fight, the same way
     * out as in the fight (the navigation has a start to wait for and may lead past the creeper or nowhere, and the fuse is 30 ticks); else a
     * place to walk to. False when there is nothing to do.
     */
    private boolean plan(ServerLevel level, Creeper creeper) {
        flee = null;
        dashing = false;
        if (lit(creeper) && !burns(creeper)) return false;
        if (fights()) {
            if (!lit(creeper)) return false;
            fighting = true;
            return true;
        }
        if (lit(creeper)) { dashing = true; return true; }
        Vec3 target = LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, creeper.position());
        Vec3 clear = retreatSpot(threats(level), target);
        flee = clear != null ? clear : target;
        return flee != null;
    }

    /**
     * The fuse is lit and he is to be out of its sight before it blows: along the way {@link CompanionFuseEscape} finds over the floor as it
     * is, a push at a time to the next cell. Where there is none, a blow at the creeper: it throws it back, which is more than standing.
     */
    private void retreat(ServerLevel level, Creeper creeper) {
        Vec3 step = escape.step(level, threats(level));
        if (step != null) {
            mention(escape.holding() ? "hold" : "path", creeper, escape.goal());
            owner.getNavigation().stop();
            faceTowards(owner, step);
            owner.getMoveControl().setWantedPosition(step.x, step.y, step.z, FIGHT_FLEE_SPEED);
            return;
        }
        mention("strike", creeper, null);
        strike(creeper);
    }

    /** A retreat event when the way or the mode changes, not every tick. */
    private void mention(String mode, Creeper creeper, BlockPos goal) {
        String key = mode + goal;
        if (key.equals(shown)) return;
        shown = key;
        event("retreat_" + mode, creeper, escape.length(), "goal", String.valueOf(goal));
    }

    private void event(String action, Creeper creeper, int path, Object... more) {
        List<Object> fields = new ArrayList<>(List.of("entity", owner.getUUID(), "action", action, "tick", owner.level().getGameTime(), "health", owner.getHealth(), "path", path));
        if (creeper != null) fields.addAll(List.of("distance", Math.round(owner.distanceTo(creeper) * 10) / 10.0, "swell", Math.round(creeper.getSwelling(0) * 100) / 100.0));
        fields.addAll(Arrays.asList(more));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "creeper_guard", fields.toArray());
    }

    /** The move control turns him 90 degrees a tick and walks as he faces meanwhile: from facing the creeper that first step would go sideways, so he is turned to the heading at once. */
    static void faceTowards(CompanionEntity owner, Vec3 spot) {
        float yaw = (float) (Mth.atan2(spot.z - owner.getZ(), spot.x - owner.getX()) * 180 / Math.PI) - 90;
        if (Math.abs(Mth.wrapDegrees(yaw - owner.getYRot())) > TURN_AT) owner.setYRot(yaw);
    }

    private void strike(Creeper creeper) {
        if (owner.tickCount < nextSwing || !owner.isWithinMeleeAttackRange(creeper)) return;
        nextSwing = owner.tickCount + owner.combat.weaponCooldownTicks();
        owner.selectWeaponForCombat();
        owner.swing(InteractionHand.MAIN_HAND);
        owner.doHurtTarget(creeper);
    }

    /** Every creeper about that could hurt him, in sight or not: the way out of one must not lead to another. */
    private List<Creeper> threats(ServerLevel level) {
        return level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(THREAT_RANGE), creeper -> creeper.isAlive() && !creeper.isNoAi());
    }

    /**
     * The preferred place to go if the walk to it keeps clear of every creeper about; else the best place that is: farthest from the nearest
     * of them (the blast gap is enough), and one he can walk to. Null when there is none.
     */
    private Vec3 retreatSpot(List<Creeper> threats, Vec3 preferred) {
        if (preferred != null && clear(preferred, threats, GAP_SLACK)) return preferred;
        if (threats.isEmpty()) return null;
        List<Vec3> spots = new ArrayList<>();
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
        List<Vec3> ranked = spots.stream().filter(spot -> clear(spot, threats, GAP_SLACK))
                .sorted(Comparator.comparingDouble((Vec3 spot) -> -gap(spot, threats)).thenComparingDouble(owner.position()::distanceToSqr)).limit(PATH_TESTS).toList();
        for (Vec3 spot : ranked) if (reachable(spot)) return spot;
        return null;
    }

    /** The walk from where he stands to the spot passes no creeper nearer than he is to it now (or than the blast gap), give or take the slack. */
    private boolean clear(Vec3 spot, List<Creeper> threats, double slack) {
        Vec3 from = owner.position();
        for (Creeper creeper : threats) {
            double floor = Math.min(from.distanceTo(creeper.position()), SAFE_GAP);
            if (segmentDistance(from, spot, creeper.position()) < floor - slack) return false;
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
